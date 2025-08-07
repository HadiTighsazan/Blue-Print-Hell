package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.model.large.LargeGroupRegistry.GroupState;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.motion.KinematicsRegistry;

import java.awt.*;
import java.util.*;
import java.util.List;

public final class MergerBehavior implements SystemBehavior {

    private final SystemBoxModel      box;
    private final LargeGroupRegistry  registry;
    private final PacketLossModel     lossModel;

    // نگهداری بیت‌پکت‌ها خارج از بافر برای جلوگیری از گم شدن
    private final Map<Integer, List<BitPacket>> groupBitCollections = new HashMap<>();

    // ردیابی تعداد پکت‌های ساخته شده
    private final Map<Integer, Integer> groupMergeCount = new HashMap<>();
    private final Map<Integer, Integer> groupReportedMerged = new HashMap<>();

    // برای جلوگیری از پردازش مکرر
    private final Set<BitPacket> processedBits = Collections.newSetFromMap(new IdentityHashMap<>());

    private static final int BITS_PER_MERGE = 4;
    private static final int MERGED_PACKET_SIZE = 4;

    // مجموعه‌ی گروه‌هایی که برای merge آماده‌اند
    private final Set<Integer> readyToMerge = new LinkedHashSet<>();
    // برای ردیابی BitPacket‌هایی که در انتظار merge هستند
    private final Map<Integer, Set<BitPacket>> pendingMergeBits = new HashMap<>();

    public MergerBehavior(SystemBoxModel box,
                          LargeGroupRegistry registry,
                          PacketLossModel lossModel) {
        this.box       = Objects.requireNonNull(box, "box");
        this.registry  = Objects.requireNonNull(registry, "registry");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
    }

    @Override
    public void update(double dt) {
        // 1) میل کردن بیت‌های جدید
        checkBufferForBitPackets();
        // 2) تلاش مجدد برای merge معلق‌ها
        retryPendingMerges();

            // 3) یک‌بار sweep کن روی همه گروه‌ها – چه در لیست بیت‌ها، چه در pending
                    Set<Integer> allGs = new HashSet<>();
            allGs.addAll(groupBitCollections.keySet());
            allGs.addAll(pendingMergeBits.keySet());
            for (Integer gid : allGs) {
                   List<BitPacket> bits = groupBitCollections.getOrDefault(gid, Collections.emptyList());
                   if (bits.size() >= BITS_PER_MERGE) {
                            readyToMerge.add(gid);
                        }
                }

        // 4) حالا همه‌شان را merge کن
        drainMerges();

        // 5) (اختیاری) برای اطمینان دوباره sweep کن
        // checkAndMergePendingBits();
    }


    private void retryPendingMerges() {
        if (pendingMergeBits.isEmpty()) return;
        Set<Integer> groupIds = new HashSet<>(pendingMergeBits.keySet());
        for (Integer groupId : groupIds) {
            List<BitPacket> bits = groupBitCollections.get(groupId);
            if (bits != null && bits.size() >= BITS_PER_MERGE) {
                readyToMerge.add(groupId);
            }
        }
    }

    private void checkBufferForBitPackets() {
        Queue<PacketModel> buffer = box.getBuffer();
        if (buffer == null || buffer.isEmpty()) return;
        List<BitPacket> bitsToProcess = new ArrayList<>();
        for (PacketModel p : buffer) {
            if (p instanceof BitPacket bp && !processedBits.contains(bp)) {
                bitsToProcess.add(bp);
            }
        }
        for (BitPacket bp : bitsToProcess) {
            processBitPacket(bp);
            processedBits.add(bp);
        }
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (!(packet instanceof BitPacket bp)) return;
        int groupId = bp.getGroupId();
        Set<BitPacket> pending = pendingMergeBits.get(groupId);
        if (pending != null && pending.contains(bp)) return;
        if (processedBits.contains(bp)) return;
        processBitPacket(bp);
        processedBits.add(bp);
    }

    private void processBitPacket(BitPacket bp) {

        if (!removeFromBufferSafely(bp)) {
            return;
        }
        int groupId = bp.getGroupId();
        groupBitCollections
                .computeIfAbsent(groupId, k -> new ArrayList<>())
                .add(bp);
        GroupState st = registry.get(groupId);
        if (st == null) {
            registry.createGroupWithId(
                    groupId,
                    bp.getParentSizeUnits(),
                    bp.getParentSizeUnits(),
                    bp.getColorId()
            );
            st = registry.get(groupId);
        }
        registry.registerArrival(groupId, bp);

        // نیز اگر ≥4 بیت داریم آماده‌سازی
        if (groupBitCollections.get(groupId).size() >= BITS_PER_MERGE) {
            readyToMerge.add(groupId);
        }
    }

    /**
     * تا وقتی گروه‌های آماده داریم، merge یا flush‌شان کن
     */
    private void drainMerges() {
        Iterator<Integer> it = readyToMerge.iterator();
        while (it.hasNext()) {
            int gid = it.next();
            tryMergeGroup(gid);
            it.remove();
        }
    }

    private void tryMergeGroup(int groupId) {
        List<BitPacket> bits = groupBitCollections.get(groupId);
        if (bits == null) return;

        // صرفا بر اساس تعداد بیت‌ها merge
        while (bits.size() >= BITS_PER_MERGE) {
            List<BitPacket> toMerge = new ArrayList<>(bits.subList(0, BITS_PER_MERGE));
            LargePacket mergedPkt = createMergedPacket(toMerge);
            if (mergedPkt == null) {
                lossModel.incrementBy(BITS_PER_MERGE);
                bits.subList(0, BITS_PER_MERGE).clear();
                continue;
            }
            if (box.enqueue(mergedPkt)) {
                bits.subList(0, BITS_PER_MERGE).clear();
                Set<BitPacket> pending = pendingMergeBits.get(groupId);
                if (pending != null) {
                    toMerge.forEach(pending::remove);
                    if (pending.isEmpty()) pendingMergeBits.remove(groupId);
                }
                groupMergeCount.merge(groupId, 1, Integer::sum);
            } else {
                pendingMergeBits
                        .computeIfAbsent(groupId, k -> new HashSet<>())
                        .addAll(toMerge);
                break;
            }
        }
        // جمع باقیمانده (<4) صرفا نگهداری می‌شود
        if (bits.isEmpty() && !pendingMergeBits.containsKey(groupId)) {
            groupBitCollections.remove(groupId);
            checkAndCloseGroup(groupId);
        }
    }

    private LargePacket createMergedPacket(List<BitPacket> bits) {
        if (bits == null || bits.size() != BITS_PER_MERGE) return null;
        BitPacket first = bits.get(0);
        Color mergedColor = Color.getHSBColor(first.getColorId() / 360.0f, 0.8f, 0.9f);
        LargePacket merged = new LargePacket(
                PacketType.SQUARE,
                Config.DEFAULT_PACKET_SPEED,
                MERGED_PACKET_SIZE
        );
        merged.setCustomColor(mergedColor);
        int visual = MERGED_PACKET_SIZE * Config.PACKET_SIZE_MULTIPLIER;
        merged.setWidth(visual);
        merged.setHeight(visual);
        merged.setGroupInfo(first.getGroupId(), BITS_PER_MERGE, first.getColorId());
        KinematicsRegistry.copyProfile(first, merged);
        return merged;
    }

    private void checkAndCloseGroup(int groupId) {
        GroupState st = registry.get(groupId);
        if (st == null) return;
        int mergeCount = groupMergeCount.getOrDefault(groupId, 0);
        int totalBits = mergeCount * BITS_PER_MERGE;
        if (mergeCount > 0) {
            int reported = groupReportedMerged.getOrDefault(groupId, 0);
            int delta = totalBits - reported;
            if (delta > 0) {
                registry.registerPartialMerge(groupId, delta, MERGED_PACKET_SIZE);
                groupReportedMerged.put(groupId, totalBits);
            }
        }
        if (totalBits >= st.expectedBits) {
            registry.closeGroup(groupId);
            registry.removeGroup(groupId);
            groupMergeCount.remove(groupId);
        }
    }

    private boolean removeFromBufferSafely(PacketModel target) {
        Queue<PacketModel> buffer = box.getBuffer();
        if (buffer == null) return false;
        List<PacketModel> tmp = new ArrayList<>();
        boolean removed = false;
        PacketModel p;
        while ((p = box.pollPacket()) != null) {
            if (!removed && p == target) {
                removed = true;
            } else {
                tmp.add(p);
            }
        }
        for (PacketModel q : tmp) box.enqueue(q);
        return removed;
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        if (!enabled) {
            // ذخیره موقت داده‌ها
        } else {
            processedBits.clear();
        }
    }

    public void clear() {
        groupBitCollections.clear();
        groupMergeCount.clear();
        groupReportedMerged.clear();
        processedBits.clear();
        pendingMergeBits.clear();
        readyToMerge.clear();
    }

}

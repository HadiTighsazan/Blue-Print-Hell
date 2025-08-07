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
        // 1) پردازش بیت‌های جدید از بافر
        checkBufferForBitPackets();

        // 2) چک مجدد تمام گروه‌ها برای merge - این خط حیاتی است!
        checkAllGroupsForMerge();

        // 3) انجام merge برای گروه‌های آماده
        drainMerges();
    }

    /**
     * متد جدید: چک مستمر همه گروه‌ها برای امکان merge
     * این متد در هر update فراخوانی می‌شود و مشکل را حل می‌کند
     */
    private void checkAllGroupsForMerge() {
        // بررسی همه گروه‌هایی که بیت دارند
        for (Map.Entry<Integer, List<BitPacket>> entry : groupBitCollections.entrySet()) {
            int groupId = entry.getKey();
            List<BitPacket> bits = entry.getValue();

            // اگر 4 یا بیشتر بیت داریم و هنوز در صف merge نیست
            if (bits.size() >= BITS_PER_MERGE && !readyToMerge.contains(groupId)) {
                readyToMerge.add(groupId);
                System.out.println("Group " + groupId + " ready for merge with " + bits.size() + " bits");
            }
        }

        // همچنین pending bits را هم چک کنیم
        for (Integer groupId : pendingMergeBits.keySet()) {
            List<BitPacket> bits = groupBitCollections.get(groupId);
            if (bits != null && bits.size() >= BITS_PER_MERGE && !readyToMerge.contains(groupId)) {
                readyToMerge.add(groupId);
                System.out.println("Group " + groupId + " (from pending) ready for merge with " + bits.size() + " bits");
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

        // اضافه کردن به کلکسیون بیت‌ها
        groupBitCollections
                .computeIfAbsent(groupId, k -> new ArrayList<>())
                .add(bp);

        // ثبت در registry
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

        System.out.println("BitPacket added to group " + groupId +
                ", total bits: " + groupBitCollections.get(groupId).size());

        // بررسی فوری برای merge - اما این کافی نیست!
        if (groupBitCollections.get(groupId).size() >= BITS_PER_MERGE) {
            readyToMerge.add(groupId);
            System.out.println("Group " + groupId + " marked ready for merge immediately");
        }
    }

    /**
     * انجام merge برای گروه‌های آماده
     */
    private void drainMerges() {
        Iterator<Integer> it = readyToMerge.iterator();
        while (it.hasNext()) {
            int gid = it.next();
            it.remove(); // حذف از صف قبل از پردازش
            tryMergeGroup(gid);
        }
    }

    private void tryMergeGroup(int groupId) {
        List<BitPacket> bits = groupBitCollections.get(groupId);
        if (bits == null || bits.size() < BITS_PER_MERGE) {
            System.out.println("Group " + groupId + " not ready for merge, only " +
                    (bits != null ? bits.size() : 0) + " bits");
            return;
        }

        // merge به تعداد دفعات ممکن
        int mergeCount = 0;
        while (bits.size() >= BITS_PER_MERGE) {
            List<BitPacket> toMerge = new ArrayList<>(bits.subList(0, BITS_PER_MERGE));

            LargePacket mergedPkt = createMergedPacket(toMerge);
            if (mergedPkt == null) {
                lossModel.incrementBy(BITS_PER_MERGE);
                bits.subList(0, BITS_PER_MERGE).clear();
                System.out.println("Failed to create merged packet for group " + groupId);
                continue;
            }

            if (box.enqueue(mergedPkt)) {
                // موفقیت در merge
                bits.subList(0, BITS_PER_MERGE).clear();

                // پاکسازی pending bits
                Set<BitPacket> pending = pendingMergeBits.get(groupId);
                if (pending != null) {
                    toMerge.forEach(pending::remove);
                    if (pending.isEmpty()) {
                        pendingMergeBits.remove(groupId);
                    }
                }

                groupMergeCount.merge(groupId, 1, Integer::sum);
                mergeCount++;

                System.out.println("Successfully merged 4 bits from group " + groupId +
                        " into packet of size 4. Remaining bits: " + bits.size());
            } else {
                // بافر پر است، بیت‌ها را در pending نگه دار
                pendingMergeBits
                        .computeIfAbsent(groupId, k -> new HashSet<>())
                        .addAll(toMerge);
                System.out.println("Buffer full, keeping bits in pending for group " + groupId);
                break;
            }
        }

        // اگر هنوز بیت کافی داریم، دوباره به صف اضافه کن
        if (bits.size() >= BITS_PER_MERGE) {
            readyToMerge.add(groupId);
            System.out.println("Group " + groupId + " still has " + bits.size() +
                    " bits, re-adding to merge queue");
        }

        // اگر همه بیت‌ها پردازش شدند
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

        // کپی پروفایل حرکتی از اولین بیت
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
            System.out.println("Group " + groupId + " closed after merging " + totalBits + " bits");
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

        for (PacketModel q : tmp) {
            box.enqueue(q);
        }

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
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
        checkAndMergePendingBits();

        checkBufferForBitPackets();
        retryPendingMerges();

    }
    private void retryPendingMerges() {
        if (pendingMergeBits.isEmpty()) return;

        // کپی از کلیدها برای جلوگیری از ConcurrentModificationException
        Set<Integer> groupIds = new HashSet<>(pendingMergeBits.keySet());

        for (Integer groupId : groupIds) {
            // بررسی اینکه آیا می‌توانیم دوباره تلاش کنیم
            List<BitPacket> bits = groupBitCollections.get(groupId);
            if (bits != null && bits.size() >= BITS_PER_MERGE) {
                tryMergeGroup(groupId);
            }
        }
    }

    private void checkBufferForBitPackets() {
        Queue<PacketModel> buffer = box.getBuffer();
        if (buffer == null || buffer.isEmpty()) return;

        List<BitPacket> bitsToProcess = new ArrayList<>();

        // جمع‌آوری بیت‌پکت‌های پردازش نشده
        for (PacketModel p : buffer) {
            if (p instanceof BitPacket bp && !processedBits.contains(bp)) {
                bitsToProcess.add(bp);
            }
        }

        // پردازش هر بیت‌پکت
        for (BitPacket bp : bitsToProcess) {
            processBitPacket(bp);
            processedBits.add(bp);
        }
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (!(packet instanceof BitPacket bp)) return;

        // بررسی که آیا این بیت قبلاً پردازش شده و منتظر merge است
        int groupId = bp.getGroupId();
        Set<BitPacket> pending = pendingMergeBits.get(groupId);
        if (pending != null && pending.contains(bp)) {
            // این بیت در انتظار merge است، دوباره پردازش نمی‌کنیم
            return;
        }

        // جلوگیری از پردازش دوباره بیت‌های پردازش شده
        if (processedBits.contains(bp)) return;

        processBitPacket(bp);
        processedBits.add(bp);
    }

    private void processBitPacket(BitPacket bp) {
        System.out.println("Merger: Processing BitPacket #" + bp.getIndexInGroup() +
                " from group " + bp.getGroupId());

        // حذف فوری از بافر و انتقال به مپ
        if (!removeFromBufferSafely(bp)) {
            System.err.println("Failed to remove BitPacket from buffer!");
            return;
        }

        // اضافه به کلکسیون گروه
        int groupId = bp.getGroupId();
        groupBitCollections.computeIfAbsent(groupId, k -> new ArrayList<>()).add(bp);

        // ثبت در registry
        registry.registerArrival(groupId, bp);

        // اطمینان از وجود گروه در registry
        GroupState st = registry.get(groupId);
        if (st == null) {
            registry.createGroupWithId(groupId, bp.getParentSizeUnits(),
                    bp.getParentSizeUnits(), bp.getColorId());
        }

        System.out.println("  Group " + groupId + " now has " +
                groupBitCollections.get(groupId).size() + " bits collected");

        // تلاش برای ادغام
        tryMergeGroup(groupId);
    }

    private void tryMergeGroup(int groupId) {
        List<BitPacket> bits = groupBitCollections.get(groupId);
        if (bits == null || bits.size() < BITS_PER_MERGE) {
            return;
        }

        System.out.println("Merger: Attempting merge for group " + groupId +
                " with " + bits.size() + " bits");

        boolean mergedAny = false;

        while (bits.size() >= BITS_PER_MERGE) {
            // برداشتن 4 بیت اول
            List<BitPacket> bitsToMerge = new ArrayList<>(bits.subList(0, BITS_PER_MERGE));

            // ساخت پکت حجیم سایز 4
            LargePacket mergedPacket = createMergedPacket(bitsToMerge);

            if (mergedPacket != null) {
                // تلاش برای اضافه کردن به بافر
                boolean enqueued = box.enqueue(mergedPacket);

                if (enqueued) {
                    // موفقیت - حذف بیت‌های استفاده شده
                    bits.subList(0, BITS_PER_MERGE).clear();

                    // حذف از pending اگر بودند
                    Set<BitPacket> pending = pendingMergeBits.get(groupId);
                    if (pending != null) {
                        bitsToMerge.forEach(pending::remove);
                        if (pending.isEmpty()) {
                            pendingMergeBits.remove(groupId);
                        }
                    }

                    // ثبت آمار
                    groupMergeCount.merge(groupId, 1, Integer::sum);
                    mergedAny = true;

                    System.out.println("  Successfully merged 4 bits into size-4 packet");
                    System.out.println("  Remaining bits: " + bits.size());
                } else {
                    // بافر پر است - نگه داریم برای بعد
                    System.err.println("  Buffer full, marking bits as pending for later merge");

                    // اضافه کردن به pending list
                    pendingMergeBits.computeIfAbsent(groupId, k -> new HashSet<>())
                            .addAll(bitsToMerge);

                    // خروج از حلقه ولی نگه داشتن بیت‌ها
                    break;
                }
            } else {
                // خطا در ساخت پکت
                System.err.println("  Failed to create merged packet");
                lossModel.incrementBy(BITS_PER_MERGE);
                bits.subList(0, BITS_PER_MERGE).clear();
            }
        }

        // بررسی تکمیل گروه
        if (bits.isEmpty() && !pendingMergeBits.containsKey(groupId)) {
            groupBitCollections.remove(groupId);
            checkAndCloseGroup(groupId);
        }
    }

    private LargePacket createMergedPacket(List<BitPacket> bits) {
        if (bits == null || bits.size() != BITS_PER_MERGE) {
            return null;
        }

        // اطلاعات از اولین بیت
        BitPacket firstBit = bits.get(0);
        int groupId = firstBit.getGroupId();
        int colorId = firstBit.getColorId();
        Color mergedColor = Color.getHSBColor(colorId / 360.0f, 0.8f, 0.9f);

        System.out.println("  Creating merged packet from bits of group " + groupId);

        // ساخت پکت حجیم سایز 4
        LargePacket merged = new LargePacket(
                PacketType.SQUARE,  // نوع مربع برای پکت ادغام شده
                Config.DEFAULT_PACKET_SPEED,
                MERGED_PACKET_SIZE
        );

        // تنظیم رنگ گروه
        merged.setCustomColor(mergedColor);

        // تنظیم سایز ویژوال
        int visualSize = MERGED_PACKET_SIZE * Config.PACKET_SIZE_MULTIPLIER;
        merged.setWidth(visualSize);
        merged.setHeight(visualSize);

        // اطلاعات گروه
        merged.setGroupInfo(groupId, BITS_PER_MERGE, colorId);

        // کپی پروفایل حرکتی
        KinematicsRegistry.copyProfile(firstBit, merged);

        return merged;
    }

    private void checkAndMergePendingBits() {
        // کپی برای جلوگیری از ConcurrentModificationException
        Set<Integer> groupIds = new HashSet<>(groupBitCollections.keySet());

        for (Integer groupId : groupIds) {
            tryMergeGroup(groupId);
        }
    }

    private void checkAndCloseGroup(int groupId) {
        GroupState st = registry.get(groupId);
        if (st == null) return;

        int mergeCount = groupMergeCount.getOrDefault(groupId, 0);
        int totalProcessedBits = mergeCount * BITS_PER_MERGE;

        if (mergeCount > 0) {
            int alreadyReported = groupReportedMerged.getOrDefault(groupId, 0);
            int delta = totalProcessedBits - alreadyReported;
            if (delta > 0) {
                registry.registerPartialMerge(groupId, delta, MERGED_PACKET_SIZE);
                groupReportedMerged.put(groupId, totalProcessedBits);
            }
        }

        // بررسی تکمیل
        if (totalProcessedBits >= st.expectedBits) {
            registry.closeGroup(groupId);
            registry.removeGroup(groupId);
            groupMergeCount.remove(groupId);

            System.out.println("Group " + groupId + " fully processed. " +
                    "Created " + mergeCount + " size-4 packets from " +
                    totalProcessedBits + " bits.");
        }
    }

    private boolean removeFromBufferSafely(PacketModel target) {
        Queue<PacketModel> buffer = box.getBuffer();
        if (buffer == null) return false;

        List<PacketModel> temp = new ArrayList<>();
        boolean removed = false;

        // خالی کردن بافر
        PacketModel p;
        while ((p = box.pollPacket()) != null) {
            if (!removed && p == target) {
                removed = true; // حذف این پکت
            } else {
                temp.add(p);
            }
        }

        // بازگرداندن بقیه
        for (PacketModel q : temp) {
            box.enqueue(q);
        }

        return removed;
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        if (!enabled) {
            // ذخیره موقت داده‌ها
        } else {
            // بازیابی داده‌ها
            processedBits.clear();
        }
    }

    public void clear() {
        groupBitCollections.clear();
        groupMergeCount.clear();
        groupReportedMerged.clear();
        processedBits.clear();
        pendingMergeBits.clear();
    }


}
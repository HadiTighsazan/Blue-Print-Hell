
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

    // Map برای نگهداری بیت‌پکت‌های هر گروه
    private final Map<Integer, List<BitPacket>> groupBitCollections = new HashMap<>();

    // Map برای track کردن تعداد پکت‌های سایز 4 ساخته شده از هر گروه
    private final Map<Integer, Integer> groupMergeCount = new HashMap<>();
    private final Map<Integer, Integer> groupReportedMerged = new HashMap<>();
    // ثابت برای تعداد بیت‌های مورد نیاز برای ساخت یک پکت سایز 4
    private static final int BITS_PER_MERGE = 4;
    private static final int MERGED_PACKET_SIZE = 4;

    public MergerBehavior(SystemBoxModel box,
                          LargeGroupRegistry registry,
                          PacketLossModel lossModel) {
        this.box       = Objects.requireNonNull(box, "box");
        this.registry  = Objects.requireNonNull(registry, "registry");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
    }

    @Override
    public void update(double dt) {
        // بررسی دوره‌ای برای ادغام بیت‌پکت‌های موجود
        checkAndMergePendingBits();
    }

    @Override
    public void onPacketEnqueued(PacketModel packet) {
        onPacketEnqueued(packet, null);
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (!(packet instanceof BitPacket bp)) return;

        // بیت‌پکت را از بافر حذف کن (نباید در جریان عادی باشد)
        removeFromBuffer(packet);

        // بیت‌پکت را به کلکسیون گروه اضافه کن
        int groupId = bp.getGroupId();
        groupBitCollections.computeIfAbsent(groupId, k -> new ArrayList<>()).add(bp);
        registry.registerArrival(groupId, bp);
        // ثبت در registry
        GroupState st = registry.get(groupId);
        if (st == null) {
            // اگر گروه وجود ندارد، یکی بساز
            registry.createGroupWithId(groupId, bp.getParentSizeUnits(),
                    bp.getParentSizeUnits(), bp.getColorId());
        }

        // بررسی برای ادغام
        tryMergeGroup(groupId);
    }

    @Override
    public void onEnabledChanged(boolean enabled) {  }

    /**
     * بررسی و ادغام بیت‌پکت‌های یک گروه خاص
     */
    private void tryMergeGroup(int groupId) {
        List<BitPacket> bits = groupBitCollections.get(groupId);
        if (bits == null || bits.size() < BITS_PER_MERGE) {
            return; // هنوز به اندازه کافی بیت نداریم
        }

        // تا زمانی که حداقل 4 بیت داریم، ادغام کن
        while (bits.size() >= BITS_PER_MERGE) {
            // 4 بیت اول را بردار
            List<BitPacket> bitsToMerge = new ArrayList<>(bits.subList(0, BITS_PER_MERGE));

            // پکت حجیم سایز 4 بساز
            LargePacket mergedPacket = createMergedPacket(bitsToMerge);

            if (mergedPacket != null) {
                // پکت جدید را به بافر اضافه کن
                boolean enqueued = box.enqueue(mergedPacket);

                if (enqueued) {
                    // بیت‌های استفاده شده را حذف کن
                    bits.subList(0, BITS_PER_MERGE).clear();

                    // تعداد ادغام‌های این گروه را افزایش بده
                    groupMergeCount.merge(groupId, 1, Integer::sum);



                    System.out.println("Merged 4 bits from group " + groupId +
                            " into size-4 packet. Remaining bits: " + bits.size());
                } else {
                    // اگر نتوانستیم به بافر اضافه کنیم، loss محسوب می‌شود
                    lossModel.incrementBy(BITS_PER_MERGE);
                    bits.subList(0, BITS_PER_MERGE).clear();
                }
            } else {
                // اگر نتوانستیم پکت بسازیم، این بیت‌ها را loss در نظر بگیر
                lossModel.incrementBy(BITS_PER_MERGE);
                bits.subList(0, BITS_PER_MERGE).clear();
            }
        }

        // اگر هیچ بیتی باقی نمانده، گروه را پاک کن
        if (bits.isEmpty()) {
            groupBitCollections.remove(groupId);
            checkAndCloseGroup(groupId);
        }
    }

    /**
     * ساخت یک پکت حجیم سایز 4 از 4 بیت‌پکت
     */
    private LargePacket createMergedPacket(List<BitPacket> bits) {
        if (bits == null || bits.size() != BITS_PER_MERGE) {
            return null;
        }

        // اطلاعات مشترک را از اولین بیت بگیر
        BitPacket firstBit = bits.get(0);
        int groupId = firstBit.getGroupId();
        int colorId = firstBit.getColorId();
        Color mergedColor = Color.getHSBColor(colorId / 360.0f, 0.8f, 0.9f);

        // پکت حجیم سایز 4 بساز
        // نوع را SQUARE انتخاب می‌کنیم (یا می‌توانید از نوع اصلی استفاده کنید)
        LargePacket merged = new LargePacket(
                PacketType.SQUARE,
                Config.DEFAULT_PACKET_SPEED,
                MERGED_PACKET_SIZE  // سایز 4
        );

        // رنگ گروه را تنظیم کن
        merged.setCustomColor(mergedColor);

        // سایز ویژوال را تنظیم کن
        int visualSize = MERGED_PACKET_SIZE * Config.PACKET_SIZE_MULTIPLIER;
        merged.setWidth(visualSize);
        merged.setHeight(visualSize);

        // اطلاعات گروه را تنظیم کن (برای tracking)
        merged.setGroupInfo(groupId, BITS_PER_MERGE, colorId);

        // پروفایل حرکتی را از بیت اول کپی کن
        KinematicsRegistry.copyProfile(firstBit, merged);

        return merged;
    }

    /**
     * بررسی دوره‌ای برای گروه‌هایی که ممکن است بیت‌های معلق داشته باشند
     */
    private void checkAndMergePendingBits() {
        // کپی از keySet برای جلوگیری از ConcurrentModificationException
        Set<Integer> groupIds = new HashSet<>(groupBitCollections.keySet());

        for (Integer groupId : groupIds) {
            tryMergeGroup(groupId);
        }
    }

    /**
     * بررسی و بستن گروه در صورت تکمیل
     */
    private void checkAndCloseGroup(int groupId) {
        GroupState st = registry.get(groupId);
        if (st == null) return;

        // محاسبه تعداد کل بیت‌های پردازش شده
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
        // اگر همه بیت‌های مورد انتظار پردازش شدند
        if (totalProcessedBits >= st.expectedBits) {
            registry.closeGroup(groupId);
            registry.removeGroup(groupId);
            groupMergeCount.remove(groupId);

            System.out.println("Group " + groupId + " fully processed. " +
                    "Created " + mergeCount + " size-4 packets from " +
                    totalProcessedBits + " bits.");
        }
    }

    /**
     * حذف پکت از بافر
     */
    private void removeFromBuffer(PacketModel target) {
        Deque<PacketModel> tmp = new ArrayDeque<>();
        PacketModel p;
        boolean removed = false;
        while ((p = box.pollPacket()) != null) {
            if (!removed && p == target) {
                removed = true; // skip
            } else {
                tmp.addLast(p);
            }
        }
        for (PacketModel q : tmp) box.enqueue(q);
    }


    public void clear() {
        groupBitCollections.clear();
        groupMergeCount.clear();
        groupReportedMerged.clear();
    }

    // متدهای کمکی برای debugging و monitoring
    public int getPendingBitsCount(int groupId) {
        List<BitPacket> bits = groupBitCollections.get(groupId);
        return bits != null ? bits.size() : 0;
    }

    public int getMergeCount(int groupId) {
        return groupMergeCount.getOrDefault(groupId, 0);
    }
}
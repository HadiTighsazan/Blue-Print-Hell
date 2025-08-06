package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.motion.KinematicsProfile;
import com.blueprinthell.motion.KinematicsRegistry;

import java.awt.*;
import java.util.*;
import java.util.List;

public final class DistributorBehavior implements SystemBehavior {

    private final SystemBoxModel     box;
    private final LargeGroupRegistry registry;
    private final PacketLossModel    lossModel;
    private final Random             rnd = new Random();

    // برای جلوگیری از پردازش دوباره
    private final Set<PacketModel> processedPackets = Collections.newSetFromMap(new WeakHashMap<>());

    public DistributorBehavior(SystemBoxModel box,
                               LargeGroupRegistry registry,
                               PacketLossModel lossModel) {
        this.box       = Objects.requireNonNull(box, "box");
        this.registry  = Objects.requireNonNull(registry, "registry");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
    }

    @Override
    public void update(double dt) {
        // پردازش پکت‌های حجیم که ممکن است در بافر منتظر باشند
        processLargePacketsInBuffer();
    }

    private void processLargePacketsInBuffer() {
        Queue<PacketModel> buffer = box.getBuffer();
        if (buffer == null || buffer.isEmpty()) return;

        // جمع‌آوری پکت‌های حجیم برای پردازش
        List<LargePacket> toProcess = new ArrayList<>();
        for (PacketModel p : buffer) {
            if (p instanceof LargePacket lp && !processedPackets.contains(p)) {
                toProcess.add(lp);
            }
        }

        // پردازش هر پکت حجیم
        for (LargePacket lp : toProcess) {
            splitLarge(lp);
            processedPackets.add(lp);
        }
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (!(packet instanceof LargePacket lp)) return;

        // جلوگیری از پردازش دوباره
        if (processedPackets.contains(packet)) return;

        splitLarge(lp);
        processedPackets.add(packet);
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        if (enabled) {
            processedPackets.clear();
        }
    }

    private void splitLarge(LargePacket large) {
        int parentSize = large.getOriginalSizeUnits();
        int expectedBits = parentSize;

        System.out.println("Distributor: Splitting large packet size " + parentSize);

        int groupId;
        int colorId;
        Color groupColor;

        // مدیریت گروه
        if (!large.hasGroup()) {
             colorId = large.getColorId();
             groupColor = large.getCustomColor() != null
                                    ? large.getCustomColor()
                                    : Color.getHSBColor(colorId / 360.0f, 0.8f, 0.9f);
            groupId = registry.createGroup(parentSize, expectedBits, colorId);
            large.setGroupInfo(groupId, expectedBits, colorId);
            large.setCustomColor(groupColor);
        } else {
            groupId = large.getGroupId();
            colorId = large.getColorId();
            groupColor = large.getCustomColor();
            if (registry.get(groupId) == null) {
                registry.createGroupWithId(groupId, parentSize, expectedBits, colorId);
            }
        }

        // حذف ایمن از بافر
        if (!removeFromBuffer(large)) {
            System.err.println("Failed to remove large packet from buffer!");
            return;
        }

        int successfulBits = 0;
        int lostBits = 0;

        // ایجاد بیت‌پکت‌ها
        for (int i = 0; i < expectedBits; i++) {
            // مهم: بیت‌پکت‌ها باید نوع و سایز مناسب داشته باشند
            // از CIRCLE استفاده می‌کنیم چون سایز 1 دارد
            BitPacket bit = new BitPacket(
                    PacketType.CIRCLE,  // تغییر به CIRCLE برای سایز صحیح
                    Config.DEFAULT_PACKET_SPEED,
                    groupId,
                    parentSize,
                    i,
                    colorId);

            // تنظیم سایز صحیح برای بیت‌پکت (سایز 1)
            int bitSize = Config.PACKET_SIZE_UNITS_CIRCLE * Config.PACKET_SIZE_MULTIPLIER;
            bit.setWidth(bitSize);
            bit.setHeight(bitSize);

            // اختصاص پروفایل حرکتی پیام‌رسان
            KinematicsRegistry.setProfile(bit, randomMessengerProfile());

            // اضافه کردن به بافر
            boolean accepted = box.enqueue(bit);
            if (accepted) {
                registry.registerSplit(groupId, bit);
                successfulBits++;
                System.out.println("  Created BitPacket #" + i + " for group " + groupId);
            } else {
                lostBits++;
                System.err.println("  Failed to enqueue BitPacket #" + i);
            }
        }

        System.out.println("Distributor: Created " + successfulBits + " bits, lost " + lostBits);

        if (lostBits > 0) {
            lossModel.incrementBy(lostBits);
            registry.markBitLost(groupId, lostBits);
        }
    }

    private boolean removeFromBuffer(PacketModel target) {
        Deque<PacketModel> tmp = new ArrayDeque<>();
        PacketModel p;
        boolean removed = false;

        while ((p = box.pollPacket()) != null) {
            if (!removed && p == target) {
                removed = true; // حذف
            } else {
                tmp.addLast(p);
            }
        }

        // بازگرداندن بقیه پکت‌ها
        for (PacketModel q : tmp) {
            box.enqueue(q);
        }

        return removed;
    }

    private KinematicsProfile randomMessengerProfile() {
        int r = rnd.nextInt(3);
        switch (r) {
            case 0: return KinematicsProfile.MSG1;
            case 1: return KinematicsProfile.MSG2;
            default: return KinematicsProfile.MSG3;
        }
    }

    public void clear() {
        processedPackets.clear();
    }
}
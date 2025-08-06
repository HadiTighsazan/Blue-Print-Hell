package com.blueprinthell.test;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.systems.MergerBehavior;
import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.PortShape;
import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.model.large.LargePacket;

import java.util.Arrays;
import java.util.Queue;

public class MergerBehaviorTest {

    public static void main(String[] args) {
        System.out.println("Testing MergerBehavior with partial merging...\n");

        // ایجاد اجزای تست
        SystemBoxModel mergerBox = new SystemBoxModel(
                "test-merger",
                100, 100, Config.BOX_SIZE, Config.BOX_SIZE,
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE),
                Arrays.asList(PortShape.CIRCLE)
        );

        LargeGroupRegistry registry = new LargeGroupRegistry();
        PacketLossModel lossModel = new PacketLossModel();

        MergerBehavior merger = new MergerBehavior(mergerBox, registry, lossModel);

        // شبیه‌سازی یک پکت حجیم سایز 10 که به 10 بیت‌پکت تقسیم شده
        int groupId = 1;
        int originalSize = 10;
        int colorId = 180; // رنگ سبز

        // ثبت گروه در registry
        registry.createGroupWithId(groupId, originalSize, originalSize, colorId);

        System.out.println("Creating 10 BitPackets from a size-10 LargePacket...\n");

        // ایجاد و ارسال 10 بیت‌پکت به merger
        for (int i = 0; i < 10; i++) {
            BitPacket bit = new BitPacket(
                    PacketType.SQUARE,
                    Config.DEFAULT_PACKET_SPEED,
                    groupId,
                    originalSize,
                    i,
                    colorId
            );

            System.out.println("Sending BitPacket #" + i + " to merger...");

            // ابتدا به بافر اضافه کن
            mergerBox.enqueue(bit);

            // سپس onPacketEnqueued را صدا بزن (شبیه‌سازی ورود واقعی)
            merger.onPacketEnqueued(bit, null);

            // بررسی بافر
            checkBuffer(mergerBox, i + 1);
        }

        System.out.println("\n=== Final Results ===");
        System.out.println("Buffer size: " + mergerBox.getBuffer().size());
        System.out.println("Expected: 2 size-4 packets + 2 remaining bits");
        System.out.println("Loss count: " + lossModel.getLostCount());

        // نمایش محتویات بافر
        System.out.println("\nBuffer contents:");
        Queue<PacketModel> buffer = mergerBox.getBuffer();
        int largeCount = 0;
        int bitCount = 0;

        for (PacketModel p : buffer) {
            if (p instanceof LargePacket lp) {
                System.out.println("  - LargePacket size " + lp.getOriginalSizeUnits());
                largeCount++;
            } else if (p instanceof BitPacket bp) {
                System.out.println("  - BitPacket #" + bp.getIndexInGroup());
                bitCount++;
            }
        }

        System.out.println("\nSummary: " + largeCount + " LargePackets, " + bitCount + " BitPackets");

        // بررسی وضعیت گروه
        System.out.println("\n" + registry.getGroupStatus(groupId));
    }

    private static void checkBuffer(SystemBoxModel box, int bitsSent) {
        int expectedMergedPackets = bitsSent / 4;
        int expectedRemainingBits = bitsSent % 4;

        System.out.println("  After " + bitsSent + " bits: Buffer has " +
                box.getBuffer().size() + " items " +
                "(expected: " + expectedMergedPackets + " merged + " +
                expectedRemainingBits + " bits)");
    }
}
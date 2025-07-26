package com.blueprinthell.test;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.systems.*;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ManualPhase2Tests {

    public static void main(String[] args) {
        // for deterministic MaliciousBehavior (though we're not re‐testing that here)
        Config.TROJAN_PROBABILITY = 1.0;

        run("5. SpyBehavior", ManualPhase2Tests::testSpyBehavior);
        run("6. VpnBehavior", ManualPhase2Tests::testVpnBehavior);
        run("7. AntiTrojanBehavior", ManualPhase2Tests::testAntiTrojanBehavior);
        run("8. DistributorBehavior", ManualPhase2Tests::testDistributorBehavior);
        run("9. MergerBehavior", ManualPhase2Tests::testMergerBehavior);
    }

    private static void run(String title, Runnable test) {
        System.out.println("\n--- " + title + " ---");
        test.run();
        System.out.println("--- END " + title + " ---");
    }

    /**
     * 5) SpyBehavior: normal packets pass through, ConfidentialPacket’s are dropped.
     */
    private static void testSpyBehavior() {
        // set up box + registry + loss counter
        SystemBoxModel box = new SystemBoxModel(
                0, 0, 100, 100,
                1, PortShape.SQUARE,
                1, PortShape.SQUARE
        );
        BehaviorRegistry registry = new BehaviorRegistry();
        PacketLossModel loss = new PacketLossModel();
        SpyBehavior spy = new SpyBehavior(box, registry, loss);
        registry.register(box, spy);

        // normal packet
        PacketModel p1 = new PacketModel(PacketType.SQUARE, 50.0);
        box.enqueue(p1);
        spy.onPacketEnqueued(p1, null);
        PacketModel out1 = box.pollPacket();
        System.out.println("Normal passed? " + (out1 == p1));

        // confidential packet
        PacketModel p2 = new ConfidentialPacket(PacketType.SQUARE, 50.0);
        box.enqueue(p2);
        spy.onPacketEnqueued(p2, null);
        PacketModel out2 = box.pollPacket();
        System.out.println("Confidential dropped? "
                + (out2 == null && loss.getLostCount() == 1));
    }

    /**
     * 6) VpnBehavior: every incoming packet is wrapped into a ProtectedPacket.
     */
    private static void testVpnBehavior() {
        SystemBoxModel box = new SystemBoxModel(
                0, 0, 100, 100,
                1, PortShape.SQUARE,
                1, PortShape.SQUARE
        );
        VpnBehavior vpn = new VpnBehavior(box);

        PacketModel p = new PacketModel(PacketType.SQUARE, 60.0);
        box.enqueue(p);
        vpn.onPacketEnqueued(p, null);

        PacketModel out = box.pollPacket();
        boolean wrapped = out instanceof ProtectedPacket;
        System.out.println("Wrapped to Protected? " + wrapped
                + ", type=" + (out != null ? out.getType() : "null"));
    }

    /**
     * 7) AntiTrojanBehavior: first TrojanPacket converts to Messenger, second is in cooldown.
     */
    /** 7) AntiTrojanBehavior:
     *    - اولین پکت تروجان باید تبدیل شود
     *    - دومین پکت تروجان در دورهٔ cooldown نباید تبدیل شود
     */
    private static void testAntiTrojanBehavior() {
        // شبیه‌سازی باکسی با یک پورت ورودی/خروجی
        SystemBoxModel box = new SystemBoxModel(
                0, 0, 100, 100,
                1, PortShape.SQUARE,
                1, PortShape.SQUARE
        );
        // هیچ سیمی لازم نیست برای این تست
        List<WireModel> wires = Collections.emptyList();
        AntiTrojanBehavior anti = new AntiTrojanBehavior(box, wires);

        // 7a) تست تبدیل اول
        PacketModel t1 = TrojanPacket.wrap(
                new PacketModel(PacketType.SQUARE, Config.DEFAULT_PACKET_SPEED)
        );
        box.enqueue(t1);
        anti.onPacketEnqueued(t1, null);
        PacketModel r1 = box.pollPacket();
        boolean firstConverted =
                (r1 != null) &&
                        (r1.getType() == PacketType.SQUARE) &&
                        !(r1 instanceof TrojanPacket);
        System.out.println("First Trojan → Messenger conversion? " + firstConverted);

        // 7b) تست سرکوب تبدیل دوم در دورهٔ cooldown
        PacketModel t2 = TrojanPacket.wrap(
                new PacketModel(PacketType.SQUARE, Config.DEFAULT_PACKET_SPEED)
        );
        box.enqueue(t2);
        anti.onPacketEnqueued(t2, null);
        PacketModel r2 = box.pollPacket();
        boolean secondSuppressed = (r2 instanceof TrojanPacket);
        System.out.println("Second conversion suppressed by cooldown? " + secondSuppressed);
    }


    /**
     * 8) DistributorBehavior: a LargePacket(8) splits into 8 BitPackets.
     */
    private static void testDistributorBehavior() {
        UnlimitedBox box = new UnlimitedBox();
        LargeGroupRegistry registry = new LargeGroupRegistry();
        PacketLossModel loss = new PacketLossModel();
        DistributorBehavior dist = new DistributorBehavior(box, registry, loss);

        // create a LargePacket of size 8
        LargePacket bulk = new LargePacket(
                PacketType.SQUARE,              // type arbitrary
                Config.DEFAULT_PACKET_SPEED,
                8                               // originalSizeUnits
        );
        box.enqueue(bulk);
        dist.onPacketEnqueued(bulk, null);

        // count how many BitPackets came out
        int count = 0;
        PacketModel pkt;
        while ((pkt = box.pollPacket()) != null) {
            if (pkt instanceof BitPacket) count++;
        }
        System.out.println("BitPackets produced: " + count + " (expected 8)");
    }

    // 9) MergerBehavior: feeding all BitPackets back should restore the original LargePacket
// Added explicit update(dt) call to flush any pending merge logic
    private static void testMergerBehavior() {
        UnlimitedBox box = new UnlimitedBox();
        LargeGroupRegistry registry = new LargeGroupRegistry();
        PacketLossModel loss = new PacketLossModel();
        DistributorBehavior dist = new DistributorBehavior(box, registry, loss);
        MergerBehavior merge = new MergerBehavior(box, registry, loss);

        // 1) تقسیم Bulk
        LargePacket bulk = new LargePacket(PacketType.SQUARE, Config.DEFAULT_PACKET_SPEED, 8);
        box.enqueue(bulk);
        dist.onPacketEnqueued(bulk, null);

        // 2) جمع بیت‌ها در یک لیست
        List<BitPacket> bits = new ArrayList<>();
        PacketModel pkt;
        while ((pkt = box.pollPacket()) != null) {
            if (pkt instanceof BitPacket bp) {
                bits.add(bp);
            }
        }

        // 3) دوباره در بافر قرار بده و ثبت کن
        for (BitPacket b : bits) {
            box.enqueue(b);
            merge.onPacketEnqueued(b, null);
        }

        // 4) شبیه‌سازی یک update تا ادغام کامل شود
        merge.update(1.0);

        // 5) حالا باید LargePacket بازیابی‌شده باشد
        PacketModel out = box.pollPacket();
        int restoredSize = (out instanceof LargePacket)
                ? ((LargePacket) out).getOriginalSizeUnits()
                : -1;
        System.out.println("Merger restored size=" + restoredSize + " (expected 8)");
    }

}

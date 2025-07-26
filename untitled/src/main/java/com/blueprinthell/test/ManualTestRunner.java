package com.blueprinthell.test;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PacketType;
import com.blueprinthell.motion.MotionStrategy;
import com.blueprinthell.motion.MotionStrategyFactory;
import com.blueprinthell.model.PacketOps;
import com.blueprinthell.model.TrojanPacket;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.PortShape;
import com.blueprinthell.controller.systems.MaliciousBehavior;
import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.large.LargeGroupRegistry;

import java.util.Arrays;
import java.util.List;

/**
 * A simple manual test‐runner that exercises:
 * 1) MotionStrategyFactory
 * 2) PacketOps (toTrojan / unwrapTrojan) with PacketType checks
 * 3) MaliciousBehavior (noise + trojan conversion) with PacketType checks
 * 4) LargeGroupRegistry.computePartialLoss(...)
 *
 * Run with:
 *   javac -cp .;... ManualTestRunner.java
 *   java -cp .;... com.blueprinthell.test.ManualTestRunner
 */
public class ManualTestRunner {

    public static void main(String[] args) {
        // deterministic MaliciousBehavior tests
        Config.TROJAN_PROBABILITY = 1.0;

        runScenario("1. MotionStrategyFactory", ManualTestRunner::testMotionStrategy);
        runScenario("2. PacketOps (Trojan wrap/unwarp)", ManualTestRunner::testPacketOps);
        runScenario("3. MaliciousBehavior (noise & trojan)", ManualTestRunner::testMaliciousBehavior);
        runScenario("4. LargeGroupRegistry.computePartialLoss", ManualTestRunner::testComputePartialLoss);

        System.out.println("\n=== ALL SCENARIOS RUN ===");
    }

    private static void runScenario(String title, Runnable test) {
        System.out.println("\n--- " + title + " ---");
        test.run();
        System.out.println("--- END OF " + title + " ---");
    }

    /** 1) Verify MotionStrategy selection for compatible vs incompatible ports. */
    private static void testMotionStrategy() {
        PacketModel pkt = new PacketModel(PacketType.SQUARE, 100.0);
        MotionStrategy s1 = MotionStrategyFactory.create(pkt, true);
        System.out.println("Compatible SQUARE → " + s1.getClass().getSimpleName());
        MotionStrategy s2 = MotionStrategyFactory.create(pkt, false);
        System.out.println("Incompatible SQUARE → " + s2.getClass().getSimpleName());

        PacketModel tkt = new PacketModel(PacketType.TRIANGLE, 120.0);
        MotionStrategy s3 = MotionStrategyFactory.create(tkt, true);
        System.out.println("Compatible TRIANGLE → " + s3.getClass().getSimpleName());
        MotionStrategy s4 = MotionStrategyFactory.create(tkt, false);
        System.out.println("Incompatible TRIANGLE → " + s4.getClass().getSimpleName());
    }

    /** 2) Wrap to Trojan and unwrap, checking PacketType. */
    private static void testPacketOps() {
        PacketModel pkt = new PacketModel(PacketType.SQUARE, 80.0);
        PacketModel trojan = PacketOps.toTrojan(pkt);
        System.out.println("toTrojan returned TrojanPacket? " + (trojan instanceof TrojanPacket)
                + " (noise=" + trojan.getNoise() + ")");

        PacketModel unwrapped = PacketOps.unwrapTrojan(trojan);
        System.out.println("unwrapTrojan returned PacketType = " + unwrapped.getType()
                + " (noise=" + unwrapped.getNoise() + ")");
    }

    /** 3) Enqueue into SystemBoxModel and apply MaliciousBehavior, checking PacketType. */
    private static void testMaliciousBehavior() {
        SystemBoxModel box = new SystemBoxModel(
                0, 0,
                100, 100,
                1, PortShape.SQUARE,
                1, PortShape.SQUARE
        );
        PacketLossModel loss = new PacketLossModel();
        MaliciousBehavior mal = new MaliciousBehavior(box, Config.TROJAN_PROBABILITY);

        PacketModel pkt = new PacketModel(PacketType.SQUARE, 90.0);
        pkt.setNoise(0.0);
        box.enqueue(pkt);
        mal.onPacketEnqueued(pkt, null);

        PacketModel result = box.pollPacket();
        System.out.println("After MaliciousBehavior, buffer gave PacketType = "
                + result.getType() + ", noise=" + result.getNoise());

        PacketModel unwrapped = PacketOps.unwrapTrojan(result);
        System.out.println(" unwrapTrojan(result) → PacketType = "
                + unwrapped.getType() + ", noise=" + unwrapped.getNoise());
    }

    /** 4) Test static computePartialLoss formula in LargeGroupRegistry. */
    private static void testComputePartialLoss() {
        List<Integer> parts1 = Arrays.asList(5);
        int loss1 = LargeGroupRegistry.computePartialLoss(8, parts1);
        System.out.println("computePartialLoss(8,[5]) = " + loss1 + "  (expected 3)");

        List<Integer> parts2 = Arrays.asList(3, 5);
        int loss2 = LargeGroupRegistry.computePartialLoss(8, parts2);
        System.out.println("computePartialLoss(8,[3,5]) = " + loss2 + "  (expected 1)");
    }
}

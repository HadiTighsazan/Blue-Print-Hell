package com.blueprinthell.test;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.ConfidentialThrottleController;
import com.blueprinthell.controller.PacketDispatcherController;
import com.blueprinthell.controller.PacketRouterController;
import com.blueprinthell.controller.SimulationController;
import com.blueprinthell.model.*;
import com.blueprinthell.motion.MotionStrategy;
import com.blueprinthell.motion.MotionStrategyFactory;

import java.util.*;

/**
 * Manual tests for phase‑2 packet mechanics.
 * اجرا: java com.blueprinthell.test.ManualPhase2MechanicsTest
 */
public class ManualPhase2MechanicsTest {

    public static void main(String[] args) {
        testLongWireAcceleration();
        testSystemDisableOnHighSpeedEntry();
        testConfidentialPacketCoexistence();
        testFallbackReturnOnDisabledSystem();
        testPortFilteringInRouter();
    }

    /** 1) Long‑Wire Acceleration */
    private static void testLongWireAcceleration() {
        System.out.println("=== Long‑Wire Acceleration ===");
        PacketModel pkt = new PacketModel(PacketType.TRIANGLE, Config.MSG3_INCOMPAT_V0);
        PortModel srcPort = new PortModel(0, 0, pkt.getType().toPortShape(), false);
        PortModel dstPort = new PortModel((int)(Config.MSG3_INCOMPAT_V0 * 10), 0, PortShape.SQUARE, true);
        WireModel wire = new WireModel(srcPort, dstPort);
        pkt.attachToWire(wire, 0.0);
        MotionStrategy strat = MotionStrategyFactory.create(pkt, false);

        double initial = pkt.getSpeed();
        strat.update(pkt, 1.0);
        double after1 = pkt.getSpeed();
        strat.update(pkt, 1.0);
        double after2 = pkt.getSpeed();

        System.out.println("Initial speed: " + initial);
        System.out.println("Speed after 1s: " + after1);
        System.out.println("Speed after 2s: " + after2);
        System.out.println("Expected: after1 > initial, after2 >= after1");
        System.out.println();
    }

    /** 2) System Disable on High‑Speed Entry */
    private static void testSystemDisableOnHighSpeedEntry() {
        System.out.println("=== System Disable on High‑Speed Entry ===");
        // Setup system and dispatcher
        SystemBoxModel dest = new SystemBoxModel(
                100, 0, Config.SYSTEM_WIDTH, Config.SYSTEM_HEIGHT,
                1, PortShape.SQUARE, 1, PortShape.SQUARE
        );
        PortModel srcPort = new PortModel(0, 0, PortShape.SQUARE, false);
        PortModel dstPort = dest.getInPorts().get(0);
        WireModel wire = new WireModel(srcPort, dstPort);

        CoinModel coinModel = new CoinModel();
        PacketLossModel lossModel = new PacketLossModel();
        PacketDispatcherController dispatcher = new PacketDispatcherController(
                List.of(wire), Map.of(wire, dest), coinModel, lossModel
        );

        PacketModel fast = new PacketModel(PacketType.SQUARE, Config.MAX_ALLOWED_SPEED * 1.1);
        wire.attachPacket(fast, 1.0);

        System.out.println("Initially enabled: " + dest.isEnabled());
        dispatcher.update(0.1);
        System.out.println("After dispatch: enabled = " + dest.isEnabled());

        // Simulate time passing for disable duration
        dest.update(Config.SYSTEM_DISABLE_DURATION + 0.1);
        System.out.println("After " + (Config.SYSTEM_DISABLE_DURATION + 0.1) + "s: enabled = " + dest.isEnabled());
        System.out.println("Expected: true -> false -> true");
        System.out.println();
    }

    /** 3) Confidential Packet Coexistence */
    private static void testConfidentialPacketCoexistence() {
        System.out.println("=== Confidential Packet Coexistence ===");
        SystemBoxModel dest = new SystemBoxModel(
                0, 0, Config.SYSTEM_WIDTH, Config.SYSTEM_HEIGHT,
                List.of(PortShape.SQUARE), List.of(PortShape.SQUARE)
        );
        PortModel srcPort = new PortModel(0, 0, PortShape.SQUARE, false);
        PortModel dstPort = dest.getInPorts().get(0);
        WireModel wire = new WireModel(srcPort, dstPort);

        PacketModel normal = new PacketModel(PacketType.SQUARE, Config.DEFAULT_PACKET_SPEED);
        dest.enqueue(normal);
        System.out.println("Dest buffer initially full: " + !dest.getBuffer().isEmpty());

        ConfidentialPacket cp = new ConfidentialPacket(PacketType.SQUARE, Config.CONF_SPEED);
        wire.attachPacket(cp, 0.0);
        Map<WireModel, SystemBoxModel> destMap = Map.of(wire, dest);
        ConfidentialThrottleController thr = new ConfidentialThrottleController(
                List.of(wire), destMap
        );
        thr.update(0.1);
        System.out.println("Confidential speed after throttle: " + cp.getSpeed());
        System.out.println("Expected: " + Config.CONF_SLOW_SPEED);
        System.out.println();
    }

    /** 4) Fallback Return on Disabled System */
    private static void testFallbackReturnOnDisabledSystem() {
        System.out.println("=== Fallback Return on Disabled System ===");
        SimulationController sim = new SimulationController(10);

        SystemBoxModel dest = new SystemBoxModel(
                0, 0, Config.SYSTEM_WIDTH, Config.SYSTEM_HEIGHT,
                1, PortShape.SQUARE, 1, PortShape.SQUARE
        );
        PortModel dstPort = dest.getInPorts().get(0);
        sim.registerSystemPort(dest, dstPort);
        WireModel.setSimulationController(sim);

        PortModel srcPort = new PortModel(0, 0, PortShape.SQUARE, false);
        WireModel wire = new WireModel(srcPort, dstPort);

        ConfidentialPacket pkt = new ConfidentialPacket(PacketType.SQUARE, Config.DEFAULT_PACKET_SPEED);
        wire.attachPacket(pkt, 0.5);

        dest.disable();
        System.out.println("Before update, progress = " + pkt.getProgress());
        wire.update(0.1);
        System.out.println("After update, progress = " + pkt.getProgress());
        System.out.println("Expected: progress < 0.5");
        System.out.println();
    }

    /** 5) Port‑Filtering in Router */
    private static void testPortFilteringInRouter() {
        System.out.println("=== Port‑Filtering in Router ===");
        List<PortShape> inShapes = List.of(PortShape.SQUARE);
        List<PortShape> outShapes = List.of(PortShape.SQUARE, PortShape.TRIANGLE);
        SystemBoxModel srcBox = new SystemBoxModel(
                0, 0, Config.SYSTEM_WIDTH, Config.SYSTEM_HEIGHT,
                inShapes, outShapes
        );
        PacketLossModel lossModel = new PacketLossModel();

        List<PortModel> outs = srcBox.getOutPorts();
        PortModel out1 = outs.get(0), out2 = outs.get(1);
        SystemBoxModel dest1 = new SystemBoxModel(
                200, 0, Config.SYSTEM_WIDTH, Config.SYSTEM_HEIGHT,
                List.of(PortShape.SQUARE), List.of(PortShape.SQUARE)
        );
        SystemBoxModel dest2 = new SystemBoxModel(
                300, 0, Config.SYSTEM_WIDTH, Config.SYSTEM_HEIGHT,
                List.of(PortShape.TRIANGLE), List.of(PortShape.TRIANGLE)
        );
        dest2.disable();

        WireModel w1 = new WireModel(out1, dest1.getInPorts().get(0));
        WireModel w2 = new WireModel(out2, dest2.getInPorts().get(0));
        Map<WireModel, SystemBoxModel> destMap = Map.of(w1, dest1, w2, dest2);
        PacketRouterController router = new PacketRouterController(
                srcBox, List.of(w1, w2), destMap, lossModel
        );

        PacketModel pkt = new PacketModel(PacketType.SQUARE, Config.DEFAULT_PACKET_SPEED);
        srcBox.enqueue(pkt);
        router.update(0.1);

        System.out.println("Packets on w1: " + w1.getPackets().size());
        System.out.println("Packets on w2: " + w2.getPackets().size());
        System.out.println("Expected: only on w1");
        System.out.println();
    }
}

package com.blueprinthell.test;

import com.blueprinthell.controller.systems.*;
import com.blueprinthell.model.*;
import com.blueprinthell.config.Config;
import java.util.*;

public class SpySystemTest {

    public static void main(String[] args) {
        System.out.println("=== SPY SYSTEM TELEPORT TEST ===\n");

        // Setup
        PacketLossModel lossModel = new PacketLossModel();
        List<WireModel> wires = new ArrayList<>();
        Map<WireModel, SystemBoxModel> destMap = new HashMap<>();
        BehaviorRegistry registry = new BehaviorRegistry();

        // Create 3 spy systems
        SystemBoxModel spy1 = createSpySystem("SPY-1", 100, 100, registry, lossModel, wires, destMap);
        SystemBoxModel spy2 = createSpySystem("SPY-2", 300, 100, registry, lossModel, wires, destMap);
        SystemBoxModel spy3 = createSpySystem("SPY-3", 500, 100, registry, lossModel, wires, destMap);

        // Create a normal destination system
        SystemBoxModel normalDest = createNormalSystem("DEST", 700, 100);

        // Connect spy systems to destination with wires
        connectSystems(spy2, normalDest, wires, destMap);
        connectSystems(spy3, normalDest, wires, destMap);

        System.out.println("Setup complete:");
        System.out.println("- 3 Spy systems created");
        System.out.println("- SPY-2 and SPY-3 connected to destination");
        System.out.println("- SPY-1 will receive packets\n");

        // Test 1: Normal packet teleport
        System.out.println("TEST 1: Normal Packet Teleport");
        System.out.println("-------------------------------");

        PacketModel normalPacket = new PacketModel(PacketType.SQUARE, Config.DEFAULT_PACKET_SPEED);
        PortModel inputPort = spy1.getInPorts().get(0);

        System.out.println("Enqueueing normal packet to SPY-1...");
        spy1.enqueue(normalPacket, inputPort);

        // Simulate update cycle
        simulateUpdate(spy1, 0.1);

        // Check results
        System.out.println("\nResults:");
        System.out.println("SPY-1 buffer size: " + spy1.getBuffer().size());
        System.out.println("SPY-2 buffer size: " + spy2.getBuffer().size());
        System.out.println("SPY-3 buffer size: " + spy3.getBuffer().size());

        SpyBehavior spy1Behavior = getSpyBehavior(spy1, registry);
        if (spy1Behavior != null) {
            spy1Behavior.printStats();
        }

        System.out.println("\n-----------------------------------\n");

        // Test 2: Confidential packet destruction
        System.out.println("TEST 2: Confidential Packet Destruction");
        System.out.println("----------------------------------------");

        ConfidentialPacket confPacket = ConfidentialPacket.wrap(
                new PacketModel(PacketType.CIRCLE, Config.DEFAULT_PACKET_SPEED)
        );

        System.out.println("Enqueueing confidential packet to SPY-1...");
        spy1.enqueue(confPacket, inputPort);

        simulateUpdate(spy1, 0.1);

        System.out.println("\nResults:");
        System.out.println("SPY-1 buffer size: " + spy1.getBuffer().size());
        System.out.println("Packets lost: " + lossModel.getLostCount());

        if (spy1Behavior != null) {
            spy1Behavior.printStats();
        }

        System.out.println("\n-----------------------------------\n");

        // Test 3: Multiple packets
        System.out.println("TEST 3: Multiple Packets");
        System.out.println("-------------------------");

        for (int i = 0; i < 5; i++) {
            PacketModel packet = new PacketModel(
                    i % 2 == 0 ? PacketType.SQUARE : PacketType.TRIANGLE,
                    Config.DEFAULT_PACKET_SPEED
            );
            spy1.enqueue(packet, inputPort);
            System.out.println("Added packet " + (i+1) + ": " + packet.getType());
        }

        // Multiple update cycles
        for (int i = 0; i < 5; i++) {
            simulateUpdate(spy1, 0.1);
            simulateUpdate(spy2, 0.1);
            simulateUpdate(spy3, 0.1);
        }

        System.out.println("\nFinal Results:");
        System.out.println("SPY-1 buffer: " + spy1.getBuffer().size());
        System.out.println("SPY-2 buffer: " + spy2.getBuffer().size());
        System.out.println("SPY-3 buffer: " + spy3.getBuffer().size());

        if (spy1Behavior != null) {
            spy1Behavior.printStats();
        }

        System.out.println("\n=== TEST COMPLETE ===");
    }

    private static SystemBoxModel createSpySystem(String id, int x, int y,
                                                  BehaviorRegistry registry,
                                                  PacketLossModel lossModel,
                                                  List<WireModel> wires,
                                                  Map<WireModel, SystemBoxModel> destMap) {
        SystemBoxModel box = new SystemBoxModel(
                id, x, y, 96, 96,
                Arrays.asList(PortShape.SQUARE, PortShape.CIRCLE),
                Arrays.asList(PortShape.TRIANGLE, PortShape.SQUARE)

        );
        box.setPrimaryKind(SystemKind.SPY);

        SpyBehavior spy = new SpyBehavior(box, registry, lossModel, wires, destMap);
        registry.register(box, spy);
        box.addBehavior(spy);

        return box;
    }

    private static SystemBoxModel createNormalSystem(String id, int x, int y) {
        SystemBoxModel box = new SystemBoxModel(
                id, x, y, 96, 96,
                Arrays.asList(PortShape.TRIANGLE, PortShape.SQUARE),
                Arrays.asList(PortShape.CIRCLE)

        );
        box.setPrimaryKind(SystemKind.NORMAL);
        return box;
    }

    private static void connectSystems(SystemBoxModel src, SystemBoxModel dst,
                                       List<WireModel> wires,
                                       Map<WireModel, SystemBoxModel> destMap) {
        if (src.getOutPorts().isEmpty() || dst.getInPorts().isEmpty()) {
            System.out.println("Cannot connect: missing ports");
            return;
        }

        for (PortModel srcPort : src.getOutPorts()) {
            for (PortModel dstPort : dst.getInPorts()) {
                if (srcPort.getShape() == dstPort.getShape()) {
                    WireModel wire = new WireModel(srcPort, dstPort);
                    wires.add(wire);
                    destMap.put(wire, dst);
                    System.out.println("Connected " + src.getId() + "(" + srcPort.getShape() + ") -> "
                            + dst.getId() + "(" + dstPort.getShape() + ")");
                }
            }
        }

    }

    private static void simulateUpdate(SystemBoxModel box, double dt) {
        // Simulate the update cycle
        box.update(dt);

        // Process behaviors
        for (SystemBehavior behavior : box.getBehaviors()) {
            behavior.update(dt);
        }
    }

    private static SpyBehavior getSpyBehavior(SystemBoxModel box, BehaviorRegistry registry) {
        List<SystemBehavior> behaviors = registry.get(box);
        if (behaviors != null) {
            for (SystemBehavior b : behaviors) {
                if (b instanceof SpyBehavior) {
                    return (SpyBehavior) b;
                }
            }
        }
        return null;
    }
}
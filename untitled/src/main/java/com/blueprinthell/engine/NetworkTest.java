// NetworkManualTest.java
package com.blueprinthell.engine;

import com.blueprinthell.model.Packet;
import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.SystemBox;
import com.blueprinthell.model.Wire;
import com.blueprinthell.engine.NetworkController;

import java.util.Arrays;


public class NetworkTest {
    public static void main(String[] args) {
        System.out.println("=== Scenario 1: Single Packet Transit ===");
        scenarioSingleTransit();
        System.out.println();
        System.out.println("=== Scenario 2: Buffer Overflow ===");
        scenarioBufferOverflow();
    }

    private static void scenarioSingleTransit() {
        SystemBox A = new SystemBox(0,0,0,0,0,1);
        SystemBox B = new SystemBox(0,0,0,0,1,1);
        SystemBox C = new SystemBox(0,0,0,0,1,0);
        Wire w1 = new Wire(A.getOutPorts().get(0), B.getInPorts().get(0));
        Wire w2 = new Wire(B.getOutPorts().get(0), C.getInPorts().get(0));
        NetworkController ctrl = new NetworkController(Arrays.asList(w1,w2), Arrays.asList(A,B,C));

        Packet pkt = new Packet(PacketType.SQUARE, 100);
        w1.attachPacket(pkt, 0.0);

        double dt1 = w1.getLength() / pkt.getSpeed() + 0.01;
        ctrl.tick(dt1);
        System.out.println("After A->B: coins=" + ctrl.getCoins() + ", packetLoss=" + ctrl.getPacketLoss());

        double dt2 = w2.getLength() / pkt.getSpeed() + 0.01;
        ctrl.tick(dt2);
        System.out.println("After B->C: coins=" + ctrl.getCoins() + ", packetLoss=" + ctrl.getPacketLoss());
    }

    private static void scenarioBufferOverflow() {
        SystemBox A = new SystemBox(0,0,0,0,0,1);
        SystemBox B = new SystemBox(0,0,0,0,1,0);
        Wire w1 = new Wire(A.getOutPorts().get(0), B.getInPorts().get(0));
        NetworkController ctrl = new NetworkController(Arrays.asList(w1), Arrays.asList(A,B));

        for (int i = 0; i < 6; i++) {
            Packet p = new Packet(PacketType.SQUARE, 100);
            w1.attachPacket(p, 0.0);
        }

        double dt = w1.getLength() / 100 + 0.01;
        ctrl.tick(dt);
        System.out.println("After overflow test: coins=" + ctrl.getCoins() + ", packetLoss=" + ctrl.getPacketLoss());
    }
}

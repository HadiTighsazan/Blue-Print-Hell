package com.blueprinthell.controller.systems;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.ProtectedPacket;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.PacketOps;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Random;


public final class MaliciousBehavior implements SystemBehavior {

    private final SystemBoxModel box;
    private final Random rnd = new Random();
    private final double trojanProbability; // 0..1

    public MaliciousBehavior(SystemBoxModel box, double trojanProbability) {
        this.box = Objects.requireNonNull(box, "box");
        this.trojanProbability = trojanProbability;
    }

    @Override
    public void update(double dt) {
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, com.blueprinthell.model.PortModel enteredPort) {
        applyRules(packet);
    }

    @Override
    public void onPacketEnqueued(PacketModel packet) {
        applyRules(packet);
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
    }


    private void applyRules(PacketModel packet) {
        if (packet instanceof ProtectedPacket || PacketOps.isProtected(packet)) {
            return;
        }

        if (packet.getNoise() == 0.0) {
            packet.increaseNoise(1.0);
        }

        if (rnd.nextDouble() < trojanProbability) {
            PacketModel trojan = PacketOps.toTrojan(packet);
            if (trojan != packet) {
                replaceInBuffer(packet, trojan);
                packet = trojan;
            }
        }

        RouteHints.setForceIncompatible(packet, true);
    }

    private void replaceInBuffer(PacketModel oldPkt, PacketModel newPkt) {
        Deque<PacketModel> temp = new ArrayDeque<>();
        PacketModel p;
        boolean replaced = false;
        while ((p = box.pollPacket()) != null) {
            if (!replaced && p == oldPkt) {
                temp.addLast(newPkt);
                replaced = true;
            } else {
                temp.addLast(p);
            }
        }
        for (PacketModel q : temp) {
            box.enqueue(q);
        }
    }
}

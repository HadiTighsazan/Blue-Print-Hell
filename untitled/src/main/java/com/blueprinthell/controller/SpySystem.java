package com.blueprinthell.controller;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.ProtectedPacket;
import com.blueprinthell.model.Updatable;

import java.util.List;
import java.util.Random;


public class SpySystem implements Updatable {
    private final List<WireModel> wires;
    private final PacketLossModel lossModel;
    private final Random random = new Random();
    private final double teleportProbability;
    private final double destructionProbability;


    public SpySystem(List<WireModel> wires,
                     PacketLossModel lossModel,
                     double teleportProbability,
                     double destructionProbability) {
        this.wires = wires;
        this.lossModel = lossModel;
        this.teleportProbability = teleportProbability;
        this.destructionProbability = destructionProbability;
    }

    @Override
    public void update(double dt) {
        for (WireModel wire : wires) {
            List<PacketModel> packets = List.copyOf(wire.getPackets());
            for (PacketModel packet : packets) {
                if (packet instanceof ProtectedPacket pp && !pp.isShieldDepleted()) continue;
                double roll = random.nextDouble();
                if (roll < destructionProbability) {
                    wire.removePacket(packet);
                    lossModel.increment();
                } else if (roll < destructionProbability + teleportProbability) {
                    WireModel target = pickRandomWire(wire);
                    if (target != null) {
                        double progress = packet.getProgress();
                        wire.removePacket(packet);
                        target.attachPacket(packet, progress);
                    }
                }
            }
        }
    }

    private WireModel pickRandomWire(WireModel exclude) {
        if (wires.size() < 2) return null;
        WireModel choice;
        do {
            choice = wires.get(random.nextInt(wires.size()));
        } while (choice == exclude);
        return choice;
    }
}

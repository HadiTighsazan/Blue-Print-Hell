package com.blueprinthell.controller;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.ProtectedPacket;
import com.blueprinthell.model.Updatable;

import java.util.List;
import java.util.Random;

/**
 * SpySystem - a malicious system that occasionally teleports or destroys packets in-flight.
 * <p>
 * Teleportation moves a packet from its current wire to a random other wire at the same progress.
 * Destruction removes a packet entirely, counting toward packet loss.
 * ProtectedPacket instances are immune while their shield remains.
 */
public class SpySystem implements Updatable {
    private final List<WireModel> wires;
    private final PacketLossModel lossModel;
    private final Random random = new Random();
    private final double teleportProbability;
    private final double destructionProbability;

    /**
     * @param wires                list of all wires in the network
     * @param lossModel            model tracking lost packets
     * @param teleportProbability  chance (0.0–1.0) to teleport a packet each update
     * @param destructionProbability chance (0.0–1.0) to destroy a packet each update
     */
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
            // iterate safely since we may modify the list
            List<PacketModel> packets = List.copyOf(wire.getPackets());
            for (PacketModel packet : packets) {
                // skip protected packets
                if (packet instanceof ProtectedPacket pp && !pp.isShieldDepleted()) continue;
                double roll = random.nextDouble();
                if (roll < destructionProbability) {
                    // destroy packet
                    wire.removePacket(packet);
                    lossModel.increment();
                } else if (roll < destructionProbability + teleportProbability) {
                    // teleport packet
                    WireModel target = pickRandomWire(wire);
                    if (target != null) {
                        double progress = packet.getProgress();
                        // remove from current and attach to target
                        wire.removePacket(packet);
                        target.attachPacket(packet, progress);
                    }
                }
            }
        }
    }

    /** Picks a random wire different from the excluded one, or null if none available. */
    private WireModel pickRandomWire(WireModel exclude) {
        if (wires.size() < 2) return null;
        WireModel choice;
        do {
            choice = wires.get(random.nextInt(wires.size()));
        } while (choice == exclude);
        return choice;
    }
}

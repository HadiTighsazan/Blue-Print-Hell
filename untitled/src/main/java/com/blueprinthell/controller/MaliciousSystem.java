package com.blueprinthell.controller;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MaliciousSystem — on packet arrival, inverts compatibility by always applying noise increment,
 * then forwards packets along outgoing wires.
 */
public class MaliciousSystem implements Updatable {
    private final SystemBoxModel boxModel;
    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destMap;
    private final double noiseIncrement;

    /**
     * @param boxModel       the system box acting as malicious node
     * @param wires          all network wires
     * @param destMap        mapping from wire to destination box
     * @param noiseIncrement amount of noise to add to each packet
     */
    public MaliciousSystem(SystemBoxModel boxModel,
                           List<WireModel> wires,
                           Map<WireModel, SystemBoxModel> destMap,
                           double noiseIncrement) {
        this.boxModel = boxModel;
        this.wires = wires;
        this.destMap = destMap;
        this.noiseIncrement = noiseIncrement;
    }

    @Override
    public void update(double dt) {
        // Process all packets that have arrived at this system
        List<PacketModel> toProcess = new ArrayList<>(boxModel.getBuffer());
        boxModel.clearBuffer();
        for (PacketModel packet : toProcess) {
            // Invert compatibility by always adding noise
            packet.increaseNoise(noiseIncrement);
            // Forward along all outgoing wires
            for (WireModel wire : wires) {
                if (boxModel.getOutPorts().contains(wire.getSrcPort())) {
                    wire.attachPacket(packet, 0.0);
                }
            }
        }
    }
}

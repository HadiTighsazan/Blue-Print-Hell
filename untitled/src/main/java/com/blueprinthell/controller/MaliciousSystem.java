package com.blueprinthell.controller;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class MaliciousSystem implements Updatable {
    private final SystemBoxModel boxModel;
    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destMap;
    private final double noiseIncrement;


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
        List<PacketModel> toProcess = new ArrayList<>(boxModel.getBuffer());
        boxModel.clearBuffer();
        for (PacketModel packet : toProcess) {
            packet.increaseNoise(noiseIncrement);
            for (WireModel wire : wires) {
                if (boxModel.getOutPorts().contains(wire.getSrcPort())) {
                    wire.attachPacket(packet, 0.0);
                }
            }
        }
    }
}

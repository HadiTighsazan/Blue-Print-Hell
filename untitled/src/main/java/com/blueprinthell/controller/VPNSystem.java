package com.blueprinthell.controller;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.ProtectedPacket;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.Updatable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class VPNSystem implements Updatable {

    private final SystemBoxModel boxModel;
    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destMap;
    private final double shieldCapacity;


    public VPNSystem(SystemBoxModel boxModel,
                     List<WireModel> wires,
                     Map<WireModel, SystemBoxModel> destMap,
                     double shieldCapacity) {
        this.boxModel = boxModel;
        this.wires = wires;
        this.destMap = destMap;
        this.shieldCapacity = shieldCapacity;
    }

    @Override
    public void update(double dt) {
        List<PacketModel> toWrap = new ArrayList<>(boxModel.getBuffer());
        boxModel.clearBuffer();
        for (PacketModel original : toWrap) {
            ProtectedPacket pp = ProtectedPacket.wrap(original, shieldCapacity);
            for (WireModel wire : wires) {
                if (boxModel.getOutPorts().contains(wire.getSrcPort())) {
                    wire.attachPacket(pp, 0.0);
                }
            }
        }
    }
}

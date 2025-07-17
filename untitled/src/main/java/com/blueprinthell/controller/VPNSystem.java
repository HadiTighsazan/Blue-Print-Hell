package com.blueprinthell.controller;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.ProtectedPacket;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.Updatable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * VPNSystem — wraps packets passing through this system with a noise shield.
 */
public class VPNSystem implements Updatable {

    private final SystemBoxModel boxModel;
    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destMap;
    private final double shieldCapacity;

    /**
     * @param boxModel        The system box this VPN applies to
     * @param wires           All wires in the network
     * @param destMap         Mapping of WireModel to destination SystemBoxModel
     * @param shieldCapacity  Amount of noise shield to apply
     */
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
        // Process all packets that have arrived in this system's buffer
        List<PacketModel> toWrap = new ArrayList<>(boxModel.getBuffer());
        boxModel.clearBuffer();
        for (PacketModel original : toWrap) {
            // Wrap with protection
            ProtectedPacket pp = ProtectedPacket.wrap(original, shieldCapacity);
            // Re-dispatch along all outgoing wires
            for (WireModel wire : wires) {
                if (boxModel.getOutPorts().contains(wire.getSrcPort())) {
                    wire.attachPacket(pp, 0.0);
                }
            }
        }
    }
}

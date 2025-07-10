package com.blueprinthell.controller;

import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.Updatable;

import java.util.List;
import java.util.Map;

/**
 * Controller to dispatch packets arriving at the end of wires into system boxes.
 */
public class PacketDispatcherController implements Updatable {
    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destinationMap;

    /**
     * @param wires list of wires to monitor
     * @param destinationMap mapping from each wire to its target SystemBoxModel
     */
    public PacketDispatcherController(List<WireModel> wires,
                                      Map<WireModel, SystemBoxModel> destinationMap) {
        this.wires = wires;
        this.destinationMap = destinationMap;
    }

    @Override
    public void update(double dt) {
        for (WireModel wire : wires) {
            List<PacketModel> arrived = wire.update(dt);
            SystemBoxModel dest = destinationMap.get(wire);
            for (PacketModel packet : arrived) {
                dest.enqueue(packet);
            }
        }
    }
}

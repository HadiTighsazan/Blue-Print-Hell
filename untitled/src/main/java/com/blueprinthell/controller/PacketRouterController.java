package com.blueprinthell.controller;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;

import java.util.List;
import java.util.Map;

/**
 * Controller that routes packets from intermediate system buffers
 * onto their outgoing wires in a multi-stage pipeline.
 * <p>
 * Implements the Updatable interface to be invoked each simulation tick.
 */
public class PacketRouterController implements Updatable {
    private final SystemBoxModel box;
    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destMap;

    /**
     * Constructs a PacketRouterController for a given intermediate box.
     *
     * @param box     the SystemBoxModel with both input and output ports
     * @param wires   the list of all wires in the current level
     * @param destMap mapping from each wire to its destination box
     */
    public PacketRouterController(SystemBoxModel box,
                                  List<WireModel> wires,
                                  Map<WireModel, SystemBoxModel> destMap) {
        this.box = box;
        this.wires = wires;
        this.destMap = destMap;
    }

    @Override
    public void update(double dt) {
        PacketModel packet;
        // Continue routing until buffer is empty
        while ((packet = box.pollPacket()) != null) {
            // Determine an outgoing port (simple strategy: first available)
            PortModel outPort = null;
            if (!box.getOutPorts().isEmpty()) {
                outPort = box.getOutPorts().get(0);
            }
            if (outPort == null) {
                // No outgoing port: drop packet or log warning
                continue;
            }
            // Find corresponding wire for this outPort
            for (WireModel wire : wires) {
                if (wire.getSrcPort() == outPort) {
                    // Attach packet at start of this wire
                    wire.attachPacket(packet, 0.0);
                    // Ensure destination mapping remains intact
                    destMap.putIfAbsent(wire, destMap.get(wire));
                    break;
                }
            }
        }
    }
}

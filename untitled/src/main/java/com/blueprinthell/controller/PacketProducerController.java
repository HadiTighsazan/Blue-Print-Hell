package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;

import java.util.List;
import java.util.Map;

/**
 * Controller responsible for producing packets at source systems once when started.
 */
public class PacketProducerController implements Updatable {
    private final List<SystemBoxModel> sourceBoxes;
    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destMap;
    private final double baseSpeed;
    private boolean produced;

    /**
     * Constructs a producer that will emit a burst of packets when triggered.
     * @param sourceBoxes systems with no input ports
     * @param wires       list of wires connecting those ports
     * @param destMap     mapping of wires to destination boxes
     * @param baseSpeed   initial speed for each packet
     */
    public PacketProducerController(List<SystemBoxModel> sourceBoxes,
                                    List<WireModel> wires,
                                    Map<WireModel, SystemBoxModel> destMap,
                                    double baseSpeed) {
        this.sourceBoxes = sourceBoxes;
        this.wires = wires;
        this.destMap = destMap;
        this.baseSpeed = baseSpeed;
        this.produced = true; // initially do not produce until startProduction called
    }

    /**
     * Resets state so that on next update, packets will be generated.
     */
    public void startProduction() {
        this.produced = false;
    }

    @Override
    public void update(double dt) {
        if (!produced) {
            // For each source system and each of its output ports, produce packets
            for (SystemBoxModel box : sourceBoxes) {
                if (box.getInPorts().isEmpty()) {
                    box.getOutPorts().forEach(port -> {
                        wires.stream()
                                .filter(w -> w.getSrcPort() == port)
                                .forEach(wire -> {
                                    PacketType type = port.getType();
                                    for (int i = 0; i < Config.PACKETS_PER_PORT; i++) {
                                        PacketModel pkt = new PacketModel(type, baseSpeed);
                                        // start slightly off to avoid zero-progress collision
                                        wire.attachPacket(pkt, 0.01);
                                    }
                                });
                    });

            }
        }
        produced = true;
    }
}
}

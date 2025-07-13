package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Produces a stream of packets from all source boxes at fixed intervals.
 * Activated once with startProduction() and continues until all packets for the level are produced.
 * Number of packets per output port is configurable (e.g. 3 × level).
 */
public class PacketProducerController implements Updatable {

    private static final Random RND = new Random();
    private static final double INTERVAL_SEC = 0.4;

    private final List<SystemBoxModel> sourceBoxes;
    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destMap;
    private final double baseSpeed;
    private final int packetsPerPort;
    private final int totalToProduce;

    private double acc = 0;
    private boolean running = false;
    private int producedCount = 0;

    /**
     * @param sourceBoxes     list of source system boxes
     * @param wires            list of wires connecting ports
     * @param destMap          destination mapping for wires
     * @param baseSpeed        initial speed for packets
     * @param packetsPerPort   number of packets to emit per output port this level
     */
    public PacketProducerController(List<SystemBoxModel> sourceBoxes,
                                    List<WireModel> wires,
                                    Map<WireModel, SystemBoxModel> destMap,
                                    double baseSpeed,
                                    int packetsPerPort) {
        this.sourceBoxes = sourceBoxes;
        this.wires = wires;
        this.destMap = destMap;
        this.baseSpeed = baseSpeed;
        this.packetsPerPort = packetsPerPort;
        int outs = sourceBoxes.stream().mapToInt(b -> b.getOutPorts().size()).sum();
        this.totalToProduce = outs * packetsPerPort;
    }

    /** Starts packet production. */
    public void startProduction() { running = true; }

    /** Stops packet production. */
    public void stopProduction()  { running = false; }

    /** Checks if all packets have been produced. */
    public boolean isFinished()   { return producedCount >= totalToProduce; }

    /** Returns the count of produced packets so far. */
    public int getProducedCount() { return producedCount; }

    @Override
    public void update(double dt) {
        if (!running || isFinished()) return;
        acc += dt;
        while (acc >= INTERVAL_SEC && !isFinished()) {
            acc -= INTERVAL_SEC;
            emitOnce();
        }
    }

    /** Emits one packet per source output port, type randomized. */
    private void emitOnce() {
        for (SystemBoxModel box : sourceBoxes) {
            if (!box.getInPorts().isEmpty()) continue;
            box.getOutPorts().forEach(port -> {
                wires.stream()
                        .filter(w -> w.getSrcPort() == port)
                        .findFirst()
                        .ifPresent(wire -> {
                            if (producedCount < totalToProduce) {
                                PacketType type = RND.nextBoolean() ? PacketType.SQUARE : PacketType.TRIANGLE;
                                PacketModel pkt = new PacketModel(type, baseSpeed);
                                wire.attachPacket(pkt, 0.01);
                                producedCount++;
                            }
                        });
            });
        }
        if (isFinished()) running = false;
    }
}

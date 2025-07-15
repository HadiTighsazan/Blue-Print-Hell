package com.blueprinthell.controller;

import com.blueprinthell.model.*;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Produces a stream of packets from every output‑port of all **source** boxes at a fixed interval.
 * <p>
 * – تعداد پکت به ازای هر پورت خروجی توسط سازنده (packetsPerPort) تعیین می‌شود.<br>
 * – متد {@link #startProduction()} فقط زمانی فراخوانی می‌شود که کاربر Start را بزند.<br>
 * – متد {@link #reset()} برای Time‑Travel یا ریست مرحله شمارنده‌ها و فلگ‌ها را صفر می‌کند.
 */
public class PacketProducerController implements Updatable {

    private static final Random  RND          = new Random();
    private static final double  INTERVAL_SEC = 0.4;

    private final List<SystemBoxModel>           sourceBoxes;
    private final List<WireModel>                wires;
    private final Map<WireModel, SystemBoxModel> destMap; // حفظ برای آینده (در حال حاضر استفاده نمی‌شود)
    private final double                         baseSpeed;
    private final int                            packetsPerPort;
    private final int                            totalToProduce;

    /* -------- runtime state -------- */
    private double  acc            = 0.0;
    private boolean running        = false;
    private int     producedCount  = 0;

    public PacketProducerController(List<SystemBoxModel> sourceBoxes,
                                    List<WireModel> wires,
                                    Map<WireModel, SystemBoxModel> destMap,
                                    double baseSpeed,
                                    int packetsPerPort) {
        this.sourceBoxes     = sourceBoxes;
        this.wires           = wires;
        this.destMap         = destMap;
        this.baseSpeed       = baseSpeed;
        this.packetsPerPort  = packetsPerPort;
        int outs = sourceBoxes.stream().mapToInt(b -> b.getOutPorts().size()).sum();
        this.totalToProduce  = outs * packetsPerPort;
    }

    /* ---------------- Public API ---------------- */
    public void startProduction() { running = true; }
    public void stopProduction()  { running = false; }

    /** Resets timer and counters so producer can safely be reused after a time‑rewind. */
    public void reset() {
        running = false;
        producedCount = 0;
        acc = 0.0;
    }

    public boolean isFinished()      { return producedCount >= totalToProduce; }
    public int  getProducedCount()   { return producedCount; }
    public int  getPacketsPerPort()  { return packetsPerPort; }

    @Override
    public void update(double dt) {
        if (!running || isFinished()) return;
        acc += dt;
        while (acc >= INTERVAL_SEC && !isFinished()) {
            acc -= INTERVAL_SEC;
            emitOnce();
        }
        if (isFinished()) running = false;
    }

    /* ---------------- Internal helpers ---------------- */
    private void emitOnce() {
        for (SystemBoxModel box : sourceBoxes) {
            // Safety: only true source boxes (0 in‑ports)
            if (!box.getInPorts().isEmpty()) continue;
            box.getOutPorts().forEach(port -> wires.stream()
                    .filter(w -> w.getSrcPort() == port)
                    .findFirst()
                    .ifPresent(wire -> {
                        if (producedCount < totalToProduce) {
                            PacketType type = RND.nextBoolean() ? PacketType.SQUARE : PacketType.TRIANGLE;
                            PacketModel pkt = new PacketModel(type, baseSpeed);
                            wire.attachPacket(pkt, 0.01);
                            producedCount++;
                        }
                    }));
        }
    }
}

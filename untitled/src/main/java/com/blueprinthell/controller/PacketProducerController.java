package com.blueprinthell.controller;

import com.blueprinthell.model.*;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Produces a stream of packets from every output‑port of all **source** boxes at a fixed interval.
 * <p>
 * – تعداد پکت به ازای هر پورت خروجی توسط سازنده (packetsPerPort) تعیین می‌شود.
 * – متد {@link #startProduction()} فقط زمانی فراخوانی می‌شود که کاربر Start را بزند.
 * – متد {@link #reset()} شمارنده‌ی زمان‌بندی را ریست می‌کند اما تعداد پکت‌های تولیدشده و اعتبار بازگشتی را حفظ می‌کند.
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

    /* -------- runtime state -------- */
    private double acc = 0.0;
    private boolean running = false;
    private int producedCount = 0;
    private int returnedCredits = 0;

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

    /* ---------------- Public API ---------------- */
    public void startProduction() { running = true; }
    public void stopProduction()  { running = false; }

    /**
     * Resets only the timing accumulator and running flag.
     * تعداد پکت‌های تولیدشده و returnedCredits حفظ می‌شوند.
     */
    public void reset() {
        running = false;
        acc = 0.0;
    }

    /**
     * هر بار که یک پکت پس از عقب‌گرد زمان به مبدا می‌رسد فراخوانی شود
     * تا برای بازتولید اعتبار بگیرد.
     */
    public void onPacketReturned() {
        returnedCredits++;
    }

    /**
     * زمانی تمام شده محسوب می‌شود که هیچ اعتبار بازگشتی نمانده
     * و همه‌ی پکت‌های جدید تولید شده باشند.
     */
    public boolean isFinished() {
        return producedCount >= totalToProduce && returnedCredits == 0;
    }
    public int getProducedCount() { return producedCount; }
    public int getPacketsPerPort() { return packetsPerPort; }

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
            if (!box.getInPorts().isEmpty()) continue;
            box.getOutPorts().forEach(port -> wires.stream()
                    .filter(w -> w.getSrcPort() == port)
                    .findFirst()
                    .ifPresent(wire -> {
                        PacketModel pkt;
                        if (returnedCredits > 0) {
                            pkt = new PacketModel(randomType(), baseSpeed);
                            returnedCredits--;
                        } else if (producedCount < totalToProduce) {
                            pkt = new PacketModel(randomType(), baseSpeed);
                            producedCount++;
                        } else {
                            return;
                        }
                        wire.attachPacket(pkt, 0.01);
                    }));
        }
    }

    private PacketType randomType() {
        return RND.nextBoolean() ? PacketType.SQUARE : PacketType.TRIANGLE;
    }
}

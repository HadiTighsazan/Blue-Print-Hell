package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;
import java.util.*;

public final class AntiTrojanBehavior implements SystemBehavior {

    private final SystemBoxModel box;
    private final List<WireModel> wires;
    private final double radiusPx;
    private final double cooldownSec;

    private double cooldownLeft = 0.0;

    // Statistics
    private long trojansDetected = 0;
    private long trojansCleaned = 0;
    private int scanCycles = 0;

    public AntiTrojanBehavior(SystemBoxModel box, List<WireModel> wires) {
        this(box, wires, Config.ANTI_TROJAN_RADIUS_PX, Config.ANTI_TROJAN_COOLDOWN_S);
    }

    public AntiTrojanBehavior(SystemBoxModel box,
                              List<WireModel> wires,
                              double radiusPx,
                              double cooldownSec) {
        this.box = Objects.requireNonNull(box, "box");
        this.wires = Objects.requireNonNull(wires, "wires");
        this.radiusPx = radiusPx;
        this.cooldownSec = cooldownSec;
    }

    @Override
    public void update(double dt) {
        if (cooldownLeft > 0) {
            cooldownLeft -= dt;
            if (cooldownLeft < 0) cooldownLeft = 0;
            return;
        }

        // Scan for trojans in range
        boolean cleanedAny = scanAndCleanTrojans();

        if (cleanedAny) {
            startCooldown();
        }
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (cooldownLeft > 0) return;

        // Check if the incoming packet is a trojan
        if (packet instanceof TrojanPacket) {
            PacketModel clean = unTrojan(packet);
            if (replaceInBuffer(packet, clean)) {
                trojansCleaned++;
                startCooldown();
            }
        }
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        if (enabled) {
            // Reset cooldown when re-enabled
            cooldownLeft = 0;
        }
    }

    /**
     * Scan all wires for trojans within range and clean them
     * @return true if any trojans were cleaned
     */
    private boolean scanAndCleanTrojans() {
        scanCycles++;
        boolean cleanedAny = false;
        double r2 = radiusPx * radiusPx;

        // Create a list to store trojans to clean (to avoid concurrent modification)
        List<TrojanCleanupTask> cleanupTasks = new ArrayList<>();

        // Scan all wires
        for (WireModel w : wires) {
            for (PacketModel pkt : w.getPackets()) {
                if (!(pkt instanceof TrojanPacket)) continue;

                // Calculate distance
                if (isWithinRange(pkt, r2)) {
                    trojansDetected++;
                    cleanupTasks.add(new TrojanCleanupTask(w, pkt));
                }
            }
        }

        // Clean detected trojans
        for (TrojanCleanupTask task : cleanupTasks) {
            PacketModel clean = unTrojan(task.trojan);
            double progress = task.trojan.getProgress();

            if (task.wire.removePacket(task.trojan)) {
                task.wire.attachPacket(clean, progress);
                trojansCleaned++;
                cleanedAny = true;
            }
        }

        // Also check buffer for trojans
        if (cleanFirstTrojanInBuffer()) {
            cleanedAny = true;
        }

        return cleanedAny;
    }

    /**
     * Check if a packet is within the anti-trojan range
     */
    private boolean isWithinRange(PacketModel pkt, double radiusSquared) {
        int dx = pkt.getCenterX() - box.getCenterX();
        int dy = pkt.getCenterY() - box.getCenterY();
        double distSquared = (double) dx * dx + (double) dy * dy;
        return distSquared <= radiusSquared;
    }

    /**
     * Clean a trojan packet back to its original form
     */
    private PacketModel unTrojan(PacketModel pkt) {
        if (pkt instanceof TrojanPacket tp) {
            PacketModel orig = tp.getOriginal();
            if (orig != null) {
                // Create a clean copy with current state
                PacketModel clean = clonePlain(orig);
                // Preserve motion properties
                clean.setProgress(pkt.getProgress());
                clean.setSpeed(pkt.getSpeed());
                clean.setAcceleration(pkt.getAcceleration());
                // Reset noise as a bonus of cleaning
                clean.resetNoise();
                return clean;
            }
        }
        return clonePlain(pkt);
    }

    /**
     * Create a plain copy of a packet
     */
    private PacketModel clonePlain(PacketModel src) {
        PacketModel c = new PacketModel(src.getType(), src.getBaseSpeed());
        c.setProgress(src.getProgress());
        c.setSpeed(src.getSpeed());
        c.setAcceleration(src.getAcceleration());
        c.resetNoise(); // Clean packets have no noise
        return c;
    }

    /**
     * Replace a packet in the buffer
     */
    private boolean replaceInBuffer(PacketModel oldPkt, PacketModel newPkt) {
        Deque<PacketModel> temp = new ArrayDeque<>();
        boolean replaced = false;
        PacketModel p;

        while ((p = box.pollPacket()) != null) {
            if (!replaced && p == oldPkt) {
                temp.addLast(newPkt);
                replaced = true;
            } else {
                temp.addLast(p);
            }
        }

        for (PacketModel q : temp) {
            box.enqueue(q);
        }

        return replaced;
    }

    /**
     * Clean the first trojan found in buffer
     */
    private boolean cleanFirstTrojanInBuffer() {
        Deque<PacketModel> temp = new ArrayDeque<>();
        boolean cleaned = false;
        PacketModel p;

        while ((p = box.pollPacket()) != null) {
            if (!cleaned && p instanceof TrojanPacket) {
                temp.addLast(unTrojan(p));
                trojansCleaned++;
                cleaned = true;
            } else {
                temp.addLast(p);
            }
        }

        for (PacketModel q : temp) {
            box.enqueue(q);
        }

        return cleaned;
    }

    private void startCooldown() {
        cooldownLeft = cooldownSec;
        try { box.disableFor(cooldownSec); } catch (Throwable ignore) {}
    }

    public void clear() {
        cooldownLeft = 0.0;
        trojansDetected = 0;
        trojansCleaned = 0;
        scanCycles = 0;
    }

    // Helper class for cleanup tasks
    private static class TrojanCleanupTask {
        final WireModel wire;
        final PacketModel trojan;

        TrojanCleanupTask(WireModel wire, PacketModel trojan) {
            this.wire = wire;
            this.trojan = trojan;
        }
    }

    // Telemetry getters
    public long getTrojansDetected() { return trojansDetected; }
    public long getTrojansCleaned() { return trojansCleaned; }
    public int getScanCycles() { return scanCycles; }
    public boolean isOnCooldown() { return cooldownLeft > 0; }
    public double getCooldownRemaining() { return cooldownLeft; }
}
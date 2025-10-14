package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;
import java.util.*;

public final class AntiTrojanBehavior implements SystemBehavior, SnapshottableBehavior  {

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
            cooldownLeft = 0;
        }
    }


    private boolean scanAndCleanTrojans() {
        scanCycles++;
        boolean cleanedAny = false;
        double r2 = radiusPx * radiusPx;

        List<TrojanCleanupTask> cleanupTasks = new ArrayList<>();

        for (WireModel w : wires) {
            for (PacketModel pkt : w.getPackets()) {
                if (!(pkt instanceof TrojanPacket)) continue;

                if (isWithinRange(pkt, r2)) {
                    trojansDetected++;
                    cleanupTasks.add(new TrojanCleanupTask(w, pkt));
                }
            }
        }

        for (TrojanCleanupTask task : cleanupTasks) {
            PacketModel clean = unTrojan(task.trojan);
            double progress = task.trojan.getProgress();

            if (task.wire.removePacket(task.trojan)) {
                task.wire.attachPacket(clean, progress);
                trojansCleaned++;
                cleanedAny = true;
            }
        }

        if (cleanFirstTrojanInBuffer()) {
            cleanedAny = true;
        }

        return cleanedAny;
    }


    private boolean isWithinRange(PacketModel pkt, double radiusSquared) {
        int dx = pkt.getCenterX() - box.getCenterX();
        int dy = pkt.getCenterY() - box.getCenterY();
        double distSquared = (double) dx * dx + (double) dy * dy;
        return distSquared <= radiusSquared;
    }


    private PacketModel unTrojan(PacketModel pkt) {
        if (pkt instanceof TrojanPacket tp) {
            PacketModel orig = tp.getOriginal();
            if (orig != null) {
                PacketModel clean = clonePlain(orig);

                clean.setProgress(pkt.getProgress());
                clean.setSpeed(pkt.getSpeed());
                clean.setAcceleration(pkt.getAcceleration());

                clean.resetNoise();
                return clean;
            }
        }
        return clonePlain(pkt);
    }


    private PacketModel clonePlain(PacketModel src) {
        PacketModel c = new PacketModel(src.getType(), src.getBaseSpeed());
        c.setProgress(src.getProgress());
        c.setSpeed(src.getSpeed());
        c.setAcceleration(src.getAcceleration());
        c.resetNoise();
        return c;
    }


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

    private static class TrojanCleanupTask {
        final WireModel wire;
        final PacketModel trojan;

        TrojanCleanupTask(WireModel wire, PacketModel trojan) {
            this.wire = wire;
            this.trojan = trojan;
        }
    }
    @Override
    public Map<String, Object> captureState() {
        Map<String, Object> state = new HashMap<>();
        state.put("cooldownLeft", cooldownLeft);
        return state;
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        if (state != null && state.containsKey("cooldownLeft")) {
            this.cooldownLeft = ((Number) state.get("cooldownLeft")).doubleValue();
        }
    }

}
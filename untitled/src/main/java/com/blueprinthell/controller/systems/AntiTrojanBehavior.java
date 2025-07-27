package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.TrojanPacket;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.PortModel;

import java.util.*;


public final class AntiTrojanBehavior implements SystemBehavior {

    private final SystemBoxModel box;
    private final List<WireModel> wires;
    private final double radiusPx;
    private final double cooldownSec;

    private double cooldownLeft = 0.0;

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

        double r2 = radiusPx * radiusPx;
        for (WireModel w : wires) {
            for (PacketModel pkt : w.getPackets()) {
                if (!(pkt instanceof TrojanPacket)) continue;
                if (distanceSquared(pkt, box) <= r2) {
                    PacketModel clean = unTrojan(pkt);
                    double prog = pkt.getProgress();
                    if (w.removePacket(pkt)) {
                        w.attachPacket(clean, prog);
                        startCooldown();
                        return;
                    }
                }
            }
        }

        replaceFirstTrojanInBuffer();
    }

    @Override
    public void onPacketEnqueued(PacketModel packet) {
        onPacketEnqueued(packet, null);
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (cooldownLeft > 0) return;
        if (packet instanceof TrojanPacket) {
            PacketModel clean = unTrojan(packet);
            if (replaceInBuffer(packet, clean)) {
                startCooldown();
            }
        }
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
    }


    private void startCooldown() {
        cooldownLeft = cooldownSec;
    }

    private PacketModel unTrojan(PacketModel pkt) {
        if (pkt instanceof TrojanPacket tp) {
            PacketModel orig = tp.getOriginal();
            return (orig != null) ? orig : clonePlain(tp);
        }
        return pkt;
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
        for (PacketModel q : temp) box.enqueue(q);
        return replaced;
    }

    private void replaceFirstTrojanInBuffer() {
        Deque<PacketModel> temp = new ArrayDeque<>();
        boolean converted = false;
        PacketModel p;
        while ((p = box.pollPacket()) != null) {
            if (!converted && p instanceof TrojanPacket) {
                temp.addLast(unTrojan(p));
                converted = true;
            } else {
                temp.addLast(p);
            }
        }
        for (PacketModel q : temp) box.enqueue(q);
        if (converted) startCooldown();
    }

    private static double distanceSquared(PacketModel pkt, SystemBoxModel box) {
        int dx = pkt.getCenterX() - box.getCenterX();
        int dy = pkt.getCenterY() - box.getCenterY();
        return (double) dx * dx + (double) dy * dy;
    }

    public void clear() {
        cooldownLeft = 0.0;
    }
}

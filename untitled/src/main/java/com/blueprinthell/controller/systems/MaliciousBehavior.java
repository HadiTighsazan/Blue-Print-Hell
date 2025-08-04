package com.blueprinthell.controller.systems;

import com.blueprinthell.model.*;
import java.util.*;

public final class MaliciousBehavior implements SystemBehavior {

    private final SystemBoxModel box;
    private final Random rnd = new Random();
    private final double trojanProbability;

    // Statistics
    private long noisedPackets = 0;
    private long trojanizedPackets = 0;
    private long forcedIncompatibleRoutes = 0;

    public MaliciousBehavior(SystemBoxModel box, double trojanProbability) {
        this.box = Objects.requireNonNull(box, "box");
        this.trojanProbability = Math.max(0.0, Math.min(1.0, trojanProbability));
    }

    @Override
    public void update(double dt) {
        // No periodic updates needed
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (packet == null) return;

        // Check if packet is protected - if so, try to revert it first
        if (packet instanceof ProtectedPacket || PacketOps.isProtected(packet)) {
            // Try to consume global VPN protection
            PacketModel orig = VpnRevertHints.consumeGlobal(packet);

            if (orig != null) {
                // Successfully reverted - replace in buffer and process the original
                replaceInBuffer(packet, orig);
                packet = orig;
            } else {
                // Cannot affect protected packets that we can't revert
                return;
            }
        }

        applyMaliciousEffects(packet);
        if (!RouteHints.peekForceIncompatible(packet)) {
            RouteHints.setForceIncompatible(packet, true);
            forcedIncompatibleRoutes++;
        }
    }

    private void applyMaliciousEffects(PacketModel packet) {
        boolean modified = false;

        // 1. Add noise if packet has none
        if (packet.getNoise() == 0.0) {
            packet.increaseNoise(1.0);
            noisedPackets++;
            modified = true;
        }

        // 2. Possibly convert to Trojan
        if (rnd.nextDouble() < trojanProbability) {
            PacketModel trojan = PacketOps.toTrojan(packet);
            if (trojan != packet) {
                replaceInBuffer(packet, trojan);
                packet = trojan;
                trojanizedPackets++;
                modified = true;
            }
        }

        // 3. Mark for incompatible routing
        RouteHints.setForceIncompatible(packet, true);
        forcedIncompatibleRoutes++;

        // Log the malicious action for debugging
        if (modified) {
            logMaliciousAction(packet);
        }
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        // Reset statistics when re-enabled
        if (enabled) {
            noisedPackets = 0;
            trojanizedPackets = 0;
            forcedIncompatibleRoutes = 0;
        }
    }

    private void replaceInBuffer(PacketModel oldPkt, PacketModel newPkt) {
        Deque<PacketModel> temp = new ArrayDeque<>();
        PacketModel p;
        boolean replaced = false;

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
    }

    private void logMaliciousAction(PacketModel packet) {
        // For debugging - can be expanded with proper logging
        String action = String.format(
                "Malicious action on %s: noise=%.1f, trojan=%b",
                packet.getType(),
                packet.getNoise(),
                packet instanceof TrojanPacket
        );
        // System.out.println(action); // Uncomment for debugging
    }

    // Telemetry getters
    public long getNoisedPackets() { return noisedPackets; }
    public long getTrojanizedPackets() { return trojanizedPackets; }
    public long getForcedIncompatibleRoutes() { return forcedIncompatibleRoutes; }
}
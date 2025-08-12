package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;
import com.blueprinthell.motion.KinematicsProfile;
import com.blueprinthell.motion.KinematicsRegistry;

import java.util.*;

public final class VpnBehavior implements SystemBehavior {

    private final SystemBoxModel box;
    private final double shieldCapacity;

    // Track all packets protected by this VPN instance
    private final Set<PacketModel> myProtectedPackets = Collections.newSetFromMap(new WeakHashMap<>());

    public VpnBehavior(SystemBoxModel box) {
        this(box, Config.DEFAULT_SHIELD_CAPACITY);
    }

    public VpnBehavior(SystemBoxModel box, double shieldCapacity) {
        this.box = Objects.requireNonNull(box, "box");
        this.shieldCapacity = shieldCapacity;
    }

    @Override
    public void update(double dt) {
        // No periodic updates needed
    }

    // در VpnBehavior.java - متد onPacketEnqueued را جایگزین کنید:

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (packet == null) return;

        // اگر قبلاً محافظت شده، نادیده بگیر
        if (packet instanceof ProtectedPacket || PacketOps.isProtected(packet)) {
            return;
        }

        // پیام‌رسان‌ها → Protected
        if (PacketOps.isMessenger(packet)) {
            PacketModel prot = PacketOps.toProtected(packet, shieldCapacity);
            VpnRevertHints.markGlobal(prot, packet);
            myProtectedPackets.add(prot);
            replaceInBuffer(packet, prot);

            return;
        }

        // پکت محرمانه عادی → پکت محرمانه VPN (سایز 4 به 6)
        if (packet instanceof ConfidentialPacket && !PacketOps.isConfidentialVpn(packet)) {
            PacketModel conf6 = PacketOps.toConfidentialVpn(packet);

            // مهم: تنظیم پروفایل حرکتی برای keep-distance
            KinematicsRegistry.setProfile(conf6, KinematicsProfile.CONFIDENTIAL_VPN);

            VpnRevertHints.markGlobal(conf6, packet);
            myProtectedPackets.add(conf6);
            replaceInBuffer(packet, conf6);
            return;
        }
    }
    @Override
    public void onEnabledChanged(boolean enabled) {
        if (!enabled) {
            // VPN disabled - revert all protected packets
            revertAllProtectedPackets();
        }
    }

    /**
     * Revert all packets protected by this VPN instance
     * This affects packets in:
     * 1. This system's buffer
     * 2. Other systems' buffers
     * 3. On wires
     */
    private void revertAllProtectedPackets() {
        // 1. Revert packets in our own buffer
        revertBufferPackets();

        // 2. Clear our tracking (weak references will handle cleanup)
        myProtectedPackets.clear();

        // Note: Packets on wires or in other systems will be reverted
        // when they're processed by those systems (via global hints)
    }

    private void revertBufferPackets() {
        Queue<PacketModel> buffer = box.getBuffer();
        if (buffer == null || buffer.isEmpty()) return;

        List<PacketModel> toProcess = new ArrayList<>(buffer);
        for (PacketModel p : toProcess) {
            // Try local revert first, then global
            PacketModel orig = VpnRevertHints.consumeGlobal(p);
            if (orig != null) {
                replaceInBuffer(p, orig);
            }
        }
    }

    private void replaceInBuffer(PacketModel oldPkt, PacketModel newPkt) {
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
    }

    public void clear() {
        myProtectedPackets.clear();
        // Note: Don't clear global hints here as other VPNs might be using them
    }

    // For debugging/telemetry
    public int getProtectedPacketCount() {
        return myProtectedPackets.size();
    }
}
package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;
import com.blueprinthell.motion.KinematicsProfile;
import com.blueprinthell.motion.KinematicsRegistry;

import java.util.*;

public final class VpnBehavior implements SystemBehavior {

    private final SystemBoxModel box;
    private final double shieldCapacity;

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


    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (packet == null) return;
        if (packet instanceof ProtectedPacket || PacketOps.isProtected(packet)) {
            return;
        }

        if (PacketOps.isMessenger(packet)) {
            PacketModel prot = PacketOps.toProtected(packet, shieldCapacity);
            VpnRevertHints.markGlobal(prot, packet);
            myProtectedPackets.add(prot);
            replaceInBuffer(packet, prot);

            return;
        }

        if (packet instanceof ConfidentialPacket && !PacketOps.isConfidentialVpn(packet)) {
            PacketModel conf6 = PacketOps.toConfidentialVpn(packet);

            KinematicsRegistry.setProfile(conf6, KinematicsProfile.CONFIDENTIAL_VPN);

            VpnRevertHints.markGlobal(conf6, packet);
            myProtectedPackets.add(conf6);
            replaceInBuffer(packet, conf6);

        }
    }
    @Override
    public void onEnabledChanged(boolean enabled) {
        if (!enabled) {
            revertAllProtectedPackets();
        }
    }


    private void revertAllProtectedPackets() {
        revertBufferPackets();

        myProtectedPackets.clear();


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
    }

    public int getProtectedPacketCount() {
        return myProtectedPackets.size();
    }
}
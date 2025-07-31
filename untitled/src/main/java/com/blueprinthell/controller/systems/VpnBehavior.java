package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.ProtectedPacket;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.PacketOps;
import com.blueprinthell.model.PacketOps.PacketTag;
import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.ConfidentialPacket;
import com.blueprinthell.motion.KinematicsProfile;
import com.blueprinthell.motion.KinematicsRegistry;

import java.util.*;
import java.util.Objects;

public final class VpnBehavior implements SystemBehavior {

    private final SystemBoxModel box;
    private final double shieldCapacity;

    private final Map<PacketModel, PacketModel> protectedMap = new WeakHashMap<>();
    private final VpnRevertHints revertHints = new VpnRevertHints();

    public VpnBehavior(SystemBoxModel box) {
        this(box, Config.DEFAULT_SHIELD_CAPACITY);
    }

    public VpnBehavior(SystemBoxModel box, double shieldCapacity) {
        this.box = Objects.requireNonNull(box, "box");
        this.shieldCapacity = shieldCapacity;
    }

    @Override
    public void update(double dt) {
    }

    @Override
    public void onPacketEnqueued(PacketModel packet) {
        onPacketEnqueued(packet, null);
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (packet == null) return;

        if (packet instanceof ProtectedPacket || PacketOps.isProtected(packet)) {
            return;
        }

        if (PacketOps.isMessenger(packet)) {
            PacketModel prot = PacketOps.toProtected(packet, shieldCapacity);
            revertHints.mark(prot, packet);
            /* ADD START */ VpnRevertHints.markGlobal(prot, packet); /* ADD END */
            replaceInBuffer(packet, prot);
            protectedMap.put(prot, packet);
            return;
        }

        if (packet instanceof ConfidentialPacket && !PacketOps.isConfidentialVpn(packet)) {
            PacketModel conf6 = PacketOps.toConfidentialVpn(packet);
            revertHints.mark(conf6, packet);
             VpnRevertHints.markGlobal(conf6, packet);
            replaceInBuffer(packet, conf6);
            return;
        }

        if (PacketOps.isConfidential(packet)) {
            KinematicsRegistry.setProfile(packet, KinematicsProfile.CONFIDENTIAL_VPN);
            PacketOps.tag(packet, PacketTag.CONFIDENTIAL_VPN);
            return;
        }

    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        if (!enabled) {
            Queue<PacketModel> buf = box.getBuffer();
            if (buf == null || buf.isEmpty()) return;

            List<PacketModel> toReplace = new ArrayList<>(buf);
            for (PacketModel p : toReplace) {
                PacketModel orig = revertHints.consume(p);
                if (orig != null) {
                    replaceInBuffer(p, orig);
                }
                else {
                    PacketModel orig2 = VpnRevertHints.consumeGlobal(p);
                    if (orig2 != null) {
                        replaceInBuffer(p, orig2);
                    }
                }
            }
        }
    }

    private void replaceInBuffer(PacketModel oldPkt, PacketModel newPkt) {
        replaceInBufferGeneric(box, oldPkt, newPkt);
    }

    private boolean replaceInBufferIfPresent(SystemBoxModel targetBox, PacketModel oldPkt, PacketModel newPkt) {
        return replaceInBufferGeneric(targetBox, oldPkt, newPkt);
    }

    private boolean replaceInBufferGeneric(SystemBoxModel targetBox, PacketModel oldPkt, PacketModel newPkt) {
        Deque<PacketModel> temp = new ArrayDeque<>();
        boolean replaced = false;
        PacketModel p;
        while ((p = targetBox.pollPacket()) != null) {
            if (!replaced && p == oldPkt) {
                temp.addLast(newPkt);
                replaced = true;
            } else {
                temp.addLast(p);
            }
        }
        for (PacketModel q : temp) {
            targetBox.enqueue(q);
        }
        return replaced;
    }

    public void clear() {
        protectedMap.clear();
        VpnRevertHints.clearAllGlobal();
    }
}

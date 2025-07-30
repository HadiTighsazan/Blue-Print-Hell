package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.ProtectedPacket;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.PacketOps;
import com.blueprinthell.model.PortModel;
import com.blueprinthell.motion.KinematicsProfile;
import com.blueprinthell.motion.KinematicsRegistry;

import java.util.*;

public final class VpnBehavior implements SystemBehavior {

    private final SystemBoxModel box;
    private final double shieldCapacity;

    private final Map<PacketModel, PacketModel> protectedMap = new WeakHashMap<>();

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
        // --- Phase-2 semantics ---
        // 1) If packet is Protected already: do nothing.
        if (packet instanceof ProtectedPacket || PacketOps.isProtected(packet)) return;

        // 2) If packet is Confidential: keep it Confidential but assign VPN profile & tag.
        if (PacketOps.isConfidential(packet)) {
            KinematicsRegistry.setProfile(packet, KinematicsProfile.CONFIDENTIAL_VPN);
            // Tag so coinValue=4 applies without further wiring
            PacketOps.tag(packet, PacketOps.PacketTag.CONFIDENTIAL_VPN);
            return;
        }

        // 3) Otherwise (Messenger etc.): convert to Protected with shadow profile and mark mapping for revert.
        PacketModel prot = PacketOps.toProtected(packet, shieldCapacity);
        if (prot == packet) return;

        replaceInBuffer(packet, prot);
        protectedMap.put(prot, packet);
        VpnRevertHints.mark(prot, packet);

    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        if (enabled) return;

        List<Map.Entry<PacketModel, PacketModel>> snapshot = new ArrayList<>(protectedMap.entrySet());
        for (Map.Entry<PacketModel, PacketModel> e : snapshot) {
            PacketModel prot = e.getKey();
            PacketModel orig = e.getValue();

            if (!replaceInBufferIfPresent(box, prot, orig)) {
                VpnRevertHints.mark(prot, orig);
            }
            protectedMap.remove(prot);
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
    }
}

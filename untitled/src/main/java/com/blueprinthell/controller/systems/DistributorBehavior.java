package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.motion.KinematicsProfile;
import com.blueprinthell.motion.KinematicsRegistry;

import java.awt.*;
import java.util.*;

public final class DistributorBehavior implements SystemBehavior {

    private final SystemBoxModel     box;
    private final LargeGroupRegistry registry;
    private final PacketLossModel    lossModel;
    private final Random             rnd = new Random();

    public DistributorBehavior(SystemBoxModel box,
                               LargeGroupRegistry registry,
                               PacketLossModel lossModel) {
        this.box       = Objects.requireNonNull(box, "box");
        this.registry  = Objects.requireNonNull(registry, "registry");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
    }

    @Override public void update(double dt) { /* no-op */ }

    @Override public void onPacketEnqueued(PacketModel packet) {
        onPacketEnqueued(packet, null);
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (!(packet instanceof LargePacket lp)) return;
        splitLarge(lp);
    }

    @Override public void onEnabledChanged(boolean enabled) {  }

    private void splitLarge(LargePacket large) {
        int parentSize   = large.getOriginalSizeUnits();
        int expectedBits = parentSize;

        int groupId;
        int colorId;
        Color groupColor;

        if (!large.hasGroup()) {
            colorId = rnd.nextInt(360);
            groupColor = Color.getHSBColor(colorId / 360.0f, 0.8f, 0.9f); // اضافه شود
            groupId = registry.createGroup(parentSize, expectedBits, colorId);
            large.setGroupInfo(groupId, expectedBits, colorId);
            large.setCustomColor(groupColor);
        } else {
            groupId = large.getGroupId();
            colorId = large.getColorId();
            groupColor = large.getCustomColor();
            if (registry.get(groupId) == null) {
                registry.createGroupWithId(groupId, parentSize, expectedBits, colorId);
            }
        }

        removeFromBuffer(large);

        int lostBits = 0;
        for (int i = 0; i < expectedBits; i++) {
            BitPacket bit = new BitPacket(
                    large.getType(),
                    Config.DEFAULT_PACKET_SPEED,
                    groupId,
                    parentSize,
                    i,
                    colorId);

            // Phase-3: assign a random messenger profile to each bit
            KinematicsRegistry.setProfile(bit, randomMessengerProfile());

            boolean accepted = box.enqueue(bit);
            if (!accepted) {
                lostBits++;
            } else {
                registry.registerSplit(groupId, bit);
            }
        }

        if (lostBits > 0) {
            lossModel.incrementBy(lostBits);
            registry.markBitLost(groupId, lostBits);
        }
    }

    private void removeFromBuffer(PacketModel target) {
        Deque<PacketModel> tmp = new ArrayDeque<>();
        PacketModel p;
        boolean removed = false;
        while ((p = box.pollPacket()) != null) {
            if (!removed && p == target) {
                removed = true; // skip
            } else {
                tmp.addLast(p);
            }
        }
        for (PacketModel q : tmp) box.enqueue(q);
    }

    // Helper: choose a random messenger profile MSG1/MSG2/MSG3
    private KinematicsProfile randomMessengerProfile() {
        int r = rnd.nextInt(3);
        switch (r) {
            case 0: return KinematicsProfile.MSG1;
            case 1: return KinematicsProfile.MSG2;
            default: return KinematicsProfile.MSG3;
        }
    }
}

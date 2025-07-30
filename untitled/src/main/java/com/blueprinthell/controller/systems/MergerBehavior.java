package com.blueprinthell.controller.systems;

import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.model.large.LargeGroupRegistry.GroupState;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.motion.KinematicsRegistry;

import java.util.*;

public final class MergerBehavior implements SystemBehavior {

    private final SystemBoxModel      box;
    private final LargeGroupRegistry  registry;
    private final PacketLossModel     lossModel;

    public MergerBehavior(SystemBoxModel box,
                          LargeGroupRegistry registry,
                          PacketLossModel lossModel) {
        this.box       = Objects.requireNonNull(box, "box");
        this.registry  = Objects.requireNonNull(registry, "registry");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
    }

    @Override public void update(double dt) { /* no periodic logic */ }

    @Override
    public void onPacketEnqueued(PacketModel packet) {
        onPacketEnqueued(packet, null);
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (!(packet instanceof BitPacket bp)) return;

        GroupState st = registry.get(bp.getGroupId());
        if (st == null) return; // unknown group (defensive)

        boolean completed = registry.registerArrival(bp.getGroupId(), bp);
        bp.markRegisteredAtMerger();

        if (completed && st.isComplete()) {
            mergeGroup(st);
        }
    }

    @Override public void onEnabledChanged(boolean enabled) {  }

    private void mergeGroup(GroupState st) {
        int gid = st.groupId;

        // Extract all group's bits currently buffered in this box
        List<BitPacket> bits = extractBitsFromBuffer(gid);
        int collected = bits.size();
        if (collected < st.expectedBits) {
            // Defensive: registry says complete, but buffer doesn't hold all bits yet
            // Put them back at the front to preserve order and retry later
            for (int i = bits.size() - 1; i >= 0; i--) box.enqueueFront(bits.get(i));
            return;
        }

        PacketModel sample = bits.get(0);
        LargePacket large = LargePacket.fromSample(sample,
                st.originalSizeUnits,
                gid,
                st.expectedBits,
                st.colorId,
                true);
        KinematicsRegistry.copyProfile(sample, large);

        // Try to enqueue rebuilt large; if failed, count entire size as loss
        if (!box.enqueue(large)) {
            lossModel.incrementBy(st.originalSizeUnits);
        }

        // --- Phase-3 addition: partial-loss accounting for split-to-multiple-larges case ---
        // If design requires loss when N splits into multiple large packets of sizes n_i,
        // compute and add the registry-provided partial loss here.
        int partialLoss = registry.computeSimpleLoss(gid);
        if (partialLoss > 0) {
            lossModel.incrementBy(partialLoss);
        }

        // Close and remove group after merge
        registry.closeGroup(gid);
        registry.removeGroup(gid);
    }

    private void removePacketFromBuffer(PacketModel pkt) {
        Deque<PacketModel> tmp = new ArrayDeque<>();
        PacketModel p;
        while ((p = box.pollPacket()) != null) {
            if (p == pkt) continue; // skip
            tmp.addLast(p);
        }
        for (PacketModel q : tmp) box.enqueue(q);
    }

    private List<BitPacket> extractBitsFromBuffer(int groupId) {
        Deque<PacketModel> tmp = new ArrayDeque<>();
        List<BitPacket> out = new ArrayList<>();
        PacketModel p;
        while ((p = box.pollPacket()) != null) {
            if (p instanceof BitPacket bp && bp.getGroupId() == groupId) {
                out.add(bp);
            } else {
                tmp.addLast(p);
            }
        }
        for (PacketModel q : tmp) box.enqueue(q);
        return out;
    }
}

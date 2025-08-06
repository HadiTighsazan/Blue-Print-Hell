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

import java.awt.*;
import java.util.*;
import java.util.List;

public final class MergerBehavior implements SystemBehavior {

    private final SystemBoxModel      box;
    private final LargeGroupRegistry  registry;
    private final PacketLossModel     lossModel;

    // Track multiple large packets created from same original
    private final Map<Integer, List<Integer>> groupToMergedSizes = new HashMap<>();

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

        // Create merged large packet with size = number of bits collected
        PacketModel sample = bits.get(0);
        LargePacket large = LargePacket.fromSample(sample,
                st.originalSizeUnits,  // اندازه = تعداد بیت‌های جمع‌آوری شده
                gid,
                st.expectedBits,
                st.colorId,
                true);
        Color mergedColor = Color.getHSBColor(st.colorId / 360.0f, 0.8f, 0.9f);
        large.setCustomColor(mergedColor);
        KinematicsRegistry.copyProfile(sample, large);

        // Try to enqueue rebuilt large
        if (!box.enqueue(large)) {
            lossModel.incrementBy(collected);
        } else {
            // Track this merged packet size for loss calculation
            groupToMergedSizes.computeIfAbsent(gid, k -> new ArrayList<>()).add(collected);
        }

        // Check if all bits have been processed
        boolean allBitsProcessed = checkAllBitsProcessed(st);

        if (allBitsProcessed) {
            // Calculate loss using the formula
            calculateAndApplyLoss(gid, st.originalSizeUnits);

            // Close and remove group after all processing
            registry.closeGroup(gid);
            registry.removeGroup(gid);
            groupToMergedSizes.remove(gid);
        }
    }

    private boolean checkAllBitsProcessed(GroupState st) {
        // Check if all expected bits have been either:
        // 1. Merged into large packets
        // 2. Lost
        int totalProcessed = 0;
        List<Integer> mergedSizes = groupToMergedSizes.get(st.groupId);
        if (mergedSizes != null) {
            totalProcessed = mergedSizes.stream().mapToInt(Integer::intValue).sum();
        }
        totalProcessed += st.getLostBits();

        return totalProcessed >= st.expectedBits;
    }

    private void calculateAndApplyLoss(int groupId, int originalSize) {
        List<Integer> mergedSizes = groupToMergedSizes.get(groupId);
        if (mergedSizes == null || mergedSizes.isEmpty()) {
            // No successful merges, full loss
            lossModel.incrementBy(originalSize);
            return;
        }

        // Apply the formula: N - floor(k * (n1 * n2 * ... * nk)^(1/k))
        int N = originalSize;
        int k = mergedSizes.size();

        if (k == 1) {
            // Single merge: loss = N - n1
            int loss = N - mergedSizes.get(0);
            if (loss > 0) {
                lossModel.incrementBy(loss);
            }
        } else {
            // Multiple merges: use geometric mean formula
            double product = 1.0;
            for (int size : mergedSizes) {
                product *= size;
            }

            // Calculate k-th root of product
            double kthRoot = Math.pow(product, 1.0 / k);

            // Calculate floor(k * kthRoot)
            int restored = (int) Math.floor(k * kthRoot);

            // Calculate loss
            int loss = N - restored;

            if (loss > 0) {
                lossModel.incrementBy(loss);
            }
        }
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
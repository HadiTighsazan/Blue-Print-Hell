package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public final class SpyBehavior implements SystemBehavior, Updatable {

    private final SystemBoxModel box;
    private final BehaviorRegistry registry;
    private final PacketLossModel lossModel;
    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destMap;

    private final Random rnd = new Random();

    // Thread-safe transfer mechanism
    private static final ReentrantLock TRANSFER_LOCK = new ReentrantLock();
    private static final Set<PacketModel> PACKETS_IN_TRANSFER = Collections.newSetFromMap(new WeakHashMap<>());

    // Telemetry
    private long teleportCount = 0;
    private long destroyedConfidentialCount = 0;

    public SpyBehavior(SystemBoxModel box,
                       BehaviorRegistry registry,
                       PacketLossModel lossModel,
                       List<WireModel> wires,
                       Map<WireModel, SystemBoxModel> destMap) {
        this.box = Objects.requireNonNull(box, "box");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
        this.wires = Objects.requireNonNull(wires, "wires");
        this.destMap = Objects.requireNonNull(destMap, "destMap");
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (packet == null) return;

        // Handle VPN revert if needed
        if (PacketOps.isProtected(packet)) {
            PacketModel origGlobal = VpnRevertHints.consumeGlobal(packet);
            if (origGlobal != null) {
                replaceInBuffer(packet, origGlobal);
                packet = origGlobal;
            }
        }

        // Destroy confidential packets
        if (PacketOps.isConfidential(packet)) {
            if (box.removeFromBuffer(packet)) {
                destroyedConfidentialCount++;
                lossModel.increment();
            }
            return;
        }

        // Skip if not from input port
        if (enteredPort == null || !enteredPort.isInput()) {
            return;
        }

        // Thread-safe transfer check
        TRANSFER_LOCK.lock();
        try {
            if (PACKETS_IN_TRANSFER.contains(packet)) {
                return; // Already being processed
            }
            PACKETS_IN_TRANSFER.add(packet);
        } finally {
            TRANSFER_LOCK.unlock();
        }

        // Find another spy system
        final SystemBoxModel target = chooseAnotherSpy();
        if (target != null && transferToAnotherSpy(packet, target)) {
            teleportCount++;
        }

        // Clean up tracking
        TRANSFER_LOCK.lock();
        try {
            PACKETS_IN_TRANSFER.remove(packet);
        } finally {
            TRANSFER_LOCK.unlock();
        }
    }

    /**
     * Smart selection of another spy system
     * Considers: availability, buffer space, and outbound connections
     */
    private SystemBoxModel chooseAnotherSpy() {
        final List<SpyCandidate> candidates = new ArrayList<>();

        // Find all spy systems except self
        for (Map.Entry<SystemBoxModel, List<SystemBehavior>> e : registry.view().entrySet()) {
            final SystemBoxModel candidate = e.getKey();
            if (candidate == box) continue;

            final List<SystemBehavior> behaviors = e.getValue();
            if (behaviors == null) continue;

            for (SystemBehavior b : behaviors) {
                if (b instanceof SpyBehavior) {
                    // Check if candidate has usable outbound connections
                    if (hasUsableOutbound(candidate)) {
                        int bufferSpace = Config.MAX_BUFFER_CAPACITY - candidate.getBuffer().size();
                        int score = calculateSpyScore(candidate, bufferSpace);
                        candidates.add(new SpyCandidate(candidate, score));
                    }
                    break;
                }
            }
        }

        if (candidates.isEmpty()) return null;

        // Sort by score (higher is better)
        candidates.sort((a, b) -> Integer.compare(b.score, a.score));

        // Weighted random selection from top candidates
        int topCount = Math.min(3, candidates.size());
        List<SpyCandidate> topCandidates = candidates.subList(0, topCount);

        // Calculate total weight
        int totalWeight = topCandidates.stream().mapToInt(c -> c.score).sum();
        if (totalWeight <= 0) return topCandidates.get(0).box;

        // Weighted random selection
        int random = rnd.nextInt(totalWeight);
        int cumulative = 0;
        for (SpyCandidate c : topCandidates) {
            cumulative += c.score;
            if (random < cumulative) {
                return c.box;
            }
        }

        return topCandidates.get(0).box;
    }

    private int calculateSpyScore(SystemBoxModel candidate, int bufferSpace) {
        int score = 0;

        // Buffer space (more space = higher score)
        score += bufferSpace * 10;

        // Number of outbound connections
        int outboundCount = 0;
        for (WireModel w : wires) {
            if (w.getSrcPort() != null && candidate.getOutPorts().contains(w.getSrcPort())) {
                outboundCount++;
                // Check if destination is enabled
                SystemBoxModel dest = destMap.get(w);
                if (dest != null && dest.isEnabled()) {
                    score += 5; // Bonus for enabled destination
                }
            }
        }
        score += outboundCount * 3;

        // Penalty if disabled
        if (!candidate.isEnabled()) {
            score -= 50;
        }

        return Math.max(0, score);
    }

    private boolean hasUsableOutbound(SystemBoxModel system) {
        if (system.getOutPorts() == null || system.getOutPorts().isEmpty()) return false;

        for (WireModel w : wires) {
            if (w == null) continue;
            PortModel src = w.getSrcPort();
            if (src != null && system.getOutPorts().contains(src)) {
                // Check if destination is reachable
                SystemBoxModel dest = destMap.get(w);
                if (dest != null && dest.isEnabled()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean transferToAnotherSpy(PacketModel packet, SystemBoxModel target) {
        if (packet == null || target == null) return false;

        boolean removed = false;
        boolean enqueued = false;

        TRANSFER_LOCK.lock();
        try {
            removed = box.removeFromBuffer(packet);
            if (removed) {
                enqueued = target.enqueue(packet, null);
                if (!enqueued) {
                    // If enqueue failed, put it back
                    box.enqueueFront(packet);
                    removed = false;
                }
            }
        } finally {
            TRANSFER_LOCK.unlock();
        }

        return removed && enqueued;
    }

    private void replaceInBuffer(PacketModel oldPkt, PacketModel newPkt) {
        Deque<PacketModel> temp = new ArrayDeque<>();
        boolean replaced = false;
        PacketModel p;

        TRANSFER_LOCK.lock();
        try {
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
        } finally {
            TRANSFER_LOCK.unlock();
        }
    }

    @Override
    public void update(double dt) {
        // No periodic updates needed
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        // No special handling needed
    }

    public void clear() {
        TRANSFER_LOCK.lock();
        try {
            PACKETS_IN_TRANSFER.clear();
        } finally {
            TRANSFER_LOCK.unlock();
        }
    }

    // Helper class for spy candidate scoring
    private static class SpyCandidate {
        final SystemBoxModel box;
        final int score;

        SpyCandidate(SystemBoxModel box, int score) {
            this.box = box;
            this.score = score;
        }
    }

    // Telemetry getters
    public long getTeleportCount() { return teleportCount; }
    public long getDestroyedConfidentialCount() { return destroyedConfidentialCount; }
}
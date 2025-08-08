package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;
import com.blueprinthell.motion.MotionStrategy;
import com.blueprinthell.motion.MotionStrategyFactory;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SpyBehavior V3 - Enhanced with proper teleport processing
 */
public final class SpyBehavior implements SystemBehavior {

    private final SystemBoxModel box;
    private final BehaviorRegistry registry;
    private final PacketLossModel lossModel;
    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destMap;
    private final Random rnd = new Random();

    // Thread-safe transfer mechanism
    private static final ReentrantLock TRANSFER_LOCK = new ReentrantLock();
    private static final Set<PacketModel> PACKETS_IN_TRANSFER = Collections.newSetFromMap(new WeakHashMap<>());

    // Global teleported packets queue
    private static final Map<SystemBoxModel, Queue<PacketModel>> TELEPORTED_PACKETS = new HashMap<>();

    // Telemetry
    private long teleportCount = 0;
    private long destroyedConfidentialCount = 0;
    private long teleportAttempts = 0;
    private long teleportFailures = 0;

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
    public void update(double dt) {
        // CRITICAL: Process any teleported packets that arrived at this spy
        processTeleportedPacketsForThisBox();
    }

    /**
     * Process teleported packets for THIS specific spy box
     */
    private void processTeleportedPacketsForThisBox() {
        TRANSFER_LOCK.lock();
        try {
            Queue<PacketModel> myTeleportedPackets = TELEPORTED_PACKETS.get(box);
            if (myTeleportedPackets == null || myTeleportedPackets.isEmpty()) {
                return;
            }

            // Process all teleported packets
            List<PacketModel> toProcess = new ArrayList<>(myTeleportedPackets);
            myTeleportedPackets.clear(); // Clear the queue after copying

            for (PacketModel packet : toProcess) {

                // Try to route the teleported packet
                if (!routeTeleportedPacket(packet)) {
                    // If routing failed, put it in the buffer
                    if (!box.enqueue(packet)) {
                        lossModel.incrementPacket(packet);
                    } else {
                    }
                }
            }
        } finally {
            TRANSFER_LOCK.unlock();
        }
    }

    /**
     * Route a teleported packet directly to an output wire
     */
    private boolean routeTeleportedPacket(PacketModel packet) {
        // Find available output ports
        List<PortModel> availableOuts = new ArrayList<>();

        for (PortModel port : box.getOutPorts()) {
            WireModel wire = findWireForPort(port);
            if (wire != null) {
                SystemBoxModel dest = destMap.get(wire);
                if (dest != null && dest.isEnabled()) {
                    // Check if wire is not too crowded
                    if (wire.getPackets().size() < 3) {
                        availableOuts.add(port);
                    }
                }
            }
        }

        if (availableOuts.isEmpty()) {
            return false;
        }

        // Choose a random available port
        PortModel chosenPort = availableOuts.get(rnd.nextInt(availableOuts.size()));
        WireModel chosenWire = findWireForPort(chosenPort);

        if (chosenWire == null) {
            return false;
        }

        // Attach packet to wire
        boolean compatible = chosenPort.isCompatible(packet);
        packet.setStartSpeedMul(1.0);

        MotionStrategy ms = MotionStrategyFactory.create(packet, compatible);
        packet.setMotionStrategy(ms);

        chosenWire.attachPacket(packet, 0.0);

        return true;
    }

    // در SpyBehavior.java - تغییرات نهایی:

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
                lossModel.incrementPacket(packet);
            }
            return;
        }

        // CRITICAL: Don't re-teleport already teleported packets
        if (TeleportTracking.isTeleported(packet)) {
            return;
        }

        // Only teleport packets that entered through input port
        if (enteredPort == null || !enteredPort.isInput()) {
            return;
        }

        // Attempt teleport
        teleportAttempts++;
        performTeleport(packet);
    }

    private boolean transferToAnotherSpyDirect(PacketModel packet, SystemBoxModel target) {
        if (packet == null || target == null) {
            return false;
        }

        TRANSFER_LOCK.lock();
        try {
            // Mark packet as teleported
            TeleportTracking.markTeleported(packet);

            // Add directly to target's buffer (not to teleport queue)
            // This ensures it's processed by router immediately
            boolean added = target.enqueue(packet);

            if (added) {
                TeleportTracking.markTeleported(packet);
                return true;
            } else {
                // Unmark if failed
                TeleportTracking.clearTeleported(packet);
                return false;
            }

        } finally {
            TRANSFER_LOCK.unlock();
        }
    }
    private void performTeleport(PacketModel packet) {
        // Find another spy system
        final SystemBoxModel targetSpy = chooseAnotherSpy();

        if (targetSpy == null) {
            teleportFailures++;
            return;
        }


        // CRITICAL: Remove packet from buffer BEFORE transferring
        if (!box.removeFromBuffer(packet)) {
            teleportFailures++;
            return;
        }

        if (transferToAnotherSpyDirect(packet, targetSpy)) {
            teleportCount++;
        } else {
            // If transfer failed, put packet back in buffer
            box.enqueue(packet);
            teleportFailures++;
        }
    }




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
                    // Check if candidate is enabled
                    if (!candidate.isEnabled()) {
                        continue;
                    }

                    // Check if candidate has usable outbound connections

                    int bufferSpace = Config.MAX_BUFFER_CAPACITY - candidate.getBuffer().size();
                    int score = calculateSpyScore(candidate, bufferSpace);
                    candidates.add(new SpyCandidate(candidate, score));

                    break;
                }
            }
        }



        if (candidates.isEmpty()) return null;

        // Sort by score (higher is better)
        candidates.sort((a, b) -> Integer.compare(b.score, a.score));

        // Return best candidate
        return candidates.get(0).box;
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
                SystemBoxModel dest = destMap.get(w);
                if (dest != null && dest.isEnabled()) {
                    score += 5; // Bonus for enabled destination
                }
            }
        }
        score += outboundCount * 3;

        return Math.max(1, score);
    }

    private boolean hasUsableOutbound(SystemBoxModel system) {
        if (system.getOutPorts() == null || system.getOutPorts().isEmpty()) {
            return false;
        }

        for (WireModel w : wires) {
            if (w == null) continue;
            PortModel src = w.getSrcPort();
            if (src != null && system.getOutPorts().contains(src)) {
                SystemBoxModel dest = destMap.get(w);
                if (dest != null && dest.isEnabled()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Transfer packet to another spy system's queue
     */
    private boolean transferToAnotherSpy(PacketModel packet, SystemBoxModel target) {
        if (packet == null || target == null) {
            return false;
        }

        TRANSFER_LOCK.lock();
        try {
            // Remove from current buffer
            boolean removed = box.removeFromBuffer(packet);
            if (!removed) {
                return false;
            }

            // Add to target's teleported queue
            Queue<PacketModel> targetQueue = TELEPORTED_PACKETS.computeIfAbsent(
                    target, k -> new LinkedList<>()
            );
            targetQueue.offer(packet);

            return true;

        } finally {
            TRANSFER_LOCK.unlock();
        }
    }

    private WireModel findWireForPort(PortModel port) {
        for (WireModel wire : wires) {
            if (wire.getSrcPort() == port) {
                return wire;
            }
        }
        return null;
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
    public void onEnabledChanged(boolean enabled) {
        // Clear teleport queue if disabled
        if (!enabled) {
            TRANSFER_LOCK.lock();
            try {
                TELEPORTED_PACKETS.remove(box);
            } finally {
                TRANSFER_LOCK.unlock();
            }
        }
    }

    public void clear() {
        TRANSFER_LOCK.lock();
        try {
            PACKETS_IN_TRANSFER.clear();
            TELEPORTED_PACKETS.clear();
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
    public long getTeleportAttempts() { return teleportAttempts; }
    public long getTeleportFailures() { return teleportFailures; }


}
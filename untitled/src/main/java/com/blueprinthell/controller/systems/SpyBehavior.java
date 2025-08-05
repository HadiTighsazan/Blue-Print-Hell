package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;
import com.blueprinthell.motion.MotionStrategy;
import com.blueprinthell.motion.MotionStrategyFactory;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SpyBehavior V2 - با قابلیت تله‌پورت کامل و مسیریابی خودکار
 */
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

    // Teleported packets tracking
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
        // Process any teleported packets waiting to be routed
        processTeleportedPackets();
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (packet == null) return;

        System.out.println("[SPY] Packet entered: " + packet.getType() +
                " via port: " + (enteredPort != null ? "YES" : "NO") +
                ", is input: " + (enteredPort != null ? enteredPort.isInput() : "N/A"));

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
                System.out.println("[SPY] Destroyed confidential packet");
            }
            return;
        }

        // Only teleport packets that entered through input port
        if (enteredPort == null || !enteredPort.isInput()) {
            System.out.println("[SPY] Not from input port, skipping teleport");
            return;
        }

        // Thread-safe transfer check
        TRANSFER_LOCK.lock();
        try {
            if (PACKETS_IN_TRANSFER.contains(packet)) {
                System.out.println("[SPY] Packet already in transfer");
                return;
            }
            PACKETS_IN_TRANSFER.add(packet);
        } finally {
            TRANSFER_LOCK.unlock();
        }

        // Attempt teleport
        teleportAttempts++;

        // Find another spy system
        final SystemBoxModel target = chooseAnotherSpy();

        if (target == null) {
            System.out.println("[SPY] No other spy systems found!");
            teleportFailures++;
        } else {
            System.out.println("[SPY] Found target spy system, attempting transfer...");

            if (transferToAnotherSpy(packet, target)) {
                teleportCount++;
                System.out.println("[SPY] ✓ Teleport successful! Total: " + teleportCount);
            } else {
                teleportFailures++;
                System.out.println("[SPY] ✗ Teleport failed!");
            }
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
     * Process teleported packets and route them to output
     */
    private void processTeleportedPackets() {
        Queue<PacketModel> teleported = TELEPORTED_PACKETS.get(box);
        if (teleported == null || teleported.isEmpty()) {
            return;
        }

        // Process each teleported packet
        while (!teleported.isEmpty()) {
            PacketModel packet = teleported.poll();

            System.out.println("[SPY] Processing teleported packet: " + packet.getType());

            // Find an available output port
            PortModel outputPort = findAvailableOutputPort();
            if (outputPort == null) {
                // No available output, keep in buffer
                box.enqueue(packet);
                System.out.println("[SPY] No output available, keeping in buffer");
                continue;
            }

            // Find the wire for this output port
            WireModel wire = findWireForPort(outputPort);
            if (wire == null) {
                // No wire connected, keep in buffer
                box.enqueue(packet);
                System.out.println("[SPY] No wire connected to output port");
                continue;
            }

            // Route the packet directly to the wire
            boolean compatible = outputPort.isCompatible(packet);
            packet.setStartSpeedMul(1.0);

            MotionStrategy ms = MotionStrategyFactory.create(packet, compatible);
            packet.setMotionStrategy(ms);

            wire.attachPacket(packet, 0.0);

            System.out.println("[SPY] ✓ Routed teleported packet to wire");
        }
    }

    /**
     * Find an available output port with empty wire
     */
    private PortModel findAvailableOutputPort() {
        for (PortModel port : box.getOutPorts()) {
            WireModel wire = findWireForPort(port);
            if (wire != null && wire.getPackets().isEmpty()) {
                SystemBoxModel dest = destMap.get(wire);
                if (dest != null && dest.isEnabled()) {
                    return port;
                }
            }
        }
        return null;
    }

    /**
     * Find wire connected to a specific port
     */
    private WireModel findWireForPort(PortModel port) {
        for (WireModel wire : wires) {
            if (wire.getSrcPort() == port) {
                return wire;
            }
        }
        return null;
    }

    /**
     * Smart selection of another spy system
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
                    // Check if candidate is enabled
                    if (!candidate.isEnabled()) {
                        continue;
                    }

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

        System.out.println("[SPY] Found " + candidates.size() + " valid spy candidates");

        if (candidates.isEmpty()) return null;

        // Sort by score (higher is better)
        candidates.sort((a, b) -> Integer.compare(b.score, a.score));

        // Return best candidate (or random from top 3)
        if (candidates.size() == 1) {
            return candidates.get(0).box;
        }

        int topCount = Math.min(3, candidates.size());
        return candidates.get(rnd.nextInt(topCount)).box;
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
     * Transfer packet to another spy system
     */
    private boolean transferToAnotherSpy(PacketModel packet, SystemBoxModel target) {
        if (packet == null || target == null) {
            return false;
        }

        boolean removed = false;

        TRANSFER_LOCK.lock();
        try {
            // Remove from current buffer
            removed = box.removeFromBuffer(packet);
            if (!removed) {
                System.out.println("[SPY] Failed to remove packet from source buffer");
                return false;
            }

            // Add to teleported queue of target system
            Queue<PacketModel> targetQueue = TELEPORTED_PACKETS.computeIfAbsent(
                    target, k -> new LinkedList<>()
            );
            targetQueue.offer(packet);

            System.out.println("[SPY] Packet added to teleport queue of target system");

        } finally {
            TRANSFER_LOCK.unlock();
        }

        return true;
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
            TELEPORTED_PACKETS.remove(box);
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

    public void printStats() {
        System.out.println("[SPY STATS] Attempts: " + teleportAttempts +
                ", Success: " + teleportCount +
                ", Failures: " + teleportFailures +
                ", Destroyed: " + destroyedConfidentialCount);
    }
}
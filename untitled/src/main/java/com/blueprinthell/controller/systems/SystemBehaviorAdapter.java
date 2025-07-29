package com.blueprinthell.controller.systems;

import com.blueprinthell.model.*;

import java.util.*;

public final class SystemBehaviorAdapter implements Updatable {

    private final SystemBoxModel box;
    private final SystemBehavior behavior;

    /** Tracks which PacketModel instances have already been reported to the behavior. */
    private final Set<PacketModel> seen = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Cache to avoid rebuilding port lists every call. */
    private volatile java.util.List<PortModel> cachedInPorts = null;
    private volatile java.util.List<PortModel> cachedOutPorts = null;

    private boolean lastEnabledState;

    public SystemBehaviorAdapter(SystemBoxModel box, SystemBehavior behavior) {
        this.box = Objects.requireNonNull(box, "box");
        this.behavior = Objects.requireNonNull(behavior, "behavior");
        this.lastEnabledState = box.isEnabled();
    }

    @Override
    public void update(double dt) {
        // 1) Propagate enable/disable transitions
        checkEnabledState();
        // 2) Detect newly enqueued packets and emit entry events
        checkNewPackets();
    }

    /**
     * Emits onPacketEnqueued exactly once per packet entry.
     * Also prunes the internal 'seen' set for packets that have left the buffer to avoid leaks.
     */
    public void checkNewPackets() {
        final Queue<PacketModel> buf = box.getBuffer();
        if (buf == null || buf.isEmpty()) {
            // prune all if buffer empty
            if (!seen.isEmpty()) seen.clear();
            return;
        }

        // Build a temporary identity set of what is currently in the buffer (no allocations per packet beyond identity map entry)
        final Set<PacketModel> current = Collections.newSetFromMap(new IdentityHashMap<>(Math.max(16, buf.size() * 2)));
        for (PacketModel p : buf) {
            current.add(p);
            if (!seen.contains(p)) {
                // Newly observed in the buffer -> report once
                final PortModel entered = findEnteredPort(p);
                try {
                    behavior.onPacketEnqueued(p, entered);
                } catch (Throwable t) {
                    // Behaviors are user logic; isolate failures to not break the simulation loop
                    // Consider logging via your logging facility
                    // e.g., Logger.warn("Behavior onPacketEnqueued failed", t);
                }
                seen.add(p);
            }
        }
        // Prune 'seen' entries that are no longer present in the buffer
        if (seen.size() != current.size() || !seen.containsAll(current)) {
            seen.retainAll(current);
        }
    }

    /**
     * Best-effort resolution of the enteredPort for a freshly-enqueued packet.
     * Resolution order:
     * <ol>
     *   <li>Use {@link EnteredPortTracker#consume(PacketModel)} if present (exact, preferred).</li>
     *   <li>Use packet.getCurrentWire().getDstPort() if it belongs to this box.</li>
     *   <li>Return null when unknown; behaviors should tolerate null (e.g., use one-shot guards).</li>
     * </ol>
     */
    public PortModel findEnteredPort(PacketModel packet) {
        if (packet == null) return null;

        // 1) Exact path: tracker populated by SystemBoxModel.enqueue
        final PortModel tracked = EnteredPortTracker.consume(packet);
        if (tracked != null) return tracked;

        // 2) Heuristic: the packet may still reference its current wire
        try {
            final WireModel w = packet.getCurrentWire();
            if (w != null) {
                final PortModel dst = w.getDstPort();
                if (dst != null && belongsToBox(dst)) {
                    return dst;
                }
            }
        } catch (Throwable ignore) {
            // Be conservative; simply return null and let behavior decide.
        }

        // 3) Unknown
        return null;
    }

    /**
     * Emits onEnabledChanged when the box enable state flips.
     */
    public void checkEnabledState() {
        final boolean enabled = box.isEnabled();
        if (enabled != lastEnabledState) {
            try {
                behavior.onEnabledChanged(enabled);
            } catch (Throwable t) {
                // Swallow to keep loop robust; consider logging
            }
            lastEnabledState = enabled;
        }
    }

    // ------------------------- helpers -------------------------

    private boolean belongsToBox(PortModel p) {
        if (p == null) return false;
        // Cache to reduce list allocations on every call
        java.util.List<PortModel> ins = cachedInPorts;
        java.util.List<PortModel> outs = cachedOutPorts;
        if (ins == null) {
            ins = box.getInPorts();
            cachedInPorts = ins;
        }
        if (outs == null) {
            outs = box.getOutPorts();
            cachedOutPorts = outs;
        }
        return (ins != null && ins.contains(p)) || (outs != null && outs.contains(p));
    }

    // --------------------- integration hook ---------------------

    /**
     * Lightweight tracker for mapping a packet to the port through which it entered a box.
     * <p>
     * Usage (recommended): in {@code SystemBoxModel.enqueue(packet, enteredPort)} call
     * {@code EnteredPortTracker.record(packet, enteredPort);} right before enqueuing.
     * Then {@link #findEnteredPort(PacketModel)} will return the exact port.
     * </p>
     * The map is a WeakHashMap keyed by PacketModel to avoid retaining packets after they leave.
     */
    public static final class EnteredPortTracker {
        private static final WeakHashMap<PacketModel, PortModel> MAP = new WeakHashMap<>();

        private EnteredPortTracker() {}

        /** Record the port a packet entered from (call-site: SystemBoxModel.enqueue). */
        public static void record(PacketModel p, PortModel port) {
            if (p == null || port == null) return;
            MAP.put(p, port);
        }

        /** Fetch and consume the recorded port for a packet. */
        public static PortModel consume(PacketModel p) {
            if (p == null) return null;
            return MAP.remove(p);
        }

        /** Clear all hints (e.g., at level resets). */
        public static void clear() {
            MAP.clear();
        }
    }
}

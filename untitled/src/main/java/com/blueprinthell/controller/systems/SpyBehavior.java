package com.blueprinthell.controller.systems;

import com.blueprinthell.model.*;

import java.util.*;


public final class SpyBehavior implements SystemBehavior, Updatable {

    private final SystemBoxModel box;
    private final BehaviorRegistry registry;
    private final PacketLossModel lossModel;
    private final List<WireModel> wires;              // for optional reachability checks
    private final Map<WireModel, SystemBoxModel> destMap; // unused for core behavior but kept for future use

    private final Random rnd = new Random();

    // Debug/telemetry
    private long teleportCount = 0;
    private long destroyedConfidentialCount = 0;
    private volatile boolean lastEnabledState = true;

    public SpyBehavior(SystemBoxModel box,
                       BehaviorRegistry registry,
                       PacketLossModel lossModel,
                       List<WireModel> wires,
                       Map<WireModel, SystemBoxModel> destMap) {
        this.box = Objects.requireNonNull(box, "box");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
        this.wires = Objects.requireNonNullElseGet(wires, ArrayList::new);
        this.destMap = Objects.requireNonNullElseGet(destMap, HashMap::new);
    }


    @Override
    public void onPacketEnqueued(PacketModel packet) {
        // Backwards-compat overload: treat as programmatic entry (no physical port)
        onPacketEnqueued(packet, null);
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (packet == null) return;

        // 1) Protected packets are immune
        if (PacketOps.isProtected(packet)) {
            return;
        }

        // 2) Confidential packets are destroyed on entry
        if (PacketOps.isConfidential(packet)) {
            // Remove from this box and count as loss
            if (box.removeFromBuffer(packet)) {
                destroyedConfidentialCount++;
                lossModel.increment();
            }
            return;
        }

        // 3) Teleport once per physical entry only (avoid chains):
        //    Only react when we know this was an external arrival via an input port
        if (enteredPort == null || !enteredPort.isInput()) {
            return; // programmatic re-queue or output-side events -> ignore
        }

        // 4) Choose a different spy box to teleport to
        final SystemBoxModel target = chooseAnotherSpy();
        if (target == null) {
            return; // no other spies -> no-op
        }

        // 5) Transfer packet to the target spy (enteredPort == null to avoid re-trigger)
        if (transferToAnotherSpy(packet, target)) {
            teleportCount++;
        }
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        lastEnabledState = enabled;
        // No special state to clear; behavior is event-driven
    }


    @Override
    public void update(double dt) {
        // SpyBehavior is event-driven via onPacketEnqueued. No per-tick work required.
    }


    private SystemBoxModel chooseAnotherSpy() {
        final List<SystemBoxModel> candidates = new ArrayList<>();
        try {
            // registry.view() is expected to expose all (box -> behaviors) mappings
            for (Map.Entry<SystemBoxModel, List<SystemBehavior>> e : registry.view().entrySet()) {
                final SystemBoxModel candidate = e.getKey();
                if (candidate == box) continue;
                final List<SystemBehavior> bs = e.getValue();
                if (bs == null) continue;
                for (SystemBehavior b : bs) {
                    if (b instanceof SpyBehavior) {
                        // Optional: require at least one outbound wire to avoid dead-ends
                        if (hasUsableOutbound(candidate)) {
                            candidates.add(candidate);
                        } else {
                            // fallback: still accept, but lower priority if there are better ones
                        }
                        break;
                    }
                }
            }
        } catch (Throwable ignore) {
            // Be conservative: if registry.view() contract changes, just return null.
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(rnd.nextInt(candidates.size()));
    }

    private boolean hasUsableOutbound(SystemBoxModel system) {
        // Minimal heuristic: has at least one out port, and ideally a wire starting from it
        if (system.getOutPorts() == null || system.getOutPorts().isEmpty()) return false;
        // If wires are provided, check for at least one outgoing wire
        if (wires != null && !wires.isEmpty()) {
            for (WireModel w : wires) {
                if (w == null) continue;
                PortModel src = w.getSrcPort();
                if (src != null && system.getOutPorts().contains(src)) {
                    return true;
                }
            }
            return false;
        }
        return true; // no wire list -> assume usable
    }

    /** Transfer packet from this box to the target spy box. */
    private boolean transferToAnotherSpy(PacketModel packet, SystemBoxModel target) {
        if (packet == null || target == null) return false;
        // Remove from source first; if enqueue at target fails, put it back at the front to preserve order
        if (!box.removeFromBuffer(packet)) {
            return false; // nothing to do
        }
        final boolean ok = target.enqueue(packet, null); // programmatic entry; will not retrigger spy teleport
        if (!ok) {
            // Restore to source at the front (best-effort)
            box.enqueueFront(packet);
            return false;
        }
        return true;
    }



    public void clear() {
        // no internal collections to clear
    }
}

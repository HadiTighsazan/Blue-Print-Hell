package com.blueprinthell.controller.systems;

import com.blueprinthell.model.*;

import java.util.*;

public final class SpyBehavior implements SystemBehavior, Updatable {

    private final SystemBoxModel box;
    private final BehaviorRegistry registry;
    private final PacketLossModel lossModel;
    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destMap;

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
        onPacketEnqueued(packet, null);
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (packet == null) return;

        // --- Phase-2 addition: revert Protected back to original if mapping exists
        if (PacketOps.isProtected(packet)) {
            PacketModel orig = VpnRevertHints.consume(packet);
            if (orig != null) {
                // Replace in this box's buffer to preserve order
                replaceInBuffer(packet, orig);
                return; // after revert, no teleport in the same cycle
            }
            // If no mapping, Protected passes unaffected per spec
            return;
        }

        // Confidential packets are destroyed (count as loss)
        if (PacketOps.isConfidential(packet)) {
            if (box.removeFromBuffer(packet)) {
                destroyedConfidentialCount++;
                lossModel.increment();
            }
            return;
        }

        if (enteredPort == null || !enteredPort.isInput()) {
            return; // programmatic re-queue or output-side events -> ignore
        }

        final SystemBoxModel target = chooseAnotherSpy();
        if (target == null) {
            return; // no other spies -> no-op
        }

        if (transferToAnotherSpy(packet, target)) {
            teleportCount++;
        }
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        lastEnabledState = enabled;
    }


    @Override
    public void update(double dt) {
    }

    private SystemBoxModel chooseAnotherSpy() {
        final List<SystemBoxModel> candidates = new ArrayList<>();
        try {
            for (Map.Entry<SystemBoxModel, List<SystemBehavior>> e : registry.view().entrySet()) {
                final SystemBoxModel candidate = e.getKey();
                if (candidate == box) continue;
                final List<SystemBehavior> bs = e.getValue();
                if (bs == null) continue;
                for (SystemBehavior b : bs) {
                    if (b instanceof SpyBehavior) {
                        if (hasUsableOutbound(candidate)) {
                            candidates.add(candidate);
                        } else {
                        }
                        break;
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(rnd.nextInt(candidates.size()));
    }

    private boolean hasUsableOutbound(SystemBoxModel system) {
        if (system.getOutPorts() == null || system.getOutPorts().isEmpty()) return false;
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

    // Utility: replace a packet in the current box buffer with another one (preserve ordering)
    private void replaceInBuffer(PacketModel oldPkt, PacketModel newPkt) {
        Deque<PacketModel> temp = new ArrayDeque<>();
        boolean replaced = false;
        PacketModel p;
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
    }

    public void clear() {
        // no internal collections to clear
    }
}

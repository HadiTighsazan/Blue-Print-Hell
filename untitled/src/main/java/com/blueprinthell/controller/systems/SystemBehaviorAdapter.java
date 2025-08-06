package com.blueprinthell.controller.systems;

import com.blueprinthell.model.*;
import java.util.*;

public final class SystemBehaviorAdapter implements Updatable {

    private final SystemBoxModel box;
    private final SystemBehavior behavior;
    private final Set<PacketModel> seen = Collections.newSetFromMap(new IdentityHashMap<>());
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
        // 1) Check enable/disable transitions
        checkEnabledState();

        // 2) Detect newly enqueued packets
        checkNewPackets();

        // 3) CRITICAL FIX: Propagate update to behavior
        // This ensures SpyBehavior.update() gets called for processing teleported packets
        try {
            behavior.update(dt);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void checkNewPackets() {
        final Queue<PacketModel> buf = box.getBuffer();
        if (buf == null || buf.isEmpty()) {
            seen.clear();
            return;
        }

        final PacketModel[] snapshot = buf.toArray(new PacketModel[0]);
        final Set<PacketModel> current = Collections.newSetFromMap(
                new IdentityHashMap<>(Math.max(16, snapshot.length * 2)));

        for (PacketModel p : snapshot) {
            if (p == null) continue;
            current.add(p);
            if (!seen.contains(p)) {
                final PortModel entered = findEnteredPort(p);
                try {
                    behavior.onPacketEnqueued(p, entered);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }

        seen.retainAll(current);
        for (PacketModel p : snapshot) {
            if (p != null) seen.add(p);
        }
    }

    public PortModel findEnteredPort(PacketModel packet) {
        if (packet == null) return null;

        // 1) Check tracked port
        final PortModel tracked = EnteredPortTracker.peek(packet);
        if (tracked != null) {
            return tracked;
        }

        // 2) Check current wire
        try {
            final WireModel w = packet.getCurrentWire();
            if (w != null) {
                final PortModel dst = w.getDstPort();
                if (dst != null && belongsToBox(dst)) {
                    return dst;
                }
            }
        } catch (Throwable ignore) {}

        return null;
    }

    public void checkEnabledState() {
        final boolean enabled = box.isEnabled();
        if (enabled != lastEnabledState) {
            try {
                behavior.onEnabledChanged(enabled);
            } catch (Throwable t) {
                // Log but continue
            }
            lastEnabledState = enabled;
        }
    }

    private boolean belongsToBox(PortModel p) {
        if (p == null) return false;
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

    public static final class EnteredPortTracker {
        private static final WeakHashMap<PacketModel, PortModel> MAP = new WeakHashMap<>();

        private EnteredPortTracker() {}

        public static void record(PacketModel p, PortModel port) {
            if (p == null) return;
            final PortModel last = MAP.get(p);
            if (last == port) return;
            MAP.put(p, port);
        }

        public static PortModel peek(PacketModel p) {
            if (p == null) return null;
            return MAP.get(p);
        }

        public static PortModel consume(PacketModel p) {
            if (p == null) return null;
            return MAP.remove(p);
        }

        public static void clearPacket(PacketModel p) {
            if (p != null) MAP.remove(p);
        }

        public static void clear() {
            MAP.clear();
        }
    }
}
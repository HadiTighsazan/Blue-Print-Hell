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
        try { behavior.update(dt); } catch (Throwable t) { t.printStackTrace(); }
    }

    /**
     * Emits onPacketEnqueued exactly once per packet entry.
     * Also prunes the internal 'seen' set for packets that have left the buffer to avoid leaks.
     */
    public void checkNewPackets() {
        final Queue<PacketModel> buf = box.getBuffer();
        if (buf == null || buf.isEmpty()) {
            seen.clear();
            return;
        }
// snapshot to avoid CME (fail-fast iterator on ArrayDeque)
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
                    t.printStackTrace(); // حداقل لاگ کن که حلقه‌ی شبیه‌سازی نمیره هوا
                }
            }
        }
// پاکسازی دیده‌ها
        seen.retainAll(current);
// و سپس هر چی الان توی بافره رو به عنوان دیده‌شده علامت بزن
        for (PacketModel p : snapshot) if (p != null) seen.add(p);

    }


    public PortModel findEnteredPort(PacketModel packet) {
        if (packet == null) return null;

        // 1) Exact path: از tracker استفاده کن اما consume نکن
        final PortModel tracked = EnteredPortTracker.peek(packet);
        if (tracked != null) {
            System.out.println("[ADAPTER] Found tracked port for packet: " + packet.getType());
            return tracked;
        }

        // 2) Heuristic: بررسی wire فعلی پکت
        try {
            final WireModel w = packet.getCurrentWire();
            if (w != null) {
                final PortModel dst = w.getDstPort();
                if (dst != null && belongsToBox(dst)) {
                    System.out.println("[ADAPTER] Found port via wire heuristic");
                    return dst;
                }
            }
        } catch (Throwable ignore) {
            // Be conservative; simply return null and let behavior decide.
        }

        // 3) Unknown
        System.out.println("[ADAPTER] Could not determine entered port");
        return null;
    }

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


    public static final class EnteredPortTracker {
        private static final WeakHashMap<PacketModel, PortModel> MAP = new WeakHashMap<>();

        private EnteredPortTracker() {}

        public static void record(PacketModel p, PortModel port) {
            if (p == null) return;
            final PortModel last = MAP.get(p);
            // بیشتر مدل‌ها هویتی‌اند؛ اگر equals سفارشی داری، می‌تونی Objects.equals(last, port) بذاری
            if (last == port) return;
            MAP.put(p, port);
        }


        public static PortModel peek(PacketModel p) {
            if (p == null) return null;
            return MAP.get(p);
        }

        /** Fetch and consume the recorded port for a packet */
        public static PortModel consume(PacketModel p) {
            if (p == null) return null;
            return MAP.remove(p);
        }

        /** Clear specific packet's port info */
        public static void clearPacket(PacketModel p) {
            if (p != null) MAP.remove(p);
        }

        /** Clear all hints (e.g., at level resets) */
        public static void clear() {
            MAP.clear();
        }
    }
}

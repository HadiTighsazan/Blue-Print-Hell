package com.blueprinthell.controller.systems;

import com.blueprinthell.model.*;
import com.blueprinthell.model.large.LargePacket;

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
        checkEnabledState();

        checkNewPackets();

        try {
            behavior.update(dt);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }


    public void checkNewPackets() {
        // ترکیب هر دو بافر
        final List<PacketModel> allPackets = new ArrayList<>();

        // اضافه کردن پکت‌های معمولی از bitBuffer
        final Queue<PacketModel> bitBuf = box.getBitBuffer();
        if (bitBuf != null && !bitBuf.isEmpty()) {
            allPackets.addAll(bitBuf);
        }

        // اضافه کردن پکت‌های حجیم از largeBuffer
        final Queue<LargePacket> largeBuf = box.getLargeBuffer();
        if (largeBuf != null && !largeBuf.isEmpty()) {
            allPackets.addAll(largeBuf);
        }

        if (allPackets.isEmpty()) {
            seen.clear();
            return;
        }

        final Set<PacketModel> current = Collections.newSetFromMap(
                new IdentityHashMap<>(Math.max(16, allPackets.size() * 2)));

        for (PacketModel p0 : allPackets) {
            if (p0 == null) continue;
            PacketModel p = p0;

            // *** فقط برای ProtectedPacket تلاش برای بازگردانی انجام بده ***
            if (p instanceof ProtectedPacket) {
                try {
                    PacketModel original = VpnRevertHints.consumeGlobal(p);
                    if (original != null && original != p) {
                        // جایگزینی در بافر مناسب
                        if (p instanceof LargePacket) {
                            // largeBuffer
                            Deque<LargePacket> tempLarge = new ArrayDeque<>();
                            LargePacket lp;
                            boolean replaced = false;
                            while ((lp = box.pollLarge()) != null) {
                                if (!replaced && lp == p) {
                                    // original باید LargePacket باشد؛ اگر نبود، از enqueue عمومی استفاده کن
                                    if (original instanceof LargePacket) {
                                        tempLarge.addLast((LargePacket) original);
                                    } else {
                                        // برگشتِ غیرهم‌نوع: به بافر عمومی برگردان
                                        box.enqueue(original);
                                    }
                                    replaced = true;
                                } else {
                                    tempLarge.addLast(lp);
                                }
                            }
                            for (LargePacket l : tempLarge) {
                                box.enqueue(l);
                            }
                        } else {
                            // bitBuffer
                            Deque<PacketModel> temp = new ArrayDeque<>();
                            PacketModel q;
                            boolean replaced = false;
                            while ((q = box.pollPacket()) != null) {
                                if (!replaced && q == p) {
                                    temp.addLast(original);
                                    replaced = true;
                                } else {
                                    temp.addLast(q);
                                }
                            }
                            for (PacketModel r : temp) {
                                box.enqueue(r);
                            }
                        }

                        EnteredPortTracker.clearPacket(p);
                        EnteredPortTracker.clearPacket(original);
                        p = original;
                    }
                } catch (Throwable ignore) {}
            }

            current.add(p);

            // اولین ورود به این باکس → فراخوانی behavior.onPacketEnqueued
            if (!seen.contains(p)) {
                final PortModel entered = findEnteredPort(p);
                try {
                    behavior.onPacketEnqueued(p, entered);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }

        // همگام‌سازی مجموعه seen
        seen.retainAll(current);
        seen.addAll(current);
    }
    public PortModel findEnteredPort(PacketModel packet) {
        if (packet == null) return null;

        // رکورد ورود را یک‌بار مصرف کن تا در تیک‌های بعدی نلغزد
        final PortModel tracked = EnteredPortTracker.consume(packet);
        if (tracked != null) {
            return tracked;
        }

        // fallback: از روی سیم فعلی
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
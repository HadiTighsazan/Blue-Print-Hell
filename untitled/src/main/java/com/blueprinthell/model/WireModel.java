package com.blueprinthell.model;

import java.awt.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.blueprinthell.controller.SimulationController;

// NEW:
import java.util.Map;

public class WireModel implements Serializable {
    private static final double SPAWN_SEPARATION = 0.02;
    private static final double MAX_SPAWN_SPREAD = 0.10;

    private static final long serialVersionUID = 4L;

    private final PortModel src;
    private final PortModel dst;

    private WirePath path;
    private final List<PacketModel> packets = new ArrayList<>();

    private static SimulationController simulationController;

    private static Set<PortModel> sourceInputPorts = Collections.emptySet();

    private static Map<PortModel, SystemBoxModel> portToBoxMap = Collections.emptyMap();

    private boolean isForPreviousLevels = false;

    private int largePacketPassCount = 0;
    private static final int MAX_LARGE_PACKET_PASSES = 3;

    public static void setSimulationController(SimulationController sc) {
        simulationController = sc;
    }

    public static void setSourceInputPorts(List<SystemBoxModel> sources) {
        sourceInputPorts = sources.stream()
                .flatMap(b -> b.getInPorts().stream())
                .collect(Collectors.toSet());
    }

    public static void setPortToBoxMap(Map<PortModel, SystemBoxModel> map) {
        portToBoxMap = (map != null) ? map : Collections.emptyMap();
    }

    public WireModel(PortModel src, PortModel dst) {
        this.src = src;
        this.dst = dst;
        this.path = buildDefaultPath();
    }

    public static SimulationController getSimulationController() {
        return simulationController;
    }

    public boolean isForPreviousLevels() {
        return isForPreviousLevels;
    }

    public void setForPreviousLevels(boolean flag) {
        this.isForPreviousLevels = flag;
    }

    private void syncEndpoints() {
        Point a = centreOf(src);
        Point b = centreOf(dst);

        List<Point> pts = path.getPoints();
        if (pts.isEmpty()) return;

        boolean changeA = !pts.get(0).equals(a);
        boolean changeB = !pts.get(pts.size() - 1).equals(b);
        if (!changeA && !changeB) return;

        List<Point> newPts = new ArrayList<>(pts);
        if (changeA) newPts.set(0, a);
        if (changeB) newPts.set(newPts.size() - 1, b);
        this.path = new WirePath(newPts);
    }


    public List<PacketModel> update(double dt) {
        List<PacketModel> arrived = new ArrayList<>();
        Iterator<PacketModel> it = packets.iterator();

        while (it.hasNext()) {
            PacketModel p = it.next();

            boolean destDisabled = (simulationController != null && !simulationController.isSystemEnabled(dst));

            if (p.isReturning() || destDisabled) {
                double length = getLength();
                if (length > 0) {
                    double deltaProg = p.getSpeed() * dt / length;
                    double newProg   = p.getProgress() - deltaProg;
                    p.setProgress(Math.max(0.0, newProg));
                } else {
                    p.setProgress(0.0);
                }

                if ((p.isReturning() || destDisabled) && p.getProgress() <= 0.0) {
                    SystemBoxModel srcBox = (portToBoxMap != null) ? portToBoxMap.get(getSrcPort()) : null;
                    boolean accepted = false;

                    if (srcBox != null) {
                        accepted = srcBox.getInPorts().isEmpty()
                                ? srcBox.enqueueFront(p)
                                : srcBox.enqueue(p);
                    }


                    if (accepted) {
                        it.remove();
                        p.attachToWire(null, 0.0);
                        p.setReturning(false);

                        if (simulationController != null) {
                            // برای همه پکت‌ها، نه فقط غیر-messenger
                            simulationController.onPacketReturned();
                        }
                    } else {
                        p.setProgress(0.0);
                    }
                }

                continue;
            }
            double cc = p.getCollisionCooldown();
            if (cc > 1e-9 && p.isHoldWhileCooldown()) {
                p.setCollisionCooldown(cc - dt);
                if (p.getCollisionCooldown() <= 0) {
                    p.setHoldWhileCooldown(false);
                }
                continue;
            }


            p.advance(dt);

            if (p.getProgress() >= 1.0) {
                it.remove();
                arrived.add(p);

                if (simulationController != null && sourceInputPorts.contains(dst)) {
                    simulationController.onPacketReturned();
                }
            }
        }
        return arrived;
    }


    public void attachPacket(PacketModel packet, double initialProgress) {
        double p = initialProgress;
        if (p <= 0.001) {
            int count = 0;
            for (PacketModel q : packets) {
                if (!q.isReturning() && q.getProgress() <= MAX_SPAWN_SPREAD + 1e-9) count++;
            }
            p = Math.min(MAX_SPAWN_SPREAD, Math.max(0.0, count * SPAWN_SEPARATION));
        } else if (p >= 0.999) {
            int count = 0;
            for (PacketModel q : packets) {
                if (q.isReturning() && q.getProgress() >= 1.0 - MAX_SPAWN_SPREAD - 1e-9) count++;
            }
            p = Math.max(1.0 - MAX_SPAWN_SPREAD, 1.0 - count * SPAWN_SEPARATION);
        }
        packets.add(packet);
        packet.attachToWire(this, p);
    }

    public boolean removePacket(PacketModel p) {
        return packets.remove(p);
    }

    public double getLength() { syncEndpoints(); return WirePhysics.length(path); }
    public Point pointAt(double t) { syncEndpoints(); return WirePhysics.pointAt(path, t); }
    public boolean contains(Point p, double tolPx) { syncEndpoints(); return WirePhysics.contains(path, p, tolPx); }
    public WirePath getPath() { syncEndpoints(); return path; }

    public void setPath(WirePath newPath) { this.path = newPath; }

    private WirePath buildDefaultPath() {
        return new WirePath(List.of(centreOf(src), centreOf(dst)));
    }

    private static Point centreOf(PortModel pm) {
        return new Point(pm.getX() + pm.getWidth()/2, pm.getY() + pm.getHeight()/2);
    }

    public List<PacketModel> getPackets() { return Collections.unmodifiableList(packets); }
    public PortModel getSrcPort() { return src; }
    public PortModel getDstPort() { return dst; }
    public void clearPackets() { packets.clear(); }

    public List<Point> getBendPoints() {
        List<Point> pts = path.getPoints();
        return (pts.size() <= 2) ?
                Collections.emptyList() :
                Collections.unmodifiableList(pts.subList(1, pts.size() - 1));
    }
    public int getLargePacketPassCount() {
        return largePacketPassCount;
    }

    public void incrementLargePacketPass() {
        largePacketPassCount++;
    }

    public boolean shouldBeDestroyed() {
        return largePacketPassCount >= MAX_LARGE_PACKET_PASSES;
    }

    public void resetLargePacketCounter() {
        largePacketPassCount = 0;
    }

    // فاصله‌ی خطی روی خود سیم بین دو پکت (برحسب پیکسل)
    public double getAlongDistance(PacketModel a, PacketModel b) {
        if (a == null || b == null) return Double.POSITIVE_INFINITY;
        try {
            if (a.getCurrentWire() != this || b.getCurrentWire() != this) {
                return Double.POSITIVE_INFINITY;
            }
        } catch (Throwable ignore) {}

        double t1 = a.getProgress();
        double t2 = b.getProgress();

        // کلمپ به [0..1]
        if (t1 < 0) t1 = 0; else if (t1 > 1) t1 = 1;
        if (t2 < 0) t2 = 0; else if (t2 > 1) t2 = 1;

        return Math.abs(t1 - t2) * getLength();
    }


}

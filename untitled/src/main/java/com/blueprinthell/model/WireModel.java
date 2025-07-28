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
    private static final long serialVersionUID = 4L;

    private final PortModel src;
    private final PortModel dst;

    private WirePath path;
    private final List<PacketModel> packets = new ArrayList<>();

    private static SimulationController simulationController;

    private static Set<PortModel> sourceInputPorts = Collections.emptySet();

    private static Map<PortModel, SystemBoxModel> portToBoxMap = Collections.emptyMap();

    private boolean isForPreviousLevels = false;

    public static void setSimulationController(SimulationController sc) {
        simulationController = sc;
    }

    public static void setSourceInputPorts(List<SystemBoxModel> sources) {
        sourceInputPorts = sources.stream()
                .flatMap(b -> b.getInPorts().stream())
                .collect(Collectors.toSet());
    }

    // NEW: تزریق نگاشت پورت→باکس (در Registrar ست کن)
    public static void setPortToBoxMap(Map<PortModel, SystemBoxModel> map) {
        portToBoxMap = (map != null) ? map : Collections.emptyMap();
    }

    public WireModel(PortModel src, PortModel dst) {
        this.src = src;
        this.dst = dst;
        this.path = buildDefaultPath();
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

                if (p.isReturning() && p.getProgress() <= 0.0) {
                    SystemBoxModel srcBox = (portToBoxMap != null) ? portToBoxMap.get(getSrcPort()) : null;

                    // ورود به صف مبدأ/سیستم واسط
                    boolean accepted = false;
                    if (srcBox != null) {
                        if (srcBox.getInPorts().isEmpty()) {
                            // مبدأ بدون ورودی: جلوی صف بگذار تا همان پکت دوباره خارج شود
                            accepted = srcBox.enqueueFront(p);
                        } else {
                            // سیستم واسط: ورود عادی به صف
                            accepted = srcBox.enqueue(p);
                        }
                    }

                    if (accepted) {
                        // از سیم جدا و وضعیت بازگشت را خاموش کن
                        it.remove();
                        p.attachToWire(null, 0.0);
                        p.setReturning(false);

                        // نکتهٔ کلیدی: برای Messenger/سبز notify نکن تا Producer پکت جدید نسازد
                        if (simulationController != null) {
                            boolean isMessenger = PacketOps.isMessenger(p);
                            if (!isMessenger) {
                                simulationController.onPacketReturned();
                            }
                        }
                    } else {
                        // اگر وارد صف نشد، در ابتدای سیم متوقف بماند
                        p.setProgress(0.0);
                    }
                }

                // در حالت بازگشت/یا مقصد غیرفعال، حلقه را ادامه بده (بدون advance)
                continue;
            }
            // --- END ---

            // حالت عادی: استراتژی سرعت/شتاب خودش progress را جلو می‌برد
            p.advance(dt);

            if (p.getProgress() >= 1.0) {
                it.remove();
                arrived.add(p);

                // اگر مقصد یک "ورودیِ سیستم‌های مبدأ" است، اطلاع بده (رفتار موجود قبلی)
                if (simulationController != null && sourceInputPorts.contains(dst)) {
                    simulationController.onPacketReturned();
                }
            }
        }
        return arrived;
    }


    public void attachPacket(PacketModel packet, double initialProgress) {
        packets.add(packet);
        packet.attachToWire(this, initialProgress);
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
}

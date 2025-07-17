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

/**
 * Runtime model for a wire: holds its two endpoint ports, current geometric {@link WirePath},
 * and the packets travelling on it.
 * <p>
 * کلید: {@code WirePath#getPoints()} لیستی غیرقابل تغییر می‌دهد. برای همگام‌سازی
 * نقاط ابتدا/انتها باید یک کپی قابل‌تغییر بسازیم و {@link WirePath} جدید ایجاد کنیم.
 * همچنین هر زمان که پکتی به انتها رسید و اگر مقصدش سیستم منبع باشد،
 * اعلان بازگشت پکت به SimulationController ارسال می‌شود.
 */
public class WireModel implements Serializable {
    private static final long serialVersionUID = 4L;

    /* ---------------- immutable association ---------------- */
    private final PortModel src;
    private final PortModel dst;

    /* ---------------- mutable state ---------------- */
    private WirePath path;
    private final List<PacketModel> packets = new ArrayList<>();

    /** مرجع به SimulationController برای اعلام بازگشت پکت */
    private static SimulationController simulationController;

    /**
     * مجموعه‌ی پورت‌های ورودی سیستم‌های منبع (برای تشخیص بازگشت)
     */
    private static Set<PortModel> sourceInputPorts = Collections.emptySet();

    /** تنظیم SimulationController برای دریافت اعلان‌های بازگشت پکت */
    public static void setSimulationController(SimulationController sc) {
        simulationController = sc;
    }

    /**
     * تنظیم لیست سیستم‌های منبع برای تشخیص بازگشت پکت‌ها
     */
    public static void setSourceInputPorts(List<SystemBoxModel> sources) {
        sourceInputPorts = sources.stream()
                .flatMap(b -> b.getInPorts().stream())
                .collect(Collectors.toSet());
    }

    public WireModel(PortModel src, PortModel dst) {
        this.src = src;
        this.dst = dst;
        this.path = buildDefaultPath();
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

    /**
     * Advances all attached packets; returns those that reached the destination.
     * برای هر پکتی که progress>=1.0 شود و dst یکی از پورت‌های منبع باشد، اعلان بازگشت ارسال می‌شود.
     */
    public List<PacketModel> update(double dt) {
        List<PacketModel> arrived = new ArrayList<>();
        Iterator<PacketModel> it = packets.iterator();
        while (it.hasNext()) {
            PacketModel p = it.next();
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

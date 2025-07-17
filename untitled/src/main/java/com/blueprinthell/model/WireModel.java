package com.blueprinthell.model;

import java.awt.*;
import java.io.Serializable;
import java.util.*;
import java.util.List;

/**
 * Runtime model for a wire: holds its two endpoint ports, current geometric {@link WirePath},
 * and the packets travelling on it.
 * <p>
 * کلید: {@code WirePath#getPoints()} لیستی غیرقابل تغییر می‌دهد. برای همگام‌سازی
 * نقاط ابتدا/انتها باید یک کپی قابل‌تغییر بسازیم و {@link WirePath} جدید ایجاد کنیم.
 */
public class WireModel implements Serializable {
    private static final long serialVersionUID = 4L;

    /* ---------------- immutable association ---------------- */
    private final PortModel src;
    private final PortModel dst;

    /* ---------------- mutable state ---------------- */
    /** Current poly‑line path; first/last points kept in‑sync with port centres. */
    private WirePath path;

    /** Ordered list of packets on this wire. */
    private final List<PacketModel> packets = new ArrayList<>();

    public WireModel(PortModel src, PortModel dst) {
        this.src = src;
        this.dst = dst;
        this.path = buildDefaultPath();
    }

    /* ====================================================================== */
    /*                              Sync helpers                               */
    /* ====================================================================== */

    /**
     * Lazily ensure endpoints match current port centres. Rebuilds {@code path}
     * only when necessary to avoid mutating the unmodifiable list returned by
     * {@code WirePath#getPoints()}.
     */
    private void syncEndpoints() {
        Point a = centreOf(src);
        Point b = centreOf(dst);

        List<Point> pts = path.getPoints(); // unmodifiable
        if (pts.isEmpty()) return;

        boolean changeA = !pts.get(0).equals(a);
        boolean changeB = !pts.get(pts.size() - 1).equals(b);
        if (!changeA && !changeB) return; // nothing to do

        List<Point> newPts = new ArrayList<>(pts);
        if (changeA) newPts.set(0, a);
        if (changeB) newPts.set(newPts.size() - 1, b);
        this.path = new WirePath(newPts);
    }

    /* ====================================================================== */
    /*                                Update                                  */
    /* ====================================================================== */

    /** Advances all attached packets; returns those that reached the destination. */
    public List<PacketModel> update(double dt) {
        List<PacketModel> arrived = new ArrayList<>();
        for (Iterator<PacketModel> it = packets.iterator(); it.hasNext();) {
            PacketModel p = it.next();
            p.advance(dt);
            if (p.getProgress() >= 1.0) {
                it.remove();
                arrived.add(p);
            }
        }
        return arrived;
    }

    /* ====================================================================== */
    /*                           Packet attachment                             */
    /* ====================================================================== */

    public void attachPacket(PacketModel packet, double initialProgress) {
        packets.add(packet);
        packet.attachToWire(this, initialProgress);
    }

    public boolean removePacket(PacketModel p) { return packets.remove(p); }

    /* ====================================================================== */
    /*                               Geometry                                  */
    /* ====================================================================== */

    public double getLength() { syncEndpoints(); return WirePhysics.length(path); }
    public Point  pointAt(double t) { syncEndpoints(); return WirePhysics.pointAt(path, t); }
    public boolean contains(Point p, double tolPx) { syncEndpoints(); return WirePhysics.contains(path, p, tolPx); }
    public WirePath getPath() { syncEndpoints(); return path; }

    /** Caller must ensure endpoints already match ports */
    public void setPath(WirePath newPath) { this.path = newPath; }

    private WirePath buildDefaultPath() {
        return new WirePath(List.of(centreOf(src), centreOf(dst)));
    }

    private static Point centreOf(PortModel pm) {
        return new Point(pm.getX() + pm.getWidth()/2, pm.getY() + pm.getHeight()/2);
    }

    /* ====================================================================== */
    /*                                Accessors                                */
    /* ====================================================================== */

    public List<PacketModel> getPackets() { return Collections.unmodifiableList(packets); }
    public PortModel getSrcPort() { return src; }
    public PortModel getDstPort() { return dst; }
    public void clearPackets() { packets.clear(); }

    /** Intermediate bend points (excluding endpoints). */
    public List<Point> getBendPoints() {
        List<Point> pts = path.getPoints();
        return (pts.size() <= 2) ? Collections.emptyList()
                : Collections.unmodifiableList(pts.subList(1, pts.size() - 1));
    }
}

package com.blueprinthell.model;

import com.blueprinthell.config.Config;

import java.awt.Point;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Domain model for a wire connecting two ports, managing packet movement.
 */
public class WireModel implements Serializable {
    private static final long serialVersionUID = 4L;

    private final PortModel src;
    private final PortModel dst;
    private final List<PacketModel> packets = new ArrayList<>();

    public WireModel(PortModel src, PortModel dst) {
        this.src = src;
        this.dst = dst;
    }

    /**
     * Advances all attached packets by dt, returns list of arrived packets.
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
            }
        }
        return arrived;
    }

    /**
     * Attaches a packet to this wire at initial progress t in [0,1].
     */
    public void attachPacket(PacketModel packet, double initialProgress) {
        // ----- physics tuning based on port compatibility & packet type -----
        boolean compatible = src.getShape() == packet.getType().toPortShape();
        double speed  = packet.getBaseSpeed();
        double accel  = 0.0;

        switch (packet.getType()) {
            case SQUARE -> {
                speed = speed * (compatible ? 0.5 : 1.0); // نصف سرعت روی پورت سازگار
            }
            case TRIANGLE -> {
                if (!compatible) accel = Config.ACC_TRIANGLE; // شتاب مثبت در پورت ناسازگار
            }
        }
        // cap speed
        if (speed > Config.MAX_SPEED) speed = Config.MAX_SPEED;
        packet.setSpeed(speed);
        packet.setAcceleration(accel);

        // attach to wire & set initial progress
        packets.add(packet);
        packet.attachToWire(this, initialProgress);
    }

    /**
     * Removes the specified packet from this wire, if present.
     * @param packet the packet to remove
     * @return true if the packet was removed
     */
    public boolean removePacket(PacketModel packet) {
        return packets.remove(packet);
    }

    /**
     * Returns the current length of the wire based on port positions.
     */
    public double getLength() {
        Point p1 = new Point(src.getX() + src.getWidth() / 2, src.getY() + src.getHeight() / 2);
        Point p2 = new Point(dst.getX() + dst.getWidth() / 2, dst.getY() + dst.getHeight() / 2);
        return p1.distance(p2);
    }

    public List<PacketModel> getPackets() {
        return Collections.unmodifiableList(packets);
    }

    public PortModel getSrcPort() {
        return src;
    }

    public PortModel getDstPort() {
        return dst;
    }

    /**
     * Clears all packets currently on this wire.
     */
    public void clearPackets() {
        packets.clear();
    }

    /**
     * Computes the point along the wire at normalized progress t.
     */
    public Point pointAt(double t) {
        Point p1 = new Point(src.getX() + src.getWidth() / 2, src.getY() + src.getHeight() / 2);
        Point p2 = new Point(dst.getX() + dst.getWidth() / 2, dst.getY() + dst.getHeight() / 2);
        int x = (int) (p1.x + t * (p2.x - p1.x));
        int y = (int) (p1.y + t * (p2.y - p1.y));
        return new Point(x, y);
    }
}

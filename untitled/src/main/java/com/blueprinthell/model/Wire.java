package com.blueprinthell.model;

import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class Wire extends GameObject implements Serializable {
    private static final long serialVersionUID = 4L;

    private final Port src, dst;
    private final double length;
    private final List<Packet> packets = new ArrayList<>();

    public Wire(Port src, Port dst) {
        super(
                Math.min(src.getCenterX(), dst.getCenterX()),
                Math.min(src.getCenterY(), dst.getCenterY()),
                Math.max(2, Math.abs(dst.getCenterX() - src.getCenterX())),
                Math.max(2, Math.abs(dst.getCenterY() - src.getCenterY()))
        );
        this.src = src;
        this.dst = dst;
        this.length = Math.hypot(
                dst.getCenterX() - src.getCenterX(),
                dst.getCenterY() - src.getCenterY()
        );
    }


    public List<Packet> update(double dt) {
        List<Packet> arrived = new ArrayList<>();
        Iterator<Packet> it = packets.iterator();
        while (it.hasNext()) {
            Packet p = it.next();
            p.advance(dt);
            if (p.getProgress() >= 1.0) {
                it.remove();
                arrived.add(p);
            }
        }
        return arrived;
    }

    public void attachPacket(Packet p, double initialProgress) {
        p.attachToWire(this, initialProgress);
    }

    public double getLength() { return length; }
    public List<Packet> getPackets() { return packets; }

    public Port getSrcPort() { return src; }
    public Port getDstPort() { return dst; }

    public Point pointAt(double t) {
        int x = (int) (src.getCenterX() + t * (dst.getCenterX() - src.getCenterX()));
        int y = (int) (src.getCenterY() + t * (dst.getCenterY() - src.getCenterY()));
        return new Point(x, y);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        int x1 = src.getCenterX() - getX();
        int y1 = src.getCenterY() - getY();
        int x2 = dst.getCenterX() - getX();
        int y2 = dst.getCenterY() - getY();
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(Color.WHITE);
        g2.drawLine(x1, y1, x2, y2);
        g2.dispose();
    }

    public Port getSrc() {
        return src;
    }
    public Port getDst(){
        return dst;
    }
}

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
    private final int relX1, relY1, relX2, relY2;


    public Wire(Port src, Port dst) {
        super(0, 0, 1, 1);
        this.src = src;
        this.dst = dst;
        Container content = src.getRootPane().getContentPane();
        Point p1 = SwingUtilities.convertPoint(src, src.getWidth()/2, src.getHeight()/2, content);
        Point p2 = SwingUtilities.convertPoint(dst, dst.getWidth()/2, dst.getHeight()/2, content);
        int minX = Math.min(p1.x, p2.x);
        int minY = Math.min(p1.y, p2.y);
        int w = Math.max(2, Math.abs(p2.x - p1.x));
        int h = Math.max(2, Math.abs(p2.y - p1.y));
        setBounds(minX, minY, w, h);
        relX1 = p1.x - minX;
        relY1 = p1.y - minY;
        relX2 = p2.x - minX;
        relY2 = p2.y - minY;
        this.length = Math.hypot(p2.x - p1.x, p2.y - p1.y);
    }

    public void attachPacket(Packet p, double initP) {
        p.attachToWire(this, initP);
    }

    public void update(double dt) {
        Iterator<Packet> it = packets.iterator();
        while (it.hasNext()) {
            Packet p = it.next();
            p.advance(dt);
            if (p.getProgress() >= 1.0) {
                it.remove();
                // TODO: enqueue to dst's system
            }
        }
    }

    public double getLength() { return length; }
    public List<Packet> getPackets() { return packets; }

    public Point pointAt(double t) {
        int gx = (int) (relX1 + t * (relX2 - relX1));
        int gy = (int) (relY1 + t * (relY2 - relY1));
        Container content = src.getRootPane().getContentPane();
        return new Point(getX() + gx, getY() + gy);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(Color.WHITE);
        g2.drawLine(relX1, relY1, relX2, relY2);
        g2.dispose();
    }
}
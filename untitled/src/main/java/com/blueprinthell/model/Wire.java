package com.blueprinthell.model;

import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class Wire extends JComponent implements Serializable {
    private static final long serialVersionUID = 4L;

    private final Port src, dst;
    private final double length;
    private final List<Packet> packets = new ArrayList<>();

    public Wire(Port src, Port dst) {
        this.src = src;
        this.dst = dst;
        setOpaque(false);
        Point p1 = convertPortCenter(src);
        Point p2 = convertPortCenter(dst);
        this.length = p1.distance(p2);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        Container parent = getParent();
        setBounds(0, 0, parent.getWidth(), parent.getHeight());
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
        packets.add(p);
        p.attachToWire(this, initialProgress);
    }

    public double getLength() { return length; }
    public List<Packet> getPackets() { return packets; }
    public Port getSrcPort() { return src; }
    public Port getDstPort() { return dst; }

    public Point pointAt(double t) {
        Point p1 = convertPortCenter(src);
        Point p2 = convertPortCenter(dst);
        int x = (int) (p1.x + t * (p2.x - p1.x));
        int y = (int) (p1.y + t * (p2.y - p1.y));
        return new Point(x, y);
    }

    private Point convertPortCenter(Port port) {
        return SwingUtilities.convertPoint(
                port, port.getWidth() / 2, port.getHeight() / 2,
                this
        );
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(Color.GREEN);
        Point p1 = convertPortCenter(src);
        Point p2 = convertPortCenter(dst);
        g2.drawLine(p1.x, p1.y, p2.x, p2.y);
        g2.dispose();
    }
}

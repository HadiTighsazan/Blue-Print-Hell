package com.blueprinthell.view;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.WireModel;
import javax.swing.*;
import java.awt.*;
import java.util.List;


public class WireView extends JComponent {
    private final WireModel model;
    private final PortView  src;
    private final PortView  dst;

    private static final Color[] BEND_COLORS = {
            Color.RED, Color.GREEN, Color.BLUE
    };
    private static final int BEND_RADIUS = 8;

    private static final int HIT_PAD = 8;

    public WireView(WireModel model, PortView src, PortView dst) {
        this.model = model;
        this.src   = src;
        this.dst   = dst;
        setOpaque(false);
    }

    public WireModel getModel() {
        return model;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        Container parent = getParent();
        setBounds(0, 0, parent.getWidth(), parent.getHeight());
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setStroke(new BasicStroke(Config.STROKE_WIDTH_WIRE));
        g2.setColor(Config.COLOR_WIRE);

        Point p1 = SwingUtilities.convertPoint(src, src.getWidth()/2, src.getHeight()/2, this);
        Point p2 = SwingUtilities.convertPoint(dst, dst.getWidth()/2, dst.getHeight()/2, this);

        List<Point> bends = model.getBendPoints();

        Point prev = p1;
        if (bends != null) {
            for (int i = 0; i < bends.size(); i++) {
                Point bp = bends.get(i);
                Point pb = SwingUtilities.convertPoint(getParent(), bp.x, bp.y, this);
                g2.drawLine(prev.x, prev.y, pb.x, pb.y);
                prev = pb;
            }
        }
        g2.drawLine(prev.x, prev.y, p2.x, p2.y);

        if (bends != null) {
            for (int i = 0; i < bends.size(); i++) {
                Point bp = bends.get(i);
                Point pb = SwingUtilities.convertPoint(getParent(), bp.x, bp.y, this);
                Color c = BEND_COLORS[i % BEND_COLORS.length];
                g2.setColor(c);
                g2.fillOval(pb.x - BEND_RADIUS/2, pb.y - BEND_RADIUS/2, BEND_RADIUS, BEND_RADIUS);
            }
        }

        g2.dispose();
    }



    @Override
    public boolean contains(int x, int y) {
        Point p1 = SwingUtilities.convertPoint(src, src.getWidth()/2, src.getHeight()/2, this);
        Point p2 = SwingUtilities.convertPoint(dst, dst.getWidth()/2, dst.getHeight()/2, this);

        List<Point> bends = model.getBendPoints();

        Point prev = p1;
        if (bends != null) {
            for (Point bp : bends) {
                Point pb = SwingUtilities.convertPoint(getParent(), bp.x, bp.y, this);
                if (isNearSegment(x, y, prev, pb)) return true;
                prev = pb;
            }
        }
        return isNearSegment(x, y, prev, p2);
    }

    private static boolean isNearSegment(int x, int y, Point a, Point b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        if (dx == 0 && dy == 0) return a.distance(x, y) <= HIT_PAD;

        double t = ((x - a.x) * dx + (y - a.y) * dy) / (dx*dx + dy*dy);
        t = Math.max(0, Math.min(1, t));
        double px = a.x + t * dx;
        double py = a.y + t * dy;
        double dist = Point.distance(px, py, x, y);
        return dist <= HIT_PAD;
    }
}

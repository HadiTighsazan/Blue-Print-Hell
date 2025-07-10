package com.blueprinthell.view;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.WireModel;
import javax.swing.*;
import java.awt.*;

/**
 * Swing view for displaying a wire between two PortViews.
 */
public class WireView extends JComponent {
    private final WireModel model;
    private final PortView src;
    private final PortView dst;

    /**
     * Constructs a WireView that draws the given model between two PortViews.
     * @param model the backing WireModel
     * @param src   source port view
     * @param dst   destination port view
     */
    public WireView(WireModel model, PortView src, PortView dst) {
        this.model = model;
        this.src = src;
        this.dst = dst;
        setOpaque(false);
    }

    /**
     * Returns the backing WireModel for this view.
     */
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
        g2.drawLine(p1.x, p1.y, p2.x, p2.y);
        g2.dispose();
    }

    @Override
    public boolean contains(int x, int y) {
        // hit-test: distance from (x,y) to the line segment
        Point p1 = SwingUtilities.convertPoint(src, src.getWidth()/2, src.getHeight()/2, this);
        Point p2 = SwingUtilities.convertPoint(dst, dst.getWidth()/2, dst.getHeight()/2, this);
        double vx = p2.x - p1.x;
        double vy = p2.y - p1.y;
        double wx = x - p1.x;
        double wy = y - p1.y;
        double c2 = vx*vx + vy*vy;
        double t = (c2 == 0) ? 0 : ((vx*wx + vy*wy) / c2);
        if (t < 0) t = 0;
        else if (t > 1) t = 1;
        double projX = p1.x + t * vx;
        double projY = p1.y + t * vy;
        double dx = x - projX;
        double dy = y - projY;
        double dist = Math.sqrt(dx*dx + dy*dy);
        // threshold: half stroke width + small tolerance
        double threshold = Config.STROKE_WIDTH_WIRE * 0.75;
        return dist <= threshold;
    }
}

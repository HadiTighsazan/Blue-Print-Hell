package com.blueprinthell.view;

import com.blueprinthell.controller.gameplay.EliphasCenteringController;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class EliphasPointRenderer extends JComponent {
    private final EliphasCenteringController controller;
    private Timer repaintTimer;

    public EliphasPointRenderer(EliphasCenteringController controller) {
        this.controller = controller;
        setOpaque(false);
        setFocusable(false);
    }

    @Override public void addNotify() {
        super.addNotify();
        if (repaintTimer == null) {
            repaintTimer = new Timer(100, e -> repaint()); // 10fps برای شمارنده و حلقه‌ها
            repaintTimer.start();
        }
    }
    @Override public void removeNotify() {
        if (repaintTimer != null) { repaintTimer.stop(); repaintTimer = null; }
        super.removeNotify();
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (controller == null) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Map<Point, Double> map = controller.getActivePoints();
        int R = (int) Math.round(controller.getEffectRadiusPixels());

        for (Map.Entry<Point, Double> e : map.entrySet()) {
            Point p = e.getKey();
            double timeLeft = Math.max(0, e.getValue());

            // حلقه‌ی اثر
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(new Color(130, 40, 230, 170));
            g2.drawOval(p.x - R, p.y - R, R * 2, R * 2);

            // متن زمان
            g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
            FontMetrics fm = g2.getFontMetrics();
            String s = String.format("%.1fs", timeLeft);
            g2.setColor(Color.WHITE);
            g2.drawString(s, p.x - fm.stringWidth(s) / 2, p.y + fm.getAscent() / 2);
        }
        g2.dispose();
    }
}

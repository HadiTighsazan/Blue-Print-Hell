package com.blueprinthell.view;

import com.blueprinthell.controller.gameplay.AccelerationFreezeController;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * رندر کننده نقاط انجماد فعال
 */
public class FreezePointRenderer extends JComponent {

    private final AccelerationFreezeController controller;
    private static final int EFFECT_RADIUS = 30;

    public FreezePointRenderer(AccelerationFreezeController controller) {
        this.controller = controller;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();

        // فعال کردن antialiasing
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        Map<Point, Double> freezePoints = controller.getActiveFreezePoints();

        for (Map.Entry<Point, Double> entry : freezePoints.entrySet()) {
            Point point = entry.getKey();
            double timeRemaining = entry.getValue();

            // محاسبه شفافیت بر اساس زمان باقیمانده
            float alpha = (float) Math.min(1.0, timeRemaining / 20.0);

            // رسم دایره تأثیر
            g2.setColor(new Color(100, 200, 255, (int)(alpha * 100)));
            g2.fillOval(point.x - EFFECT_RADIUS,
                    point.y - EFFECT_RADIUS,
                    EFFECT_RADIUS * 2,
                    EFFECT_RADIUS * 2);

            // رسم حاشیه
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(new Color(50, 150, 255, (int)(alpha * 200)));
            g2.drawOval(point.x - EFFECT_RADIUS,
                    point.y - EFFECT_RADIUS,
                    EFFECT_RADIUS * 2,
                    EFFECT_RADIUS * 2);

            // نمایش زمان باقیمانده
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 12));
            String timeText = String.format("%.1fs", timeRemaining);
            FontMetrics fm = g2.getFontMetrics();
            int textX = point.x - fm.stringWidth(timeText) / 2;
            int textY = point.y + fm.getAscent() / 2;
            g2.drawString(timeText, textX, textY);
        }

        g2.dispose();
    }
}
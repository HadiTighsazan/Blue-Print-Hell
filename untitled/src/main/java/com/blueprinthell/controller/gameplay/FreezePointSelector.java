// فایل: untitled/src/main/java/com/blueprinthell/controller/FreezePointSelector.java
package com.blueprinthell.controller.gameplay;

import com.blueprinthell.model.WireModel;
import com.blueprinthell.view.screens.GameScreenView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * کلاس برای انتخاب نقطه انجماد روی سیم‌ها
 */
public class FreezePointSelector {

    private final GameScreenView gameView;
    private final List<WireModel> wires;
    private final Consumer<Point> onPointSelected;
    private JComponent overlay;
    private MouseAdapter mouseHandler;
    private final Runnable onCanceled;

    public FreezePointSelector(GameScreenView gameView,
                               List<WireModel> wires,
                               Consumer<Point> onPointSelected,
                               Runnable onCanceled) {
        this.gameView = gameView;
        this.wires = wires;
        this.onPointSelected = onPointSelected;
        this.onCanceled = onCanceled;
    }


    /**
     * شروع فرآیند انتخاب نقطه
     */
    public void startSelection() {
        JPanel gameArea = gameView.getGameArea();

        // ایجاد overlay شفاف
        overlay = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();

                // پس‌زمینه نیمه‌شفاف
                g2.setColor(new Color(0, 0, 0, 100));
                g2.fillRect(0, 0, getWidth(), getHeight());

                // هایلایت سیم‌ها
                g2.setStroke(new BasicStroke(4f));
                g2.setColor(new Color(100, 200, 255, 200));

                for (WireModel wire : wires) {
                    List<Point> path = wire.getPath().getPoints();
                    for (int i = 1; i < path.size(); i++) {
                        Point p1 = path.get(i - 1);
                        Point p2 = path.get(i);
                        g2.drawLine(p1.x, p1.y, p2.x, p2.y);
                    }
                }

                // نمایش متن راهنما
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Arial", Font.BOLD, 18));
                String msg = "Click on a wire to select freeze point (ESC to cancel)";
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(msg)) / 2;
                int y = 50;
                g2.drawString(msg, x, y);

                g2.dispose();
            }
        };

        overlay.setBounds(0, 0, gameArea.getWidth(), gameArea.getHeight());
        overlay.setOpaque(false);

        // اضافه کردن mouse handler
        mouseHandler = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Point clickPoint = e.getPoint();
                Point nearestPoint = findNearestPointOnWires(clickPoint);

                if (nearestPoint != null) {
                    endSelection();
                    onPointSelected.accept(nearestPoint);
                }
            }
        };

        overlay.addMouseListener(mouseHandler);

        // اضافه کردن ESC handler
        InputMap im = overlay.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = overlay.getActionMap();

        im.put(KeyStroke.getKeyStroke("ESCAPE"), "cancel");
        am.put("cancel", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                endSelection();
                if (onCanceled != null) onCanceled.run();
            }
        });


        gameArea.add(overlay);
        gameArea.setComponentZOrder(overlay, 0);
        overlay.requestFocusInWindow();
        gameArea.revalidate();
        gameArea.repaint();
    }

    /**
     * پایان فرآیند انتخاب
     */
    public void endSelection() {
        if (overlay != null) {
            JPanel gameArea = gameView.getGameArea();
            gameArea.remove(overlay);
            gameArea.revalidate();
            gameArea.repaint();
            overlay = null;
            mouseHandler = null;
        }
    }

    /**
     * یافتن نزدیک‌ترین نقطه روی سیم‌ها به نقطه کلیک
     */
    private Point findNearestPointOnWires(Point clickPoint) {
        final double MAX_DISTANCE = 20.0; // حداکثر فاصله قابل قبول
        Point nearestPoint = null;
        double minDistance = Double.MAX_VALUE;

        for (WireModel wire : wires) {
            List<Point> path = wire.getPath().getPoints();

            // بررسی هر segment از سیم
            for (int i = 0; i < path.size() - 1; i++) {
                Point p1 = path.get(i);
                Point p2 = path.get(i + 1);

                Point nearest = getNearestPointOnSegment(clickPoint, p1, p2);
                double dist = clickPoint.distance(nearest);

                if (dist < minDistance && dist <= MAX_DISTANCE) {
                    minDistance = dist;
                    nearestPoint = nearest;
                }
            }
        }

        return nearestPoint;
    }

    /**
     * یافتن نزدیک‌ترین نقطه روی یک segment به نقطه داده شده
     */
    private Point getNearestPointOnSegment(Point p, Point a, Point b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));

        int x = (int) (a.x + t * dx);
        int y = (int) (a.y + t * dy);
        return new Point(x, y);
    }
}
package com.blueprinthell.controller.gameplay;

import com.blueprinthell.model.WireModel;
import com.blueprinthell.view.screens.GameScreenView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Overlay شفاف برای انتخاب نقطهٔ اسنپ‌شده روی سنترلاین سیم‌ها.
 * رویدادها را consume می‌کند تا کلیک به زیرین‌ها (WireEditor) نرسد و شکستگی ایجاد نشود.
 */
public final class EliphasPointSelector extends JComponent
        implements MouseListener, MouseMotionListener, KeyListener {

    private final GameScreenView gameView;
    private final JComponent gameArea;
    private final List<WireModel> wires;
    private final Consumer<Point> onSelected;
    private final Runnable onCanceled;

    private static final int SNAP_VIS_RADIUS = 10;
    private static final double MAX_SNAP_DIST = 24.0; // px
    private static final int SAMPLES = 64;

    private Point hoverSnap; // نقطهٔ اسنپ‌شده برای پیش‌نمایش

    public EliphasPointSelector(GameScreenView gameView,
                                List<WireModel> wires,
                                Consumer<Point> onSelected,
                                Runnable onCanceled) {
        this.gameView = Objects.requireNonNull(gameView);
        this.gameArea = gameView.getGameArea();
        this.wires = Objects.requireNonNull(wires);
        this.onSelected = Objects.requireNonNull(onSelected);
        this.onCanceled = onCanceled;
        setOpaque(false);
        setFocusable(true);
        enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        addMouseListener(this);
        addMouseMotionListener(this);
        addKeyListener(this);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
    }

    public void start() {
        setBounds(0, 0, gameArea.getWidth(), gameArea.getHeight());
        gameArea.add(this, 0); // بالاترین لایه
        gameArea.setComponentZOrder(this, 0);
        requestFocusInWindow();
        repaint();
    }

    public void stop() {
        removeMouseListener(this);
        removeMouseMotionListener(this);
        removeKeyListener(this);
        Container p = getParent();
        if (p != null) p.remove(this);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (hoverSnap == null) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(160, 70, 255, 180));
        int r = SNAP_VIS_RADIUS;
        g2.drawOval(hoverSnap.x - r, hoverSnap.y - r, 2*r, 2*r);
        g2.drawLine(hoverSnap.x - r - 3, hoverSnap.y, hoverSnap.x + r + 3, hoverSnap.y);
        g2.drawLine(hoverSnap.x, hoverSnap.y - r - 3, hoverSnap.x, hoverSnap.y + r + 3);
        g2.dispose();
    }

    // MouseMotion
    @Override public void mouseMoved(MouseEvent e) {
        e.consume();
        hoverSnap = findSnapPoint(e.getX(), e.getY());
        repaint();
    }
    @Override public void mouseDragged(MouseEvent e) { mouseMoved(e); }

    // Mouse
    @Override public void mouseClicked(MouseEvent e) {
        e.consume();
        if (SwingUtilities.isLeftMouseButton(e) && hoverSnap != null) {
            onSelected.accept(new Point(hoverSnap));
            stop();
        }
    }
    @Override public void mousePressed(MouseEvent e)  { e.consume(); }
    @Override public void mouseReleased(MouseEvent e) { e.consume(); }
    @Override public void mouseEntered(MouseEvent e)  { /* no-op */ }
    @Override public void mouseExited(MouseEvent e)   { /* no-op */ }

    // ESC = انصراف (بدون بازگشت سکه)
    @Override public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            e.consume();
            if (onCanceled != null) onCanceled.run();
            stop();
        }
    }
    @Override public void keyReleased(KeyEvent e) { }
    @Override public void keyTyped(KeyEvent e)    { }

    // نزدیک‌ترین نقطه روی سنترلاین (نمونه‌گیری از pointAt(t))
    private Point findSnapPoint(int mx, int my) {
        double bestD2 = MAX_SNAP_DIST * MAX_SNAP_DIST;
        Point best = null;

        for (WireModel w : wires) {
            for (int i = 0; i <= SAMPLES; i++) {
                double t = (double) i / (double) SAMPLES;
                Point p = w.pointAt(t);
                int dx = p.x - mx, dy = p.y - my;
                int d2 = dx*dx + dy*dy;
                if (d2 < bestD2) {
                    bestD2 = d2;
                    best = p;
                }
            }
        }
        return best;
    }
}

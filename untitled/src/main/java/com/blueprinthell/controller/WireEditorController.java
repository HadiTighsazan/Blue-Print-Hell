package com.blueprinthell.controller;

import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.WirePath;
import com.blueprinthell.model.CoinModel;
import com.blueprinthell.model.WirePhysics;
import com.blueprinthell.view.SystemBoxView;
import com.blueprinthell.view.WireView;
import com.blueprinthell.model.WireUsageModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Controller that enables the player to add or move up‑to three intermediate control‑points
 * ("bends") on a selected wire.  هنگام هر ویرایش، اختلاف طول جدید/قدیم به {@link WireUsageModel}
 * اعمال می‌شود تا HUD و منطق ظرفیت سیم فوراً به‌روز شود.
 */
public class WireEditorController {
    /* constants */
    private static final int MAX_BENDS     = 3;
    private static final int HANDLE_RADIUS = 6; // px
    private static final int CLICK_DIST    = 8; // px
    private static final int BEND_COST     = 1; // coins

    /* injected refs */
    private final JPanel              canvas;
    private final WireModel           wire;
    private final WireView            wireView;
    private final List<SystemBoxView> obstacles;
    private final CoinModel           coins;
    private final WireUsageModel      usage;     // ‑‑‑ NEW: برای بروزرسانی طول مصرفی
    private final Runnable            networkChanged;

    /* state */
    private int dragIndex = -1;

    public WireEditorController(JPanel canvas,
                                WireModel wire,
                                WireView wireView,
                                List<SystemBoxView> systemBoxes,
                                CoinModel coins,
                                WireUsageModel usage,
                                Runnable networkChanged) {
        this.canvas = canvas;
        this.wire   = wire;
        this.wireView = wireView;
        this.obstacles = systemBoxes;
        this.coins  = coins;
        this.usage  = usage;
        this.networkChanged = networkChanged;
        installMouseHandlers();
    }

    /* ------------------------------------------------------------------ */
    private void installMouseHandlers() {
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), canvas);
                selectOrAddHandle(p);
            }
            @Override public void mouseDragged(MouseEvent e) {
                Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), canvas);
                dragHandle(p);
            }
            @Override public void mouseReleased(MouseEvent e) { dragIndex = -1; }
        };
        wireView.addMouseListener(ma);
        wireView.addMouseMotionListener(ma);
    }

    /* ---------------- handle selection / creation ---------------------- */
    private void selectOrAddHandle(Point click) {
        List<Point> cps = new java.util.ArrayList<>(wire.getPath().getPoints());
        for (int i = 1; i < cps.size() - 1; i++) {
            if (cps.get(i).distance(click) <= HANDLE_RADIUS) { dragIndex = i; return; }
        }
        if (cps.size() - 2 >= MAX_BENDS) return;
        int segIdx = nearestSegmentIndex(cps, click);
        if (segIdx >= 0) {
            if (coins.getCoins() < BEND_COST) { Toolkit.getDefaultToolkit().beep(); return; }
            coins.spend(BEND_COST);
            cps.add(segIdx + 1, click);
            dragIndex = segIdx + 1;
            commitPath(cps);
        }
    }

    /* ---------------- dragging ---------------------------------------- */
    private void dragHandle(Point p) {
        if (dragIndex < 0) return;
        List<Point> cps = new java.util.ArrayList<>(wire.getPath().getPoints());
        cps.set(dragIndex, p);
        if (intersectsAnyObstacle(cps)) return;
        commitPath(cps);
    }

    /* ---------------- commit with usage update ------------------------ */
    private void commitPath(List<Point> cps) {
        double oldLen = wire.getLength();
        WirePath newPath = new WirePath(cps);
        double newLen = WirePhysics.length(newPath);
        double delta  = newLen - oldLen;

        // تلاش برای رزرو طول اضافی (در صورت افزایشی بودن)
        if (delta > 1e-6) { // افزایش طول
            if (!usage.useWire(delta)) { Toolkit.getDefaultToolkit().beep(); return; }
        }
        // آزاد کردن طول در صورت کوتاه‌تر شدن
        else if (delta < -1e-6) {
            usage.freeWire(-delta);
        }

        wire.setPath(newPath);
        wireView.repaint();
        if (networkChanged != null) networkChanged.run();
    }

    /* ---------------- obstacle & geometry helpers --------------------- */
    private boolean intersectsAnyObstacle(List<Point> cps) {
        for (int i = 0; i < cps.size() - 1; i++) {
            Point a = cps.get(i), b = cps.get(i + 1);
            for (SystemBoxView box : obstacles) {
                Rectangle r = box.getBounds();
                if (r.contains(a) && r.contains(b)) continue;
                if (segmentIntersectsRectExcludingEndpoints(a, b, r)) return true;
            }
        }
        return false;
    }

    private static boolean segmentIntersectsRectExcludingEndpoints(Point a, Point b, Rectangle r) {
        if (!new java.awt.geom.Line2D.Double(a, b).intersects(r)) return false;
        if (r.contains(a) || r.contains(b)) {
            double mx = (a.x + b.x)/2.0, my = (a.y + b.y)/2.0;
            return r.contains(mx, my);
        }
        return true;
    }

    /* ---------------- misc helpers ------------------------------------ */
    private static int nearestSegmentIndex(List<Point> cps, Point click) {
        double min = CLICK_DIST; int idx = -1;
        for (int i = 0; i < cps.size() - 1; i++) {
            double d = distPointToSegment(click, cps.get(i), cps.get(i+1));
            if (d < min) { min = d; idx = i; }
        }
        return idx;
    }

    private static double distPointToSegment(Point c, Point a, Point b) {
        double vx = b.x - a.x, vy = b.y - a.y;
        double wx = c.x - a.x, wy = c.y - a.y;
        double c1 = vx * wx + vy * wy;
        if (c1 <= 0) return a.distance(c);
        double c2 = vx*vx + vy*vy;
        if (c2 <= c1) return b.distance(c);
        double t = c1 / c2;
        double px = a.x + t * vx, py = a.y + t * vy;
        return Point.distance(c.x, c.y, px, py);
    }
}

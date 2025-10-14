package com.blueprinthell.controller.ui.editor;

import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.WireUsageModel;
import com.blueprinthell.view.SystemBoxView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;


public class SystemBoxDragController extends MouseAdapter implements MouseMotionListener {

    private final SystemBoxModel model;
    private final SystemBoxView view;
    private final List<WireModel> wires;
    private final WireUsageModel usageModel;

    private Point offset;
    private final Map<WireModel, Double> oldLengths;

    private static volatile boolean DRAG_ENABLED = true;

    private static boolean SISYPHUS_MODE = false;
    private static int SISY_RADIUS_PX = 120;
    private static Predicate<SystemBoxModel> SISY_FILTER = null;    // فیلتر باکس مجاز (غیرمرجع)
    private static List<WireModel> SISY_WIRES = null;
    private static List<SystemBoxView> SISY_OBSTACLES = null;
    private static Runnable SISY_ON_FINISH = null;
    private static Point SISY_START_POS = null;

    private static boolean GLOBAL_DRAG_LOCK = false;

    private static boolean SISY_ARMED = false;
    private static boolean SISY_USED  = false;
    private static boolean SISY_MOVED = false;

    private static Runnable NETWORK_CHANGED = null;

    public SystemBoxDragController(SystemBoxModel model,
                                   SystemBoxView view,
                                   List<WireModel> wires,
                                   WireUsageModel usageModel) {
        this.model = model;
        this.view = view;
        this.wires = wires;
        this.usageModel = usageModel;
        this.oldLengths = new HashMap<>();

        view.addMouseListener(this);
        view.addMouseMotionListener(this);
    }

    public static void setNetworkChanged(Runnable r) { NETWORK_CHANGED = r; }

    @Override
    public void mousePressed(MouseEvent e) {
        if (!DRAG_ENABLED && !SISYPHUS_MODE) return;

        if (!SISYPHUS_MODE && GLOBAL_DRAG_LOCK) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        if (!DRAG_ENABLED) return;

        if (SISYPHUS_MODE) {
            if (SISY_FILTER != null && !SISY_FILTER.test(model)) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }
            SISY_START_POS = new Point(view.getX(), view.getY());
            SISY_MOVED = false;
        }

        offset = e.getPoint();

        oldLengths.clear();
        for (WireModel wire : wires) {
            if (belongsToThisBox(wire.getSrcPort()) || belongsToThisBox(wire.getDstPort())) {
                oldLengths.put(wire, wire.getLength());
            }
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (!DRAG_ENABLED && !SISYPHUS_MODE) return;

        if (!DRAG_ENABLED) return;

        int newX = view.getX() + e.getX() - offset.x;
        int newY = view.getY() + e.getY() - offset.y;

        if (SISYPHUS_MODE && SISY_START_POS != null) {
            int targetX = newX;
            int targetY = newY;

            int dx = targetX - SISY_START_POS.x;
            int dy = targetY - SISY_START_POS.y;
            double dist = Math.hypot(dx, dy);
            if (dist > SISY_RADIUS_PX) {
                double scale = SISY_RADIUS_PX / Math.max(1e-9, dist);
                targetX = SISY_START_POS.x + (int) Math.round(dx * scale);
                targetY = SISY_START_POS.y + (int) Math.round(dy * scale);
            }

            Rectangle newBounds = new Rectangle(targetX, targetY, view.getWidth(), view.getHeight());
            if (intersectsAnyWireExceptOwn(newBounds, model)) {
                return;
            }

            newX = targetX;
            newY = targetY;

            if (newX != SISY_START_POS.x || newY != SISY_START_POS.y) {
                SISY_MOVED = true;
            }
        }

        view.setLocation(newX, newY);
        model.setX(newX);
        model.setY(newY);

        for (Map.Entry<WireModel, Double> entry : oldLengths.entrySet()) {
            WireModel wire = entry.getKey();
            double previous = entry.getValue();
            double current = wire.getLength();
            double delta = current - previous;
            if (delta > 0) {
                usageModel.useWire(delta);
            } else if (delta < 0) {
                usageModel.freeWire(-delta);
            }
            entry.setValue(current);
        }

        JComponent parent = (JComponent) view.getParent();
        parent.revalidate();
        parent.repaint();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        // پایان حالت یک‌باره و «قفلِ سخت» تا ریست مرحله
        if (SISYPHUS_MODE) {
            SISYPHUS_MODE = false;
            if (SISY_MOVED) SISY_USED = true;

            SISY_START_POS = null;
            SISY_MOVED = false;

            forceDragLockUntilStageReset();

            if (SISY_ON_FINISH != null) {
                try { SISY_ON_FINISH.run(); } catch (Throwable ignore) {}
            }
        }

        if (GLOBAL_DRAG_LOCK) {
            DRAG_ENABLED = false;
        }
        if (NETWORK_CHANGED != null) {
            try { NETWORK_CHANGED.run(); } catch (Throwable ignore) {}
        }

    }

    @Override
    public void mouseMoved(MouseEvent e) {
        // intentionally empty
    }

    public static void enableSisyphusOneShot(
            int radiusPx,
            Predicate<SystemBoxModel> filter,
            List<WireModel> wires,
            List<SystemBoxView> obstacles,
            Runnable onFinish
    ) {
        SISYPHUS_MODE  = true;
        SISY_ARMED     = true;
        SISY_USED      = false;
        SISY_MOVED     = false;
        SISY_START_POS = null;

        SISY_RADIUS_PX = Math.max(1, radiusPx);
        SISY_FILTER    = filter;
        SISY_WIRES     = wires;
        SISY_OBSTACLES = obstacles;
        SISY_ON_FINISH = onFinish;

        setDragEnabled(true);
    }

    public static void forceDragLockUntilStageReset() {
        GLOBAL_DRAG_LOCK = true;
        DRAG_ENABLED = false;
    }

    public static void clearDragLock() {
        GLOBAL_DRAG_LOCK = false;
    }

    public static void setDragEnabled(boolean enabled) {
        if (GLOBAL_DRAG_LOCK && enabled && !SISYPHUS_MODE) {
            DRAG_ENABLED = false;
            return;
        }
        DRAG_ENABLED = enabled;
    }



    private boolean belongsToThisBox(PortModel p) {
        if (p == null) return false;
        return model.getInPorts().contains(p) || model.getOutPorts().contains(p);
    }


    private boolean intersectsAnyWireExceptOwn(Rectangle newBounds, SystemBoxModel movedBox) {
        if (SISY_WIRES == null) return false;

        for (WireModel w : SISY_WIRES) {
            PortModel src = w.getSrcPort();
            PortModel dst = w.getDstPort();
            if (belongsToThisBox(src) || belongsToThisBox(dst)) continue;

            List<Point> cps = w.getPath().getPoints();
            for (int i = 0; i < cps.size() - 1; i++) {
                Point a = cps.get(i), b = cps.get(i + 1);
                if (segmentIntersectsRectExcludingEndpoints(a, b, newBounds)) return true;
            }
        }
        return false;
    }

    private static boolean segmentIntersectsRectExcludingEndpoints(Point a, Point b, Rectangle r) {
        if (!new java.awt.geom.Line2D.Double(a, b).intersects(r)) return false;
        if (r.contains(a) || r.contains(b)) {
            double mx = (a.x + b.x) / 2.0;
            double my = (a.y + b.y) / 2.0;
            return r.contains(mx, my);
        }
        return true;
    }
}

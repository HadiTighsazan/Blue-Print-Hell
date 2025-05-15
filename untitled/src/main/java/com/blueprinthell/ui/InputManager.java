package com.blueprinthell.ui;

import com.blueprinthell.engine.NetworkController;
import com.blueprinthell.model.Port;
import com.blueprinthell.model.Wire;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;


public class InputManager {
    private final NetworkController controller;

    private JComponent eventContainer;
    private Container  hitContainer;

    private Port   dragSource;
    private Port   dragTarget;
    private Point  mousePos;
    private boolean validTarget;
    private boolean enoughLength;

    private Consumer<Wire> onWireCreated;

    public InputManager(NetworkController controller) {
        this.controller = controller;
    }

    /** کانتینرِ اصلی (مثلاً GameScreen یا contentPane) که رویدادهای موسش رو هم می‌گیریم */
    public void registerHitContainer(Container c) {
        this.hitContainer = c;
        // ⇓⇓ این دو خط را اضافه کن ⇓⇓
        c.addMouseListener(containerMouseAdapter);
        c.addMouseMotionListener(containerMotionAdapter);
    }

    public void registerEventContainer(JComponent c) {
        this.eventContainer = c;
        c.addMouseListener(containerMouseAdapter);
        c.addMouseMotionListener(containerMotionAdapter);
    }

    public void registerPort(Port p) {
        p.addMouseListener(portMouseAdapter);
        // ⇓⇓ این خط را اضافه کنید ⇓⇓
        p.addMouseMotionListener(containerMotionAdapter);
    }


    public void setWireCreatedCallback(Consumer<Wire> cb) {
        this.onWireCreated = cb;
    }


    private final MouseAdapter containerMouseAdapter = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e) || dragSource != null) return;
            Point pt = e.getPoint();
            Point hitPt = SwingUtilities.convertPoint(eventContainer, pt, hitContainer);
            Component under = SwingUtilities.getDeepestComponentAt(hitContainer, hitPt.x, hitPt.y);
            if (under instanceof Port port && !port.isInput()) {
                startDrag(port);
            }
        }
        @Override
        public void mouseReleased(MouseEvent e) {
            if (dragSource != null) {
                // ← اضافه می‌کنیم تا آخرین نقطه رو ثبت کنه
                updateDragState(e.getPoint());
                endDrag();
            }
        }
    };

    private final MouseMotionAdapter containerMotionAdapter = new MouseMotionAdapter() {
        @Override
        public void mouseDragged(MouseEvent e) {
            updateDragState(e.getPoint());
        }
        @Override
        public void mouseMoved(MouseEvent e) {
            updateDragState(e.getPoint());
        }
    };

    private final MouseAdapter portMouseAdapter = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e) || dragSource != null) return;
            Port p = (Port)e.getSource();
            if (!p.isInput()) {
                startDrag(p);
            }
        }
        @Override
        public void mouseReleased(MouseEvent e) {
            if (dragSource != null) {
                // ← همینجا هم
                // e.getPoint() در اینجا مختصات Relative به خود پورته،
                // باید اول تبدیل کنی به مختصات eventContainer:
                Point pt = SwingUtilities.convertPoint(
                        (Component)e.getSource(), e.getPoint(),
                        eventContainer
                );
                updateDragState(pt);
                endDrag();
            }
        }
    };


    private void startDrag(Port src) {
        this.dragSource = src;
        this.dragTarget = null;

        // مختصات نقطهٔ شروع نشانگر ماوس، البته در فضای previewLayer:
        this.mousePos = SwingUtilities.convertPoint(
                src,
                src.getWidth()/2,
                src.getHeight()/2,
                eventContainer   // که همان previewLayer است
        );


        this.validTarget  = false;
        this.enoughLength = false;
        eventContainer.repaint();
    }

    private void updateDragState(Point pt) {
        if (dragSource == null) return;
        this.mousePos = pt;

        Point hitPt = SwingUtilities.convertPoint(eventContainer, pt, hitContainer);
        Component under = SwingUtilities.getDeepestComponentAt(hitContainer, hitPt.x, hitPt.y);
        if (under instanceof Port p) {
            dragTarget = p;
            validTarget = dragSource.isCompatibleWith(p);
        } else {
            dragTarget = null;
            validTarget = false;
        }

        double dx = dragSource.getCenterX() - pt.x;
        double dy = dragSource.getCenterY() - pt.y;
        double dist = Math.hypot(dx, dy);
        enoughLength = controller.getRemainingWireLength() >= dist;

        eventContainer.repaint();
    }

    private void endDrag() {
        if (validTarget && enoughLength && dragTarget != null) {
            Wire w = new Wire(dragSource, dragTarget);
            controller.addWire(w);
            if (onWireCreated != null) onWireCreated.accept(w);
        }
        dragSource = null;
        dragTarget = null;
        mousePos    = null;
        validTarget = false;
        enoughLength= false;
        eventContainer.repaint();
    }


    public Port getDragSource() { return dragSource; }
    public Point getMousePos()    { return mousePos; }
    public boolean isValidTarget(){ return validTarget; }
    public boolean isEnoughLength(){ return enoughLength; }
}

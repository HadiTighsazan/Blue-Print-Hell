package com.blueprinthell.ui;

import com.blueprinthell.engine.NetworkController;
import com.blueprinthell.model.Port;
import com.blueprinthell.model.Wire;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class InputManager {
    private final NetworkController controller;
    private JComponent eventContainer;
    private Container hitContainer;
    private Port dragSource, dragTarget;
    private Point mousePos;
    private boolean validTarget, enoughLength;
    private Consumer<Wire> onWireCreated;

    private final List<Port> ports = new ArrayList<>();

    private boolean allowSystemMove = true;
    private Runnable systemMovedCallback;

    public InputManager(NetworkController ctrl) {
        this.controller = ctrl;
    }

    public void registerHitContainer(Container c) {
        this.hitContainer = c;
        c.addMouseListener(containerMouseAdapter);
        c.addMouseMotionListener(containerMotionAdapter);
    }

    public void setAllowSystemMove(boolean allow) {
        this.allowSystemMove = allow;
    }

    public void setSystemMovedCallback(Runnable callback) {
        this.systemMovedCallback = callback;
    }

    public void registerEventContainer(JComponent c) {
        this.eventContainer = c;
        c.addMouseListener(containerMouseAdapter);
        c.addMouseMotionListener(containerMotionAdapter);
    }

    public void registerPort(Port p) {
        ports.add(p);
        p.addMouseListener(portMouseAdapter);
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
            if (dragSource != null) endDrag();
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
            Port p = (Port) e.getSource();
            if (!p.isInput()) {
                Point clickInPreview = SwingUtilities.convertPoint(
                        p, e.getX(), e.getY(), eventContainer
                );
                startDrag(p, clickInPreview);
            }
        }
    };

    private void startDrag(Port src, Point initMousePos) {
        this.dragSource = src;
        this.dragTarget = null;
        this.mousePos = initMousePos;
        this.validTarget = false;
        this.enoughLength = false;
        eventContainer.setEnabled(true);
        eventContainer.repaint();
    }

    private void startDrag(Port src) {
        this.dragSource = src;
        this.mousePos = SwingUtilities.convertPoint(
                src, src.getWidth() / 2, src.getHeight() / 2, eventContainer
        );
        this.validTarget = false;
        this.enoughLength = false;
        eventContainer.setEnabled(true);
        eventContainer.repaint();
    }

    private void updateDragState(Point pt) {
        if (dragSource == null) return;
        this.mousePos = pt;

        Point hitPt = SwingUtilities.convertPoint(eventContainer, pt, hitContainer);
        Component under = SwingUtilities.getDeepestComponentAt(hitContainer, hitPt.x, hitPt.y);

        Port target = null;
        if (under instanceof Port p) {
            target = p;
        } else if (under == eventContainer) {
            for (Port p : ports) {
                Point loc = SwingUtilities.convertPoint(
                        p.getParent(), p.getLocation(), hitContainer);
                Rectangle r = new Rectangle(loc.x, loc.y, p.getWidth(), p.getHeight());
                if (r.contains(hitPt)) {
                    target = p;
                    break;
                }
            }
        }

        if (target != null) {
            dragTarget = target;
            validTarget = dragSource.isCompatibleWith(target);
            boolean occupied = isPortConnected(dragSource) || isPortConnected(target);
            validTarget = validTarget && !occupied;
        } else {
            dragTarget = null;
            validTarget = false;
        }

        Point start = SwingUtilities.convertPoint(
                dragSource, dragSource.getWidth() / 2, dragSource.getHeight() / 2, eventContainer
        );
        double dx = start.x - pt.x;
        double dy = start.y - pt.y;
        enoughLength = controller.getRemainingWireLength() >= Math.hypot(dx, dy);

        eventContainer.repaint();
    }

    private void endDrag() {
        if (validTarget && enoughLength && dragTarget != null
                && !isPortConnected(dragSource) && !isPortConnected(dragTarget)) {
            Wire w = new Wire(dragSource, dragTarget);
            controller.addWire(w);
            if (onWireCreated != null) onWireCreated.accept(w);
        }
        dragSource = dragTarget = null;
        mousePos = null;
        validTarget = false;
        enoughLength = false;
        eventContainer.setEnabled(false);
        eventContainer.repaint();
    }


    public Port getDragSource() { return dragSource; }
    public Point getMousePos() { return mousePos; }
    public boolean isValidTarget() { return validTarget; }
    public boolean isEnoughLength() { return enoughLength; }

    private boolean isPortConnected(Port p) {
        return controller.getWires().stream()
                .anyMatch(w -> w.getSrcPort() == p || w.getDstPort() == p);
    }

}

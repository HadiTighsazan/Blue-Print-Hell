package com.blueprinthell.controller.ui.editor;

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


public class SystemBoxDragController extends MouseAdapter implements MouseMotionListener {
    private final SystemBoxModel model;
    private final SystemBoxView view;
    private final List<WireModel> wires;
    private final WireUsageModel usageModel;
    private Point offset;
    private Map<WireModel, Double> oldLengths;
    private static volatile boolean DRAG_ENABLED = true;

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

    @Override
    public void mousePressed(MouseEvent e) {
        if (!DRAG_ENABLED) return;
        offset = e.getPoint();
        oldLengths.clear();
        for (WireModel wire : wires) {
            if (model.getInPorts().contains(wire.getSrcPort())
                    || model.getOutPorts().contains(wire.getSrcPort())
                    || model.getInPorts().contains(wire.getDstPort())
                    || model.getOutPorts().contains(wire.getDstPort())) {
                oldLengths.put(wire, wire.getLength());
            }
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (!DRAG_ENABLED) return;
        int newX = view.getX() + e.getX() - offset.x;
        int newY = view.getY() + e.getY() - offset.y;
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
    public void mouseMoved(MouseEvent e) {
    }
    public static void setDragEnabled(boolean enabled) { DRAG_ENABLED = enabled; }
    public static boolean isDragEnabled() { return DRAG_ENABLED; }
}

package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.WireUsageModel;
import com.blueprinthell.view.PortView;
import com.blueprinthell.view.WireView;
import com.blueprinthell.view.screens.GameScreenView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Controller to allow dynamic wiring between ports with length constraint.
 */
public class WireCreationController {
    private final GameScreenView gameView;
    private final SimulationController simulation;
    private final List<SystemBoxModel> boxes;
    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destMap;
    private final Map<PortModel, WireModel> portConnection = new HashMap<>();
    private final WireUsageModel usageModel;

    // Preview state
    private boolean drawing = false;
    private PortModel startPort;
    private Point startPt;

    // Overlay and listener
    private final Overlay overlay;
    private final MouseMotionListener previewListener;

    public WireCreationController(GameScreenView gameView,
                                  SimulationController simulation,
                                  List<SystemBoxModel> boxes,
                                  List<WireModel> wires,
                                  Map<WireModel, SystemBoxModel> destMap,
                                  WireUsageModel usageModel) {
        this.gameView = gameView;
        this.simulation = simulation;
        this.boxes = boxes;
        this.wires = wires;
        this.destMap = destMap;
        this.usageModel = usageModel;

        // Initialize existing wires and lock their ports
        for (WireModel w : wires) {
            this.destMap.put(w, findDestBox(w.getDstPort()));
            portConnection.put(w.getSrcPort(), w);
            portConnection.put(w.getDstPort(), w);
            // consume existing length
            usageModel.useWire(w.getLength());
        }

        JPanel area = gameView.getGameArea();
        area.setLayout(null);

        // Setup overlay
        overlay = new Overlay();
        area.add(overlay, 0);
        overlay.setBounds(0, 0, area.getWidth(), area.getHeight());
        overlay.setVisible(false);
        area.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                overlay.setSize(area.getSize());
            }
        });

        // Preview listener for mouse movement
        previewListener = new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                if (drawing) {
                    Point p = SwingUtilities.convertPoint((Component)e.getSource(), e.getPoint(), overlay);
                    overlay.updateLine(startPt, p);
                }
            }
        };

        // Attach click listener to all ports
        attachToPorts(area);

        // Cancel preview on background click
        area.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (drawing) cancelPreview(area);
            }
        });
    }

    private void attachToPorts(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof PortView pv) {
                pv.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        handlePortClick(pv);
                    }
                });
            } else if (c instanceof Container inner && c != overlay) {
                attachToPorts(inner);
            }
        }
    }

    private void handlePortClick(PortView pv) {
        PortModel pm = pv.getModel();
        JPanel area = gameView.getGameArea();
        if (!drawing) {
            if (!pm.isInput() && !portConnection.containsKey(pm)) {
                drawing = true;
                startPort = pm;
                Point center = new Point(pv.getWidth()/2, pv.getHeight()/2);
                startPt = SwingUtilities.convertPoint(pv, center, overlay);
                overlay.updateLine(startPt, startPt);
                overlay.setVisible(true);
                area.addMouseMotionListener(previewListener);
            }
        } else {
            if (pm.isInput() && startPort.isCompatibleWith(pm)
                    && !portConnection.containsKey(pm)) {
                WireModel wm = new WireModel(startPort, pm);
                double length = wm.getLength();
                // Check available wire length
                if (usageModel.useWire(length)) {
                    // Model
                    wires.add(wm);
                    destMap.put(wm, findDestBox(pm));
                    // View
                    PortView srcPV = findPortView(gameView.getGameArea(), startPort);
                    WireView wv = new WireView(wm, srcPV, pv);
                    wv.setBounds(0, 0, area.getWidth(), area.getHeight());
                    area.add(wv, 0);
                    area.revalidate(); area.repaint();

                    // Lock ports
                    portConnection.put(startPort, wm);
                    portConnection.put(pm, wm);
                } else {
                    // Show warning: not enough wire
                    JOptionPane.showMessageDialog(gameView.getFrame(),
                            "Not enough wire remaining!", "Wire Limit", JOptionPane.WARNING_MESSAGE);
                }
            }
            cancelPreview(area);
        }
    }

    private void cancelPreview(JPanel area) {
        drawing = false;
        startPort = null;
        overlay.clearLine();
        overlay.setVisible(false);
        area.removeMouseMotionListener(previewListener);
    }

    public void freePortsForWire(WireModel wm) {
        portConnection.remove(wm.getSrcPort());
        portConnection.remove(wm.getDstPort());
        // free wire length
        usageModel.freeWire(wm.getLength());
    }

    private SystemBoxModel findDestBox(PortModel pm) {
        for (SystemBoxModel box : boxes) {
            if (box.getInPorts().contains(pm)) return box;
        }
        throw new IllegalStateException("Destination not found");
    }

    private PortView findPortView(Container c, PortModel pm) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof PortView pv && pv.getModel() == pm) return pv;
            if (comp instanceof Container inner) {
                PortView found = findPortView(inner, pm);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static class Overlay extends JComponent {
        private Point p1, p2;
        @Override public boolean contains(int x, int y) { return false; }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (p1 != null && p2 != null) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(Color.RED);
                g2.setStroke(new BasicStroke(Config.STROKE_WIDTH_WIRE));
                g2.drawLine(p1.x, p1.y, p2.x, p2.y);
                g2.dispose();
            }
        }
        void updateLine(Point from, Point to) { p1 = from; p2 = to; repaint(); }
        void clearLine() { p1 = p2 = null; repaint(); }
    }
}

package com.blueprinthell.controller.wire;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.pvp.PvPClientController; // <-- وارد کردن کلاس جدید
import com.blueprinthell.controller.simulation.SimulationController;
import com.blueprinthell.model.*;
import com.blueprinthell.shared.protocol.NetworkProtocol;
import com.blueprinthell.view.*;
import com.blueprinthell.view.screens.GameScreenView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class WireCreationController {
    private final GameScreenView gameView;
    private final SimulationController simulation;
    private final List<SystemBoxModel> boxes;
    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destMap;
    private final Set<PortModel> lockedInputs = new HashSet<>();
    private final WireUsageModel usageModel;
    private final CoinModel coinModel;
    private final Runnable networkChanged;

    private boolean drawing = false;
    private PortModel startPort;
    private Point startPt;

    private final Overlay overlay;
    private final MouseMotionListener previewListener;

    private final JPanel area;
    private final Map<PortModel, SystemBoxModel> portToBoxMap;

    // --- NEW: Fields for PvP Mode ---
    private PvPClientController pvpController;
    private boolean isPvPMode = false;
    // --- End of New Fields ---

    public WireCreationController(GameScreenView gameView,
                                  SimulationController simulation,
                                  List<SystemBoxModel> boxes,
                                  List<WireModel> wires,
                                  Map<WireModel, SystemBoxModel> destMap,
                                  WireUsageModel usageModel,
                                  CoinModel coinModel,
                                  Runnable networkChanged) {
        this.gameView = java.util.Objects.requireNonNull(gameView, "gameView is null");
        this.simulation = java.util.Objects.requireNonNull(simulation, "simulation is null");
        this.boxes = (boxes != null) ? boxes : new ArrayList<>();
        this.wires = (wires != null) ? wires : new ArrayList<>();
        this.destMap = (destMap != null) ? destMap : new java.util.HashMap<>();
        this.usageModel = java.util.Objects.requireNonNull(usageModel, "usageModel is null");
        this.coinModel = java.util.Objects.requireNonNull(coinModel, "coinModel is null");
        this.networkChanged = (networkChanged != null) ? networkChanged : () -> {};
        this.area = this.gameView.getGameArea();
        if (this.area == null) {
            throw new IllegalStateException("gameView.getGameArea() returned null");
        }
        area.setLayout(null);
        this.portToBoxMap = buildPortToBoxMap(this.boxes);

        // Logic for initializing existing wires remains the same
        for (WireModel w : new ArrayList<>(this.wires)) {
            if (w == null) continue;
            PortModel dstPort = w.getDstPort();
            SystemBoxModel destBox = (dstPort != null) ? findDestBox(dstPort) : null;
            if (destBox == null) {
                this.wires.remove(w);
                continue;
            }
            this.destMap.put(w, destBox);
            if (dstPort != null) lockedInputs.add(dstPort);
            this.usageModel.useWire(w.getLength());
            w.setPortToBoxMap(this.portToBoxMap);
        }

        overlay = new Overlay();
        area.add(overlay);
        overlay.setBounds(0, 0, area.getWidth(), area.getHeight());
        overlay.setVisible(false);
        area.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                overlay.setSize(area.getSize());
            }
        });

        previewListener = new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                if (!drawing) return;
                Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), overlay);
                overlay.updateLine(startPt, p);
            }
        };

        attachToPorts(area);
        area.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (drawing) cancelPreview();
            }
        });
    }

    /**
     * Injects the PvP controller to switch this controller into network mode.
     * @param pvpController The active PvP client controller, or null to revert to single-player.
     */
    public void setPvpController(PvPClientController pvpController) {
        this.pvpController = pvpController;
        this.isPvPMode = (pvpController != null);
    }

    private void attachToPorts(Container c) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof PortView pv) {
                pv.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        handlePortClick(pv);
                    }
                });
            } else if (comp instanceof Container inner && comp != overlay) {
                attachToPorts(inner);
            }
        }
    }

    private void handlePortClick(PortView pv) {
        PortModel pm = pv.getModel();
        if (!drawing) {
            // Start drawing a wire from an output port
            if (!pm.isInput()) {
                drawing = true;
                startPort = pm;
                Point center = new Point(pv.getWidth() / 2, pv.getHeight() / 2);
                startPt = SwingUtilities.convertPoint(pv, center, overlay);
                overlay.beginPreview();
                overlay.updateLine(startPt, startPt);
                overlay.setVisible(true);
                area.addMouseMotionListener(previewListener);
                area.setComponentZOrder(overlay, area.getComponentCount() - 1);
            }
        } else {
            // Finish drawing a wire to an input port
            if (pm.isInput()) {
                if (lockedInputs.contains(pm) || !startPort.canConnectTo(pm)) {
                    Toolkit.getDefaultToolkit().beep();
                    cancelPreview();
                    return;
                }

                if (isPvPMode) {
                    // --- PvP Mode: Send an action to the server ---
                    SystemBoxModel fromBox = portToBoxMap.get(startPort);
                    SystemBoxModel toBox = portToBoxMap.get(pm);
                    if (fromBox != null && toBox != null && pvpController != null) {
                        int fromPortIndex = fromBox.getOutPorts().indexOf(startPort);
                        int toPortIndex = toBox.getInPorts().indexOf(pm);

                        NetworkProtocol.PlayerAction_CreateWire action = new NetworkProtocol.PlayerAction_CreateWire(
                                "dummy_match_id", // This will be set by PvPClientController
                                fromBox.getId(), fromPortIndex,
                                toBox.getId(), toPortIndex
                        );
                        // pvpController.sendCreateWireAction(action); // Let PvPClientController build the message
                        System.out.println("PvP: Requesting to create wire...");
                    }
                } else {
                    // --- Single-Player Mode: Create wire locally ---
                    WireModel wm = new WireModel(startPort, pm);
                    if (!usageModel.useWire(wm.getLength())) {
                        Toolkit.getDefaultToolkit().beep();
                        cancelPreview();
                        return;
                    }
                    wm.setPortToBoxMap(portToBoxMap);
                    wires.add(wm);
                    destMap.put(wm, findDestBox(pm));
                    lockedInputs.add(pm);
                    PortView srcPV = findPortView(area, startPort);
                    WireView wv = new WireView(wm, srcPV, pv);
                    wv.setBounds(0, 0, area.getWidth(), area.getHeight());
                    area.add(wv, 0);
                    area.revalidate();
                    area.repaint();
                    if (networkChanged != null) networkChanged.run();
                    new WireEditorController(area, wm, wv, gameView.getSystemBoxViews(), coinModel, usageModel, networkChanged);
                }
            }
            cancelPreview();
        }
    }

    private void cancelPreview() {
        drawing = false;
        overlay.clearLine();
        overlay.setVisible(false);
        area.removeMouseMotionListener(previewListener);
        overlay.endPreview();
    }

    // --- Helper methods remain the same ---

    public void freePortsForWire(WireModel wm) {
        lockedInputs.remove(wm.getDstPort());
        usageModel.freeWire(wm.getLength());
        if (networkChanged != null) networkChanged.run();
    }

    private PortView findPortView(Container c, PortModel pm) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof PortView pv && pv.getModel() == pm) return pv;
            if (comp instanceof Container inner) {
                PortView f = findPortView(inner, pm);
                if (f != null) return f;
            }
        }
        return null;
    }

    private static class Overlay extends JComponent {
        private Point p1,p2;
        @Override public boolean contains(int x,int y){return false;}
        void beginPreview(){}
        void endPreview(){}
        @Override protected void paintComponent(Graphics g){
            super.paintComponent(g);
            if(p1!=null&&p2!=null){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setColor(Color.RED);
                g2.setStroke(new BasicStroke(Config.STROKE_WIDTH_WIRE));
                g2.drawLine(p1.x,p1.y,p2.x,p2.y);
                g2.dispose();
            }
        }
        void updateLine(Point a,Point b){p1=a;p2=b; repaint();}
        void clearLine(){p1=p2=null; repaint();}
    }

    private static Map<PortModel, SystemBoxModel> buildPortToBoxMap(List<SystemBoxModel> boxes) {
        Map<PortModel, SystemBoxModel> map = new HashMap<>();
        for (SystemBoxModel b : boxes) {
            for (PortModel p : b.getInPorts()) map.put(p, b);
            for (PortModel p : b.getOutPorts()) map.put(p, b);
        }
        return map;
    }

    private SystemBoxModel findDestBox(PortModel pm) {
        return boxes.stream()
                .filter(b -> b.getInPorts().contains(pm))
                .findFirst()
                .orElse(null);
    }
}
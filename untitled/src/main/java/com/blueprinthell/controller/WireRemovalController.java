package com.blueprinthell.controller;

import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.WireUsageModel;
import com.blueprinthell.view.WireView;
import com.blueprinthell.view.screens.GameScreenView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Map;


public class WireRemovalController {
    private final GameScreenView gameView;
    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destMap;
    private final WireCreationController creator;
    private final WireUsageModel usageModel;
    private final Runnable networkChanged;
    private boolean removalMode = false;
    private final MouseAdapter removalMouseAdapter;

    public WireRemovalController(GameScreenView gameView,
                                 List<WireModel> wires,
                                 Map<WireModel, SystemBoxModel> destMap,
                                 WireCreationController creator,
                                 WireUsageModel usageModel,
                                 Runnable networkChanged) {
        this.gameView = gameView;
        this.wires    = wires;
        this.destMap  = destMap;
        this.creator  = creator;
        this.usageModel = usageModel;
        this.networkChanged = networkChanged;

        JPanel area = gameView.getGameArea();
        JRootPane root = SwingUtilities.getRootPane(area);
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "TOGGLE_REMOVE");
        am.put("TOGGLE_REMOVE", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                removalMode = !removalMode;
                Cursor cursor = removalMode
                        ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
                        : Cursor.getDefaultCursor();
                area.setCursor(cursor);
                JComponent glass = (JComponent) root.getGlassPane();
                glass.setCursor(cursor);
                glass.setVisible(removalMode);
                if (removalMode) glass.requestFocusInWindow();
            }
        });

        removalMouseAdapter = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (!removalMode) return;
                Point click = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), area);
                Component[] comps = area.getComponents();
                for (int i = comps.length - 1; i >= 0; i--) {
                    Component c = comps[i];
                    if (c instanceof WireView wv) {
                        Point local = SwingUtilities.convertPoint(area, click, wv);
                        if (wv.contains(local.x, local.y)) {
                            WireModel wm = wv.getModel();
                            // Prevent removal of wires from previous levels
                            if (wm.isForPreviousLevels()) {
                                Toolkit.getDefaultToolkit().beep();
                                removalMode = false;
                                area.setCursor(Cursor.getDefaultCursor());
                                JComponent glassPane = (JComponent) root.getGlassPane();
                                glassPane.setCursor(Cursor.getDefaultCursor());
                                glassPane.setVisible(false);
                                break;
                            }
                            wires.remove(wm);
                            destMap.remove(wm);
                            creator.freePortsForWire(wm);
                            usageModel.freeWire(wm.getLength());
                            area.remove(wv);
                            area.revalidate();
                            area.repaint();
                            if (networkChanged != null) networkChanged.run();
                            removalMode = false;
                            area.setCursor(Cursor.getDefaultCursor());
                            JComponent glass2 = (JComponent) root.getGlassPane();
                            glass2.setCursor(Cursor.getDefaultCursor());
                            glass2.setVisible(false);
                            break;
                        }
                    }
                }
            }
        };

        JComponent glassPane = (JComponent) root.getGlassPane();
        glassPane.setVisible(false);
        glassPane.setFocusable(true);
        glassPane.addMouseListener(removalMouseAdapter);

        area.setFocusable(true);
    }

    public void removeWire(WireModel wm) {
        if (wm == null) return;
        if (wm.isForPreviousLevels()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        SwingUtilities.invokeLater(() -> performRemoval(wm));
    }

    private void performRemoval(WireModel wm) {
        JPanel area = gameView.getGameArea();
        JRootPane root = SwingUtilities.getRootPane(area);

        wires.remove(wm);
        destMap.remove(wm);
        creator.freePortsForWire(wm);
        usageModel.freeWire(wm.getLength());

        for (Component c : area.getComponents()) {
            if (c instanceof WireView wv && wv.getModel() == wm) {
                area.remove(wv);
                break;
            }
        }

        area.revalidate();
        area.repaint();
        if (networkChanged != null) networkChanged.run();
    }

    public void scheduleRemoval(WireModel wire) {
        if (wire == null) return;

        wires.remove(wire);
        destMap.remove(wire);
        creator.freePortsForWire(wire);
        usageModel.freeWire(wire.getLength());

        SwingUtilities.invokeLater(() -> {
            JPanel area = gameView.getGameArea();
            Component[] comps = area.getComponents();

            for (Component c : comps) {
                if (c instanceof WireView wv && wv.getModel() == wire) {
                    area.remove(c);
                    break;
                }
            }

            area.revalidate();
            area.repaint();

            if (networkChanged != null) {
                networkChanged.run();
            }
        });
    }
}

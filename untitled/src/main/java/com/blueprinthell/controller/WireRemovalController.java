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

/**
 * Controller to remove wires: toggle with SPACE, then click any wire to delete it.
 * Also frees the ports in the WireCreationController and wire usage length.
 */
public class WireRemovalController {
    private final GameScreenView gameView;
    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destMap;
    private final WireCreationController creator;
    private final WireUsageModel usageModel;
    private final Runnable networkChanged;
    private boolean removalMode = false;

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
        area.setFocusable(true);
        area.requestFocusInWindow();

        InputMap im = area.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = area.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "TOGGLE_REMOVE");
        am.put("TOGGLE_REMOVE", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                removalMode = !removalMode;
                Cursor cursor = removalMode
                        ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
                        : Cursor.getDefaultCursor();
                area.setCursor(cursor);
            }
        });

        area.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (!removalMode) return;
                Point click = e.getPoint();
                Component[] comps = area.getComponents();
                for (int i = comps.length - 1; i >= 0; i--) {
                    Component c = comps[i];
                    if (c instanceof WireView wv) {
                        Point local = SwingUtilities.convertPoint(area, click, wv);
                        if (wv.contains(local.x, local.y)) {
                            WireModel wm = wv.getModel();
                            wires.remove(wm);
                            destMap.remove(wm);
                            creator.freePortsForWire(wm);
                            usageModel.freeWire(wm.getLength());
                            area.remove(wv);
                            area.revalidate();
                            area.repaint();
                            if (networkChanged != null) networkChanged.run();
                            break;
                        }
                    }
                }
            }
        });
    }
}

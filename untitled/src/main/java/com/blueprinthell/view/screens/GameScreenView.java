package com.blueprinthell.view.screens;

import com.blueprinthell.config.KeyBindings;
import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.view.HudView;
import com.blueprinthell.view.PortView;
import com.blueprinthell.view.SystemBoxView;
import com.blueprinthell.view.WireView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Game screen panel containing the HUD bar at top and game area below.
 * Supports temporal navigation via configurable key bindings.
 */
public class GameScreenView extends JPanel {
    private final HudView hudView;
    private final JPanel gameArea;

    /** Listener for key-binding changes to re-apply InputMap. */
    private final BiConsumer<Integer, Integer> keyListener = this::applyKeyBindings;

    public GameScreenView(HudView hudView) {
        super(new BorderLayout());
        this.hudView = hudView;
        setBackground(Color.WHITE);

        // HUD bar
        hudView.setPreferredSize(new Dimension(0, 50));
        add(hudView, BorderLayout.NORTH);

        // Game area panel
        gameArea = new JPanel(null);
        gameArea.setOpaque(false);
        add(gameArea, BorderLayout.CENTER);

        setFocusable(true);
        // initial key bindings
        applyKeyBindings(KeyBindings.INSTANCE.getBackKey(), KeyBindings.INSTANCE.getForwardKey());
        // listen for future changes
        KeyBindings.INSTANCE.addListener(keyListener);
    }

    /* ---------------- Key bindings handling ---------------- */
    private void applyKeyBindings(int backKey, int forwardKey) {
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
        im.clear();
        im.put(KeyStroke.getKeyStroke(backKey, 0), "time-back");
        im.put(KeyStroke.getKeyStroke(forwardKey, 0), "time-forward");
        am.put("time-back", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { navigateTime(-1); }
        });
        am.put("time-forward", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { navigateTime(1); }
        });
    }

    /* ---------------- Temporal navigation ---------------- */
    private TemporalNavigationListener temporalListener;
    public void setTemporalNavigationListener(TemporalNavigationListener l) { this.temporalListener = l; }
    private void navigateTime(int dir) { if (temporalListener != null) temporalListener.onNavigate(dir); }

    public interface TemporalNavigationListener { void onNavigate(int direction); }

    /* ---------------- Reset / view building ---------------- */
    public void reset(List<SystemBoxModel> boxes, List<WireModel> wires) {
        gameArea.removeAll();
        // boxes
        for (SystemBoxModel b : boxes) gameArea.add(new SystemBoxView(b));
        // wires
        for (WireModel w : wires) {
            PortView src = findPortView(gameArea, w.getSrcPort());
            PortView dst = findPortView(gameArea, w.getDstPort());
            if (src != null && dst != null) gameArea.add(new WireView(w, src, dst), 0);
        }
        gameArea.revalidate();
        gameArea.repaint();
        requestFocusInWindow();
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

    /* ---------------- Visibility helpers ---------------- */
    public void showScreen() { setVisible(true); requestFocusInWindow(); }
    public void hideScreen() { setVisible(false); }

    /* ---------------- Accessors ---------------- */
    public JPanel getGameArea() { return gameArea; }
    public HudView getHudView() { return hudView; }
    public JFrame getFrame() {
        Window w = SwingUtilities.getWindowAncestor(this);
        if (w instanceof JFrame f) return f;
        throw new IllegalStateException("GameScreenView is not inside a JFrame");
    }
}

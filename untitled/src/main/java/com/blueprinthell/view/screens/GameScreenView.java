package com.blueprinthell.view.screens;

import com.blueprinthell.view.HudView;
import com.blueprinthell.view.SystemBoxView;
import com.blueprinthell.view.WireView;
import com.blueprinthell.view.PortView;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.PortModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * Game screen panel containing the HUD bar at top and game area below.
 * Supports temporal navigation via LEFT and RIGHT arrow keys.
 */
public class GameScreenView extends JPanel {
    private final HudView hudView;
    private final JPanel gameArea;

    /**
     * Constructs a GameScreenView with a HUD bar on top and freeform game area.
     */
    public GameScreenView(HudView hudView) {
        super(new BorderLayout());

        setBackground(Color.WHITE);
        this.hudView = hudView;

        // Allow this panel to receive key events
        setFocusable(true);
        setupTemporalKeyBindings();

        // HUD bar: full width, fixed height
        hudView.setPreferredSize(new Dimension(0, 50));
        add(hudView, BorderLayout.NORTH);

        // Game area: absolute positioning inside center
        gameArea = new JPanel(null);
        gameArea.setOpaque(false);
        add(gameArea, BorderLayout.CENTER);
    }

    private void setupTemporalKeyBindings() {
        InputMap im = this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = this.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "time-back");
        am.put("time-back", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                navigateTime(-1);
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "time-forward");
        am.put("time-forward", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                navigateTime(1);
            }
        });
    }

    /**
     * Navigate simulation time. direction: -1 for back, +1 for forward.
     */
    private TemporalNavigationListener temporalListener;

    /**
     * Sets the listener for temporal navigation events.
     */
    public void setTemporalNavigationListener(TemporalNavigationListener listener) {
        this.temporalListener = listener;
    }

    private void navigateTime(int direction) {
        if (temporalListener != null) {
            temporalListener.onNavigate(direction);
        }
    }

    /**
     * Listener interface for time navigation events.
     */
    public interface TemporalNavigationListener {
        /**
         * Called when user requests temporal navigation. direction: -1 = back, +1 = forward
         */
        void onNavigate(int direction);
    }

    /**
     * Resets and populates the game area with the given box and wire models.
     */
    public void reset(List<SystemBoxModel> boxes, List<WireModel> wires) {
        gameArea.removeAll();

        // Add system box views
        for (SystemBoxModel box : boxes) {
            SystemBoxView sbv = new SystemBoxView(box);
            gameArea.add(sbv);
        }

        // Add wire views behind boxes
        for (WireModel wire : wires) {
            PortView src = findPortView(gameArea, wire.getSrcPort());
            PortView dst = findPortView(gameArea, wire.getDstPort());
            if (src != null && dst != null) {
                WireView wv = new WireView(wire, src, dst);
                gameArea.add(wv, 0);
            }
        }

        gameArea.revalidate();
        gameArea.repaint();

        // Keep focus for key listening
        requestFocusInWindow();
    }

    /**
     * Recursively finds the PortView associated with the given PortModel.
     */
    private PortView findPortView(Container container, PortModel pm) {
        for (Component c : container.getComponents()) {
            if (c instanceof PortView pv && pv.getModel() == pm) {
                return pv;
            }
            if (c instanceof Container inner) {
                PortView found = findPortView(inner, pm);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Makes this game screen visible and focuses it for key input.
     */
    public void showScreen() {
        setVisible(true);
        requestFocusInWindow();
    }

    /**
     * Hides this game screen.
     */
    public void hideScreen() {
        setVisible(false);
    }

    /**
     * Provides access to the game area panel.
     */
    public JPanel getGameArea() {
        return gameArea;
    }

    /**
     * Provides access to the HUD view.
     */
    public HudView getHudView() {
        return hudView;
    }

    /**
     * Retrieves the parent JFrame containing this view.
     * @return the JFrame ancestor
     */
    public JFrame getFrame() {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window instanceof JFrame) {
            return (JFrame) window;
        }
        throw new IllegalStateException("GameScreenView is not inside a JFrame");
    }
}

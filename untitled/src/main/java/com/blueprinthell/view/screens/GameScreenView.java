package com.blueprinthell.view.screens;

import com.blueprinthell.config.KeyBindings;
import com.blueprinthell.controller.gameplay.AccelerationFreezeController;
import com.blueprinthell.controller.gameplay.EliphasCenteringController;
import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.view.*;
import com.blueprinthell.controller.ui.editor.SystemBoxDragController;
import com.blueprinthell.controller.wire.WireEditorController;
import com.blueprinthell.model.WireUsageModel;
import com.blueprinthell.model.CoinModel;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;


public class GameScreenView extends JPanel {
    private final HudView hudView;
    private final JPanel gameArea;

    private final BiConsumer<Integer, Integer> keyListener = this::applyKeyBindings;
    private AccelerationFreezeController freezeController;

    public GameScreenView(HudView hudView) {
        super(new BorderLayout());
        this.hudView = hudView;
        setBackground(Color.WHITE);

        hudView.setPreferredSize(new Dimension(0, 50));
        add(hudView, BorderLayout.NORTH);

        gameArea = new JPanel(null);
        gameArea.setFocusable(false);
        gameArea.setOpaque(false);
        add(gameArea, BorderLayout.CENTER);

        setFocusable(true);
        applyKeyBindings(KeyBindings.INSTANCE.getBackKey(), KeyBindings.INSTANCE.getForwardKey());
        KeyBindings.INSTANCE.addListener(keyListener);
    }

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

    private TemporalNavigationListener temporalListener;
    public void setTemporalNavigationListener(TemporalNavigationListener l) { this.temporalListener = l; }
    private void navigateTime(int dir) { if (temporalListener != null) temporalListener.onNavigate(dir); }

    public interface TemporalNavigationListener { void onNavigate(int direction); }


    public List<SystemBoxView> getSystemBoxViews() {
        List<SystemBoxView> list = new ArrayList<>();
        collectSystemBoxViews(gameArea, list);
        return list;
    }

    private void collectSystemBoxViews(Container c, List<SystemBoxView> list) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof SystemBoxView sbv) {
                list.add(sbv);
            } else if (comp instanceof Container inner) {
                collectSystemBoxViews(inner, list);
            }
        }
    }
    public void setFreezeController(AccelerationFreezeController fc) {
        this.freezeController = fc;
    }

    public void reset(List<SystemBoxModel> boxes, List<WireModel> wires) {
        gameArea.removeAll();
        if (freezeController != null) {
            FreezePointRenderer renderer = new FreezePointRenderer(freezeController);
            renderer.setBounds(0, 0, gameArea.getWidth(), gameArea.getHeight());
            gameArea.add(renderer, 0);

            // تایمر برای به‌روزرسانی افکت
            Timer updateTimer = new Timer(100, e -> renderer.repaint());
            updateTimer.start();
        }



        for (SystemBoxModel b : boxes) {
            gameArea.add(new SystemBoxView(b));
        }

        for (WireModel w : wires) {
            PortView src = findPortView(gameArea, w.getSrcPort());
            PortView dst = findPortView(gameArea, w.getDstPort());
            if (src != null && dst != null) {
                gameArea.add(new WireView(w, src, dst), 0);
            }
        }
        gameArea.revalidate();
        gameArea.repaint();
        requestFocusInWindow();
    }

    private PortView findPortView(Container c, PortModel pm) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof PortView pv && pv.getModel() == pm) {
                return pv;
            }
            if (comp instanceof Container inner) {
                PortView f = findPortView(inner, pm);
                if (f != null) return f;
            }
        }
        return null;
    }

    public void showScreen() {
        setVisible(true);
        requestFocusInWindow();
        gameArea.requestFocusInWindow();
    }
    public void hideScreen() { setVisible(false); }

    public JPanel getGameArea() { return gameArea; }
    public HudView getHudView() { return hudView; }
    public JFrame getFrame() {
        Window w = SwingUtilities.getWindowAncestor(this);
        if (w instanceof JFrame f) return f;
        throw new IllegalStateException("GameScreenView is not inside a JFrame");
    }
    public void rebuildControllers(List<WireModel> wires,
                                   WireUsageModel usageModel,
                                   CoinModel coinModel,
                                   Runnable networkChanged) {
        // بازسازی SystemBoxDragController برای هر SystemBoxView
        for (Component c : gameArea.getComponents()) {
            if (c instanceof SystemBoxView sbv) {
                // حذف listener های قبلی برای جلوگیری از duplicate
                var listeners = sbv.getMouseListeners();
                var motionListeners = sbv.getMouseMotionListeners();

                // بررسی که آیا قبلاً controller دارد
                boolean hasController = false;
                for (var listener : listeners) {
                    if (listener instanceof SystemBoxDragController) {
                        hasController = true;
                        break;
                    }
                }

                if (!hasController) {
                    new SystemBoxDragController(sbv.getModel(), sbv, wires, usageModel);
                }
            }
        }

        // بازسازی WireEditorController برای هر WireView
        for (Component c : gameArea.getComponents()) {
            if (c instanceof WireView wv) {
                // حذف listener های قبلی
                var listeners = wv.getMouseListeners();
                var motionListeners = wv.getMouseMotionListeners();

                // بررسی که آیا قبلاً controller دارد
                boolean hasController = false;
                for (var listener : listeners) {
                    if (listener.getClass().getName().contains("WireEditorController")) {
                        hasController = true;
                        break;
                    }
                }

                if (!hasController) {
                    new WireEditorController(gameArea, wv.getModel(), wv,
                            getSystemBoxViews(), coinModel, usageModel, networkChanged);
                }
            }
        }
    }
}
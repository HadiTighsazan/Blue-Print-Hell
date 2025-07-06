package com.blueprinthell.ui;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * InputBinder – مدیریت تمام KeyStrokeهای سراسری بازی.
 * <p>
 *  ⬅️  Rewind   (+60 فریم)
 *  ➡️  Forward  (‑60 فریم)
 *  Space  Toggle‑Delete
 * <p>
 *  کلیدها در زمان اجرا با متد {@link #rebind(int, int)} قابل تغییرند.
 */
class InputBinder {

    private final JComponent target;
    private final Runnable   onRewind;
    private final Runnable   onForward;
    private final Runnable   onToggleDelete;

    private int rewindKey;
    private int forwardKey;

    InputBinder(JComponent target,
                int rewindKey, int forwardKey,
                Runnable rewind, Runnable forward, Runnable toggleDelete) {
        this.target   = target;
        this.rewindKey   = rewindKey;
        this.forwardKey  = forwardKey;
        this.onRewind    = rewind;
        this.onForward   = forward;
        this.onToggleDelete = toggleDelete;
        apply();
    }

    /** تغییر کلیدها در زمان اجرا */
    public void rebind(int newRewindKey, int newForwardKey) {
        this.rewindKey  = newRewindKey;
        this.forwardKey = newForwardKey;
        apply();
    }

    /** KeyStroke ها را روی Input/ActionMap هدف ثبت می‌کند. */
    private void apply() {
        InputMap im = target.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = target.getActionMap();
        im.clear();
        am.clear();

        im.put(KeyStroke.getKeyStroke(rewindKey, 0),  "rew");
        im.put(KeyStroke.getKeyStroke(forwardKey, 0), "fwd");
        im.put(KeyStroke.getKeyStroke("SPACE"),      "del");

        am.put("rew", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { onRewind.run(); } });
        am.put("fwd", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { onForward.run(); } });
        am.put("del", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { onToggleDelete.run(); } });
    }
}

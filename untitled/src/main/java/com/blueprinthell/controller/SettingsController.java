package com.blueprinthell.controller;

import com.blueprinthell.config.KeyBindings;
import com.blueprinthell.view.screens.SettingsMenuView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * Controller for key‑binding selection using a two‑step dialog: first Back, then Forward.
 * Now works with a single button in SettingsMenuView.
 */
public class SettingsController {
    private final SettingsMenuView view;

    public SettingsController(SettingsMenuView view) {
        this.view = view;
        syncButtonText(KeyBindings.INSTANCE.getBackKey(), KeyBindings.INSTANCE.getForwardKey());
        KeyBindings.INSTANCE.addListener(this::syncButtonText);

        // Start rebind process when the single button is clicked
        view.keyBindingButton.addActionListener(e -> startRebindProcess());
    }

    /* Sync single button label with current bindings */
    private void syncButtonText(int back, int forward) {
        view.keyBindingButton.setText(KeyEvent.getKeyText(back) + "  |  " + KeyEvent.getKeyText(forward));
    }

    /* ---------------- Rebind process ---------------- */
    private void startRebindProcess() {
        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        KeyEventDispatcher dispatcher = new KeyEventDispatcher() {
            private boolean waitingForBack = true;
            @Override public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getID() != KeyEvent.KEY_PRESSED) return false;
                int code = e.getKeyCode();
                if (waitingForBack) {
                    KeyBindings.INSTANCE.setBackKey(code);
                    waitingForBack = false;
                    JOptionPane.showMessageDialog(view, "Press a key for Forward Time");
                } else {
                    if (code == KeyBindings.INSTANCE.getBackKey()) {
                        JOptionPane.showMessageDialog(view, "Key already used for Back! Choose another.");
                        return false;
                    }
                    KeyBindings.INSTANCE.setForwardKey(code);
                    kfm.removeKeyEventDispatcher(this);
                    JOptionPane.showMessageDialog(view, "Key bindings updated.");
                }
                return true; // consume
            }
        };

        JOptionPane.showMessageDialog(view, "Press a key for Rewind (Back Time)");
        kfm.addKeyEventDispatcher(dispatcher);
    }
}

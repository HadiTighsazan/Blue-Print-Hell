package com.blueprinthell.config;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Singleton model holding configurable key bindings for temporal navigation.
 */
public enum KeyBindings {
    INSTANCE;

    /* Default keys */
    private int backKey    = KeyEvent.VK_LEFT;
    private int forwardKey = KeyEvent.VK_RIGHT;

    /** Listeners notified when a key binding changes: consumer.accept(backKey, forwardKey) */
    private final List<BiConsumer<Integer, Integer>> listeners = new ArrayList<>();

    /* ---------------- Accessors ---------------- */
    public int getBackKey()    { return backKey;    }
    public int getForwardKey() { return forwardKey; }

    public synchronized void setBackKey(int keyCode) {
        if (keyCode == forwardKey) return; // prevent duplicate
        backKey = keyCode;
        fire();
    }

    public synchronized void setForwardKey(int keyCode) {
        if (keyCode == backKey) return;
        forwardKey = keyCode;
        fire();
    }

    /* ---------------- Listener management ---------------- */
    public synchronized void addListener(BiConsumer<Integer, Integer> l) {
        listeners.add(l);
        l.accept(backKey, forwardKey); // initial sync
    }

    public synchronized void removeListener(BiConsumer<Integer, Integer> l) {
        listeners.remove(l);
    }

    private void fire() {
        for (BiConsumer<Integer, Integer> l : listeners) {
            l.accept(backKey, forwardKey);
        }
    }
}

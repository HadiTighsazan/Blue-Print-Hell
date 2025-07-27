package com.blueprinthell.config;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;


public enum KeyBindings {
    INSTANCE;

    private int backKey    = KeyEvent.VK_LEFT;
    private int forwardKey = KeyEvent.VK_RIGHT;

    private final List<BiConsumer<Integer, Integer>> listeners = new ArrayList<>();

    public int getBackKey()    { return backKey;    }
    public int getForwardKey() { return forwardKey; }

    public synchronized void setBackKey(int keyCode) {
        if (keyCode == forwardKey) return;
        backKey = keyCode;
        fire();
    }

    public synchronized void setForwardKey(int keyCode) {
        if (keyCode == backKey) return;
        forwardKey = keyCode;
        fire();
    }

    public synchronized void addListener(BiConsumer<Integer, Integer> l) {
        listeners.add(l);
        l.accept(backKey, forwardKey);
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

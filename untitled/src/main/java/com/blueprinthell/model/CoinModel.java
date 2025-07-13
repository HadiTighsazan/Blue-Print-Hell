package com.blueprinthell.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple value object to track coins earned during a level.
 * Supports listeners so HUD or other UI can react instantly when coin amount changes.
 */
public class CoinModel implements Serializable {

    private static final long serialVersionUID = 7L;

    /* -------- listener -------- */
    public interface CoinListener { void onCoinsChanged(int newTotal); }
    private final List<CoinListener> listeners = new ArrayList<>();
    public void addListener(CoinListener l) { if (l != null && !listeners.contains(l)) listeners.add(l); }
    private void notifyListeners()          { listeners.forEach(l -> l.onCoinsChanged(coins)); }

    /* -------- state -------- */
    private int coins = 0;

    /** Adds the specified amount (non‑negative). */
    public void add(int amount) {
        if (amount <= 0) return;
        coins += amount;
        notifyListeners();
    }

    /** Attempts to spend coins; returns true on success. */
    public boolean spend(int amount) {
        if (amount <= 0 || amount > coins) return false;
        coins -= amount;
        notifyListeners();
        return true;
    }

    /** Current coin total. */
    public int getCoins() { return coins; }

    /** Reset to zero (called at level start). */
    public void reset() {
        coins = 0;
        notifyListeners();
    }
}

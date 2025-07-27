package com.blueprinthell.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


public class CoinModel implements Serializable {

    private static final long serialVersionUID = 7L;

    public interface CoinListener { void onCoinsChanged(int newTotal); }
    private final List<CoinListener> listeners = new ArrayList<>();
    public void addListener(CoinListener l) { if (l != null && !listeners.contains(l)) listeners.add(l); }
    private void notifyListeners()          { listeners.forEach(l -> l.onCoinsChanged(coins)); }

    private int coins = 0;

    public void add(int amount) {
        if (amount <= 0) return;
        coins += amount;
        notifyListeners();
    }

    public boolean spend(int amount) {
        if (amount <= 0 || amount > coins) return false;
        coins -= amount;
        notifyListeners();
        return true;
    }

    public int getCoins() { return coins; }

    public void reset() {
        coins = 0;
        notifyListeners();
    }
}

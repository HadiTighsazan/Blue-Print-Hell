package com.blueprinthell.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Model to track total and remaining wire length available to the player.
 * Now supports listeners so UI can update immediately when usage changes.
 */
public class WireUsageModel {

    /* ---------- listener interface ---------- */
    public interface UsageListener { void onUsageChanged(); }
    private final List<UsageListener> listeners = new ArrayList<>();
    public void addListener(UsageListener l) { if (l != null && !listeners.contains(l)) listeners.add(l); }
    private void notifyListeners()           { listeners.forEach(UsageListener::onUsageChanged); }

    /* ---------- state ---------- */
    private double totalWireLength;   // not final – can change each level
    private double usedWireLength;

    public WireUsageModel(double totalWireLength) {
        this.totalWireLength = totalWireLength;
        this.usedWireLength  = 0.0;
    }

    /* ---------------- Core operations ---------------- */
    /** Attempts to use the specified length of wire. Returns true if successful and notifies listeners. */
    public boolean useWire(double length) {
        if (usedWireLength + length <= totalWireLength) {
            usedWireLength += length;
            notifyListeners();
            return true;
        }
        return false;
    }

    /** Frees the specified length of wire and notifies listeners. */
    public void freeWire(double length) {
        if (length <= 0) return;
        usedWireLength = Math.max(0.0, usedWireLength - length);
        notifyListeners();
    }

    /* --------------- Reset for new level --------------- */
    public void reset(double newTotalWireLength) {
        this.totalWireLength = newTotalWireLength;
        this.usedWireLength  = 0.0;
        notifyListeners();
    }

    /* --------------- Getters --------------- */
    public double getTotalWireLength()     { return totalWireLength; }
    public double getUsedWireLength()      { return usedWireLength;  }
    public double getRemainingWireLength() { return totalWireLength - usedWireLength; }
}

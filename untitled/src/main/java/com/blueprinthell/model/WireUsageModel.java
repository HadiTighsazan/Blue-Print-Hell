package com.blueprinthell.model;

import java.util.ArrayList;
import java.util.List;

public class WireUsageModel {

    public interface UsageListener { void onUsageChanged(); }
    private final List<UsageListener> listeners = new ArrayList<>();
    public void addListener(UsageListener l) { if (l != null && !listeners.contains(l)) listeners.add(l); }
    private void notifyListeners()           { listeners.forEach(UsageListener::onUsageChanged); }

    private double totalWireLength;
    private double usedWireLength;

    public WireUsageModel(double totalWireLength) {
        this.totalWireLength = totalWireLength;
        this.usedWireLength  = 0.0;
    }

    public boolean useWire(double length) {
        if (usedWireLength + length <= totalWireLength) {
            usedWireLength += length;
            notifyListeners();
            return true;
        }
        return false;
    }

    public void freeWire(double length) {
        if (length <= 0) return;
        usedWireLength = Math.max(0.0, usedWireLength - length);
        notifyListeners();
    }

    public void reset(double newTotalWireLength) {
        this.totalWireLength = newTotalWireLength;
        this.usedWireLength  = 0.0;
        notifyListeners();
    }

    /**
     * Restores the model from given total and used lengths,
     * ensuring listeners are notified in correct order.
     */
    public void restoreState(double total, double used) {
        reset(total);
        if (used > 0) {
            useWire(used);
        }
    }

    public double getTotalWireLength()     { return totalWireLength; }
    public double getUsedWireLength()      { return usedWireLength;  }
    public double getRemainingWireLength() { return totalWireLength - usedWireLength; }

    /**
     * Restore state using the correct listener-notifying sequence.
     * This avoids direct setters and ensures HUD/listeners sync.
     */
    public void restore(double total, double used) {
        reset(total);
        if (used > 0) {
            useWire(used);
        }
    }
}

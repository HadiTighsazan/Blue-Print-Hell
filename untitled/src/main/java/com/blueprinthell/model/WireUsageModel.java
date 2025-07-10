package com.blueprinthell.model;

/**
 * Model to track total and remaining wire length available to the player.
 */
public class WireUsageModel {
    private final double totalWireLength;
    private double usedWireLength;

    /**
     * Constructs a WireUsageModel with specified total wire length.
     * @param totalWireLength the maximum total length of wire available
     */
    public WireUsageModel(double totalWireLength) {
        this.totalWireLength = totalWireLength;
        this.usedWireLength = 0.0;
    }

    /**
     * Attempts to use the specified length of wire. Returns true if successful.
     */
    public boolean useWire(double length) {
        if (usedWireLength + length <= totalWireLength) {
            usedWireLength += length;
            return true;
        }
        return false;
    }

    /**
     * Frees the specified length of wire, returning it to the pool.
     */
    public void freeWire(double length) {
        usedWireLength = Math.max(0.0, usedWireLength - length);
    }

    /**
     * Returns the total available wire length.
     */
    public double getTotalWireLength() {
        return totalWireLength;
    }

    /**
     * Returns the length of wire used so far.
     */
    public double getUsedWireLength() {
        return usedWireLength;
    }

    /**
     * Returns the remaining wire length available.
     */
    public double getRemainingWireLength() {
        return totalWireLength - usedWireLength;
    }
}

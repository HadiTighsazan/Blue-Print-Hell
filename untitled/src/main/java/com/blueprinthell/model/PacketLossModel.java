package com.blueprinthell.model;

/**
 * Model to track the number of lost packets during the simulation.
 */
public class PacketLossModel {
    private int lostCount;

    /**
     * Increments the number of lost packets by one.
     */
    public void increment() {
        lostCount++;
    }

    /**
     * Returns the total number of lost packets.
     */
    public int getLostCount() {
        return lostCount;
    }

    /**
     * Resets the lost packet count to zero.
     */
    public void reset() {
        lostCount = 0;
    }
}

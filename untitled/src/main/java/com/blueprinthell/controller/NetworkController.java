package com.blueprinthell.controller;

/**
 * Interface defining methods to capture and restore network state snapshots.
 */
public interface NetworkController {
    /**
     * Captures the current state of the network and returns a snapshot.
     * @return current network snapshot
     */
    NetworkSnapshot captureSnapshot();

    /**
     * Restores the network state from the given snapshot.
     * @param snapshot the snapshot to restore
     */
    void restoreState(NetworkSnapshot snapshot);
}

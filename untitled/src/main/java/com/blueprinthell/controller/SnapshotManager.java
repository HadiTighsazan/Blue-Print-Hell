package com.blueprinthell.controller;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages time-travel snapshots of the network state for playback and editing.
 */
public class SnapshotManager {
    // Add method to retrieve snapshots for export
    /**
     * Returns a copy of all recorded snapshots.
     */
    public List<NetworkSnapshot> getSnapshots() {
        return new ArrayList<>(snapshots);
    }


    private final List<NetworkSnapshot> snapshots;
    private int currentIndex;
    private final int maxFrames;

    /**
     * Default constructor with unlimited capacity.
     */
    public SnapshotManager() {
        this.maxFrames = Integer.MAX_VALUE;
        this.snapshots = new ArrayList<>();
        this.currentIndex = -1;
    }

    /**
     * Constructs a manager with a maximum number of snapshots to retain.
     * @param maxFrames maximum snapshots to keep
     */
    public SnapshotManager(int maxFrames) {
        this.maxFrames = maxFrames;
        this.snapshots = new ArrayList<>();
        this.currentIndex = -1;
    }

    /**
     * Alias for recordSnapshot.
     */
    public void push(NetworkSnapshot snapshot) {
        recordSnapshot(snapshot);
    }

    /**
     * Records a new snapshot, capping history at maxFrames and handling rewinds.
     */
    public void recordSnapshot(NetworkSnapshot snapshot) {
        // If we've rewound, discard future history
        if (currentIndex < snapshots.size() - 1) {
            snapshots.subList(currentIndex + 1, snapshots.size()).clear();
        }
        snapshots.add(snapshot);
        // Enforce maxFrames
        if (snapshots.size() > maxFrames) {
            snapshots.remove(0);
        }
        // Reset index to newest
        currentIndex = snapshots.size() - 1;
    }

    /**
     * Returns number of snapshots stored.
     */
    public int size() {
        return snapshots.size();
    }

    /**
     * Retrieves the snapshot from n frames ago relative to the newest.
     * @param n frames back (0 = newest)
     */
    public NetworkSnapshot getSnapshotFramesAgo(int n) {
        if (snapshots.isEmpty()) return null;
        int idx = snapshots.size() - 1 - n;
        if (idx < 0) idx = 0;
        if (idx >= snapshots.size()) idx = snapshots.size() - 1;
        return snapshots.get(idx);
    }

    /**
     * Discards the specified number of newest snapshots.
     * @param count number of newest snapshots to remove
     */
    public void discardNewest(int count) {
        if (count <= 0 || snapshots.isEmpty()) return;
        int from = snapshots.size() - count;
        if (from < 0) from = 0;
        snapshots.subList(from, snapshots.size()).clear();
        currentIndex = snapshots.size() - 1;
    }

    /**
     * Moves to the previous snapshot in the recorded history.
     */
    public NetworkSnapshot rewind() {
        if (currentIndex > 0) {
            currentIndex--;
            return snapshots.get(currentIndex);
        }
        return null;
    }

    /**
     * Moves to the next snapshot in the recorded history.
     */
    public NetworkSnapshot forward() {
        if (currentIndex < snapshots.size() - 1) {
            currentIndex++;
            return snapshots.get(currentIndex);
        }
        return null;
    }

    /**
     * Returns the current snapshot at the pointer.
     */
    public NetworkSnapshot getCurrentSnapshot() {
        if (currentIndex >= 0 && currentIndex < snapshots.size()) {
            return snapshots.get(currentIndex);
        }
        return null;
    }

    /**
     * Clears all snapshots.
     */
    public void clear() {
        snapshots.clear();
        currentIndex = -1;
    }
}

package com.blueprinthell.engine;

import java.util.LinkedList;
import java.util.Objects;


public class SnapshotManager {
    private final boolean unbounded;
    private final int capacity;
    private final LinkedList<NetworkSnapshot> snaps;

    public SnapshotManager(int capacity) {
        this.unbounded = capacity <= 0;
        this.capacity  = capacity;
        this.snaps     = new LinkedList<>();
    }

    public void push(NetworkSnapshot snap) {
        // In bounded mode, drop oldest when over capacity
        if (!unbounded && snaps.size() == capacity) {
            snaps.removeFirst();
        }
        snaps.addLast(snap);
    }

    /**
     * Returns the nth frame from the end (n=0 is latest)
     */
    public NetworkSnapshot getSnapshotFramesAgo(int framesBack) {
        int size = snaps.size();
        if (framesBack < 0 || framesBack >= size) {
            throw new IllegalArgumentException("Out of range");
        }
        // direct index: latest is at size-1, so position = size-1-framesBack
        return snaps.get(size - 1 - framesBack);
    }

    public int size() {
        return snaps.size();
    }
    public boolean isEmpty() {
        return snaps.isEmpty();
    }

    /**
     * Discards the newest 'count' snapshots (for resume after scrub)
     */
    public void discardNewest(int count) {
        for (int i = 0; i < count && !snaps.isEmpty(); i++) {
            snaps.removeLast();
        }
    }
}

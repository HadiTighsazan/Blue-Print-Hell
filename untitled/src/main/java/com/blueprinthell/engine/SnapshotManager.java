package com.blueprinthell.engine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public class SnapshotManager {
    private final Deque<NetworkSnapshot> ring;
    private final int capacity;

    public SnapshotManager(int capacity) {
        this.capacity = capacity;
        this.ring     = new ArrayDeque<>(capacity);
    }

    public void push(NetworkSnapshot snap) {
        if (ring.size() == capacity) ring.removeFirst();
        ring.addLast(snap);
    }

    /** فریمِ n ام از انتها را برمی‌گرداند (n=0 آخرین) */
    public NetworkSnapshot getSnapshotFramesAgo(int framesBack) {
        if (framesBack < 0 || framesBack >= ring.size())
            throw new IllegalArgumentException("Out of range");
        Iterator<NetworkSnapshot> it = ring.descendingIterator();
        for (int i = 0; i < framesBack; i++) it.next();
        return it.next();
    }

    public int size()    { return ring.size(); }
    public boolean isEmpty(){ return ring.isEmpty(); }
}

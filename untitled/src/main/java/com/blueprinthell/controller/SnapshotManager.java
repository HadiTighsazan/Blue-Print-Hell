package com.blueprinthell.controller;

import java.util.ArrayList;
import java.util.List;


public class SnapshotManager {

    public List<NetworkSnapshot> getSnapshots() {
        return new ArrayList<>(snapshots);
    }


    private final List<NetworkSnapshot> snapshots;
    private int currentIndex;
    private final int maxFrames;


    public SnapshotManager() {
        this.maxFrames = Integer.MAX_VALUE;
        this.snapshots = new ArrayList<>();
        this.currentIndex = -1;
    }


    public SnapshotManager(int maxFrames) {
        this.maxFrames = maxFrames;
        this.snapshots = new ArrayList<>();
        this.currentIndex = -1;
    }


    public void push(NetworkSnapshot snapshot) {
        recordSnapshot(snapshot);
    }


    public void recordSnapshot(NetworkSnapshot snapshot) {
        if (currentIndex < snapshots.size() - 1) {
            snapshots.subList(currentIndex + 1, snapshots.size()).clear();
        }
        snapshots.add(snapshot);
        if (snapshots.size() > maxFrames) {
            snapshots.remove(0);
        }
        currentIndex = snapshots.size() - 1;
    }


    public int size() {
        return snapshots.size();
    }


    public NetworkSnapshot getSnapshotFramesAgo(int n) {
        if (snapshots.isEmpty()) return null;
        int idx = snapshots.size() - 1 - n;
        if (idx < 0) idx = 0;
        if (idx >= snapshots.size()) idx = snapshots.size() - 1;
        return snapshots.get(idx);
    }


    public void discardNewest(int count) {
        if (count <= 0 || snapshots.isEmpty()) return;
        int from = snapshots.size() - count;
        if (from < 0) from = 0;
        snapshots.subList(from, snapshots.size()).clear();
        currentIndex = snapshots.size() - 1;
    }


    public NetworkSnapshot rewind() {
        if (currentIndex > 0) {
            currentIndex--;
            return snapshots.get(currentIndex);
        }
        return null;
    }


    public NetworkSnapshot forward() {
        if (currentIndex < snapshots.size() - 1) {
            currentIndex++;
            return snapshots.get(currentIndex);
        }
        return null;
    }


    public NetworkSnapshot getCurrentSnapshot() {
        if (currentIndex >= 0 && currentIndex < snapshots.size()) {
            return snapshots.get(currentIndex);
        }
        return null;
    }


    public void clear() {
        snapshots.clear();
        currentIndex = -1;
    }
}

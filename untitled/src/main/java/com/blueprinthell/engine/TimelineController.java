package com.blueprinthell.engine;

import com.blueprinthell.engine.NetworkSnapshot;

import java.util.Objects;

public class TimelineController {
    private final NetworkController controller;
    private final SnapshotManager snaps;
    private boolean playing       = true;
    private int     currentOffset = 0;

    public TimelineController(NetworkController ctrl, int maxFrames) {
        this.controller = Objects.requireNonNull(ctrl);
        this.snaps      = new SnapshotManager(maxFrames);
    }

    public void recordFrame() {
        if (playing) {
            NetworkSnapshot snap = controller.captureSnapshot();
            snaps.push(snap);
        }
    }


    public void scrubTo(int framesBack) {
        int max = snaps.size() - 1;
        if (max < 0) return;
        if (framesBack < 0) framesBack = 0;
        else if (framesBack > max) framesBack = max;

        playing       = false;
        currentOffset = framesBack;
        NetworkSnapshot snap = snaps.getSnapshotFramesAgo(framesBack);
        controller.restoreState(snap);
    }

    public void resume() {
        if (currentOffset > 0) {
            snaps.discardNewest(currentOffset);
        }
        playing       = true;
        currentOffset = 0;
    }

    public void pause() {
        playing = false;
    }

    public boolean isPlaying()       { return playing;       }
    public int     getCurrentOffset(){ return currentOffset; }

    public NetworkSnapshot getSnapshotFramesAgo(int n) {
        return snaps.getSnapshotFramesAgo(n);
    }
    public int getSnapshotCount() {
        return snaps.size();
    }
}
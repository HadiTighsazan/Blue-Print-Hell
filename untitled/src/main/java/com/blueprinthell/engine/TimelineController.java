package com.blueprinthell.engine;

public class TimelineController {
    private final NetworkController   controller;
    private final SnapshotManager     snaps;
    private boolean                   playing       = true;
    private int                       currentOffset = 0;

    public TimelineController(NetworkController ctrl, int capacityFrames) {
        this.controller = ctrl;
        this.snaps      = new SnapshotManager(capacityFrames);
    }

    public void recordFrame() {
        if (playing) {
            NetworkSnapshot snap = controller.captureSnapshot();
            snaps.push(snap);
        }
    }

    public void scrubTo(int framesBack) {
        playing = false;
        currentOffset = framesBack;
        NetworkSnapshot snap = snaps.getFramesAgo(framesBack);
        controller.restoreState(snap);
    }

    public void resume() {
        playing = true;
        currentOffset = 0;
        controller.restoreState(snaps.getFramesAgo(0));
    }

    public boolean isPlaying() { return playing; }
    public int getCurrentOffset() { return currentOffset; }
}

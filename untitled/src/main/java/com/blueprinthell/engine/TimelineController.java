package com.blueprinthell.engine;

public class TimelineController {
    private final NetworkController controller;
    private final SnapshotManager    snaps;
    private boolean playing    = true;
    private int     currentOffset = 0;

    public TimelineController(NetworkController ctrl, int maxFrames) {
        this.controller = ctrl;
        this.snaps      = new SnapshotManager(maxFrames);
    }

    public void recordFrame() {
        if (playing) {
            NetworkSnapshot snap = controller.captureSnapshot();
            snaps.push(snap);
        }
    }

    public void scrubTo(int framesBack) {
        playing       = false;
        currentOffset = framesBack;
        NetworkSnapshot snap = snaps.getSnapshotFramesAgo(framesBack);
        controller.restoreState(snap);
    }

    public void resume() {
        playing       = true;
        currentOffset = 0;
    }

    public void pause() {
        playing = false;
    }

    public boolean isPlaying()      { return playing; }
    public int     getCurrentOffset(){ return currentOffset; }
}

package com.blueprinthell.engine;

import com.blueprinthell.engine.NetworkController;
import com.blueprinthell.engine.SnapshotManager;
import com.blueprinthell.engine.NetworkSnapshot;

public class TimelineController {
    private final NetworkController controller;
    private final SnapshotManager snaps;
    private boolean playing = true;
    private int currentOffset = 0;

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
        // Pause playback and restore model to the chosen snapshot
        playing = false;
        currentOffset = framesBack;
        NetworkSnapshot snap = snaps.getSnapshotFramesAgo(framesBack);
        controller.restoreState(snap);
    }

    public void resume() {
        // Resume from the current model state (including scrubbed position)
        playing = true;
        currentOffset = 0;
        // Note: do not restore state here to avoid overriding the scrubbed state
    }

    public void pause() {
        playing = false;
    }

    public boolean isPlaying()      { return playing; }
    public int     getCurrentOffset(){ return currentOffset; }

    public NetworkSnapshot getSnapshotFramesAgo(int framesBack) {
        return snaps.getSnapshotFramesAgo(framesBack);
    }

    public int getSnapshotCount() {
        return snaps.size();
    }
}

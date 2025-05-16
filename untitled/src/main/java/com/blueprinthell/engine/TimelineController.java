package com.blueprinthell.engine;

import com.blueprinthell.engine.NetworkSnapshot;

import java.util.Objects;

public class TimelineController {
    private final NetworkController controller;
    private final SnapshotManager snaps;
    private boolean playing       = true;
    private int     currentOffset = 0;

    /**
     * @param ctrl      the network controller
     * @param maxFrames if <=0, unbounded; otherwise maximum frames to retain
     */
    public TimelineController(NetworkController ctrl, int maxFrames) {
        this.controller = Objects.requireNonNull(ctrl);
        this.snaps      = new SnapshotManager(maxFrames);
    }

    /** Records a snapshot if playing */
    public void recordFrame() {
        if (playing) {
            NetworkSnapshot snap = controller.captureSnapshot();
            snaps.push(snap);
        }
    }

    /**
     * Rewinds to a past snapshot (0=latest), pauses playback
     */
    public void scrubTo(int framesBack) {
        // Clamp to valid range
        int max = snaps.size() - 1;
        if (max < 0) return;
        if (framesBack < 0) framesBack = 0;
        else if (framesBack > max) framesBack = max;

        playing       = false;
        currentOffset = framesBack;
        NetworkSnapshot snap = snaps.getSnapshotFramesAgo(framesBack);
        controller.restoreState(snap);
    }

    /**
     * Resumes playback from the scrubbed position (discarding newer frames)
     */
    public void resume() {
        // Drop any snapshots newer than the scrub point
        if (currentOffset > 0) {
            snaps.discardNewest(currentOffset);
        }
        playing       = true;
        currentOffset = 0;
        // No restore here: the state is already at the scrub point
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
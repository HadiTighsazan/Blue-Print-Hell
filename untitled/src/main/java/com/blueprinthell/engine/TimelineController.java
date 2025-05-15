package com.blueprinthell.engine;

import com.blueprinthell.model.Packet;
import com.blueprinthell.model.SystemBox;
import java.util.List;
import java.util.Map;

public class TimelineController {
    private final NetworkController controller;
    private final SnapshotManager snaps;
    private boolean playing = true;
    private int currentOffset = 0;

    public TimelineController(NetworkController ctrl, int maxFrames) {
        this.controller = ctrl;
        this.snaps = new SnapshotManager(maxFrames);
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
        NetworkSnapshot snap = snaps.getSnapshotFramesAgo(framesBack);
        controller.restoreState(snap);
    }

    public void resume() {
        playing = true;
        currentOffset = 0;
    }
    public void pause() {
        this.playing = false;
    }

    public boolean isPlaying() { return playing; }
    public int getCurrentOffset() { return currentOffset; }


}

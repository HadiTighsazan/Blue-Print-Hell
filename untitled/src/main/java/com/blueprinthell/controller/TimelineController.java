package com.blueprinthell.controller;

import com.blueprinthell.controller.NetworkController;
import com.blueprinthell.controller.NetworkSnapshot;
import com.blueprinthell.controller.SnapshotManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Controller for recording and scrubbing through a timeline of network snapshots,
 * with export/import to JSON using Gson.
 */
public class TimelineController {
    private final NetworkController controller;
    private final SnapshotManager snaps;
    private boolean playing = true;
    private int currentOffset = 0;

    /**
     * @param controller the network controller capable of capturing/restoring snapshots
     * @param maxFrames  maximum number of snapshots to retain
     */
    public TimelineController(NetworkController controller, int maxFrames) {
        this.controller = Objects.requireNonNull(controller);
        this.snaps = new SnapshotManager(maxFrames);
    }

    /**
     * Records the current network state if playing.
     */
    public void recordFrame() {
        if (playing) {
            NetworkSnapshot snap = controller.captureSnapshot();
            snaps.push(snap);
        }
    }

    /**
     * Scrubs to a snapshot a given number of frames back.
     * @param framesBack number of frames to rewind
     */
    public void scrubTo(int framesBack) {
        int max = snaps.size() - 1;
        if (max < 0) return;
        if (framesBack < 0) framesBack = 0;
        else if (framesBack > max) framesBack = max;

        playing = false;
        currentOffset = framesBack;
        NetworkSnapshot snap = snaps.getSnapshotFramesAgo(framesBack);
        controller.restoreState(snap);
    }

    /**
     * Resumes playback, discarding any future snapshots beyond the current offset.
     */
    public void resume() {
        if (currentOffset > 0) {
            snaps.discardNewest(currentOffset);
        }
        playing = true;
        currentOffset = 0;
    }

    /**
     * Pauses playback; new frames will not be recorded.
     */
    public void pause() {
        playing = false;
    }

    public boolean isPlaying() {
        return playing;
    }

    public int getCurrentOffset() {
        return currentOffset;
    }

    /**
     * Retrieves the snapshot from n frames ago without altering state.
     */
    public NetworkSnapshot getSnapshotFramesAgo(int n) {
        return snaps.getSnapshotFramesAgo(n);
    }

    /**
     * Number of snapshots currently recorded.
     */
    public int getSnapshotCount() {
        return snaps.size();
    }

    /**
     * Exports all recorded snapshots to a JSON file using Gson.
     * @param filePath path to write the JSON file
     * @throws IOException if an I/O error occurs
     */
    public void exportToJson(Path filePath) throws IOException {
        Gson gson = new Gson();
        List<NetworkSnapshot> list = snaps.getSnapshots();
        try (java.io.Writer writer = Files.newBufferedWriter(filePath)) {
            gson.toJson(list, writer);
        }
    }

    /**
     * Imports snapshots from a JSON file using Gson, replacing the current timeline.
     * @param filePath path to read the JSON file
     * @throws IOException if an I/O error occurs
     */
    public void importFromJson(Path filePath) throws IOException {
        Gson gson = new Gson();
        java.lang.reflect.Type listType = new TypeToken<List<NetworkSnapshot>>() {}.getType();
        try (java.io.Reader reader = Files.newBufferedReader(filePath)) {
            List<NetworkSnapshot> list = gson.fromJson(reader, listType);
            snaps.clear();
            for (NetworkSnapshot snap : list) {
                snaps.push(snap);
            }
            playing = false;
            currentOffset = list.size() - 1;
            controller.restoreState(snaps.getCurrentSnapshot());
        }
    }
}

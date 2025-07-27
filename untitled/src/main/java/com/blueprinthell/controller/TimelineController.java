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


public class TimelineController {
    private final NetworkController controller;
    private final SnapshotManager snaps;
    private boolean playing = true;
    private int currentOffset = 0;


    public TimelineController(NetworkController controller, int maxFrames) {
        this.controller = Objects.requireNonNull(controller);
        this.snaps = new SnapshotManager(maxFrames);
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

        playing = false;
        currentOffset = framesBack;
        NetworkSnapshot snap = snaps.getSnapshotFramesAgo(framesBack);
        controller.restoreState(snap);
    }


    public void resume() {
        if (currentOffset > 0) {
            snaps.discardNewest(currentOffset);
        }
        playing = true;
        currentOffset = 0;
    }


    public void pause() {
        playing = false;
    }

    public boolean isPlaying() {
        return playing;
    }

    public int getCurrentOffset() {
        return currentOffset;
    }


    public NetworkSnapshot getSnapshotFramesAgo(int n) {
        return snaps.getSnapshotFramesAgo(n);
    }


    public int getSnapshotCount() {
        return snaps.size();
    }


    public void exportToJson(Path filePath) throws IOException {
        Gson gson = new Gson();
        List<NetworkSnapshot> list = snaps.getSnapshots();
        try (java.io.Writer writer = Files.newBufferedWriter(filePath)) {
            gson.toJson(list, writer);
        }
    }


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

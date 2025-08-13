package com.blueprinthell.controller;

import com.blueprinthell.snapshot.NetworkSnapshot;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import java.nio.file.*;
import java.io.IOException;

/**
 * Lightweight, debounced autosave. Periodically serializes the latest snapshot
 * to a JSON file using atomic write (temp + move).
 */
public class AutoSaveController {
    private final SnapshotService snapshotService;
    private final Timer timer;
    private final Path savePath;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    // debounce in milliseconds (min interval between two consecutive writes)
    private final int minIntervalMs;
    private long lastWriteMs = 0;

    public AutoSaveController(SnapshotService svc, Path path, int periodMs, int minIntervalMs) {
        this.snapshotService = svc;
        this.savePath = path;
        this.minIntervalMs = Math.max(200, minIntervalMs);
        this.timer = new Timer(Math.max(200, periodMs), e -> onTick());
        this.timer.setRepeats(true);
    }

    public void start() { timer.start(); }
    public void stop() { timer.stop(); }
    public boolean isRunning() { return timer.isRunning(); }

    private void onTick() {
        long now = System.currentTimeMillis();
        if (now - lastWriteMs < minIntervalMs) return; // debounce

        NetworkSnapshot snap = snapshotService.buildSnapshot();
        try {
            writeAtomically(savePath, gson.toJson(snap));
            lastWriteMs = now;
        } catch (IOException ex) {
            // Silent: autosave must be non-intrusive. You may log if you have a logger.
        }
    }

    private static void writeAtomically(Path target, String json) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        Files.writeString(tmp, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

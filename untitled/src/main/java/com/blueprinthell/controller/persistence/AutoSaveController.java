package com.blueprinthell.controller.persistence;

import com.blueprinthell.snapshot.NetworkSnapshot;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import java.nio.file.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AutoSaveController {
    private static final Path SAVE_DIR = Paths.get(System.getProperty("user.home"), ".blueprinthell", "saves");
    private static final Path PROGRESS_SAVE_FILE = SAVE_DIR.resolve("progress.json");
    private static final Path METADATA_FILE = SAVE_DIR.resolve("metadata.json");

    private static final String SECRET_KEY = "a_very_secret_key_for_blueprint_hell_game_!@#$";


    private final SnapshotService snapshotService;
    private final Timer saveTimer;
    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final int saveIntervalMs;
    private boolean enabled = false;
    private SaveMetadata lastMetadata;

    public static class SaveMetadata {
        public String timestamp;
        public int levelNumber;
        public String levelName;
        public int score;
        public int coins;
        public double progressPercent;
        public boolean isValid = true;
        public String checksum;

        public SaveMetadata() {}

        public SaveMetadata(int level, String name, int score, int coins, double progress) {
            this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            this.levelNumber = level;
            this.levelName = name;
            this.score = score;
            this.coins = coins;
            this.progressPercent = progress;
            this.isValid = true;
        }
    }

    public AutoSaveController(SnapshotService snapshotService, int saveIntervalSeconds) {
        this.snapshotService = snapshotService;
        this.saveIntervalMs = saveIntervalSeconds * 1000;

        try {
            Files.createDirectories(SAVE_DIR);
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.saveTimer = new Timer(saveIntervalMs, e -> performAutoSave());
        this.saveTimer.setRepeats(true);
    }


    public void start() {
        enabled = true;
        saveTimer.start();
        performAutoSave();
    }


    public void stop() {
        enabled = false;
        saveTimer.stop();
    }


    private void performAutoSave() {
        if (!enabled) return;

        try {
            NetworkSnapshot snapshot = snapshotService.buildSnapshot();
            String json = gson.toJson(snapshot);

            SaveMetadata metadata = createMetadata(snapshot);
            metadata.checksum = generateChecksum(json);
            lastMetadata = metadata;

            Files.writeString(PROGRESS_SAVE_FILE, json,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            String metaJson = gson.toJson(metadata);
            Files.writeString(METADATA_FILE, metaJson,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

        } catch (Exception e) {
        }
    }

    public static boolean hasSavedProgress() {
        return Files.exists(PROGRESS_SAVE_FILE);
    }

    public static SaveMetadata loadMetadata() {
        if (!Files.exists(METADATA_FILE)) return null;

        try {
            String json = Files.readString(METADATA_FILE);
            Gson gson = new Gson();
            return gson.fromJson(json, SaveMetadata.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    public static NetworkSnapshot loadSavedProgress() {
        if (!Files.exists(PROGRESS_SAVE_FILE) || !Files.exists(METADATA_FILE)) {
            return null;
        }
        try {
            String metaJson = Files.readString(METADATA_FILE);
            Gson gson = new Gson();
            SaveMetadata metadata = gson.fromJson(metaJson, SaveMetadata.class);
            if (metadata == null || metadata.checksum == null) {
                return null;
            }
            String savedChecksum = metadata.checksum;

            String progressJson = Files.readString(PROGRESS_SAVE_FILE);

            String newChecksum = generateChecksum(progressJson);

            if (!savedChecksum.equals(newChecksum)) {
                return null;
            }

            return gson.fromJson(progressJson, NetworkSnapshot.class);

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    public static void clearSavedProgress() {
        try {
            Files.deleteIfExists(PROGRESS_SAVE_FILE);
            Files.deleteIfExists(METADATA_FILE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private SaveMetadata createMetadata(NetworkSnapshot snapshot) {
        double progress = 0.0;
        if (snapshot.meta != null && snapshot.meta.producedUnits > 0) {
            int total = snapshot.meta.producedUnits;
            int produced = 0;
            if (snapshot.world.producers != null && !snapshot.world.producers.isEmpty()) {
                produced = snapshot.world.producers.get(0).producedCount;
            }
            progress = (double) produced / total * 100.0;
        }

        return new SaveMetadata(
                snapshot.meta != null ? snapshot.meta.levelNumber : 1,
                "Level " + (snapshot.meta != null ? snapshot.meta.levelNumber : 1),
                snapshot.world.score,
                snapshot.world.coins,
                progress
        );
    }


    public boolean isRunning() {
        return saveTimer.isRunning();
    }

    public void pause() {
        enabled = false;
        saveTimer.stop();
    }


    public void resume() {
        enabled = true;
        saveTimer.start();
    }
    public static SaveMetadata loadMetadataOrSynthesize() {
        SaveMetadata m = loadMetadata();
        if (m != null && m.isValid) return m;

        NetworkSnapshot snap = loadSavedProgress();
        if (snap != null) {
            SaveMetadata sm = new SaveMetadata();
            sm.isValid = true;
            sm.levelNumber = (snap.meta != null ? snap.meta.levelNumber : 1);
            sm.levelName   = "Level " + sm.levelNumber;
            sm.score       = (snap.world != null ? snap.world.score : 0);
            sm.coins       = (snap.world != null ? snap.world.coins : 0);
            sm.progressPercent = 0.0;
            try {
                sm.timestamp = Files.getLastModifiedTime(PROGRESS_SAVE_FILE)
                        .toInstant().toString().replace("Z","");
            } catch (Exception ignore) {
                sm.timestamp = java.time.LocalDateTime.now().toString();
            }
            return sm;
        }

        SaveMetadata fallback = new SaveMetadata();
        fallback.isValid = true;
        fallback.levelNumber = 1;
        fallback.levelName   = "Unknown";
        fallback.score = 0;
        fallback.coins = 0;
        fallback.progressPercent = 0.0;
        try {
            fallback.timestamp = Files.getLastModifiedTime(PROGRESS_SAVE_FILE)
                    .toInstant().toString().replace("Z","");
        } catch (Exception ignore) {
            fallback.timestamp = java.time.LocalDateTime.now().toString();
        }
        return fallback;
    }

    private static String generateChecksum(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String dataWithSecret = data + SECRET_KEY;
            byte[] hashBytes = digest.digest(dataWithSecret.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return "checksum_error";
        }
    }
}
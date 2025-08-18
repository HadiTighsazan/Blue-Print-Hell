// untitled/src/main/java/com/blueprinthell/controller/AutoSaveController.java
package com.blueprinthell.controller.persistence;

import com.blueprinthell.snapshot.NetworkSnapshot;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import java.nio.file.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Real-time progress save controller
 * Periodically saves game state for crash recovery
 */
public class AutoSaveController {
    private static final Path SAVE_DIR = Paths.get(System.getProperty("user.home"), ".blueprinthell", "saves");
    private static final Path PROGRESS_SAVE_FILE = SAVE_DIR.resolve("progress.json");
    private static final Path METADATA_FILE = SAVE_DIR.resolve("metadata.json");


    private static final Path CLEAN_EXIT_FLAG     = SAVE_DIR.resolve(".clean-exit");

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

        // ایجاد دایرکتوری ذخیره‌سازی
        try {
            Files.createDirectories(SAVE_DIR);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // تایمر ذخیره دوره‌ای
        this.saveTimer = new Timer(saveIntervalMs, e -> performAutoSave());
        this.saveTimer.setRepeats(true);
    }

    /**
     * شروع ذخیره‌سازی خودکار
     */
    public void start() {
        enabled = true;
        saveTimer.start();
        performAutoSave(); // ذخیره فوری اولیه
    }

    /**
     * توقف ذخیره‌سازی خودکار
     */
    public void stop() {
        enabled = false;
        saveTimer.stop();
    }

    /**
     * انجام عملیات ذخیره‌سازی
     */
    private void performAutoSave() {
        if (!enabled) return;

        try {
            NetworkSnapshot snapshot = snapshotService.buildSnapshot();

            // ایجاد متادیتا
            SaveMetadata metadata = createMetadata(snapshot);
            lastMetadata = metadata;

            // ذخیره snapshot
            String json = gson.toJson(snapshot);
            Files.writeString(PROGRESS_SAVE_FILE, json,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            // ذخیره متادیتا
            String metaJson = gson.toJson(metadata);
            Files.writeString(METADATA_FILE, metaJson,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);


        } catch (Exception e) {
        }
    }

    /**
     * بررسی وجود فایل ذخیره شده
     */
    public static boolean hasSavedProgress() {
        return Files.exists(PROGRESS_SAVE_FILE);
    }
    /**
     * خواندن متادیتای ذخیره شده
     */
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

    /**
     * بازیابی snapshot ذخیره شده
     */
    public static NetworkSnapshot loadSavedProgress() {
        if (!Files.exists(PROGRESS_SAVE_FILE)) return null;

        try {
            String json = Files.readString(PROGRESS_SAVE_FILE);
            Gson gson = new Gson();
            return gson.fromJson(json, NetworkSnapshot.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * حذف فایل‌های ذخیره شده (برای خروج عادی)
     */
    public static void clearSavedProgress() {
        try {
            Files.deleteIfExists(PROGRESS_SAVE_FILE);
            Files.deleteIfExists(METADATA_FILE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * ایجاد متادیتا از snapshot
     */
    private SaveMetadata createMetadata(NetworkSnapshot snapshot) {
        // محاسبه درصد پیشرفت
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

    /**
     * دریافت وضعیت فعال بودن
     */
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

        // تلاش برای ساخت از خود snapshot
        NetworkSnapshot snap = loadSavedProgress();
        if (snap != null) {
            SaveMetadata sm = new SaveMetadata();
            sm.isValid = true;
            sm.levelNumber = (snap.meta != null ? snap.meta.levelNumber : 1);
            sm.levelName   = "Level " + sm.levelNumber;
            sm.score       = (snap.world != null ? snap.world.score : 0);
            sm.coins       = (snap.world != null ? snap.world.coins : 0);
            sm.progressPercent = 0.0; // اگر خواستی از snap محاسبه کن
            try {
                sm.timestamp = Files.getLastModifiedTime(PROGRESS_SAVE_FILE)
                        .toInstant().toString().replace("Z",""); // ISO-like
            } catch (Exception ignore) {
                sm.timestamp = java.time.LocalDateTime.now().toString();
            }
            return sm;
        }

        // آخرین راه‌حل: فقط زمان فایل را نشان بده
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

}
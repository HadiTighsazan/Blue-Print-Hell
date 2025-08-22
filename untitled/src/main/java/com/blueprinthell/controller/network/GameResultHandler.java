package com.blueprinthell.controller.network;

import com.blueprinthell.client.network.ConnectionManager;
import com.blueprinthell.controller.GameController;
import com.blueprinthell.level.Level;
import com.blueprinthell.shared.protocol.NetworkProtocol.*;

/**
 * هندلر برای ثبت و ارسال نتایج بازی
 * ارتباط بین GameController و ConnectionManager
 */
public class GameResultHandler {

    private final GameController gameController;
    private final ConnectionManager connectionManager;

    private long levelStartTime;
    private GameMode currentMode;

    public GameResultHandler(GameController gameController, ConnectionManager connectionManager) {
        this.gameController = gameController;
        this.connectionManager = connectionManager;
        this.currentMode = GameMode.SOLO_OFFLINE; // پیش‌فرض
    }

    /**
     * ثبت شروع مرحله
     */
    public void onLevelStart(Level level, GameMode mode) {
        this.levelStartTime = System.currentTimeMillis();
        this.currentMode = mode;

        System.out.println("Level " + level.getLevelNumber() + " started in " + mode + " mode");
    }

    /**
     * ثبت اتمام موفق مرحله
     */
    public void onLevelCompleted(Level level) {
        long duration = System.currentTimeMillis() - levelStartTime;

        // محاسبه XP
        int baseXp = calculateXP(level.getLevelNumber(), duration);

        // ایجاد GameResult
        GameResult result = createGameResult(
                level.getLevelNumber(),
                duration,
                true, // completed
                baseXp
        );

        // ارسال به سرور یا ذخیره در صف آفلاین
        submitResult(result);

        System.out.println("Level completed: " + level.getLevelNumber() +
                " (duration=" + duration + "ms, xp=" + baseXp + ", mode=" + currentMode + ")");
    }

    /**
     * ثبت شکست در مرحله (Game Over)
     */
    public void onLevelFailed(Level level) {
        long duration = System.currentTimeMillis() - levelStartTime;

        // XP کمتر برای شکست
        int xp = calculateXP(level.getLevelNumber(), duration) / 4;

        // ایجاد GameResult
        GameResult result = createGameResult(
                level.getLevelNumber(),
                duration,
                false, // failed
                xp
        );

        // ارسال به سرور یا ذخیره در صف آفلاین
        submitResult(result);

        System.out.println("Level failed: " + level.getLevelNumber() +
                " (duration=" + duration + "ms, xp=" + xp + ", mode=" + currentMode + ")");
    }

    /**
     * ایجاد GameResult از وضعیت فعلی بازی
     */
    private GameResult createGameResult(int level, long duration, boolean completed, int xp) {
        GameResult result = new GameResult();

        result.userId = connectionManager.getUserId();
        result.mode = currentMode;
        result.ruleset = Ruleset.SP_V1; // تنها ruleset فعلی
        result.level = level;
        result.durationMs = duration;

        // اطلاعات از GameController
        if (gameController != null) {
            result.score = gameController.getScoreModel().getScore();
            result.loss = gameController.getLossModel().getLostCount();

            // محاسبه تعداد پکت‌های تحویل داده شده
            if (gameController.getProducerController() != null) {
                int produced = gameController.getProducerController().getProducedUnits();
                result.delivered = produced - result.loss;
            } else {
                result.delivered = 0;
            }
        }

        result.xp = xp;

        return result;
    }

    /**
     * ارسال نتیجه به سرور یا صف آفلاین
     */
    private void submitResult(GameResult result) {
        connectionManager.submitResult(result);
    }

    /**
     * محاسبه XP بر اساس سطح و زمان
     */
    private int calculateXP(int level, long durationMs) {
        // XP پایه برای هر سطح
        int baseXp = level * 100;

        // بونوس سرعت (کمتر از 60 ثانیه = بونوس بیشتر)
        if (durationMs < 60000) {
            baseXp += 50;
        } else if (durationMs < 120000) {
            baseXp += 25;
        }

        // ضریب برای حالت آنلاین (20% بیشتر)
        if (currentMode == GameMode.SOLO_ONLINE) {
            baseXp = (int)(baseXp * 1.2);
        }

        return baseXp;
    }

    /**
     * تغییر حالت بازی (آنلاین/آفلاین)
     */
    public void setGameMode(GameMode mode) {
        this.currentMode = mode;
    }

    public GameMode getGameMode() {
        return currentMode;
    }
}
package com.blueprinthell.level;

import com.blueprinthell.controller.GameController;
import com.blueprinthell.controller.ScreenController;
import com.blueprinthell.view.screens.MissionPassedView;

/**
 * Drives progression through sequential levels using {@link LevelGenerator}.
 * مسئول (۱) شروع بازی، (۲) نمایش صفحهٔ «Mission Passed» پس از اتمام موفق هر مرحله،
 * و (۳) شروع مرحلهٔ بعدی هنگامی‌که کاربر دکمهٔ «Next Mission» را می‌زند.
 */
public class LevelManager {
    private final GameController    gameController;
    private final ScreenController  screenController;

    private LevelDefinition current;
    private int levelIndex = -1; // 0‑based

    public LevelManager(GameController gc, ScreenController sc) {
        this.gameController  = gc;
        this.screenController = sc;
    }

    /* ---------- Public API ---------- */

    /** Starts a brand‑new game from level 0. */
    public void startGame() {
        levelIndex = 0;
        current = LevelGenerator.firstLevel();
        gameController.startLevel(current);
    }

    /**
     * Called by the simulation when ALL packets have been processed with <50% loss.
     * Shows the Mission‑Passed screen but **does not** advance the level yet.
     */
    public void reportLevelCompleted() {
        // Populate summary
        MissionPassedView mv = screenController.getMissionPassedView();
        mv.setSummary(levelIndex + 1,
                gameController.getScoreModel().getScore(),
                gameController.getLossModel().getLostCount());
        // Switch screen
        screenController.showScreen(ScreenController.MISSION_PASSED);
    }

    /**
     * Starts the next level after the user presses «Next Mission».
     */
    public void startNextLevel() {
        levelIndex++;
        current = LevelGenerator.nextLevel(current);
        gameController.startLevel(current);
        screenController.showScreen(ScreenController.GAME_SCREEN);
    }

    /* ---------- Getters ---------- */
    public int getLevelIndex()         { return levelIndex; }
    public LevelDefinition getCurrentDefinition() { return current; }
}

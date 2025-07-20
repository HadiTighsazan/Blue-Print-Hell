package com.blueprinthell.controller;

import com.blueprinthell.media.SoundSettings;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.view.screens.*;

import javax.swing.*;

/**
 * Wires UI buttons to navigation actions and level progression. Uses {@link LevelManager}
 * to manage level flow and screen transitions.
 */
public class MenuController {
    private final ScreenController screenController;
    private final LevelManager     levelManager;
    private final GameController   gameController;   // reference needed for retry

    public MenuController(ScreenController screenController,
                          GameController gameController) {
        this.screenController = screenController;
        this.gameController   = gameController; // save for later
        // LevelManager now needs ScreenController for mission‑passed screen
        this.levelManager = new LevelManager(gameController, screenController);
        // Inject back so GameController can notify success/fail
        gameController.setLevelManager(levelManager);
        attachListeners();
    }

    /* ---------------- Attach all UI listeners ---------------- */
    private void attachListeners() {
        /* ----- Main Menu ----- */
        MainMenuView mainMenu = screenController.getMainMenuView();
        mainMenu.startButton.addActionListener(e -> {
            levelManager.startGame();
            screenController.showScreen(ScreenController.GAME_SCREEN);
        });
        mainMenu.settingsButton.addActionListener(e ->
                screenController.showScreen(ScreenController.SETTINGS));
        mainMenu.exitButton.addActionListener(e -> System.exit(0));

        /* ----- Settings Menu ----- */
        SettingsMenuView settings = screenController.getSettingsMenuView();
        new SettingsController(settings);
        settings.volumeSlider.setValue(Math.round(SoundSettings.getVolume() * 100));
        settings.volumeSlider.addChangeListener(e -> {
            if (!settings.volumeSlider.getValueIsAdjusting()) {
                SoundSettings.setVolume(settings.volumeSlider.getValue() / 100f);
            }
        });
        SoundSettings.addVolumeListener(v -> settings.volumeSlider.setValue(Math.round(v * 100)));
        settings.backButton.addActionListener(e ->
                screenController.showScreen(ScreenController.MAIN_MENU));

        /* ----- Mission Passed ----- */
        MissionPassedView missionPassed = screenController.getMissionPassedView();
        missionPassed.nextMissionButton.addActionListener(e -> levelManager.startNextLevel());
        missionPassed.mainMenuButton.addActionListener(e ->
                screenController.showScreen(ScreenController.MAIN_MENU));

        /* ----- Game Over ----- */
        GameOverView gameOver = screenController.getGameOverView();
        // Retry now uses GameController.retryStage() instead of starting a fresh game
        gameOver.retryButton.addActionListener(e -> {
            gameController.retryStage();                             // ریست منطق بازی
            screenController.showScreen(ScreenController.GAME_SCREEN); // برگشت به نمای بازی
        });

        gameOver.mainMenuButton.addActionListener(e ->
                screenController.showScreen(ScreenController.MAIN_MENU));

        /* ----- Level Select (optional debugging) ----- */
        LevelSelectView levelSelect = screenController.getLevelSelectView();
        for (JButton btn : levelSelect.getLevelButtons()) {
            btn.addActionListener(e -> {
                int lv = Integer.parseInt(btn.getText().split(" ")[1]);
                levelManager.startGame();
                // Advance until desired level (1‑based index)
                for (int i = 1; i < lv; i++) levelManager.startNextLevel();
                screenController.showScreen(ScreenController.GAME_SCREEN);
            });
        }
        levelSelect.backButton.addActionListener(e ->
                screenController.showScreen(ScreenController.MAIN_MENU));
    }
}

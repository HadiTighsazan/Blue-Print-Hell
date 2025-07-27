package com.blueprinthell.controller;

import com.blueprinthell.media.SoundSettings;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.view.screens.*;

import javax.swing.*;


public class MenuController {
    private final ScreenController screenController;
    private final LevelManager     levelManager;
    private final GameController   gameController;

    public MenuController(ScreenController screenController,
                          GameController gameController) {
        this.screenController = screenController;
        this.gameController   = gameController;
        this.levelManager = new LevelManager(gameController, screenController);
        gameController.setLevelManager(levelManager);
        attachListeners();
    }

    private void attachListeners() {
        MainMenuView mainMenu = screenController.getMainMenuView();
        mainMenu.startButton.addActionListener(e -> {
            levelManager.startGame();
            screenController.showScreen(ScreenController.GAME_SCREEN);
        });
        mainMenu.settingsButton.addActionListener(e ->
                screenController.showScreen(ScreenController.SETTINGS));
        mainMenu.exitButton.addActionListener(e -> System.exit(0));

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

        MissionPassedView missionPassed = screenController.getMissionPassedView();
        missionPassed.nextMissionButton.addActionListener(e -> levelManager.startNextLevel());
        missionPassed.mainMenuButton.addActionListener(e ->
                screenController.showScreen(ScreenController.MAIN_MENU));

        GameOverView gameOver = screenController.getGameOverView();
        gameOver.retryButton.addActionListener(e -> {
            gameController.retryStage();
            screenController.showScreen(ScreenController.GAME_SCREEN);
        });

        gameOver.mainMenuButton.addActionListener(e ->
                screenController.showScreen(ScreenController.MAIN_MENU));

        LevelSelectView levelSelect = screenController.getLevelSelectView();
        for (JButton btn : levelSelect.getLevelButtons()) {
            btn.addActionListener(e -> {
                int lv = Integer.parseInt(btn.getText().split(" ")[1]);
                levelManager.startGame();
                for (int i = 1; i < lv; i++) levelManager.startNextLevel();
                screenController.showScreen(ScreenController.GAME_SCREEN);
            });
        }
        levelSelect.backButton.addActionListener(e ->
                screenController.showScreen(ScreenController.MAIN_MENU));
    }
}

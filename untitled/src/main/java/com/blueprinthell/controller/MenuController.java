package com.blueprinthell.controller;

import com.blueprinthell.view.screens.*;
import javax.swing.*;

/**
 * Controller to attach menu button actions to screen navigation, including starting game.
 */
public class MenuController {
    private final ScreenController screenController;
    private final GameController gameController;

    public MenuController(ScreenController screenController, GameController gameController) {
        this.screenController = screenController;
        this.gameController   = gameController;
        attachListeners();
    }

    private void attachListeners() {
        // Main Menu
        MainMenuView mainMenu = screenController.getMainMenuView();
        mainMenu.startButton.addActionListener(e -> {
            // اول کارت‌لی‌آوت رو به صفحهٔ بازی ببریم
            screenController.showScreen(ScreenController.GAME_SCREEN);
            // بعد سطح رو ست کنیم (و ویو و کنترلرها رو init کنیم)
            gameController.startLevel(1);
        });

        mainMenu.settingsButton.addActionListener(e ->
                screenController.showScreen(ScreenController.SETTINGS)
        );
        mainMenu.exitButton.addActionListener(e ->
                System.exit(0)
        );

        // Settings Menu
        SettingsMenuView settings = screenController.getSettingsMenuView();
        settings.backButton.addActionListener(e ->
                screenController.showScreen(ScreenController.MAIN_MENU)
        );

        // Level Select - optional if bypassed
        LevelSelectView levelSelect = screenController.getLevelSelectView();
        for (JButton btn : levelSelect.getLevelButtons()) {
            btn.addActionListener(e -> {
                int level = Integer.parseInt(btn.getText().split(" ")[1]);
                gameController.startLevel(level);
                screenController.showScreen(ScreenController.GAME_SCREEN);
            });
        }
        levelSelect.backButton.addActionListener(e ->
                screenController.showScreen(ScreenController.MAIN_MENU)
        );

        // Mission Passed
        MissionPassedView missionPassed = screenController.getMissionPassedView();
        missionPassed.nextMissionButton.addActionListener(e -> {
            gameController.startLevel(2); // next level example
            screenController.showScreen(ScreenController.GAME_SCREEN);
        });
        missionPassed.mainMenuButton.addActionListener(e ->
                screenController.showScreen(ScreenController.MAIN_MENU)
        );

        // Game Over
        GameOverView gameOver = screenController.getGameOverView();
        gameOver.retryButton.addActionListener(e -> {
            gameController.startLevel(1);
            screenController.showScreen(ScreenController.GAME_SCREEN);
        });
        gameOver.mainMenuButton.addActionListener(e ->
                screenController.showScreen(ScreenController.MAIN_MENU)
        );
    }
}

package com.blueprinthell.level;

import com.blueprinthell.controller.GameController;
import com.blueprinthell.controller.ScreenController;
import com.blueprinthell.view.screens.MissionPassedView;


public class LevelManager {
    private final GameController gameController;
    private final ScreenController screenController;

    private Level currentLevel;
    private int currentLevelNumber = 0;
    private boolean missionReported = false;

    public LevelManager(GameController gc, ScreenController sc) {
        this.gameController = gc;
        this.screenController = sc;
    }


    public void startGame() {
        currentLevelNumber = 1;
        loadLevel(currentLevelNumber);
    }


    public void loadLevel(int levelNumber) {
        if (!LevelRegistry.isValidLevel(levelNumber)) {
            throw new IllegalStateException("Invalid level number: " + levelNumber);
        }

        currentLevel = LevelRegistry.getLevel(levelNumber);
        currentLevelNumber = levelNumber;
        missionReported = false;

        gameController.startLevel(currentLevel.getDefinition());
    }


    public void reportLevelCompleted() {
        if (missionReported) return;
        missionReported = true;

        gameController.getSimulation().stop();

        MissionPassedView mv = screenController.getMissionPassedView();
        mv.setSummary(
                currentLevelNumber,
                gameController.getScoreModel().getScore(),
                gameController.getLossModel().getLostCount()
        );

        screenController.showScreen(ScreenController.MISSION_PASSED);
    }


    public void startNextLevel() {
        if (currentLevelNumber >= LevelRegistry.getTotalLevels()) {
            // Game completed!
            showGameCompleteScreen();
        } else {
            currentLevelNumber++;
            loadLevel(currentLevelNumber);
            screenController.showScreen(ScreenController.GAME_SCREEN);
        }
    }


    public void retryCurrentLevel() {
        if (currentLevel != null) {
            missionReported = false;
            gameController.startLevel(currentLevel.getDefinition());
        }
    }


    public void jumpToLevel(int levelNumber) {
        if (LevelRegistry.isValidLevel(levelNumber)) {
            currentLevelNumber = levelNumber;
            loadLevel(levelNumber);
            screenController.showScreen(ScreenController.GAME_SCREEN);
        }
    }

    public int getLevelIndex() {
        return currentLevelNumber - 1;
    }

    public Level getCurrentLevel() {
        return currentLevel;
    }

    public LevelDefinition getCurrentDefinition() {
        return currentLevel != null ? currentLevel.getDefinition() : null;
    }

    public int getCurrentLevelNumber() {
        return currentLevelNumber;
    }

    public int getTotalLevels() {
        return LevelRegistry.getTotalLevels();
    }


    private void showGameCompleteScreen() {
        // TODO: Implement game complete screen
        System.out.println("Congratulations! You've completed all levels!");
        // For now, just go back to main menu
        screenController.showScreen(ScreenController.MAIN_MENU);
    }
}
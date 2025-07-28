package com.blueprinthell.level;

import com.blueprinthell.controller.GameController;
import com.blueprinthell.controller.ScreenController;
import com.blueprinthell.view.screens.MissionPassedView;


public class LevelManager {
    private final GameController    gameController;
    private final ScreenController  screenController;

    private LevelDefinition current;
    private int levelIndex = -1;

    private boolean missionReported = false;

    public LevelManager(GameController gc, ScreenController sc) {
        this.gameController  = gc;
        this.screenController = sc;
    }


    public void startGame() {
        levelIndex = 0;
        current = LevelGenerator.firstLevel();
        missionReported = false;
        gameController.startLevel(current);
    }


    public void reportLevelCompleted() {

        if (missionReported) return;
        missionReported = true;
        gameController.getSimulation().stop();

        MissionPassedView mv = screenController.getMissionPassedView();
        mv.setSummary(levelIndex + 1,
                gameController.getScoreModel().getScore(),
                gameController.getLossModel().getLostCount());
        screenController.showScreen(ScreenController.MISSION_PASSED);
    }


    public void startNextLevel() {
        levelIndex++;
        current = LevelGenerator.nextLevel(current);
        missionReported = false;
        gameController.startLevel(current);
        screenController.showScreen(ScreenController.GAME_SCREEN);
    }

    public int getLevelIndex()         { return levelIndex; }
    public LevelDefinition getCurrentDefinition() { return current; }
}

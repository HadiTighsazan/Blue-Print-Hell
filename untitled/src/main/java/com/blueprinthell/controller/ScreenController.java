package com.blueprinthell.controller;

import com.blueprinthell.view.screens.*;
import javax.swing.*;
import java.awt.*;

/**
 * Controls navigation between different application screens using CardLayout.
 */
public class ScreenController {
    public static final String MAIN_MENU      = "MainMenu";
    public static final String SETTINGS       = "Settings";
    public static final String GAME_OVER      = "GameOver";
    public static final String MISSION_PASSED = "MissionPassed";
    public static final String LEVEL_SELECT   = "LevelSelect";
    public static final String GAME_SCREEN    = "GameScreen";

    private final JPanel mainPanel;
    private final CardLayout cardLayout;

    private final MainMenuView    mainMenuView;
    private final SettingsMenuView settingsMenuView;
    private final GameOverView     gameOverView;
    private final MissionPassedView missionPassedView;
    private final LevelSelectView   levelSelectView;

    public ScreenController(JFrame frame) {
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        frame.getContentPane().removeAll();
        frame.getContentPane().add(mainPanel, BorderLayout.CENTER);

        // Initialize screens
        mainMenuView     = new MainMenuView();
        settingsMenuView = new SettingsMenuView();
        gameOverView     = new GameOverView();
        missionPassedView= new MissionPassedView();
        levelSelectView  = new LevelSelectView();

        // GameScreenView will be registered later by GameController

        // Add to card layout
        mainPanel.add(mainMenuView,      MAIN_MENU);
        mainPanel.add(settingsMenuView,  SETTINGS);
        mainPanel.add(gameOverView,      GAME_OVER);
        mainPanel.add(missionPassedView, MISSION_PASSED);
        mainPanel.add(levelSelectView,   LEVEL_SELECT);

        // Show main menu initially
        showScreen(MAIN_MENU);
    }

    /**
     * Registers the game screen panel for game play.
     */
    public void registerGameScreen(GameScreenView gameScreen) {
        mainPanel.add(gameScreen, GAME_SCREEN);
    }

    public void showScreen(String name) {
        cardLayout.show(mainPanel, name);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    public MainMenuView getMainMenuView() {
        return mainMenuView;
    }

    public SettingsMenuView getSettingsMenuView() {
        return settingsMenuView;
    }

    public GameOverView getGameOverView() {
        return gameOverView;
    }

    public MissionPassedView getMissionPassedView() {
        return missionPassedView;
    }

    public LevelSelectView getLevelSelectView() {
        return levelSelectView;
    }
}

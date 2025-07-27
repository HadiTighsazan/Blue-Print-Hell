package com.blueprinthell.controller;

import com.blueprinthell.media.AudioController;
import com.blueprinthell.media.ResourceManager;
import com.blueprinthell.view.screens.*;

import javax.sound.sampled.Clip;
import javax.swing.*;
import java.awt.*;


public class ScreenController {
    public static final String MAIN_MENU      = "MainMenu";
    public static final String SETTINGS       = "Settings";
    public static final String GAME_OVER      = "GameOver";
    public static final String MISSION_PASSED = "MissionPassed";
    public static final String LEVEL_SELECT   = "LevelSelect";
    public static final String GAME_SCREEN    = "GameScreen";

    private final CardLayout cardLayout;
    private final JPanel mainPanel;

    private final MainMenuView      mainMenuView;
    private final SettingsMenuView  settingsMenuView;
    private final GameOverView      gameOverView;
    private final MissionPassedView missionPassedView;
    private final LevelSelectView   levelSelectView;

    private AudioController audioController;
    private String currentScreen = MAIN_MENU;

    public ScreenController(JFrame frame) {
        cardLayout = new CardLayout();
        mainPanel  = new JPanel(cardLayout);
        frame.getContentPane().removeAll();
        frame.getContentPane().add(mainPanel, BorderLayout.CENTER);

        mainMenuView      = new MainMenuView();
        settingsMenuView  = new SettingsMenuView();
        gameOverView      = new GameOverView();
        missionPassedView = new MissionPassedView();
        levelSelectView   = new LevelSelectView();

        mainPanel.add(mainMenuView,      MAIN_MENU);
        mainPanel.add(settingsMenuView,  SETTINGS);
        mainPanel.add(gameOverView,      GAME_OVER);
        mainPanel.add(missionPassedView, MISSION_PASSED);
        mainPanel.add(levelSelectView,   LEVEL_SELECT);

        showScreen(MAIN_MENU);
    }

    public void registerGameScreen(GameScreenView gameScreen) {
        mainPanel.add(gameScreen, GAME_SCREEN);
    }

    public void setAudioController(AudioController ac) { this.audioController = ac; }


    public void showScreen(String name) {
        boolean enteringGame   = GAME_SCREEN.equals(name) && !GAME_SCREEN.equals(currentScreen);
        boolean leavingGame    = !GAME_SCREEN.equals(name) && GAME_SCREEN.equals(currentScreen);
        boolean enteringGameOver = GAME_OVER.equals(name) && !GAME_OVER.equals(currentScreen);


        if (enteringGameOver) {
            try {
                Clip clip = ResourceManager.INSTANCE.getClip("gameover_jingle.wav");
                clip.stop();
                clip.setFramePosition(0);
                clip.start();
            } catch (Exception ignored) { }
        }

        currentScreen = name;
        cardLayout.show(mainPanel, name);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    public JPanel getMainPanel()                 { return mainPanel;        }
    public MainMenuView getMainMenuView()        { return mainMenuView;     }
    public SettingsMenuView getSettingsMenuView(){ return settingsMenuView; }
    public GameOverView getGameOverView()        { return gameOverView;     }
    public MissionPassedView getMissionPassedView(){ return missionPassedView; }
    public LevelSelectView getLevelSelectView()  { return levelSelectView;  }
}

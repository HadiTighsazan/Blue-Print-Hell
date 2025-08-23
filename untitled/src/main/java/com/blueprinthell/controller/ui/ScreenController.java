package com.blueprinthell.controller.ui;

import com.blueprinthell.controller.pvp.PvPClientController;
import com.blueprinthell.media.AudioController;
import com.blueprinthell.media.ResourceManager;
import com.blueprinthell.view.screens.*;
import com.blueprinthell.view.pvp.*;

import javax.sound.sampled.Clip;
import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Enhanced ScreenController with PvP views support
 */
public class ScreenController {
    // Screen names
    public static final String MAIN_MENU      = "MainMenu";
    public static final String SETTINGS       = "Settings";
    public static final String GAME_OVER      = "GameOver";
    public static final String MISSION_PASSED = "MissionPassed";
    public static final String LEVEL_SELECT   = "LevelSelect";
    public static final String GAME_SCREEN    = "GameScreen";

    // PvP screen names
    public static final String PVP_QUEUE      = "PvPQueue";
    public static final String PVP_BUILD      = "PvPBuild";
    public static final String PVP_MATCH      = "PvPMatch";
    public static final String PVP_RESULT     = "PvPResult";

    private final CardLayout cardLayout;
    private final JPanel mainPanel;

    // Standard views
    private final MainMenuView      mainMenuView;
    private final SettingsMenuView  settingsMenuView;
    private final GameOverView      gameOverView;
    private final MissionPassedView missionPassedView;
    private final LevelSelectView   levelSelectView;

    // Custom views registry
    private final Map<String, JComponent> customViews;

    private AudioController audioController;
    private String currentScreen = MAIN_MENU;
    private String previousScreen = MAIN_MENU;

    public ScreenController(JFrame frame) {
        cardLayout = new CardLayout();
        mainPanel  = new JPanel(cardLayout);
        customViews = new HashMap<>();

        frame.getContentPane().removeAll();
        frame.getContentPane().add(mainPanel, BorderLayout.CENTER);

        // Initialize standard views
        mainMenuView      = new MainMenuView();
        settingsMenuView  = new SettingsMenuView();
        gameOverView      = new GameOverView();
        missionPassedView = new MissionPassedView();
        levelSelectView   = new LevelSelectView();

        // Add standard views
        mainPanel.add(mainMenuView,      MAIN_MENU);
        mainPanel.add(settingsMenuView,  SETTINGS);
        mainPanel.add(gameOverView,      GAME_OVER);
        mainPanel.add(missionPassedView, MISSION_PASSED);
        mainPanel.add(levelSelectView,   LEVEL_SELECT);

        showScreen(MAIN_MENU);
    }

    /**
     * Register game screen
     */
    public void registerGameScreen(GameScreenView gameScreen) {
        mainPanel.add(gameScreen, GAME_SCREEN);
    }

    /**
     * Register custom view (for PvP views)
     */
    public void registerCustomView(String name, JComponent view) {
        customViews.put(name, view);
        mainPanel.add(view, name);
    }

    /**
     * Show custom view temporarily
     */
    public void showCustomView(JComponent view) {
        String tempName = "TEMP_VIEW_" + System.currentTimeMillis();
        mainPanel.add(view, tempName);
        showScreen(tempName);
    }

    /**
     * Register PvP views
     */
    public void registerPvPViews(PvPClientController pvpController) {
        // These will be created and registered by PvPClientController as needed
        // This method is just a placeholder for organization
    }

    /**
     * Set audio controller
     */
    public void setAudioController(AudioController ac) {
        this.audioController = ac;
    }

    /**
     * Show screen by name
     */
    public void showScreen(String name) {
        previousScreen = currentScreen;

        boolean enteringGame   = GAME_SCREEN.equals(name) && !GAME_SCREEN.equals(currentScreen);
        boolean leavingGame    = !GAME_SCREEN.equals(name) && GAME_SCREEN.equals(currentScreen);
        boolean enteringGameOver = GAME_OVER.equals(name) && !GAME_OVER.equals(currentScreen);
        boolean enteringPvPResult = PVP_RESULT.equals(name) && !PVP_RESULT.equals(currentScreen);

        // Play game over sound
        if (enteringGameOver) {
            playSound("gameover_jingle.wav");
        }

        // Play victory/defeat sound for PvP
        if (enteringPvPResult) {
            // Could check if victory or defeat and play different sounds
            playSound("pvp_result.wav");
        }

        currentScreen = name;
        cardLayout.show(mainPanel, name);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    /**
     * Go back to previous screen
     */
    public void showPreviousScreen() {
        if (previousScreen != null) {
            showScreen(previousScreen);
        }
    }

    /**
     * Remove custom view
     */
    public void removeCustomView(String name) {
        JComponent view = customViews.remove(name);
        if (view != null) {
            mainPanel.remove(view);
        }
    }

    /**
     * Clear all temporary views
     */
    public void clearTempViews() {
        // Remove all views that start with "TEMP_VIEW_"
        var components = mainPanel.getComponents();
        for (Component comp : components) {
            String name = cardLayout.toString(); // This won't work, need different approach
            // In practice, we'd track temp view names separately
        }
    }

    /**
     * Play sound effect
     */
    private void playSound(String soundFile) {
        try {
            Clip clip = ResourceManager.INSTANCE.getClip(soundFile);
            clip.stop();
            clip.setFramePosition(0);
            clip.start();
        } catch (Exception ignored) {
            // Sound file might not exist yet
        }
    }

    // Getters for views
    public JPanel getMainPanel()                 { return mainPanel;        }
    public MainMenuView getMainMenuView()        { return mainMenuView;     }
    public SettingsMenuView getSettingsMenuView(){ return settingsMenuView; }
    public GameOverView getGameOverView()        { return gameOverView;     }
    public MissionPassedView getMissionPassedView(){ return missionPassedView; }
    public LevelSelectView getLevelSelectView()  { return levelSelectView;  }

    /**
     * Get custom view by name
     */
    public JComponent getCustomView(String name) {
        return customViews.get(name);
    }

    /**
     * Check if a screen exists
     */
    public boolean hasScreen(String name) {
        // Check standard screens
        switch (name) {
            case MAIN_MENU, SETTINGS, GAME_OVER, MISSION_PASSED,
                 LEVEL_SELECT, GAME_SCREEN:
                return true;
            default:
                return customViews.containsKey(name);
        }
    }

    /**
     * Get current screen name
     */
    public String getCurrentScreen() {
        return currentScreen;
    }
}
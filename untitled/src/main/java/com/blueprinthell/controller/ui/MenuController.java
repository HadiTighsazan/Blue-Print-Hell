package com.blueprinthell.controller.ui;

import com.blueprinthell.controller.packet.PacketProducerController;
import com.blueprinthell.controller.persistence.AutoSaveController;
import com.blueprinthell.controller.GameController;
import com.blueprinthell.controller.ui.editor.SystemBoxDragController;
import com.blueprinthell.level.LevelRegistry;
import com.blueprinthell.media.SoundSettings;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.snapshot.NetworkSnapshot;
import com.blueprinthell.view.dialogs.ResumeDialog;
import com.blueprinthell.view.screens.*;

import javax.swing.*;
import java.awt.*;


public class MenuController {
    private final ScreenController screenController;
    private final LevelManager     levelManager;
    private final GameController gameController;

    private boolean restorationInProgress = false;
    private boolean countdownShown = false;

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
        mainMenu.startButton.addActionListener(
                e -> handleStartGame()
        );
        mainMenu.settingsButton.addActionListener(e ->
                screenController.showScreen(ScreenController.SETTINGS));

        mainMenu.exitButton.addActionListener(e -> {
            gameController.stopAutoSaveAndClear();
            System.exit(0);
        });
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

        missionPassed.nextMissionButton.addActionListener(e -> {
            levelManager.startNextLevel();
        });

        missionPassed.mainMenuButton.addActionListener(e -> {

            screenController.showScreen(ScreenController.MAIN_MENU);
        });
        GameOverView gameOver = screenController.getGameOverView();

        gameOver.retryButton.addActionListener(e -> {
            gameController.retryStage();
            screenController.showScreen(ScreenController.GAME_SCREEN);
        });

        gameOver.mainMenuButton.addActionListener(e -> {
            gameController.pauseAutoSave();
            screenController.showScreen(ScreenController.MAIN_MENU);
        });
        LevelSelectView levelSelect = screenController.getLevelSelectView();
        for (int i = 0; i < levelSelect.getLevelButtons().size(); i++) {
            JButton btn = levelSelect.getLevelButtons().get(i);
            final int levelNum = i + 1;

            if (LevelRegistry.isValidLevel(levelNum)) {
                btn.setText("Level " + levelNum + " - " + LevelRegistry.getLevel(levelNum).getName());
                btn.setEnabled(true);
                btn.addActionListener(e -> {
                    levelManager.jumpToLevel(levelNum);
                    screenController.showScreen(ScreenController.GAME_SCREEN);
                });
            } else {
                btn.setEnabled(false);
                btn.setText("Level " + levelNum + " (Locked)");
            }
        }

        levelSelect.backButton.addActionListener(e ->
                screenController.showScreen(ScreenController.MAIN_MENU));
    }

    private void handleStartGame() {

        if (AutoSaveController.hasSavedProgress()) {

            AutoSaveController.SaveMetadata metadata =
                    AutoSaveController.loadMetadataOrSynthesize();

            JFrame parentFrame = (JFrame) SwingUtilities.getWindowAncestor(
                    screenController.getMainMenuView()
            );
            ResumeDialog dialog = new ResumeDialog(parentFrame, metadata);
            dialog.setVisible(true);

            if (dialog.isResumeSelected()) {
                resumeSavedGame(); // ← این متد از progress.json می‌خوانَد
            } else {
                AutoSaveController.clearSavedProgress();
                startNewGame();
            }
        } else {
            startNewGame();
        }
    }

    private void startNewGame() {
        levelManager.startGame();
        screenController.showScreen(ScreenController.GAME_SCREEN);
    }
    private void resumeSavedGame() {
        if (restorationInProgress) return; // جلوگیری از اجرای همزمان
        restorationInProgress = true;

        NetworkSnapshot snapshot = AutoSaveController.loadSavedProgress();

        if (snapshot == null) {
            JOptionPane.showMessageDialog(null,
                    "Cheat Detected!\nStarting a new game.",
                    "Load Error",
                    JOptionPane.ERROR_MESSAGE);
            AutoSaveController.clearSavedProgress();
            startNewGame();
            restorationInProgress = false;
            return;
        }

        screenController.showScreen(ScreenController.GAME_SCREEN);

        int lvl = 1;
        try {
            if (snapshot.meta != null && snapshot.meta.levelNumber > 0) {
                lvl = snapshot.meta.levelNumber;
            }
        } catch (Exception ignore) {}

        // Load level بدون restore (فقط ساختار)
        gameController.getLevelManager().loadLevel(lvl);

        SwingUtilities.invokeLater(() -> {
            gameController.restoreState(snapshot);

            showRestoredGameCountdownOnce();

            restorationInProgress = false;
        });
    }

    private void showRestoredGameCountdownOnce() {
        if (countdownShown) return;
        countdownShown = true;

        SwingUtilities.invokeLater(() -> {
            GameScreenView gameView = gameController.getGameView();

            if (!gameView.isShowing()) {
                startRestoredGame();
                countdownShown = false;
                return;
            }

            JPanel overlay = new JPanel(new BorderLayout());
            overlay.setOpaque(true);
            overlay.setBackground(new Color(0, 0, 0, 180));
            overlay.setBounds(0, 0, gameView.getWidth(), gameView.getHeight());

            JLabel messageLabel = new JLabel("Game Restored - Starting in 3 seconds...");
            messageLabel.setFont(new Font("Arial", Font.BOLD, 32));
            messageLabel.setForeground(Color.YELLOW);
            messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
            overlay.add(messageLabel, BorderLayout.CENTER);

            gameView.add(overlay);
            gameView.setComponentZOrder(overlay, 0);
            gameView.revalidate();
            gameView.repaint();

            Timer countdown = new Timer(1000, null);
            final int[] seconds = {3};

            countdown.addActionListener(evt -> {
                seconds[0]--;
                if (seconds[0] > 0) {
                    messageLabel.setText("Game Restored - Starting in " + seconds[0] + " seconds...");
                } else {
                    // حذف overlay
                    gameView.remove(overlay);
                    gameView.revalidate();
                    gameView.repaint();

                    startRestoredGame();

                    countdown.stop();
                    countdownShown = false; // reset flag
                }
            });

            countdown.start();
        });
    }

    private void startRestoredGame() {
        gameController.getSimulation().start();
        gameController.resumeAutoSave();
        gameController.getTimeline().resume();

        PacketProducerController producer = gameController.getProducerController();

        if (producer != null && producer.isRunning()) {

            SystemBoxDragController.setDragEnabled(false);

            producer.startProduction();

            gameController.getHudCoord().setStartEnabled(false);
            gameController.getHudView().setToggleText("Pause");

        } else {

            SystemBoxDragController.setDragEnabled(true);


            gameController.updateStartEnabled();


        }
    }
}

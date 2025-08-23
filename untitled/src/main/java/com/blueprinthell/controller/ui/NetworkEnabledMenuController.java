package com.blueprinthell.controller.ui;

import com.blueprinthell.client.network.ConnectionManager;
import com.blueprinthell.controller.GameController;
import com.blueprinthell.controller.network.GameResultHandler;
import com.blueprinthell.controller.persistence.AutoSaveController;
import com.blueprinthell.controller.pvp.PvPClientController;
import com.blueprinthell.controller.ui.editor.SystemBoxDragController;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.level.LevelRegistry;
import com.blueprinthell.media.SoundSettings;
import com.blueprinthell.shared.protocol.NetworkProtocol.GameMode;
import com.blueprinthell.snapshot.NetworkSnapshot;
import com.blueprinthell.view.dialogs.ResumeDialog;
import com.blueprinthell.view.screens.*;

import javax.swing.*;
import java.awt.*;

/**
 * MenuController با پشتیبانی از شبکه
 */
public class NetworkEnabledMenuController {
    private final ScreenController screenController;
    private final LevelManager levelManager;
    private final GameController gameController;
    private final ConnectionManager connectionManager;
    private final GameResultHandler resultHandler;
    private NetworkMenuView networkMenuView;

    private boolean restorationInProgress = false;
    private boolean countdownShown = false;

    public NetworkEnabledMenuController(ScreenController screenController,
                                        GameController gameController,
                                        ConnectionManager connectionManager) {
        this.screenController = screenController;
        this.gameController = gameController;
        this.connectionManager = connectionManager;
        this.levelManager = new LevelManager(gameController, screenController);
        gameController.setLevelManager(levelManager);

        // ایجاد Result Handler
        this.resultHandler = new GameResultHandler(gameController, connectionManager);

        // افزودن Network Panel به منوی اصلی
        setupNetworkPanel();

        // اتصال listeners
        attachListeners();
    }

    private void setupNetworkPanel() {
        // ایجاد NetworkMenuView
        networkMenuView = new NetworkMenuView(connectionManager);

        // افزودن به منوی اصلی
        MainMenuView mainMenu = screenController.getMainMenuView();

        // تغییر layout منوی اصلی برای افزودن پنل شبکه
        JPanel originalPanel = new JPanel();
        originalPanel.setLayout(new BoxLayout(originalPanel, BoxLayout.Y_AXIS));
        originalPanel.setOpaque(false);

        // انتقال دکمه‌های اصلی
        originalPanel.add(Box.createVerticalGlue());
        originalPanel.add(mainMenu.startButton);
        originalPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        originalPanel.add(mainMenu.pvpButton);
        originalPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        originalPanel.add(mainMenu.settingsButton);
        originalPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        originalPanel.add(mainMenu.exitButton);
        originalPanel.add(Box.createVerticalGlue());

        // Layout جدید با پنل شبکه در کنار
        mainMenu.removeAll();
        mainMenu.setLayout(new BorderLayout(20, 0));
        mainMenu.add(originalPanel, BorderLayout.CENTER);
        mainMenu.add(networkMenuView, BorderLayout.EAST);

        mainMenu.revalidate();
        mainMenu.repaint();
    }

    private void attachListeners() {
        MainMenuView mainMenu = screenController.getMainMenuView();

        // دکمه Start با بررسی حالت آنلاین/آفلاین
        mainMenu.startButton.addActionListener(e -> handleStartGame());
        mainMenu.pvpButton.addActionListener(e -> {
            if (connectionManager.getState() != ConnectionManager.ConnectionState.CONNECTED) {
                JOptionPane.showMessageDialog(
                        mainMenu,
                        "Not connected to server.\nGo online in the panel on the right.",
                        "Connection required",
                        JOptionPane.WARNING_MESSAGE
                );

                // اختیاری: فوکوس/هایلایت بخش شبکه
                return;
            }

            // شروع صف PvP
            PvPClientController pvp = new PvPClientController(gameController, screenController, connectionManager);
            pvp.startQueue();
        });
        // دکمه Settings
        mainMenu.settingsButton.addActionListener(e ->
                screenController.showScreen(ScreenController.SETTINGS));

        // دکمه Exit
        mainMenu.exitButton.addActionListener(e -> {
            gameController.stopAutoSaveAndClear();
            System.exit(0);
        });

        // Settings Menu
        SettingsMenuView settings = screenController.getSettingsMenuView();
        new SettingsController(settings);
        settings.volumeSlider.setValue(Math.round(SoundSettings.getVolume() * 100));
        settings.volumeSlider.addChangeListener(e -> {
            if (!settings.volumeSlider.getValueIsAdjusting()) {
                SoundSettings.setVolume(settings.volumeSlider.getValue() / 100f);
            }
        });
        SoundSettings.addVolumeListener(v ->
                settings.volumeSlider.setValue(Math.round(v * 100)));
        settings.backButton.addActionListener(e ->
                screenController.showScreen(ScreenController.MAIN_MENU));

        // Mission Passed View
        MissionPassedView missionPassed = screenController.getMissionPassedView();
        missionPassed.nextMissionButton.addActionListener(e -> {
            // ثبت نتیجه موفق
            resultHandler.onLevelCompleted(levelManager.getCurrentLevel());
            levelManager.startNextLevel();
        });

        missionPassed.mainMenuButton.addActionListener(e -> {
            // ثبت نتیجه موفق
            resultHandler.onLevelCompleted(levelManager.getCurrentLevel());
            screenController.showScreen(ScreenController.MAIN_MENU);
        });

        // Game Over View
        GameOverView gameOver = screenController.getGameOverView();
        gameOver.retryButton.addActionListener(e -> {
            // ثبت نتیجه شکست
            resultHandler.onLevelFailed(levelManager.getCurrentLevel());
            gameController.retryStage();
            screenController.showScreen(ScreenController.GAME_SCREEN);
        });

        gameOver.mainMenuButton.addActionListener(e -> {
            // ثبت نتیجه شکست
            resultHandler.onLevelFailed(levelManager.getCurrentLevel());
            gameController.pauseAutoSave();
            screenController.showScreen(ScreenController.MAIN_MENU);
        });
    }

    private void handleStartGame() {
        // تعیین game mode
        GameMode mode = networkMenuView.getSelectedMode();

        // بررسی اتصال برای حالت آنلاین
        if (mode == GameMode.SOLO_ONLINE && !networkMenuView.isConnected()) {
            JOptionPane.showMessageDialog(screenController.getMainMenuView(),
                    "Please connect to server for online mode",
                    "Connection Required",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        resultHandler.setGameMode(mode);

        // بررسی saved progress
        if (AutoSaveController.hasSavedProgress()) {
            AutoSaveController.SaveMetadata metadata =
                    AutoSaveController.loadMetadataOrSynthesize();

            JFrame parentFrame = (JFrame) SwingUtilities.getWindowAncestor(
                    screenController.getMainMenuView());
            ResumeDialog dialog = new ResumeDialog(parentFrame, metadata);
            dialog.setVisible(true);

            if (dialog.isResumeSelected()) {
                resumeSavedGame();
            } else {
                AutoSaveController.clearSavedProgress();
                startNewGame(mode);
            }
        } else {
            startNewGame(mode);
        }
    }

    private void startNewGame(GameMode mode) {
        levelManager.startGame();
        resultHandler.onLevelStart(levelManager.getCurrentLevel(), mode);
        screenController.showScreen(ScreenController.GAME_SCREEN);
    }

    private void resumeSavedGame() {
        if (restorationInProgress) return;
        restorationInProgress = true;

        NetworkSnapshot snapshot = AutoSaveController.loadSavedProgress();
        if (snapshot == null) {
            JOptionPane.showMessageDialog(null,
                    "Failed to load saved game. Starting new game instead.",
                    "Load Error",
                    JOptionPane.ERROR_MESSAGE);
            startNewGame(GameMode.SOLO_OFFLINE);
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
                    messageLabel.setText("Game Restored - Starting in " +
                            seconds[0] + " seconds...");
                } else {
                    gameView.remove(overlay);
                    gameView.revalidate();
                    gameView.repaint();
                    startRestoredGame();
                    countdown.stop();
                    countdownShown = false;
                }
            });

            countdown.start();
        });
    }

    private void startRestoredGame() {
        gameController.getSimulation().start();

        if (gameController.getProducerController() != null
                && !gameController.getProducerController().isFinished()) {
            SystemBoxDragController.setDragEnabled(false);
            gameController.getProducerController().startProduction();
        }

        gameController.resumeAutoSave();
        gameController.getTimeline().resume();
        gameController.getHudCoord().setStartEnabled(false);
        gameController.getHudView().setToggleText("Pause");

        // ثبت شروع مرحله با mode فعلی
        resultHandler.onLevelStart(
                levelManager.getCurrentLevel(),
                resultHandler.getGameMode()
        );
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public GameResultHandler getResultHandler() {
        return resultHandler;
    }
}
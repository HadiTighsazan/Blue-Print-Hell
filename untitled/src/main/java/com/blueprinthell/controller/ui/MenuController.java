package com.blueprinthell.controller.ui;

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

    // --- Patch: guards for restoration & countdown ---
    private boolean restorationInProgress = false; // جلوگیری از اجرای همزمان بازیابی
    private boolean countdownShown = false;        // جلوگیری از نمایش دوبارهٔ شمارش معکوس

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
                e -> handleStartGame()  // قبلاً ممکن است مستقیم levelManager.startGame() بوده
        );
        mainMenu.settingsButton.addActionListener(e ->
                screenController.showScreen(ScreenController.SETTINGS));
        // فقط دکمه Exit فایل را پاک می‌کند (خروج عادی)
        mainMenu.exitButton.addActionListener(e -> {
            gameController.stopAutoSaveAndClear(); // explicit clear on Exit
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
            // AutoSave را stop نکنید، فقط restart کنید
            levelManager.startNextLevel();
        });

        missionPassed.mainMenuButton.addActionListener(e -> {
            // به منوی اصلی برگشتن AutoSave را پاک نمی‌کند
            // فقط متوقف می‌کند
            if (gameController.isAutoSaveRunning()) {
                // فقط timer را متوقف کن، فایل را پاک نکن
                // نیاز به متد جدید در AutoSaveController
            }
            screenController.showScreen(ScreenController.MAIN_MENU);
        });
        GameOverView gameOver = screenController.getGameOverView();

        gameOver.retryButton.addActionListener(e -> {
            gameController.retryStage();
            screenController.showScreen(ScreenController.GAME_SCREEN);
        });

        gameOver.mainMenuButton.addActionListener(e -> {
            gameController.pauseAutoSave(); // فقط pause، نه stop
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

    // شروع بازی جدید
    private void startNewGame() {
        levelManager.startGame();
        screenController.showScreen(ScreenController.GAME_SCREEN);
    }

    // --- Patch: اصلاح روند بازیابی بازی ذخیره‌شده ---
    private void resumeSavedGame() {
        if (restorationInProgress) return; // جلوگیری از اجرای همزمان
        restorationInProgress = true;

        NetworkSnapshot snapshot = AutoSaveController.loadSavedProgress();
        if (snapshot == null) {
            JOptionPane.showMessageDialog(null,
                    "Failed to load saved game. Starting new game instead.",
                    "Load Error",
                    JOptionPane.ERROR_MESSAGE);
            startNewGame();
            restorationInProgress = false;
            return;
        }

        // نمایش صفحه بازی
        screenController.showScreen(ScreenController.GAME_SCREEN);

        // ⭐ حذف فراخوانی مستقیم restoreFromSavedProgress
        // gameController.restoreFromSavedProgress(); // این خط حذف شد

        // ⭐ ابتدا level را load کنیم
        int lvl = 1;
        try {
            if (snapshot.meta != null && snapshot.meta.levelNumber > 0) {
                lvl = snapshot.meta.levelNumber;
            }
        } catch (Exception ignore) {}

        // Load level بدون restore (فقط ساختار)
        gameController.getLevelManager().loadLevel(lvl);

        // سپس بازیابی state و نمایش شمارش معکوس در چرخهٔ بعدی EDT
        SwingUtilities.invokeLater(() -> {
            // Restore state
            gameController.restoreState(snapshot);

            // نمایش countdown (یک‌بار)
            showRestoredGameCountdownOnce();

            restorationInProgress = false;
        });
    }

    // شمارش معکوس شروع پس از بازیابی — فقط یک‌بار نمایش داده می‌شود
    private void showRestoredGameCountdownOnce() {
        if (countdownShown) return; // جلوگیری از نمایش دوباره
        countdownShown = true;

        SwingUtilities.invokeLater(() -> {
            GameScreenView gameView = gameController.getGameView();

            // اگر صفحه هنوز نمایش داده نشده، مستقیم شروع کن
            if (!gameView.isShowing()) {
                startRestoredGame();
                countdownShown = false; // reset flag
                return;
            }

            // ایجاد overlay برای نمایش پیام
            JPanel overlay = new JPanel(new BorderLayout());
            overlay.setOpaque(true);
            overlay.setBackground(new Color(0, 0, 0, 180));
            overlay.setBounds(0, 0, gameView.getWidth(), gameView.getHeight());

            JLabel messageLabel = new JLabel("Game Restored - Starting in 3 seconds...");
            messageLabel.setFont(new Font("Arial", Font.BOLD, 32));
            messageLabel.setForeground(Color.YELLOW);
            messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
            overlay.add(messageLabel, BorderLayout.CENTER);

            // ابتدا اضافه کن، سپس Z-order را تنظیم کن
            gameView.add(overlay);
            gameView.setComponentZOrder(overlay, 0);
            gameView.revalidate();
            gameView.repaint();

            // تایمر شمارش معکوس
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

                    // شروع بازی و AutoSave
                    startRestoredGame();

                    countdown.stop();
                    countdownShown = false; // reset flag
                }
            });

            countdown.start();
        });
    }

    // متد شروع تمیز بازی پس از بازیابی
    private void startRestoredGame() {
        // شروع simulation
        gameController.getSimulation().start();

        // اگر Producer هنوز تمام نشده، تولید را ادامه بده
        if (gameController.getProducerController() != null
                && !gameController.getProducerController().isFinished()) {

            // قفل‌کردن درگ چون وارد اجرای مرحله می‌شویم
            SystemBoxDragController.setDragEnabled(false);

            gameController.getProducerController().startProduction();
        }

        // resume کردن AutoSave
        gameController.resumeAutoSave();

        // resume کردن timeline
        gameController.getTimeline().resume();

        // تنظیم دکمه pause
        gameController.getHudCoord().setStartEnabled(false);
        gameController.getHudView().setToggleText("Pause");
    }
}

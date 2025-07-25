package com.blueprinthell.controller;

import javax.swing.*;
import com.blueprinthell.view.HudView;

import java.awt.*;
import java.util.List;
import com.blueprinthell.controller.SimulationController;
import com.blueprinthell.controller.PacketProducerController;
import com.blueprinthell.model.WireModel;


/**
 * Entry-point class that wires together the high-level controllers and shows the main window.
 */
public class MainController {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            /* ---------------- Create main window ---------------- */
            JFrame frame = new JFrame("BlueprintHell");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
// (۱) تنظیم اندازهٔ پنجره برابر اندازهٔ صفحه
            frame.setSize(screenSize.width, screenSize.height);
// (۲) قرار دادن پنجره در حالت بزرگنمایی‌شده (maximized)
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
// اگر می‌خواهید نوار عنوان و حاشیه‌ها هم حذف شوند، قبل از setVisible:
            frame.setUndecorated(true);
            /* --------
            -------- Controllers wiring ---------------- */
            // 1. Screen manager (CardLayout)
            ScreenController screenController = new ScreenController(frame);

            // 2. Core game logic controller
            GameController gameController = new GameController(frame);
            // provide ScreenController for game-over and other screen flows
            gameController.setScreenController(screenController);

            // جدید: لینک دادن Simulation و PacketProducer
            SimulationController simController = gameController.getSimulation();
            PacketProducerController producerController = gameController.getProducerController();
            // ثبت کنترلر شبیه‌سازی در WireModel برای دریافت اعلان بازگشت پکت
            WireModel.setSimulationController(simController);
            // ثبت تولیدکننده پکت در SimulationController
            simController.setPacketProducerController(producerController);

            // Register the dynamic game screen so it can be displayed
            screenController.registerGameScreen(gameController.getGameView());

            // 3. Menu navigation controller
            new MenuController(screenController, gameController);

            // 4. UI controller (Store + Audio)
            UIController ui = new UIController(
                    frame,
                    (HudView) gameController.getGameView().getHudView(),
                    simController,
                    gameController.getCoinModel(),
                    gameController.getCollisionController(),
                    gameController.getLossModel(),
                    gameController.getWires(),
                    gameController.getHudController()
            );

            // Inject AudioController into ScreenController (music keeps looping across screens)
            screenController.setAudioController(ui.getAudioController());
            // Start background loop immediately at app launch
            ui.getAudioController().playBackgroundLoop();

            /* ---------------- Show initial screen ---------------- */
            screenController.showScreen(ScreenController.MAIN_MENU);

            /* ---------------- Display window ---------------- */
            frame.setVisible(true);
        });
    }
}

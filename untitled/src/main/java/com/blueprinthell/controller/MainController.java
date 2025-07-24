package com.blueprinthell.controller;

import javax.swing.*;
import java.awt.*;

import com.blueprinthell.view.HudView;
import com.blueprinthell.model.WireModel;

/**
 * Entry‑point class that wires together the high‑level controllers and shows the main window.
 */
public class MainController {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            /* ---------------- Create main window ---------------- */
            JFrame frame = new JFrame("BlueprintHell");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            frame.setSize(screenSize.width, screenSize.height);    // تمام‌صفحه
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            frame.setUndecorated(true);

            /* -------- Controllers wiring ---------------- */
            // 1. هستهٔ منطق بازی
            GameController gameController = new GameController(frame);
            ScreenController screenController = gameController.getScreenController();

            // 2. اتصال Simulation به مدل‌های استاتیک
            SimulationController simController = gameController.getSimulation();
            WireModel.setSimulationController(simController);

            // ★ دیگر نیازی به setPacketProducerController نیست؛
            // ★ GameController خودش PacketProducerController را تزریق کرده است.

            // 3. ثبت صفحهٔ دینامیک بازی
            screenController.registerGameScreen(gameController.getGameView());

            // 4. کنترل منو
            new MenuController(screenController, gameController);

            // 5. UIController (فروشگاه + صدا)
            UIController ui = new UIController(
                    frame,
                    (HudView) gameController.getGameView().getHudView(),
                    simController,
                    gameController.getCoinModel(),
                    gameController.getCollisionCtrl(),
                    gameController.getLossModel(),
                    gameController.getWires(),
                    gameController.getHudController()
            );

            // پخش موسیقی پس‌زمینه
            screenController.setAudioController(ui.getAudioController());
            ui.getAudioController().playBackgroundLoop();

            /* ---------------- Show initial screen ---------------- */
            screenController.showScreen(ScreenController.MAIN_MENU);

            /* ---------------- Display window ---------------- */
            frame.setVisible(true);
        });
    }
}

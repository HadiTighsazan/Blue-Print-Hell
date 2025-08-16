package com.blueprinthell.controller;

import javax.swing.*;

import com.blueprinthell.controller.packet.PacketProducerController;
import com.blueprinthell.controller.simulation.SimulationController;
import com.blueprinthell.controller.ui.MenuController;
import com.blueprinthell.controller.ui.ScreenController;
import com.blueprinthell.controller.ui.UIController;
import com.blueprinthell.view.HudView;

import java.awt.*;

import com.blueprinthell.model.WireModel;




public class MainController {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("BlueprintHell");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            frame.setSize(screenSize.width, screenSize.height);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            frame.setUndecorated(true);

            ScreenController screenController = new ScreenController(frame);

            GameController gameController = new GameController(frame);
            gameController.setScreenController(screenController);




            SimulationController simController = gameController.getSimulation();
            PacketProducerController producerController = gameController.getProducerController();
            WireModel.setSimulationController(simController);
            simController.setPacketProducerController(producerController);

            screenController.registerGameScreen(gameController.getGameView());

            new MenuController(screenController, gameController);

            UIController ui = new UIController(
                    frame,
                    (HudView) gameController.getGameView().getHudView(),
                    simController,
                    gameController.getCoinModel(),
                    gameController.getCollisionController(),
                    gameController.getLossModel(),
                    gameController.getWires(),
                    gameController.getHudController(),
                    gameController.getGameView()  // اضافه کردن این پارامتر
            );
            screenController.setAudioController(ui.getAudioController());
            ui.getAudioController().playBackgroundLoop();

            screenController.showScreen(ScreenController.MAIN_MENU);

            // اضافه کردن Shutdown Hook برای ذخیره در هنگام خروج غیرمنتظره
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                // هیچ‌گاه فایل را پاک نمی‌کنیم در shutdown
                // فقط لاگ می‌زنیم
                System.out.println("[ShutdownHook] Application shutting down - AutoSave preserved");
            }));

            // Window Listener برای خروج از دکمه X پنجره
            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    handleExitPreserve(gameController);
                }
            });

            frame.setVisible(true);
        });
    }
    private static void handleNormalExit(GameController gameController) {
        gameController.stopAutoSave(); // حذف فایل ذخیره
        System.exit(0);
    }
    private static void handleExitPreserve(GameController gameController) {
        // فقط توقف — فایل‌ها باقی بمانند
        gameController.stopAutoSave();
        System.exit(0);
    }
}

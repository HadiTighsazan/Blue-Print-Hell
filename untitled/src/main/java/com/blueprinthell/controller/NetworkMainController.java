package com.blueprinthell.controller;

import com.blueprinthell.client.network.ConnectionManager;
import com.blueprinthell.controller.packet.PacketProducerController;
import com.blueprinthell.controller.pvp.PvPClientController;
import com.blueprinthell.controller.simulation.SimulationController;
import com.blueprinthell.controller.ui.MenuController;
import com.blueprinthell.controller.ui.NetworkEnabledMenuController;
import com.blueprinthell.controller.ui.ScreenController;
import com.blueprinthell.controller.ui.UIController;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.view.HudView;

import javax.swing.*;
import java.awt.*;

/**
 * MainController با پشتیبانی از شبکه
 */
public class NetworkMainController {

    public static ConnectionManager connectionManager;
    public static PvPClientController pvpController;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // ایجاد Connection Manager
            connectionManager = new ConnectionManager(null); // userId خودکار

            // ایجاد پنجره اصلی
            JFrame frame = new JFrame("BlueprintHell - Network Edition");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);

            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            frame.setSize(screenSize.width, screenSize.height);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            frame.setUndecorated(true);

            // Screen Controller
            ScreenController screenController = new ScreenController(frame);

            // Game Controller
            GameController gameController = new GameController(frame);
            gameController.setScreenController(screenController);
            // ایجاد PvP Controller
            pvpController = new PvPClientController(
                    gameController,
                    screenController,
                    connectionManager
            );
            // تنظیمات اولیه
            SimulationController simController = gameController.getSimulation();
            PacketProducerController producerController = gameController.getProducerController();
            WireModel.setSimulationController(simController);
            simController.setPacketProducerController(producerController);

            screenController.registerGameScreen(gameController.getGameView());

            // Menu Controller با پشتیبانی شبکه
            NetworkEnabledMenuController menuController = new NetworkEnabledMenuController(
                    screenController,
                    gameController,
                    connectionManager
            );
            // UI Controller
            UIController ui = new UIController(
                    frame,
                    (HudView) gameController.getGameView().getHudView(),
                    simController,
                    gameController.getCoinModel(),
                    gameController.getCollisionController(),
                    gameController.getLossModel(),
                    gameController.getWires(),
                    gameController.getHudController(),
                    gameController.getGameView()
            );

            screenController.setAudioController(ui.getAudioController());
            ui.getAudioController().playBackgroundLoop();

            // نمایش منوی اصلی
            screenController.showScreen(ScreenController.MAIN_MENU);

            // Shutdown Hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down...");

                // قطع اتصال شبکه
                if (connectionManager != null) {
                    connectionManager.disconnect();
                }

                // ذخیره آفلاین queue (در ConnectionManager انجام می‌شود)
            }));

            // Window Listener
            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    handleExit(gameController);
                }
            });

            frame.setVisible(true);

            // تلاش برای اتصال خودکار به localhost (اختیاری)
            autoConnectToLocalServer();
        });
    }

    private static void handleExit(GameController gameController) {
        // توقف AutoSave
        gameController.stopAutoSave();

        // قطع اتصال شبکه
        if (connectionManager != null) {
            connectionManager.disconnect();
        }

        System.exit(0);
    }

    /**
     * تلاش برای اتصال خودکار به سرور محلی
     */
    private static void autoConnectToLocalServer() {
        SwingUtilities.invokeLater(() -> {
            connectionManager.connect("localhost", 7777).thenAccept(success -> {
                if (success) {
                    System.out.println("Auto-connected to local server");

                    // Sync offline queue if any
                    if (connectionManager.getOfflineQueueSize() > 0) {
                        connectionManager.syncOfflineQueue().thenAccept(count -> {
                            System.out.println("Auto-synced " + count + " offline results");
                        });
                    }
                } else {
                    System.out.println("Local server not available - running in offline mode");
                }
            });
        });
    }
}
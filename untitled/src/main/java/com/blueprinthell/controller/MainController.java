package com.blueprinthell.controller;

import javax.swing.*;
import com.blueprinthell.view.HudView;

import java.awt.*;
import java.util.List;
import com.blueprinthell.controller.SimulationController;
import com.blueprinthell.controller.PacketProducerController;
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
                    gameController.getHudController()
            );

            screenController.setAudioController(ui.getAudioController());
            ui.getAudioController().playBackgroundLoop();

            screenController.showScreen(ScreenController.MAIN_MENU);

            frame.setVisible(true);
        });
    }
}

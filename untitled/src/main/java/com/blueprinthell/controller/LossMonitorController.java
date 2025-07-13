package com.blueprinthell.controller;

import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.controller.PacketProducerController;
import com.blueprinthell.view.screens.GameOverView;

import javax.swing.*;

/**
 * Monitors PacketLoss each tick; when loss exceeds threshold on total planned packets,
 * stops simulation and shows Game‑Over screen.
 */
public class LossMonitorController implements Updatable {

    private final PacketLossModel lossModel;
    private final double plannedPackets;  // total packets planned for level
    private final double thresholdRatio;  // e.g. 0.5 for 50 %
    private final SimulationController simulation;
    private final ScreenController screenCtrl;
    private final Runnable resetLevel;

    private boolean triggered = false;

    public LossMonitorController(PacketLossModel lossModel,
                                 double plannedPackets,
                                 double thresholdRatio,
                                 SimulationController simulation,
                                 ScreenController screenCtrl,
                                 Runnable resetLevel) {
        this.lossModel = lossModel;
        this.plannedPackets = plannedPackets;
        this.thresholdRatio = thresholdRatio;
        this.simulation = simulation;
        this.screenCtrl = screenCtrl;
        this.resetLevel = resetLevel;
    }

    @Override
    public void update(double dt) {
        if (triggered) return;
        if (plannedPackets <= 0) return;
        double ratio = lossModel.getLostCount() / plannedPackets;
        if (ratio >= thresholdRatio) {
            triggered = true;
            simulation.stop();
            SwingUtilities.invokeLater(() -> {
                GameOverView gov = screenCtrl.getGameOverView();
                gov.packetLossLabel.setText("Loss: " + lossModel.getLostCount());
                gov.retryButton.addActionListener(e -> resetLevel.run());
                screenCtrl.showScreen(ScreenController.GAME_OVER);
            });
        }
    }
}

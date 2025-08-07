package com.blueprinthell.controller;

import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.view.screens.GameOverView;
import com.blueprinthell.controller.SimulationController;
import com.blueprinthell.controller.ScreenController;

import javax.swing.*;


public class LossMonitorController implements Updatable {

    private final PacketLossModel lossModel;
    private final double plannedPackets;
    private final double thresholdRatio;
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
        if (triggered) {
            return;
        }
        if (plannedPackets <= 0) {
            return;
        }
        if (screenCtrl == null) {
            return;
        }

         PacketProducerController pc = simulation.getPacketProducerController();
                 if (pc == null || pc.getProducedUnits() == 0) {
                 return;
             }

         double ratio = lossModel.getLostCount() / pc.getProducedUnits();
        if (ratio >= thresholdRatio) {
            triggered = true;
            simulation.stop();

            SwingUtilities.invokeLater(() -> {
                GameOverView gov = screenCtrl.getGameOverView();
                gov.getPacketLossLabel().setText("Loss: " + lossModel.getLostCount());

                javax.swing.AbstractButton retryBtn = gov.getRetryButton();
                if (!java.lang.Boolean.TRUE.equals(retryBtn.getClientProperty("retryBound"))) {
                    retryBtn.addActionListener(e -> resetLevel.run());
                    retryBtn.putClientProperty("retryBound", java.lang.Boolean.TRUE);
                }


                screenCtrl.showScreen(ScreenController.GAME_OVER);
            });


        }
    }
}

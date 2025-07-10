package com.blueprinthell.controller;

import com.blueprinthell.model.ScoreModel;
import com.blueprinthell.model.WireUsageModel;
import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.view.HudView;
import com.blueprinthell.model.Updatable;

/**
 * Controller to update HUD based on game metrics: score, wire usage, and packet loss.
 */
public class HudController implements Updatable {
    private final ScoreModel scoreModel;
    private final WireUsageModel usageModel;
    private final PacketLossModel lossModel;
    private final HudView hudView;

    /**
     * @param scoreModel model tracking player score
     * @param usageModel model tracking wire usage
     * @param lossModel model tracking packet loss
     * @param hudView view to display the metrics
     */
    public HudController(ScoreModel scoreModel,
                         WireUsageModel usageModel,
                         PacketLossModel lossModel,
                         HudView hudView) {
        this.scoreModel = scoreModel;
        this.usageModel = usageModel;
        this.lossModel = lossModel;
        this.hudView = hudView;
    }

    @Override
    public void update(double dt) {
        int score = scoreModel.getScore();
        double remainingWire = usageModel.getRemainingWireLength();
        int packetLoss = lossModel.getLostCount();

        hudView.setScore(score);
        hudView.setWireLength(remainingWire);
        hudView.setPacketLoss(packetLoss);
        // TODO: setTime when time model available
    }
}

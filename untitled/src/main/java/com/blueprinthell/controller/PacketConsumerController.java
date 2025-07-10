package com.blueprinthell.controller;

import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.ScoreModel;
import com.blueprinthell.model.Updatable;

/**
 * Controller that consumes packets from a system box buffer and awards points.
 */
public class PacketConsumerController implements Updatable {
    private final SystemBoxModel box;
    private final ScoreModel scoreModel;

    /**
     * @param box the system box whose buffer will be polled for arrived packets
     * @param scoreModel model to update with earned points
     */
    public PacketConsumerController(SystemBoxModel box, ScoreModel scoreModel) {
        this.box = box;
        this.scoreModel = scoreModel;
    }

    @Override
    public void update(double dt) {
        PacketModel packet;
        // Consume all arrived packets in this tick
        while ((packet = box.pollPacket()) != null) {
            // Reset noise on successful consumption
            packet.resetNoise();
            // Award points based on packet type
            scoreModel.addPoints(packet.getType().coins);
        }
    }
}

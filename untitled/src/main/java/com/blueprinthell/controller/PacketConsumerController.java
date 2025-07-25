package com.blueprinthell.controller;

import com.blueprinthell.model.*;

/**
 * Consumes packets that arrive in a sink SystemBox, awards score **و** سکه.
 */
public class PacketConsumerController implements Updatable {

    private final SystemBoxModel box;
    private final ScoreModel scoreModel;
    private final CoinModel  coinModel;

    public PacketConsumerController(SystemBoxModel box,
                                    ScoreModel scoreModel,
                                    CoinModel coinModel) {
        this.box        = box;
        this.scoreModel = scoreModel;
        this.coinModel  = coinModel;
    }

    @Override
    public void update(double dt) {
        PacketModel packet;
        while ((packet = box.pollPacket()) != null) {
            packet.resetNoise();
            int value = packet.getType().coins;
            scoreModel.addPoints(value);
            coinModel.add(value);
        }
    }
}

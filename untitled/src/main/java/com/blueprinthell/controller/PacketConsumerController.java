package com.blueprinthell.controller;

import com.blueprinthell.model.*;
import com.blueprinthell.model.large.BitPacket;


public class PacketConsumerController implements Updatable {

    private final SystemBoxModel box;
    private final ScoreModel scoreModel;
    private final CoinModel  coinModel;

    private PacketLossModel lossModel;

    public PacketConsumerController(SystemBoxModel box,
                                    ScoreModel scoreModel,
                                    CoinModel coinModel) {
        this.box        = box;
        this.scoreModel = scoreModel;
        this.coinModel  = coinModel;
    }

    public PacketConsumerController(SystemBoxModel box,
                                    ScoreModel scoreModel,
                                    CoinModel coinModel,
                                    PacketLossModel lossModel) {
        this(box, scoreModel, coinModel);
        this.lossModel = lossModel;
    }

    public void setLossModel(PacketLossModel lossModel) {
        this.lossModel = lossModel;
    }


    public static void applyConsumeLogic(PacketModel packet,
                                         ScoreModel scoreModel,
                                         CoinModel coinModel,
                                         PacketLossModel lossModel) {
        if (packet == null) return;

        if (packet instanceof BitPacket) {
            if (lossModel != null) {
                lossModel.increment();
            }
            return;
        }

        int coins = PacketOps.coinValueOnConsume(packet);
        if (coins > 0) {
            if (coinModel != null) coinModel.add(coins);
        }
    }

    @Override
    public void update(double dt) {
        PacketModel packet;
        while ((packet = box.pollPacket()) != null) {
            packet.resetNoise();
            applyConsumeLogic(packet, scoreModel, coinModel, lossModel);

            if (false) {
                packet.resetNoise();
                int value = packet.getType().coins;
                scoreModel.addPoints(value);
                coinModel.add(value);
            }
        }
    }
}

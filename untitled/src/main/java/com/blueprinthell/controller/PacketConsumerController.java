// فایل: untitled/src/main/java/com/blueprinthell/controller/PacketConsumerController.java

package com.blueprinthell.controller;

import com.blueprinthell.model.*;
import com.blueprinthell.model.large.BitPacket;

public class PacketConsumerController implements Updatable {

    private final SystemBoxModel box;
    private final ScoreModel scoreModel;
    private final CoinModel coinModel;
    private PacketLossModel lossModel;
    private SimulationController simulation; // اضافه شد

    // سازنده جدید
    public PacketConsumerController(SystemBoxModel box,
                                    ScoreModel scoreModel,
                                    CoinModel coinModel,
                                    PacketLossModel lossModel,
                                    SimulationController simulation) {
        this.box = box;
        this.scoreModel = scoreModel;
        this.coinModel = coinModel;
        this.lossModel = lossModel;
        this.simulation = simulation;
    }

    // سازنده‌های قبلی را نگه می‌داریم برای سازگاری
    public PacketConsumerController(SystemBoxModel box,
                                    ScoreModel scoreModel,
                                    CoinModel coinModel) {
        this(box, scoreModel, coinModel, null, null);
    }

    public PacketConsumerController(SystemBoxModel box,
                                    ScoreModel scoreModel,
                                    CoinModel coinModel,
                                    PacketLossModel lossModel) {
        this(box, scoreModel, coinModel, lossModel, null);
    }

    public void setLossModel(PacketLossModel lossModel) {
        this.lossModel = lossModel;
    }

    public void setSimulation(SimulationController simulation) {
        this.simulation = simulation;
    }

    @Override
    public void update(double dt) {
        PacketModel packet;
        while ((packet = box.pollPacket()) != null) {
            packet.resetNoise();
            applyConsumeLogic(packet, scoreModel, coinModel, lossModel);

            // اطلاع به producer که پکت مصرف شد - استفاده از static method
            SimulationController sim = WireModel.getSimulationController();
            if (sim != null && sim.getPacketProducerController() != null) {
                sim.getPacketProducerController().onPacketConsumed();
            }
        }
    }

    public static void applyConsumeLogic(PacketModel packet,
                                         ScoreModel scoreModel,
                                         CoinModel coinModel,
                                         PacketLossModel lossModel) {
        if (packet == null) return;

        if (packet instanceof BitPacket) {
            if (lossModel != null) lossModel.increment();
            return;
        }

        // برای پکت‌های حجیم و سایر پکت‌ها
        int coins = PacketOps.coinValueOnConsume(packet);
        if (coins > 0) {
            if (coinModel != null) coinModel.add(coins);
        }
    }
}
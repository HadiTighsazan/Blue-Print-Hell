
package com.blueprinthell.controller;

import com.blueprinthell.model.*;
import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.model.large.MergedPacket;

public class PacketConsumerController implements Updatable {

    private final SystemBoxModel box;
    private final ScoreModel scoreModel;
    private final CoinModel coinModel;
    private PacketLossModel lossModel;
    private SimulationController simulation;

    private LargeGroupRegistry largeGroupRegistry;
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

        // بیت پکت: Loss آنی ندارد
        if (packet instanceof com.blueprinthell.model.large.BitPacket) {
            return;
        }

        if (packet instanceof LargePacket lp) {
            if (lp.isRebuiltFromBits() && lp.getGroupId() >= 0) {

                SimulationController sim = WireModel.getSimulationController();
                if (sim != null) {
                    // رجیستری را از PacketLossModel برداریم تا وابستگی کم شود
                    LargeGroupRegistry reg = null;
                    if (lossModel instanceof com.blueprinthell.model.PacketLossModel plm) {
                        try {
                            java.lang.reflect.Method m = plm.getClass().getMethod("getRegistryForInternalUse");
                        } catch (NoSuchMethodException ex) {
                        }
                    }
                }
            }

            return;
        }

        // سایر پکت‌ها: اقتصاد سکه + ممکن است خارج از این متد Loss آنی ثبت شود
        int coins = PacketOps.coinValueOnConsume(packet);
        if (coins > 0 && coinModel != null) coinModel.add(coins);
    }

    public void setLargeGroupRegistry(LargeGroupRegistry reg) {
        this.largeGroupRegistry = reg;
    }

}
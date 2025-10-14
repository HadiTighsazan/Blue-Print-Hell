package com.blueprinthell.controller.packet;

import com.blueprinthell.controller.simulation.SimulationController;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.model.large.LargePacket;

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


        PacketModel originalPacket = PacketOps.unwrapTrojan(packet);

        if (originalPacket instanceof com.blueprinthell.model.large.BitPacket) {
            if (lossModel != null) {
                lossModel.incrementPacket(originalPacket);
            }
            return;
        }

        if (originalPacket instanceof LargePacket lp) {
            if (lp.isRebuiltFromBits() && lp.getGroupId() >= 0) {

                SimulationController sim = WireModel.getSimulationController();
                if (sim != null) {
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

        int coins = PacketOps.coinValueOnConsume(originalPacket);
        if (coins > 0 && coinModel != null) coinModel.add(coins);
    }

    public void setLargeGroupRegistry(LargeGroupRegistry reg) {
        this.largeGroupRegistry = reg;
    }

}
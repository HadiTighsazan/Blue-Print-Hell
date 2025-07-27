package com.blueprinthell.controller;

import com.blueprinthell.model.*;

import java.util.ArrayList;
import java.util.List;


public class SnapshotController implements Updatable {
    private final List<SystemBoxModel> boxes;
    private final List<WireModel> wires;
    private final ScoreModel scoreModel;
    private final CoinModel coinModel;
    private final WireUsageModel usageModel;
    private final PacketLossModel lossModel;
    private final SnapshotManager snapshotManager;
    private double elapsedTime = 0.0;

    public SnapshotController(List<SystemBoxModel> boxes,
                              List<WireModel> wires,
                              ScoreModel scoreModel,
                              CoinModel coinModel,
                              WireUsageModel usageModel,
                              PacketLossModel lossModel,
                              SnapshotManager snapshotManager) {
        this.boxes = boxes;
        this.wires = wires;
        this.scoreModel = scoreModel;
        this.coinModel = coinModel;
        this.usageModel = usageModel;
        this.lossModel = lossModel;
        this.snapshotManager = snapshotManager;
    }

    @Override
    public void update(double dt) {
        elapsedTime += dt;
        List<NetworkSnapshot.SystemBoxState> boxStates = new ArrayList<>();
        for (SystemBoxModel box : boxes) {
            List<NetworkSnapshot.PacketState> bufferStates = new ArrayList<>();
            for (PacketModel p : box.getBuffer()) {
                bufferStates.add(new NetworkSnapshot.PacketState(0.0, p.getNoise(), p.getType()));
            }
            boxStates.add(new NetworkSnapshot.SystemBoxState(
                    box.getX(), box.getY(), box.getWidth(), box.getHeight(),
                    box.getInShapes(), box.getOutShapes(), bufferStates));
        }
        List<NetworkSnapshot.WireState> wireStates = new ArrayList<>();
        for (WireModel wire : wires) {
            PortModel src = wire.getSrcPort();
            PortModel dst = wire.getDstPort();
            List<NetworkSnapshot.PacketState> packetStates = new ArrayList<>();
            for (PacketModel p : wire.getPackets()) {
                packetStates.add(new NetworkSnapshot.PacketState(
                        p.getProgress(), p.getNoise(), p.getType()));
            }
            wireStates.add(new NetworkSnapshot.WireState(
                    src.getCenterX(), src.getCenterY(),
                    dst.getCenterX(), dst.getCenterY(),
                    packetStates));
        }
        NetworkSnapshot snapshot = new NetworkSnapshot(
                scoreModel.getScore(),
                coinModel.getCoins(),
                lossModel.getLostCount(),
                boxStates,
                wireStates);
        snapshotManager.recordSnapshot(snapshot);
    }
}

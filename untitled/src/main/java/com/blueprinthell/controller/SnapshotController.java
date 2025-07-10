package com.blueprinthell.controller;

import com.blueprinthell.model.*;
import com.blueprinthell.controller.NetworkSnapshot;
import com.blueprinthell.controller.SnapshotManager;
import com.blueprinthell.model.GameObjectModel;
import com.blueprinthell.model.GameObjectModel;
import com.blueprinthell.model.GameObjectModel;
import com.blueprinthell.model.GameObjectModel;
import com.blueprinthell.model.GameObjectModel;

import java.util.ArrayList;
import java.util.List;
import com.blueprinthell.model.PortShape;
import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.GameObjectModel;
import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.WireModel;
import java.awt.Point;
import com.blueprinthell.model.PortShape;
import com.blueprinthell.model.PacketType;

/**
 * Updatable controller that records periodic snapshots of the network state
 * into a SnapshotManager, enabling temporal navigation.
 */
public class SnapshotController implements Updatable {
    private final List<SystemBoxModel> boxes;
    private final List<WireModel> wires;
    private final ScoreModel scoreModel;
    private final WireUsageModel usageModel;
    private final PacketLossModel lossModel;
    private final SnapshotManager snapshotManager;
    private double elapsedTime = 0.0;

    public SnapshotController(List<SystemBoxModel> boxes,
                              List<WireModel> wires,
                              ScoreModel scoreModel,
                              WireUsageModel usageModel,
                              PacketLossModel lossModel,
                              SnapshotManager snapshotManager) {
        this.boxes = boxes;
        this.wires = wires;
        this.scoreModel = scoreModel;
        this.usageModel = usageModel;
        this.lossModel = lossModel;
        this.snapshotManager = snapshotManager;
    }

    @Override
    public void update(double dt) {
        elapsedTime += dt;
        // Capture box states
        List<NetworkSnapshot.SystemBoxState> boxStates = new ArrayList<>();
        for (SystemBoxModel box : boxes) {
            boxStates.add(new NetworkSnapshot.SystemBoxState(
                    box.getX(), box.getY(), box.getWidth(), box.getHeight(),
                    box.getInShapes(), box.getOutShapes()));
        }
        // Capture wire states
        List<NetworkSnapshot.WireState> wireStates = new ArrayList<>();
        for (WireModel wire : wires) {
            PortModel src = wire.getSrcPort();
            PortModel dst = wire.getDstPort();
            // Packet states
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
        // Build snapshot
        // Build snapshot
        NetworkSnapshot snapshot = new NetworkSnapshot(
                scoreModel.getScore(),
                lossModel.getLostCount(),
                boxStates,
                wireStates
        );
        // Record it
        snapshotManager.recordSnapshot(snapshot);
    }
}

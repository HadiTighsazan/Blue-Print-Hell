package com.blueprinthell.level;

import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.controller.PacketProducerController;
import com.blueprinthell.level.LevelManager;
import javax.swing.SwingUtilities;
import java.util.List;

/**
 * Monitors packet flow to determine when a level is successfully completed.
 * Criteria:
 *  1. Packet production has finished
 *  2. No packets remain on any wire
 *  3. Packet loss ratio below threshold based on planned total packets
 * Then notifies LevelManager of success.
 */
public class LevelCompletionDetector implements Updatable {
    private final List<WireModel> wires;
    private final PacketLossModel lossModel;
    private final PacketProducerController producer;
    private final LevelManager levelManager;
    private final double lossThreshold;
    private final double plannedPackets;

    private boolean reported = false;

    public LevelCompletionDetector(List<WireModel> wires,
                                   PacketLossModel lossModel,
                                   PacketProducerController producer,
                                   LevelManager levelManager,
                                   double lossThreshold,
                                   double plannedPackets) {
        this.wires = wires;
        this.lossModel = lossModel;
        this.producer = producer;
        this.levelManager = levelManager;
        this.lossThreshold = lossThreshold;
        this.plannedPackets = plannedPackets;
    }

    @Override
    public void update(double dt) {
        if (!producer.isFinished() || reported) {
            return;
        }
        // Condition 1: no packets on any wire
        boolean empty = wires.stream().allMatch(w -> w.getPackets().isEmpty());
        // Condition 2: loss ratio based on total planned packets
        double ratio = plannedPackets > 0 ? lossModel.getLostCount() / plannedPackets : 0.0;
        boolean okLoss = ratio < lossThreshold;
        if (empty && okLoss) {
            reported = true;
            SwingUtilities.invokeLater(levelManager::reportLevelCompleted);
        }
    }
}

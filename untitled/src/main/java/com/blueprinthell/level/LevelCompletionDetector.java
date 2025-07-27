package com.blueprinthell.level;

import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.controller.PacketProducerController;
import javax.swing.SwingUtilities;
import java.util.List;


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
        boolean empty = wires.stream().allMatch(w -> w.getPackets().isEmpty());
         double ratio = plannedPackets > 0
                     ? (double) lossModel.getLostCount() / plannedPackets
                     : 0.0;        boolean okLoss = ratio < lossThreshold;
        if (empty && okLoss) {
            reported = true;
            SwingUtilities.invokeLater(levelManager::reportLevelCompleted);
        }
    }
}

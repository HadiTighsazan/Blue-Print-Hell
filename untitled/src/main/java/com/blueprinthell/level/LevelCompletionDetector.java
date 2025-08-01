package com.blueprinthell.level;
import com.blueprinthell.controller.PacketProducerController;
import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;
import javax.swing.SwingUtilities;
import java.util.List;

    public class LevelCompletionDetector implements Updatable {
    private final List<WireModel> wires;
    private final List<SystemBoxModel> boxes;
    private final PacketLossModel lossModel;
    private final PacketProducerController producer;
    private final LevelManager levelManager;
    private final double lossThreshold;
    private final double plannedPackets;

            private boolean reported = false;
        private double stableAcc = 0.0;
        private static final double STABLE_WINDOW_S = 0.30;

            public LevelCompletionDetector(List<WireModel> wires,
                                   List<SystemBoxModel> boxes,
                                   PacketLossModel lossModel,
                                   PacketProducerController producer,
                                   LevelManager levelManager,
                                   double lossThreshold,
                                   double plannedPackets) {
                this.wires = wires;
                this.boxes = boxes;
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

                                boolean wiresEmpty = wires.stream()
                                .allMatch(w -> w.getPackets().isEmpty());

                                boolean boxesEmpty = boxes.stream()
                               .allMatch(b -> b.getBuffer().isEmpty());

                                boolean noReturning = wires.stream()
                                .flatMap(w -> w.getPackets().stream())
                                .noneMatch(PacketModel::isReturning);

                                double ratio = plannedPackets > 0
                                ? (double) lossModel.getLostCount() / plannedPackets
                               : 0.0;
                boolean okLoss = ratio < lossThreshold;

                if (wiresEmpty && boxesEmpty && noReturning && okLoss) {
                    stableAcc += dt;
                    if (stableAcc >= STABLE_WINDOW_S) {
                        reported = true;
                        javax.swing.SwingUtilities.invokeLater(levelManager::reportLevelCompleted);
                    }
                } else {
                    stableAcc = 0.0;
                }
            }
}
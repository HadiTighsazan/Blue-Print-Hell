package com.blueprinthell.level;

import com.blueprinthell.controller.packet.PacketProducerController;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargeGroupRegistry;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.stream.Collectors;

public class LevelCompletionDetector implements Updatable {
    private final List<WireModel> wires;
    private final List<SystemBoxModel> boxes;
    private final PacketLossModel lossModel;
    private final PacketProducerController producer;
    private final LevelManager levelManager;
    private final double lossThreshold;

    private boolean reported = false;
    private double stableAcc = 0.0;
    private static final double STABLE_WINDOW_S = 1.0;

    public LevelCompletionDetector(List<WireModel> wires,
                                   List<SystemBoxModel> boxes,
                                   PacketLossModel lossModel,
                                   PacketProducerController producer,
                                   LevelManager levelManager,
                                   double lossThreshold,
                                   int plannedPackets) {
        this.wires = wires;
        this.boxes = boxes;
        this.lossModel = lossModel;
        this.producer = producer;
        this.levelManager = levelManager;
        this.lossThreshold = lossThreshold;
    }

    @Override
    public void update(double dt) {
        if (reported) {
            return;
        }

        if (!producer.isFinished()) {
            stableAcc = 0.0;
            return;
        }

        boolean wiresEmpty = wires.stream().allMatch(w -> w.getPackets().isEmpty());

        boolean noReturning = wires.stream()
                .flatMap(w -> w.getPackets().stream())
                .noneMatch(PacketModel::isReturning);

        boolean allBitsAccountedFor = checkAllBitsAccountedFor();

        boolean buffersAreClear = checkBuffersAreClear();

        if (wiresEmpty && noReturning && allBitsAccountedFor && buffersAreClear) {
            stableAcc += dt;
            if (stableAcc >= STABLE_WINDOW_S) {

                lossModel.finalizeDeferredLossNow();

                int producedUnits = producer.getProducedUnits();
                double lossRatio = producedUnits > 0
                        ? (double) lossModel.getLostCount() / producedUnits
                        : 0.0;

                boolean acceptableLoss = lossRatio <= lossThreshold;

                if (acceptableLoss) {
                    reported = true;
                    SwingUtilities.invokeLater(levelManager::reportLevelCompleted);
                } else {

                    stableAcc = 0.0;
                }
            }
        } else {
            stableAcc = 0.0;
        }
    }

    private boolean checkAllBitsAccountedFor() {
        LargeGroupRegistry registry = lossModel.getRegistryForInternalUse();
        if (registry == null) {
            return true;
        }

        return registry.view().values().stream()
                .filter(group -> !group.isClosed())
                .allMatch(LargeGroupRegistry.GroupState::allBitsAccountedFor);
    }

    private boolean checkBuffersAreClear() {
        return boxes.stream().allMatch(b -> {
            boolean noBacklog = !b.hasUnprocessedEntries();

            if (b.getPrimaryKind() == SystemKind.MERGER) {
                boolean nonBitsEmpty = b.getBitBuffer().stream().noneMatch(p -> !(p instanceof BitPacket));
                return noBacklog && b.getLargeBuffer().isEmpty() && nonBitsEmpty;
            }

            return noBacklog && b.getBitBuffer().isEmpty() && b.getLargeBuffer().isEmpty();
        });
    }
}
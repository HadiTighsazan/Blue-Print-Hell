// فایل: untitled/src/main/java/com/blueprinthell/level/LevelCompletionDetector.java

package com.blueprinthell.level;

import com.blueprinthell.controller.PacketProducerController;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.large.BitPacket;
import javax.swing.SwingUtilities;
import java.util.List;

public class LevelCompletionDetector implements Updatable {
    private final List<WireModel> wires;
    private final List<SystemBoxModel> boxes;
    private final PacketLossModel lossModel;
    private final PacketProducerController producer;
    private final LevelManager levelManager;
    private final double lossThreshold;
    private final int plannedPackets;

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
        this.plannedPackets = plannedPackets;
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

        boolean wiresEmpty = wires.stream()
                .allMatch(w -> w.getPackets().isEmpty());

        // بررسی خاص برای Merger ها و سایر سیستم‌ها
        boolean boxesReady = boxes.stream()
                .allMatch(b -> {
                    boolean noBacklog = !b.hasUnprocessedEntries();
                    boolean isSink = b.getOutPorts().isEmpty();

                    // برای Merger ها: باید کمتر از 4 بیت داشته باشند
                    if (b.getPrimaryKind() == SystemKind.MERGER) {
                        // شمارش BitPacket ها در بافر
                        long bitCount = b.getBitBuffer().stream()
                                .filter(p -> p instanceof BitPacket)
                                .count();
                        return bitCount < 4 && noBacklog;
                    }

                    // برای Sink ها
                    if (isSink) {
                        return noBacklog;
                    }

                    // برای سایر سیستم‌ها
                    boolean bufEmpty = b.getBitBuffer().isEmpty() && b.getLargeBuffer().isEmpty();
                    return noBacklog && bufEmpty;
                });

        boolean noReturning = wires.stream()
                .flatMap(w -> w.getPackets().stream())
                .noneMatch(PacketModel::isReturning);

        // محاسبه نسبت loss بر اساس واحدهای تولیدی
        int producedUnits = producer.getProducedUnits();
        double lossRatio = producedUnits > 0
                ? (double) lossModel.getLostCount() / producedUnits
                : 0.0;

        boolean acceptableLoss = lossRatio < lossThreshold; // کمتر از 50%

        if (wiresEmpty && boxesReady && noReturning) {
            if (acceptableLoss) {
                stableAcc += dt;
                if (stableAcc >= STABLE_WINDOW_S) {
                    reported = true;
                    SwingUtilities.invokeLater(levelManager::reportLevelCompleted);
                }
            } else {
                // اگر loss بیش از حد است، بازی تمام نمی‌شود
                stableAcc = 0.0;
            }
        } else {
            stableAcc = 0.0;
        }
    }
}
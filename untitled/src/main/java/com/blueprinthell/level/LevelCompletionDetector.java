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

        // تا وقتی تولید تمام نشده، امکان سکون واقعی نیست
        if (!producer.isFinished()) {
            stableAcc = 0.0;
            return;
        }

        // 1) هیچ پکتی روی هیچ واییری نباشد
        boolean wiresEmpty = wires.stream()
                .allMatch(w -> w.getPackets().isEmpty());

        // 2) آماده‌بودن تمام باکس‌ها
        //    - مرجر: largeBuffer خالی و تعداد بیت‌ها < 4 و backlog نداشته باشد
        //    - سینک: فقط backlog نداشته باشد
        //    - سایر سیستم‌ها: هر دو بافر خالی و backlog نداشته باشد
        boolean boxesReady = boxes.stream()
                .allMatch(b -> {
                    boolean noBacklog = !b.hasUnprocessedEntries();
                    boolean isSink = b.getOutPorts().isEmpty();

                    if (b.getPrimaryKind() == SystemKind.MERGER) {
                        long bitCount = b.getBitBuffer().stream()
                                .filter(p -> p instanceof BitPacket)
                                .count();
                        boolean largeEmpty = b.getLargeBuffer().isEmpty();
                        return (bitCount < 4) && largeEmpty && noBacklog;
                    }

                    if (isSink) {
                        return noBacklog;
                    }

                    boolean bufEmpty = b.getBitBuffer().isEmpty() && b.getLargeBuffer().isEmpty();
                    return noBacklog && bufEmpty;
                });

        // 3) هیچ پکت برگشتی در راه نباشد
        boolean noReturning = wires.stream()
                .flatMap(w -> w.getPackets().stream())
                .noneMatch(PacketModel::isReturning);

        if (wiresEmpty && boxesReady && noReturning) {
            // --- تسویه‌ی خسارت مؤخره در نقطه‌ی سکون ---
            // این متد (پچ 3) همه‌ی گروه‌های باز رجیستری را می‌بندد (idempotent)
            lossModel.finalizeDeferredLossNow();
                        if (lossModel != null) {
                               var reg = lossModel.getRegistryView(); // اگر ندارید: متدی اضافه کنید که LargeGroupRegistry را برگرداند/یا view() را proxied برگرداند
                                if (reg != null) reg.debugDumpClosed("BEFORE_REPORT");
                            }
            // حالا نسبت Loss با احتساب مؤخره
            int producedUnits = producer.getProducedUnits();
            double lossRatio = producedUnits > 0
                    ? (double) lossModel.getLostCount() / producedUnits
                    : 0.0;

            // کمتر از آستانه = قابل قبول (طبق کامنت موجود)
            boolean acceptableLoss = lossRatio < lossThreshold;

            if (acceptableLoss) {
                stableAcc += dt;
                if (stableAcc >= STABLE_WINDOW_S) {
                    reported = true;
                    SwingUtilities.invokeLater(levelManager::reportLevelCompleted);
                }
            } else {
                // اگر loss بیش از حد است، اینجا گزارش مرحله را نمی‌دهیم
                // (در صورت نیاز، می‌توانی در همین‌جا GameOver را هم تریگر کنی)
                stableAcc = 0.0;
            }
        } else {
            stableAcc = 0.0;
        }
    }

}
package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.WireUsageModel;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.view.SystemBoxView;
import com.blueprinthell.view.screens.GameScreenView;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class LevelBuilder {

    private final GameScreenView gameView;
    private final List<WireModel> wires;
    private final WireUsageModel usageModel;

    public LevelBuilder(GameScreenView gameView,
                        List<WireModel> wires,
                        WireUsageModel usageModel) {
        this.gameView   = gameView;
        this.wires      = wires;
        this.usageModel = usageModel;
    }

    public List<SystemBoxModel> build(LevelDefinition def,
                                      List<SystemBoxModel> existingBoxes) {
        List<LevelDefinition.BoxSpec> allSpecs = def.boxes();

        // 1) فقط سینک جعبه‌های مراحل قبل با Spec همین مرحله
        syncExistingBoxes(existingBoxes, allSpecs);

        // 2) ساخت جعبه‌های جدید (آن‌هایی که بعدِ existingBoxes می‌آیند)
        int existingCount = existingBoxes.size();
        List<LevelDefinition.BoxSpec> newSpecs = existingCount < allSpecs.size()
                ? allSpecs.subList(existingCount, allSpecs.size())
                : List.of();

        List<SystemBoxModel> newBoxes = new ArrayList<>();
        for (LevelDefinition.BoxSpec spec : newSpecs) {
            SystemBoxModel box = new SystemBoxModel(
                    spec.x(), spec.y(), spec.width(), spec.height(),
                    spec.inShapes(), spec.outShapes());
            box.setPrimaryKind(spec.kind());
            newBoxes.add(box);
        }

        // 3) نهایی‌سازی و ریست View
        List<SystemBoxModel> all = new ArrayList<>(existingBoxes);
        all.addAll(newBoxes);

        gameView.reset(all, wires);
        for (Component c : gameView.getGameArea().getComponents()) {
            if (c instanceof SystemBoxView sbv) {
                new SystemBoxDragController(sbv.getModel(), sbv, wires, usageModel);
            }
        }
        return all;
    }

    /** سینک: نوع سیستم + آپدیت شکل پورت‌های موجود + افزودن ورودی/خروجی تا اندازهٔ Spec (بدون حذف) */
    private void syncExistingBoxes(List<SystemBoxModel> existing,
                                   List<LevelDefinition.BoxSpec> specs) {
        int n = Math.min(existing.size(), specs.size());
        for (int i = 0; i < n; i++) {
            SystemBoxModel box = existing.get(i);
            LevelDefinition.BoxSpec spec = specs.get(i);

            // نوع سیستم
            box.setPrimaryKind(spec.kind());

            // ورودی‌ها: به‌روزرسانی شکل‌های موجود + افزودن ورودی‌های کم
            var inPorts  = box.getInPorts();
            var inShapes = spec.inShapes();
            for (int j = 0; j < Math.min(inPorts.size(), inShapes.size()); j++) {
                inPorts.get(j).setShape(inShapes.get(j));
            }
            for (int j = inPorts.size(); j < inShapes.size(); j++) {
                box.addInputPort(inShapes.get(j));
            }

            // خروجی‌ها: به‌روزرسانی شکل‌های موجود + افزودن خروجی‌های کم (تا سقف MAX_OUTPUT_PORTS)
            var outPorts  = box.getOutPorts();
            var outShapes = spec.outShapes();
            for (int j = 0; j < Math.min(outPorts.size(), outShapes.size()); j++) {
                outPorts.get(j).setShape(outShapes.get(j));
            }
            for (int j = outPorts.size();
                 j < outShapes.size() && j < Config.MAX_OUTPUT_PORTS;
                 j++) {
                box.addOutputPort(outShapes.get(j));
            }
            // حذف پورت انجام نمی‌دهیم.
        }
    }
}

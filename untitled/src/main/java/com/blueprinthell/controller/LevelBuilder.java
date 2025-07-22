package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.systems.BehaviorRegistry;
import com.blueprinthell.controller.systems.NormalBehavior;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.WireUsageModel;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.view.SystemBoxView;
import com.blueprinthell.view.screens.GameScreenView;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * G0 – Infrastructure update:
 * <ul>
 *   <li>Still responsible فقط برای ساخت اجزای استاتیک مرحله (Box ها) و وصل کردن Drag.</li>
 *   <li>اکنون اگر {@link BehaviorRegistry} داده شود، برای هر Box یک {@link NormalBehavior} ثبت می‌شود
 *       تا در گام‌های بعدی بتوانیم رفتارهای خاص (Spy/Malicious/...) را تزریق کنیم.</li>
 * </ul>
 */
public final class LevelBuilder {

    private final GameScreenView gameView;
    private final List<WireModel> wires;         // shared list, mutated by drag/wire controllers
    private final WireUsageModel usageModel;
    private final BehaviorRegistry behaviorRegistry; // nullable در گام صفر

    /**
     * قدیمی – بدون رجیستری رفتار (برای سازگاری موقت). پیشنهاد می‌شود از سازندهٔ کامل استفاده شود.
     */
    public LevelBuilder(GameScreenView gameView,
                        List<WireModel> wires,
                        WireUsageModel usageModel) {
        this(gameView, wires, usageModel, null);
    }

    /**
     * سازندهٔ جدید با BehaviorRegistry.
     */
    public LevelBuilder(GameScreenView gameView,
                        List<WireModel> wires,
                        WireUsageModel usageModel,
                        BehaviorRegistry behaviorRegistry) {
        this.gameView = gameView;
        this.wires = wires;
        this.usageModel = usageModel;
        this.behaviorRegistry = behaviorRegistry;
    }

    /**
     * Builds the given level definition (full stage) and returns the new box list.
     * Any previous boxes are discarded by caller responsibility.
     */
    public List<SystemBoxModel> build(LevelDefinition def) {
        // 1) build boxes
        List<SystemBoxModel> boxes = new ArrayList<>();
        for (LevelDefinition.BoxSpec spec : def.boxes()) {
            SystemBoxModel box = new SystemBoxModel(
                    spec.x(), spec.y(), spec.width(), spec.height(),
                    spec.inShapes(), spec.outShapes());
            boxes.add(box);

            // Register default behaviour
            if (behaviorRegistry != null) {
                behaviorRegistry.register(box, new NormalBehavior(box));
            }
        }

        // 2) paint them
        gameView.reset(boxes, wires);

        // 3) attach drag behaviour
        attachDragControllers();
        return boxes;
    }

    private void attachDragControllers() {
        for (Component c : gameView.getGameArea().getComponents()) {
            if (c instanceof SystemBoxView sbv) {
                new SystemBoxDragController(sbv.getModel(), sbv, wires, usageModel);
            }
        }
    }

    /**
     * Progressive levels: add a new stage on top of existing boxes.
     * Re-uses free output ports from previous boxes to satisfy input ports of new systems.
     * Throws if capacity is insufficient.
     */
    public List<SystemBoxModel> extend(List<SystemBoxModel> existingBoxes,
                                       List<LevelDefinition.BoxSpec> newSpecs) {
        // 1) calculate how many new input ports we need to feed
        int neededInputs = newSpecs.stream()
                .mapToInt(spec -> spec.inShapes().size())
                .sum();

        // 2) count available free outputs in existing boxes
        int freeOutputs = 0;
        for (SystemBoxModel box : existingBoxes) {
            freeOutputs += Math.max(0, Config.MAX_OUTPUT_PORTS - box.getOutPorts().size());
        }
        if (neededInputs > freeOutputs) {
            throw new IllegalStateException("Insufficient port capacity in existing systems for new stage");
        }

        // 3) create new boxes
        List<SystemBoxModel> newBoxes = new ArrayList<>();
        for (LevelDefinition.BoxSpec spec : newSpecs) {
            SystemBoxModel box = new SystemBoxModel(
                    spec.x(), spec.y(), spec.width(), spec.height(),
                    spec.inShapes(), spec.outShapes());
            newBoxes.add(box);

            if (behaviorRegistry != null) {
                behaviorRegistry.register(box, new NormalBehavior(box));
            }
        }

        // 4) merge existing and new, then paint
        List<SystemBoxModel> all = new ArrayList<>(existingBoxes);
        all.addAll(newBoxes);

        // 🔧 تضمین وجود حداقل یک سیستم مقصد (بدون خروجی)
        boolean hasDestination = all.stream().anyMatch(box -> box.getOutPorts().isEmpty());
        if (!hasDestination && !all.isEmpty()) {
            all.get(all.size() - 1).removeOutputPort();
        }

        gameView.reset(all, wires);

        // 5) attach drag behaviour (again, for new components)
        attachDragControllers();
        return all;
    }
}
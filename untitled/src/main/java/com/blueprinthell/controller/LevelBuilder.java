package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PortShape;
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
 * Utility class responsible **only** for instantiating the static parts of a level:
 * <ul>
 *     <li>Creating {@link SystemBoxModel} instances from a {@link LevelDefinition}</li>
 *     <li>Placing them on the {@link GameScreenView}</li>
 *     <li>Wiring mouse‑drag behaviour via {@link SystemBoxDragController}</li>
 * </ul>
 * Also handles port distribution for multi‑stage progressive levels.
 */
public final class LevelBuilder {

    private final GameScreenView gameView;
    private final List<WireModel> wires;         // shared list, mutated by drag/wire controllers
    private final WireUsageModel usageModel;

    public LevelBuilder(GameScreenView gameView,
                        List<WireModel> wires,
                        WireUsageModel usageModel) {
        this.gameView   = gameView;
        this.wires      = wires;
        this.usageModel = usageModel;
    }

    /**
     * Builds and returns the SystemBox models for the supplied blueprint,
     * distributing extra output ports among existing boxes if present.
     * Side‑effects: the related Swing components are added to the {@code gameView}.
     *
     * @param def            definition of the new stage
     * @param existingBoxes  boxes from previous stages (empty on first)
     */
    public List<SystemBoxModel> build(LevelDefinition def,
                                      List<SystemBoxModel> existingBoxes) {
        // 0) determine the new specs to create and ports requirement
        List<LevelDefinition.BoxSpec> allSpecs = def.boxes();
        int existingCount = existingBoxes.size();
        List<LevelDefinition.BoxSpec> newSpecs = existingCount < allSpecs.size()
                ? allSpecs.subList(existingCount, allSpecs.size())
                : List.of();
        int requiredPorts = newSpecs.stream()
                .mapToInt(spec -> spec.outShapes().size())
                .sum();

        // 1) distribute ports among existing boxes based on new specs need
        for (SystemBoxModel box : existingBoxes) {
            while (box.getOutPorts().size() < Config.MAX_OUTPUT_PORTS && requiredPorts > 0) {
                PortShape shape = box.getOutShapes().isEmpty()
                        ? Config.DEFAULT_PORT_SHAPE
                        : box.getOutShapes().get(0);
                box.addOutputPort(shape);
                requiredPorts--;
            }
            if (requiredPorts == 0) break;
        }

        // 2) create only new boxes for new specs
        List<SystemBoxModel> newBoxes = new ArrayList<>();
        for (LevelDefinition.BoxSpec spec : newSpecs) {
            SystemBoxModel box = new SystemBoxModel(
                    spec.x(), spec.y(), spec.width(), spec.height(),
                    spec.inShapes(), spec.outShapes());
            newBoxes.add(box);
        }

        // 3) merge existing and new, then paint
        List<SystemBoxModel> all = new ArrayList<>(existingBoxes);
        all.addAll(newBoxes);
        gameView.reset(all, wires);

        // 4) attach drag behaviour
        for (Component c : gameView.getGameArea().getComponents()) {
            if (c instanceof SystemBoxView sbv) {
                new SystemBoxDragController(sbv.getModel(), sbv, wires, usageModel);
            }
        }
        return all;
    }
}

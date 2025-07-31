package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PortShape;
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
        int existingCount = existingBoxes.size();
        List<LevelDefinition.BoxSpec> newSpecs = existingCount < allSpecs.size()
                ? allSpecs.subList(existingCount, allSpecs.size())
                : List.of();
        int inputPortsNew = newSpecs.stream()
                .mapToInt(spec -> spec.inShapes().size())
                .sum();
        int outputPortsNew = newSpecs.stream()
                .mapToInt(spec -> spec.outShapes().size())
                .sum();
        int requiredPorts = Math.max(0, inputPortsNew - outputPortsNew);
        int availableCapacity = existingBoxes.stream()
                .mapToInt(box -> Config.MAX_OUTPUT_PORTS - box.getOutPorts().size())
                .sum();
        if (requiredPorts > availableCapacity) {
            throw new IllegalStateException("Insufficient port capacity in existing systems for new stage");
        }

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

        List<SystemBoxModel> newBoxes = new ArrayList<>();
        for (LevelDefinition.BoxSpec spec : newSpecs) {
            SystemBoxModel box = new SystemBoxModel(
                    spec.x(), spec.y(), spec.width(), spec.height(),
                    spec.inShapes(), spec.outShapes());
            // [NEW] Set primaryKind from spec
            box.setPrimaryKind(spec.kind());
            newBoxes.add(box);
        }

        List<SystemBoxModel> all = new ArrayList<>(existingBoxes);
        all.addAll(newBoxes);

        boolean hasDestination = all.stream()
                .anyMatch(box -> box.getOutPorts().isEmpty());
        if (!hasDestination && !all.isEmpty()) {
            all.get(all.size() - 1).removeOutputPort();
        }

        gameView.reset(all, wires);

        for (Component c : gameView.getGameArea().getComponents()) {
            if (c instanceof SystemBoxView sbv) {
                new SystemBoxDragController(sbv.getModel(), sbv, wires, usageModel);
            }
        }
        return all;
    }
}

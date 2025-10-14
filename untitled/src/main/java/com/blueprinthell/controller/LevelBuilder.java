package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.ui.editor.SystemBoxDragController;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.WireUsageModel;
import com.blueprinthell.view.SystemBoxView;
import com.blueprinthell.view.screens.GameScreenView;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;


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

        Map<String, SystemBoxModel> byId = existingBoxes.stream()
                .collect(Collectors.toMap(SystemBoxModel::getId, box -> box));

        List<SystemBoxModel> ordered = new ArrayList<>();
        List<SystemBoxModel> newBoxes = new ArrayList<>();

        for (LevelDefinition.BoxSpec spec : def.boxes()) {
            SystemBoxModel box = byId.get(spec.id());

            if (box != null) {
                syncBox(box, spec);
            } else {
                box = new SystemBoxModel(
                        spec.id(),
                        spec.x(), spec.y(), spec.width(), spec.height(),
                        spec.inShapes(), spec.outShapes());
                box.setPrimaryKind(spec.kind());
                newBoxes.add(box);
            }
            ordered.add(box);
        }

        gameView.reset(ordered, wires);

        SystemBoxDragController.clearDragLock();
        SystemBoxDragController.setDragEnabled(true);

        for (Component c : gameView.getGameArea().getComponents()) {
            if (c instanceof SystemBoxView sbv) {
                new SystemBoxDragController(sbv.getModel(), sbv, wires, usageModel);
            }
        }

        existingBoxes.clear();
        existingBoxes.addAll(ordered);

        return ordered;
    }


    private void syncBox(SystemBoxModel box, LevelDefinition.BoxSpec spec) {

        box.setPrimaryKind(spec.kind());

        var inPorts  = box.getInPorts();
        var inShapes = spec.inShapes();
        for (int i = 0; i < Math.min(inPorts.size(), inShapes.size()); i++) {
            inPorts.get(i).setShape(inShapes.get(i));
        }
        for (int i = inPorts.size(); i < inShapes.size(); i++) {
            box.addInputPort(inShapes.get(i));
        }

        var outPorts  = box.getOutPorts();
        var outShapes = spec.outShapes();
        for (int i = 0; i < Math.min(outPorts.size(), outShapes.size()); i++) {
            outPorts.get(i).setShape(outShapes.get(i));
        }
        for (int i = outPorts.size();
             i < outShapes.size() && i < Config.MAX_OUTPUT_PORTS;
             i++) {
            box.addOutputPort(outShapes.get(i));
        }

    }


}

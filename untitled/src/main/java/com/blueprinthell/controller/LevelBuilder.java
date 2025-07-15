package com.blueprinthell.controller;

import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.WireUsageModel;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.view.SystemBoxView;
import com.blueprinthell.view.screens.GameScreenView;

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
 * All simulation or HUD concerns are intentionally left to other classes so that
 * this builder remains single‑purpose.
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
     * Builds and returns the SystemBox models for the supplied blueprint.
     * Side‑effects: the related Swing components are added to the {@code gameView}.
     */
    public List<SystemBoxModel> build(LevelDefinition def) {
        // 1) create models
        List<SystemBoxModel> boxes = new ArrayList<>();
        for (LevelDefinition.BoxSpec spec : def.boxes()) {
            boxes.add(new SystemBoxModel(spec.x(), spec.y(), spec.width(), spec.height(),
                    spec.inShapes(), spec.outShapes()));
        }

        // 2) paint on screen
        gameView.reset(boxes, wires);

        // 3) attach drag behaviour
        for (Component c : gameView.getGameArea().getComponents()) {
            if (c instanceof SystemBoxView sbv) {
                new SystemBoxDragController(sbv.getModel(), sbv, wires, usageModel);
            }
        }
        return boxes;
    }
}

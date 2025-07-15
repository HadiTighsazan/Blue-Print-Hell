package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.level.LevelCompletionDetector;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.model.*;
import com.blueprinthell.view.HudView;
import com.blueprinthell.view.screens.GameScreenView;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Extracted from the former massive GameController: this class is in charge of
 * wiring **every Updatable** required for a level into a {@link SimulationController}.
 * <p>
 * It has ZERO knowledge of Swing buttons or level‑building; its only job is to
 * register the correct runtime controllers in the correct order.
 */
public final class SimulationRegistrar {

    /* -------------------------------------------------- */
    private final SimulationController simulation;
    private final ScreenController     screenController;
    private final CollisionController  collisionController;
    private final PacketRenderController packetRenderer;

    /* Models shared with other layers */
    private final ScoreModel      scoreModel;
    private final CoinModel       coinModel;
    private final PacketLossModel lossModel;
    private final WireUsageModel  usageModel;
    private final SnapshotManager snapshotManager;
    private final HudView         hudView;
    private final LevelManager    levelManager;

    public SimulationRegistrar(SimulationController simulation,
                               ScreenController screenController,
                               CollisionController collisionController,
                               PacketRenderController packetRenderer,
                               ScoreModel scoreModel,
                               CoinModel coinModel,
                               PacketLossModel lossModel,
                               WireUsageModel usageModel,
                               SnapshotManager snapshotManager,
                               HudView hudView,
                               LevelManager levelManager) {
        this.simulation         = simulation;
        this.screenController   = screenController;
        this.collisionController= collisionController;
        this.packetRenderer     = packetRenderer;
        this.scoreModel         = scoreModel;
        this.coinModel          = coinModel;
        this.lossModel          = lossModel;
        this.usageModel         = usageModel;
        this.snapshotManager    = snapshotManager;
        this.hudView            = hudView;
        this.levelManager       = levelManager;
    }

    /**
     * Registers every runtime Updatable for a new level.
     *
     * @param boxes      all SystemBoxModel instances
     * @param wires      shared list of WireModels
     * @param destMap    map Wire→Destination Box (filled by WireCreationController)
     * @param sources    list of source boxes
     * @param sink       sink box (may be null)
     * @param producer   PacketProducerController already configured for this lvl
     */
    public void registerAll(List<SystemBoxModel> boxes,
                            List<WireModel> wires,
                            Map<WireModel, SystemBoxModel> destMap,
                            List<SystemBoxModel> sources,
                            SystemBoxModel sink,
                            PacketProducerController producer) {

        /* ---------------- Packet flow controllers ---------------- */
        simulation.register(producer);
        simulation.register(new PacketDispatcherController(wires, destMap, coinModel, lossModel));
        if (sink != null)
            simulation.register(new PacketConsumerController(sink, scoreModel, coinModel));

        boxes.stream()
                .filter(b -> !b.getInPorts().isEmpty() && !b.getOutPorts().isEmpty())
                .forEach(b -> simulation.register(new PacketRouterController(b, wires, destMap, lossModel)));

        simulation.register(packetRenderer); // renders every tick
        simulation.register(collisionController);

        /* ---------------- Loss / Completion ---------------- */
        int planned = sources.stream()
                .mapToInt(b -> b.getOutPorts().size() * producer.getPacketsPerPort())
                .sum();
        simulation.register(new LossMonitorController(lossModel, planned, 0.5,
                simulation, screenController,
                () -> levelManager.startGame() )); // retry starts from beginning

        simulation.register(new LevelCompletionDetector(wires, lossModel, producer,
                levelManager, 0.5, planned));

        /* ---------------- Misc snapshot & hud ---------------- */
        simulation.register(new SnapshotController(boxes, wires,
                scoreModel, coinModel, usageModel, lossModel, snapshotManager));

        simulation.register(new HudController(scoreModel, usageModel, lossModel,
                coinModel, levelManager, hudView));
    }
}

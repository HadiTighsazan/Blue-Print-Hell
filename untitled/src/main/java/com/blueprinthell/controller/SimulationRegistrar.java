package com.blueprinthell.controller;

import com.blueprinthell.model.*;
import com.blueprinthell.level.LevelCompletionDetector;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.view.HudView;
import com.blueprinthell.view.screens.GameScreenView;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Wires every runtime Updatable required for a level into the SimulationController.
 * Responsibilities:
 *  - Register core packet flow controllers
 *  - Register game-over / retry logic
 *  - Register snapshot & HUD controllers
 *  - Register any system-specific controllers (VPN, Spy, etc.)
 */
public final class SimulationRegistrar {

    private final SimulationController simulation;
    private final ScreenController screenController;
    private final CollisionController collisionController;
    private final PacketRenderController packetRenderer;

    private final ScoreModel    scoreModel;
    private final CoinModel     coinModel;
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
     * Registers every Updatable for a new level.
     * @param boxes   all SystemBoxModels in play
     * @param wires   list of all wires connecting boxes
     * @param destMap mapping each wire to its destination box
     * @param sources source boxes producing packets
     * @param sink    the sink box consuming packets (may be null)
     * @param producer PacketProducerController pre-configured for this level
     * @param systems  List of custom system controllers (VPNSystem, SpySystem, ...)
     */
    public void registerAll(List<SystemBoxModel> boxes,
                            List<WireModel> wires,
                            Map<WireModel, SystemBoxModel> destMap,
                            List<SystemBoxModel> sources,
                            SystemBoxModel sink,
                            PacketProducerController producer,
                            List<Updatable> systems) {

        // Register custom systems (VPN, Spy, etc.) first
        if (systems != null) {
            systems.forEach(simulation::register);
        }

        // Packet flow: production, dispatch, routing, consumption
        simulation.register(producer);
        simulation.register(new PacketDispatcherController(wires, destMap, coinModel, lossModel));
        if (sink != null) {
            simulation.register(new PacketConsumerController(sink, scoreModel, coinModel));
        }

        boxes.stream()
                .filter(b -> !b.getInPorts().isEmpty() && !b.getOutPorts().isEmpty())
                .forEach(b -> simulation.register(
                        new PacketRouterController(b, wires, destMap, lossModel)
                ));

        // Rendering & collision
        simulation.register(packetRenderer);
        simulation.register(collisionController);

        // Loss / completion

        // Loss / completion: only if a ScreenController is available
        int plannedTotal = sources.stream()
                .mapToInt(b -> b.getOutPorts().size() * producer.getPacketsPerPort())
                .sum();
        if (screenController != null) {
            simulation.register(new LossMonitorController(
                    lossModel,
                    plannedTotal,
                    0.5,
                    simulation,
                    screenController,
                    () -> levelManager.startGame()
            ));
        }
        simulation.register(new LevelCompletionDetector(
                wires, lossModel, producer,
                levelManager, 0.5, plannedTotal));

        // Snapshots & HUD
        simulation.register(new SnapshotController(
                boxes, wires,
                scoreModel, coinModel, usageModel, lossModel, snapshotManager));
        // HUD sync (wire, loss, coins, level)
        simulation.register(new HudController(
                usageModel, lossModel, coinModel, levelManager, hudView
        ));
    }
}

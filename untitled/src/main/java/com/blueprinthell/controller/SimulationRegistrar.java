package com.blueprinthell.controller;

import com.blueprinthell.model.*;
import com.blueprinthell.level.LevelCompletionDetector;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.view.HudView;
import com.blueprinthell.view.screens.GameScreenView;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


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


    public void registerAll(List<SystemBoxModel> boxes,
                            List<WireModel> wires,
                            Map<WireModel, SystemBoxModel> destMap,
                            List<SystemBoxModel> sources,
                            SystemBoxModel sink,
                            PacketProducerController producer,
                            List<Updatable> systems) {

        if (systems != null) {
            systems.forEach(simulation::register);
        }

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

        simulation.register(packetRenderer);
        simulation.register(collisionController);


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

        simulation.register(new SnapshotController(
                boxes, wires,
                scoreModel, coinModel, usageModel, lossModel, snapshotManager));
        simulation.register(new HudController(
                usageModel, lossModel, coinModel, levelManager, hudView
        ));
    }
}

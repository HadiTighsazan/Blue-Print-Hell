package com.blueprinthell.controller;

import com.blueprinthell.model.*;
import com.blueprinthell.level.LevelCompletionDetector;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.view.HudView;
import com.blueprinthell.view.screens.GameScreenView;

import java.util.*;
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

        // NEW: از ثبت تکراری جلوگیری کنیم
        Set<Updatable> already = new HashSet<>();
        if (systems != null) {
            already.addAll(systems);
        }

        Map<PortModel, SystemBoxModel> portToBoxMap = new HashMap<>();
        for (SystemBoxModel b : boxes) {
            b.getInPorts().forEach(p  -> portToBoxMap.put(p, b));
            b.getOutPorts().forEach(p -> portToBoxMap.put(p, b));
        }

        collisionController.setPortToBoxMap(portToBoxMap);
        WireModel.setPortToBoxMap(portToBoxMap);

        WireModel.setSimulationController(simulation);
        if (sources != null) {
            WireModel.setSourceInputPorts(sources);
        }

        // NEW: خودِ باکس‌ها را به‌عنوان Updatable رجیستر کن تا re-enable کار کند
        for (SystemBoxModel b : boxes) {
            if (!already.contains(b)) {
                simulation.register(b);
            }
        }

        simulation.register(producer);
        simulation.register(new PacketDispatcherController(wires, destMap, coinModel, lossModel));
        if (sink != null) {
            simulation.register(new PacketConsumerController(sink, scoreModel, coinModel));
        }

        boxes.stream()
                .filter(b -> !b.getOutPorts().isEmpty()) // مبدأها نیز روتر می‌گیرند
                .forEach(b -> simulation.register(
                        new PacketRouterController(b, wires, destMap, lossModel)
                ));

        simulation.register(packetRenderer);
        simulation.register(collisionController);

        // NEW: محافظت در برابر null
        int plannedTotal = (sources != null)
                ? sources.stream().mapToInt(b -> b.getOutPorts().size() * producer.getPacketsPerPort()).sum()
                : 0;

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
                wires, boxes, lossModel, producer,
                levelManager, 0.5, plannedTotal));

        simulation.register(new SnapshotController(
                boxes, wires,
                scoreModel, coinModel, usageModel, lossModel, snapshotManager));
        simulation.register(new HudController(
                usageModel, lossModel, coinModel, levelManager, hudView
        ));
    }

}

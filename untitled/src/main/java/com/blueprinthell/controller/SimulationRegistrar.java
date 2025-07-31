package com.blueprinthell.controller;

import com.blueprinthell.controller.systems.*;
import com.blueprinthell.model.*;
import com.blueprinthell.level.LevelCompletionDetector;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.view.HudView;

import java.util.*;


public class SimulationRegistrar {

    private final SimulationController simulation;
    private final ScreenController screenController;
    private final CollisionController collisionController;
    private final PacketRenderController packetRenderer;

    private final ScoreModel scoreModel;
    private final CoinModel coinModel;
    private final PacketLossModel lossModel;
    private final WireUsageModel usageModel;

    private final SnapshotManager snapshotManager;
    private final HudView hudView;
    private final LevelManager levelManager;

    private final BehaviorRegistry behaviorRegistry = new BehaviorRegistry();
    private final LargeGroupRegistry largeGroupRegistry = new LargeGroupRegistry();

    private List<LevelDefinition.BoxSpec> currentBoxSpecs = Collections.emptyList();

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
        this.simulation = Objects.requireNonNull(simulation, "simulation");
        this.screenController = screenController;
        this.collisionController = Objects.requireNonNull(collisionController, "collisionController");
        this.packetRenderer = Objects.requireNonNull(packetRenderer, "packetRenderer");
        this.scoreModel = Objects.requireNonNull(scoreModel, "scoreModel");
        this.coinModel = Objects.requireNonNull(coinModel, "coinModel");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
        this.usageModel = Objects.requireNonNull(usageModel, "usageModel");
        this.snapshotManager = Objects.requireNonNull(snapshotManager, "snapshotManager");
        this.hudView = Objects.requireNonNull(hudView, "hudView");
        this.levelManager = Objects.requireNonNull(levelManager, "levelManager");
    }



    public void setCurrentBoxSpecs(List<LevelDefinition.BoxSpec> specs) {
        this.currentBoxSpecs = (specs != null) ? specs : Collections.emptyList();
    }


    public LevelDefinition.BoxSpec findBoxSpec(SystemBoxModel box) {
        if (box == null || currentBoxSpecs == null) return null;
        for (var spec : currentBoxSpecs) {
            try {
                if (spec.x() == box.getX() && spec.y() == box.getY()
                        && spec.width() == box.getWidth() && spec.height() == box.getHeight()) {
                    return spec;
                }
            } catch (Throwable ignore) {
            }
        }
        return null;
    }

    public BehaviorRegistry getBehaviorRegistry() {
        return behaviorRegistry;
    }


    public void registerAll(List<SystemBoxModel> boxes,
                            List<WireModel> wires,
                            Map<WireModel, SystemBoxModel> destMap,
                            List<SystemBoxModel> sources,
                            SystemBoxModel sink,
                            PacketProducerController producer,
                            List<Updatable> extraSystems) {

        Objects.requireNonNull(boxes, "boxes");
        Objects.requireNonNull(wires, "wires");
        Objects.requireNonNull(destMap, "destMap");

        Set<Updatable> already = new HashSet<>();
        if (extraSystems != null) {
            for (Updatable u : extraSystems) {
                if (u != null) {
                    simulation.register(u);
                    already.add(u);
                }
            }
        }

        Map<PortModel, SystemBoxModel> portToBoxMap = new HashMap<>();
        for (SystemBoxModel b : boxes) {
            for (PortModel p : b.getInPorts()) portToBoxMap.put(p, b);
            for (PortModel p : b.getOutPorts()) portToBoxMap.put(p, b);
        }
        collisionController.setPortToBoxMap(portToBoxMap);
        WireModel.setPortToBoxMap(portToBoxMap);
        WireModel.setSimulationController(simulation);
        if (sources != null) {
            WireModel.setSourceInputPorts(sources);
        }

        for (SystemBoxModel b : boxes) {
            if (!already.contains(b)) simulation.register(b);
        }

        for (SystemBoxModel box : boxes) {
            attachBehaviorsForBox(box, boxes, wires, destMap);
        }

        for (SystemBoxModel box : boxes) {
            if (!box.getOutPorts().isEmpty()) {
                PacketRouterController router = new PacketRouterController(box, wires, destMap, lossModel);
                simulation.register(router);
            }
        }

        if (producer != null) {
            simulation.register(producer);
        }
        PacketDispatcherController dispatcher = new PacketDispatcherController(wires, destMap, coinModel, lossModel);
        simulation.register(dispatcher);

        if (sink != null) {
            PacketConsumerController consumer = new PacketConsumerController(sink, scoreModel, coinModel);
            simulation.register(consumer);
        }

        registerOptionalControllers(wires, boxes, destMap);

        int plannedTotal = (sources != null && producer != null)
                ? sources.stream().mapToInt(b -> b.getOutPorts().size() * producer.getPacketsPerPort()).sum()
                : 0;
        if (screenController != null) {
            LossMonitorController lossMonitor = new LossMonitorController(
                    lossModel,
                    plannedTotal,
                    0.5,
                    simulation,
                    screenController,
                    () -> {
                        if (producer != null) producer.reset();
                        levelManager.startGame();
                    }
            );
            simulation.register(lossMonitor);
        }

        if (producer != null) {
            LevelCompletionDetector detector = new LevelCompletionDetector(
                    wires, boxes, lossModel, producer, levelManager, 0.5, plannedTotal);
            simulation.register(detector);
        }

        SnapshotController snapshotCtrl = new SnapshotController(
                boxes, wires, scoreModel, coinModel, usageModel, lossModel, snapshotManager);
        simulation.register(snapshotCtrl);

        HudController hudController = new HudController(usageModel, lossModel, coinModel, levelManager, hudView);
        simulation.register(hudController);

        simulation.register(packetRenderer);
        simulation.register(collisionController);

    }


    private void attachBehaviorsForBox(SystemBoxModel box,
                                       List<SystemBoxModel> allBoxes,
                                       List<WireModel> wires,
                                       Map<WireModel, SystemBoxModel> destMap) {
        LevelDefinition.BoxSpec spec = findBoxSpec(box);
        SystemKind kind = null;
        try {
            if (spec != null) {
                kind = spec.kind();
            }
        } catch (Throwable ignore) {
        }
        if (kind == null) kind = SystemKind.NORMAL;

        switch (kind) {
            case SPY: {
                SpyBehavior spy = new SpyBehavior(box, behaviorRegistry, lossModel, wires, destMap);
                behaviorRegistry.register(box, spy);
                SystemBehaviorAdapter adapter = new SystemBehaviorAdapter(box, spy);
                simulation.register(adapter);
                break;
            }
            case MALICIOUS: {
                MaliciousBehavior mal = new MaliciousBehavior(box, 0.15);
                behaviorRegistry.register(box, mal);
                SystemBehaviorAdapter adapter = new SystemBehaviorAdapter(box, mal);
                simulation.register(adapter);
                break;
            }
            case VPN: {
                VpnBehavior vpn = new VpnBehavior(box);
                behaviorRegistry.register(box, vpn);
                SystemBehaviorAdapter adapter = new SystemBehaviorAdapter(box, vpn);
                simulation.register(adapter);
                break;
            }
            case ANTI_TROJAN: {
                AntiTrojanBehavior anti = new AntiTrojanBehavior(box, wires);
                behaviorRegistry.register(box, anti);
                SystemBehaviorAdapter adapter = new SystemBehaviorAdapter(box, anti);
                simulation.register(adapter);
                break;
            }
            case DISTRIBUTOR: {
                DistributorBehavior db = new DistributorBehavior(box, largeGroupRegistry, lossModel);
                behaviorRegistry.register(box, db);
                SystemBehaviorAdapter adapter = new SystemBehaviorAdapter(box, db);
                simulation.register(adapter);
                break;
            }
            case MERGER: {
                MergerBehavior mb = new MergerBehavior(box, largeGroupRegistry, lossModel);
                behaviorRegistry.register(box, mb);
                SystemBehaviorAdapter adapter = new SystemBehaviorAdapter(box, mb);
                simulation.register(adapter);
                break;
            }
            case NORMAL:
            default: {
                NormalBehavior nb = new NormalBehavior(box);
                behaviorRegistry.register(box, nb);
                SystemBehaviorAdapter adapter = new SystemBehaviorAdapter(box, nb);
                simulation.register(adapter);
                break;
            }
        }
    }

    private double estimatePlannedTotal(PacketProducerController producer, List<SystemBoxModel> sources) {
        if (producer == null || sources == null) return 0.0;
        try {
            int ppp = producer.getPacketsPerPort();
            return (double) ppp * Math.max(1, sources.size());
        } catch (Throwable ignore) {
            return 0.0;
        }
    }

    public void registerOptionalControllers(List<WireModel> wires,
                                            List<SystemBoxModel> boxes,
                                            Map<WireModel, SystemBoxModel> destMap) {
        // Throttle برای Confidential-4
        ConfidentialThrottleController throttle = new ConfidentialThrottleController(wires, destMap);
        throttle.setEnabled(true);
        simulation.register(throttle);

        WireTimeoutController timeout = new WireTimeoutController(wires, lossModel);
        simulation.register(timeout);

    }


}

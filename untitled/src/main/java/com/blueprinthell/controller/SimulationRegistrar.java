package com.blueprinthell.controller;

import com.blueprinthell.controller.systems.*;
import com.blueprinthell.model.*;
import com.blueprinthell.level.LevelCompletionDetector;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.view.HudView;

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

    // اضافه کردن BehaviorRegistry
    private final BehaviorRegistry behaviorRegistry = new BehaviorRegistry();

    // نگهداری مشخصات باکس‌ها از LevelDefinition
    private List<LevelDefinition.BoxSpec> currentBoxSpecs;

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

    // متد جدید برای ست کردن BoxSpec ها
    public void setCurrentBoxSpecs(List<LevelDefinition.BoxSpec> specs) {
        this.currentBoxSpecs = specs;
    }

    // متد پیدا کردن BoxSpec متناظر با SystemBoxModel
    private LevelDefinition.BoxSpec findBoxSpec(SystemBoxModel box) {
        if (currentBoxSpecs == null) return null;

        // مقایسه بر اساس موقعیت و اندازه
        for (LevelDefinition.BoxSpec spec : currentBoxSpecs) {
            if (spec.x() == box.getX() &&
                    spec.y() == box.getY() &&
                    spec.width() == box.getWidth() &&
                    spec.height() == box.getHeight()) {
                return spec;
            }
        }
        return null;
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

        // ثبت باکس‌ها
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

        // ثبت سیستم‌های خاص بر اساس نوع
        for (SystemBoxModel box : boxes) {
            LevelDefinition.BoxSpec spec = findBoxSpec(box);
            if (spec != null) {
                switch (spec.kind()) {
                    case SPY:
                        SpyBehavior spyBehavior = new SpyBehavior(
                                box, behaviorRegistry, lossModel, wires, destMap
                        );
                        box.addBehavior(spyBehavior);
                        behaviorRegistry.register(box, spyBehavior);
                        // سیستم جاسوس هم نیاز به روتر دارد
                        simulation.register(new PacketRouterController(box, wires, destMap, lossModel));
                        break;

                    case MALICIOUS:
                        MaliciousBehavior maliciousBehavior = new MaliciousBehavior(box, 0.15);
                        box.addBehavior(maliciousBehavior);
                        behaviorRegistry.register(box, maliciousBehavior);
                        simulation.register(new PacketRouterController(box, wires, destMap, lossModel));
                        break;

                    case VPN:
                        VpnBehavior vpnBehavior = new VpnBehavior(box);
                        box.addBehavior(vpnBehavior);
                        behaviorRegistry.register(box, vpnBehavior);
                        simulation.register(new PacketRouterController(box, wires, destMap, lossModel));
                        break;

                    case ANTI_TROJAN:
                        AntiTrojanBehavior antiTrojanBehavior = new AntiTrojanBehavior(box, wires);
                        box.addBehavior(antiTrojanBehavior);
                        behaviorRegistry.register(box, antiTrojanBehavior);
                        simulation.register(new PacketRouterController(box, wires, destMap, lossModel));
                        break;

                    case NORMAL:
                    default:
                        // سیستم‌های عادی فقط روتر دارند
                        if (!box.getOutPorts().isEmpty()) {
                            simulation.register(new PacketRouterController(box, wires, destMap, lossModel));
                        }
                        break;
                }
            } else {
                // اگر spec پیدا نشد، رفتار پیش‌فرض
                if (!box.getOutPorts().isEmpty()) {
                    simulation.register(new PacketRouterController(box, wires, destMap, lossModel));
                }
            }
        }

        simulation.register(packetRenderer);
        simulation.register(collisionController);

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
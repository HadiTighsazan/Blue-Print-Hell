package com.blueprinthell.controller;

import com.blueprinthell.controller.systems.BehaviorRegistry;
import com.blueprinthell.controller.systems.SystemBehavior;
import com.blueprinthell.level.LevelCompletionDetector;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.model.*;
import com.blueprinthell.view.HudView;
import com.blueprinthell.config.Config;

import java.util.*;

/**
 * <h2>SimulationRegistrar</h2>
 * مسئول ثبت همهٔ موجودیت‌های {@link Updatable} در حلقهٔ شبیه‌سازی به ترتیب صحیح.
 * نسخهٔ فعلی علاوه بر اجزای اصلی، Behaviourها، کنترلر دوام سیم، تایم‌اوت پکت روی سیم و throttle محرمانه را نیز ثبت می‌کند.
 */
public class SimulationRegistrar {

    private final SimulationController    simulation;
    private final ScreenController        screenController;
    private final CollisionController     collisionController;
    private final PacketRenderController  packetRenderer;
    private final ScoreModel              scoreModel;
    private final CoinModel               coinModel;
    private final PacketLossModel         lossModel;
    private final WireUsageModel          usageModel;
    private final SnapshotManager         snapshotManager;
    private final HudView                 hudView;
    private final LevelManager            levelManager;

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
        this.simulation        = simulation;
        this.screenController  = screenController;
        this.collisionController = collisionController;
        this.packetRenderer    = packetRenderer;
        this.scoreModel        = scoreModel;
        this.coinModel         = coinModel;
        this.lossModel         = lossModel;
        this.usageModel        = usageModel;
        this.snapshotManager   = snapshotManager;
        this.hudView           = hudView;
        this.levelManager      = levelManager;
    }

    /** نسخهٔ قدیمی بدون BehaviourRegistry */
    public void registerAll(List<SystemBoxModel> boxes,
                            List<WireModel> wires,
                            Map<WireModel, SystemBoxModel> destMap,
                            PacketProducerController producer,
                            PacketDispatcherController dispatcher,
                            PacketConsumerController consumer,
                            PacketRouterController router,
                            LossMonitorController lossMonitor) {
        registerAll(boxes, wires, destMap, producer, dispatcher, consumer, router, lossMonitor, null);
    }

    /** نسخهٔ جدید با BehaviourRegistry (nullable). */
    public void registerAll(List<SystemBoxModel> boxes,
                            List<WireModel> wires,
                            Map<WireModel, SystemBoxModel> destMap,
                            PacketProducerController producer,
                            PacketDispatcherController dispatcher,
                            PacketConsumerController consumer,
                            PacketRouterController router,
                            LossMonitorController lossMonitor,
                            BehaviorRegistry behaviorRegistry) {

        // 1) Core flow: تولید → ارسال روی سیم → (کنترل دوام/تایم‌اوت) → رفتارها → روت → throttle → مصرف
        simulation.register(producer);
        simulation.register(dispatcher);

        // 1.3) دوام سیم‌ها (Heavy packets count & auto-destroy)
        WireDurabilityController durability = new WireDurabilityController(wires, lossModel, Config.MAX_HEAVY_PASSES_PER_WIRE);
        dispatcher.setDurabilityController(durability);
        simulation.register(durability);

        // 1.4) تایم‌اوت پکت روی سیم
        WireTimeoutController timeoutCtrl = new WireTimeoutController(wires, lossModel, Config.MAX_TIME_ON_WIRE_SEC);
        simulation.register(timeoutCtrl);

        // 1.5) Behaviourها – قبل از Router تا روی بافرها اثر بگذارند
        if (behaviorRegistry != null) {
            for (List<SystemBehavior> list : behaviorRegistry.view().values()) {
                for (SystemBehavior b : list) {
                    simulation.register(dt -> b.update(dt));
                }
            }
        }

        // 1.6) Router
        simulation.register(router);

        // 1.7) Confidential throttle
        ConfidentialThrottleController confThrottle = new ConfidentialThrottleController(wires, destMap);
        simulation.register(confThrottle);

        // 1.8) Consumer
        simulation.register(consumer);

        // 2) Rendering
        simulation.register(packetRenderer);

        // 3) Collision & loss monitor
        collisionController.setPortToBoxMap(buildPortToBoxMap(boxes));
        simulation.register(collisionController);
        simulation.register(lossMonitor);

        // 4) Level completion detector
         int plannedTotal = Config.PACKETS_PER_PORT
                     * boxes.stream()
                            .mapToInt(b -> b.getOutPorts().size())
                           .sum();

        simulation.register(new LevelCompletionDetector(
                wires, lossModel, producer,
                levelManager, 0.5, plannedTotal));

        // 5) Snapshots & HUD
        simulation.register(new SnapshotController(
                boxes, wires,
                scoreModel, coinModel, usageModel, lossModel, snapshotManager));

        simulation.register(new HudController(
                usageModel, lossModel, coinModel, levelManager, hudView));
    }

    /**
     * پورت → باکس برای منطق bounce در CollisionController
     */
    private Map<PortModel, SystemBoxModel> buildPortToBoxMap(List<SystemBoxModel> boxes) {
        Map<PortModel, SystemBoxModel> map = new HashMap<>();
        for (SystemBoxModel b : boxes) {
            for (PortModel p : b.getInPorts())  map.put(p, b);
            for (PortModel p : b.getOutPorts()) map.put(p, b);
        }
        return map;
    }
}

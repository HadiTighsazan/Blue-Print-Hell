package com.blueprinthell.controller;

import com.blueprinthell.controller.systems.BehaviorRegistry;
import com.blueprinthell.controller.systems.SystemBehavior;
import com.blueprinthell.model.*;
import com.blueprinthell.level.LevelCompletionDetector;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.view.HudView;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.blueprinthell.config.Config;

/**
 * G0 – SimulationRegistrar (finalize):
 * <ul>
 *   <li>همان مسئولیت قبلی: ثبت همهٔ Updatable ها در لوپ شبیه‌سازی بدون تغییر رفتار موجود.</li>
 *   <li>اگر {@link BehaviorRegistry} داده شود، تمامی Behaviourها نیز به‌صورت Updatable ثبت می‌شوند
 *       (قبل از Router) تا در هر tick متد update آنها صدا زده شود.</li>
 *   <li>امضا و ترتیب اصلی حفظ شده؛ یک اورلود جدید با BehaviorRegistry اضافه شد تا کد قدیمی نشکند.</li>
 * </ul>
 */
public class SimulationRegistrar {

    private final SimulationController simulation;
    private final ScreenController     screenController;
    private final CollisionController  collisionController;
    private final PacketRenderController packetRenderer;
    private final ScoreModel           scoreModel;
    private final CoinModel            coinModel;
    private final PacketLossModel      lossModel;
    private final WireUsageModel       usageModel;
    private final SnapshotManager      snapshotManager;
    private final HudView              hudView;
    private final LevelManager         levelManager;

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
        this.simulation = simulation;
        this.screenController = screenController;
        this.collisionController = collisionController;
        this.packetRenderer = packetRenderer;
        this.scoreModel = scoreModel;
        this.coinModel = coinModel;
        this.lossModel = lossModel;
        this.usageModel = usageModel;
        this.snapshotManager = snapshotManager;
        this.hudView = hudView;
        this.levelManager = levelManager;
    }

    /** نسخهٔ قدیمی برای سازگاری – BehaviourRegistry ندارد. */
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

    /** نسخهٔ جدید با BehaviorRegistry. اگر null باشد، مثل نسخهٔ قدیم عمل می‌کند. */
    public void registerAll(List<SystemBoxModel> boxes,
                            List<WireModel> wires,
                            Map<WireModel, SystemBoxModel> destMap,
                            PacketProducerController producer,
                            PacketDispatcherController dispatcher,
                            PacketConsumerController consumer,
                            PacketRouterController router,
                            LossMonitorController lossMonitor,
                            BehaviorRegistry behaviorRegistry) {

        // 1) Core packet flow – ترتیب اصلی حفظ می‌شود
        simulation.register(producer);
        simulation.register(dispatcher);

        // 1.3) Wire durability: شمارش عبور پکت‌های حجیم و تخریب سیم‌ها
        WireDurabilityController durability = new WireDurabilityController(wires, lossModel, Config.MAX_HEAVY_PASSES_PER_WIRE);
        dispatcher.setDurabilityController(durability);
        simulation.register(durability);

        // 1.5) Register behaviours (if any) BEFORE router so they can transform packets in buffers
        if (behaviorRegistry != null) {
            for (java.util.List<SystemBehavior> list : behaviorRegistry.view().values()) {
                for (SystemBehavior b : list) {
                    simulation.register(dt -> b.update(dt));
                }
            }
        }


        simulation.register(router);

        // 1.7) Confidential throttle: کند کردن پکت‌های محرمانه قبل از باکسِ شلوغ
        ConfidentialThrottleController confThrottle = new ConfidentialThrottleController(wires, destMap);
        simulation.register(confThrottle);
        simulation.register(consumer);

        // 2) Rendering
        simulation.register(packetRenderer);

        // 3) Collision & loss
        // Inject port->box map for bounce logic (MSG1) into CollisionController
        collisionController.setPortToBoxMap(buildPortToBoxMap(boxes));
        simulation.register(collisionController);
        simulation.register(lossMonitor);

        // 4) Level completion detector (منطق اصلی بدون تغییر)
        int plannedTotal = 0;
        for (SystemBoxModel b : boxes) {
            if (!b.getInPorts().isEmpty()) {
                plannedTotal += b.getInPorts().size();
            }
        }
        simulation.register(new LevelCompletionDetector(
                wires, lossModel, producer,
                levelManager, 0.5, plannedTotal));

        // 5) Snapshots & HUD
        simulation.register(new SnapshotController(
                boxes, wires,
                scoreModel, coinModel, usageModel, lossModel, snapshotManager));

        simulation.register(new HudController(
                usageModel, lossModel, coinModel, levelManager, hudView
        ));

    }
    /**
     * Builds a Port -> Box map so CollisionController can bounce MSG1 packets back to their source box.
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

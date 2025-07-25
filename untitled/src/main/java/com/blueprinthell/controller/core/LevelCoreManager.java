package com.blueprinthell.controller.core;

import com.blueprinthell.controller.*;
import com.blueprinthell.controller.systems.BehaviorRegistry;
import com.blueprinthell.controller.systems.RouteHints;
import com.blueprinthell.controller.systems.VpnRevertHints;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.level.LevelGenerator;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.motion.KinematicsRegistry;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Facade that owns everything related to a single **level** lifecycle:
 * <ul>
 *     <li>building & extending boxes,</li>
 *     <li>wiring controllers,</li>
 *     <li>resetting transient registries between retries,</li>
 *     <li>co‑ordinating start / stop of the simulation loop.</li>
 * </ul>
 */
public class LevelCoreManager {

    /* ───────────────────────── External dependencies ───────────────────────── */
    private final GameController     gameController;
    private final LevelManager       levelManager;
    private       LevelBuilder       levelBuilder;   // late‑bound
    private final WireUsageModel     usageModel;
    private final BehaviorRegistry behaviorRegistry;   // may be null
    private final LargeGroupRegistry largeRegistry;      // may be null

    /* ───────────────────────── Runtime controllers ─────────────────────────── */
    private WireDurabilityController     durabilityController;   // optional
    private WireTimeoutController        timeoutController;      // optional
    private ConfidentialThrottleController confThrottleController; // optional

    private WireCreationController wireCreator;
    private WireRemovalController  wireRemover;

    /* ───────────────────────── Level state ─────────────────────────────────── */
    private LevelDefinition                 currentDef;
    private final List<SystemBoxModel>      boxes    = new ArrayList<>();
    private final List<WireModel>           wires    = new ArrayList<>();
    private final Map<WireModel,SystemBoxModel> destMap    = new HashMap<>();
    private final Map<PortModel,SystemBoxModel>  portToBox = new HashMap<>();

    /* ───────────────────────── Constructors ────────────────────────────────── */
    /**
     * Minimal constructor kept for legacy tests – behaviourRegistry & largeRegistry may be {@code null}.
     */
    public LevelCoreManager(GameController gc,
                            LevelManager   lm,
                            LevelBuilder   lb,
                            WireUsageModel usageModel) {
        this(gc, lm, lb, usageModel, null, null);
    }

    public LevelCoreManager(GameController gc,
                            LevelManager   lm,
                            LevelBuilder   lb,
                            WireUsageModel usageModel,
                            BehaviorRegistry behaviorRegistry) {
        this(gc, lm, lb, usageModel, behaviorRegistry, null);
    }

    /**
     * <b>Preferred</b> constructor – all dependencies injected up‑front.
     */
    public LevelCoreManager(GameController gc,
                            LevelManager   lm,
                            LevelBuilder   lb,
                            WireUsageModel usageModel,
                            BehaviorRegistry behaviorRegistry,
                            LargeGroupRegistry largeRegistry) {
        this.gameController   = gc;
        this.levelManager     = lm;
        this.levelBuilder     = lb;  // may be null → setter later
        this.usageModel       = usageModel;
        this.behaviorRegistry = behaviorRegistry;
        this.largeRegistry    = largeRegistry;
    }

    /* ───────────────────────── Public API ──────────────────────────────────── */

    /** Inject a {@link LevelBuilder} after construction (needed because builder
     *  depends on SimulationCoreManager which itself needs this LevelCoreManager). */
    public void setLevelBuilder(LevelBuilder builder) {
        this.levelBuilder = builder;
    }

    /** Main entry – load level by index (1‑based). */
    public void startLevel(int idx) {
        // TODO replace temp generator call with levelManager.get(idx) once implemented
        LevelDefinition def = LevelGenerator.firstLevel();
        for (int i = 1; i < idx; i++) def = LevelGenerator.nextLevel(def);
        startLevel(def);
    }

    /** Load a concrete {@link LevelDefinition}. */
    public void startLevel(LevelDefinition def) {
        this.currentDef = def;

        /* 1) Stop simulation & wipe transient data */
        gameController.getSimulation().stop();
        clearTransientState();

        /* 2) Reset wire quota */
        usageModel.reset(def.totalWireLength());

        /* 3) (Re)build boxes & maps */
        boxes.clear(); wires.clear(); destMap.clear();
        if (levelBuilder == null)
            throw new IllegalStateException("LevelBuilder not set before startLevel()");
        boxes.addAll(levelBuilder.build(def));
        rebuildPortToBoxMap();

        /* 4) Wire controllers */
        buildWireControllers();

        /* 5) Refresh UI */
        gameController.getHudController().refreshOnce();

        /* 6) Seek timeline to zero and run */
        gameController.getTimeline().scrubTo(0);
        gameController.getSimulation().start();
    }

    /** Remove *runtime* wires only (keeps LevelDefinition intact). */
    public void purgeCurrentLevelWires() {
        for (WireModel w : new ArrayList<>(wires)) removeWire(w);
        usageModel.reset(currentDef.totalWireLength());
        gameController.getGameView().repaint();
    }

    /** Extend an ongoing multi‑stage level with new specs (e.g. progressive waves). */
    public void extendWithSpecs(List<LevelDefinition.BoxSpec> specs) {
        boxes.clear();
        boxes.addAll(levelBuilder.extend(boxes, specs));
        rebuildPortToBoxMap();
        buildWireControllers();
    }

    /* ────────────── Wiring controllers ────────────── */
    public void buildWireControllers() {
        Runnable networkChanged = () -> {
            gameController.getHudController().refreshOnce();
            updateStartEnabled();
        };

        // اول WireCreationController می‌سازیم
        WireCreationController wireCreator = new WireCreationController(
                gameController.getGameView(),
                gameController.getSimulation(),    // یا getSimulationCoreManager() اگر نامش همینه
                boxes,
                wires,
                destMap,
                usageModel,
                gameController.getCoinModel(),
                networkChanged
        );
        this.wireCreator = wireCreator;

        // حالا WireRemovalController رو با پارامترهای صحیح بساز
        WireRemovalController wireRemover = new WireRemovalController(
                gameController.getGameView(),
                wires,
                destMap,
                wireCreator,       // همین ابجکتِ wireCreator
                usageModel,
                networkChanged
        );
        this.wireRemover = wireRemover;
    }





    /* ────────────── Wire operations ────────────── */
    public void removeWire(WireModel wire) {
        if (wireRemover != null) {
            wireRemover.removeWire(wire);
            return;
        }
        if (wires.remove(wire)) {
            destMap.remove(wire);
            usageModel.freeWire(wire.getLength());
        }
    }

    public void addWire(WireModel wire, SystemBoxModel dest) {
        wires.add(wire);
        destMap.put(wire, dest);
        usageModel.useWire(wire.getLength());
    }

    /* ────────────── UI sync helpers ────────────── */
    public void updateStartEnabled() {
        // 1) پیدا کردن و جمع‌آوری پورت‌های وصل‌نشده
        List<PortModel> unconnected = boxes.stream()
                .flatMap(b -> Stream.concat(b.getInPorts().stream(), b.getOutPorts().stream()))
                .filter(p -> !gameController.isPortConnected(p))
                .collect(Collectors.toList());

        // 2) لاگ‌شدن تعداد و نام پورت‌های وصل‌نشده
        System.out.println("[DEBUG] updateStartEnabled(): unconnected ports count = " + unconnected.size());
        if (!unconnected.isEmpty()) {
            // می‌توانی اینجا نام یا آدرس پورت‌ها را هم چاپ کنی
            unconnected.forEach(p ->
                    System.out.println("  - Unconnected Port: " + p + " (box=" + portToBox.get(p) + ")")
            );
        }

        // 3) محاسبه‌ی وضعیت دکمه
        boolean allConnected = unconnected.isEmpty();

        // 4) لاگ وضعیت نهایی
        System.out.println("[DEBUG] updateStartEnabled(): allConnected = " + allConnected);

        // 5) غیر/فعال‌سازی دکمه
        try {
            gameController.getHudController().setStartEnabled(allConnected);
        } catch (Throwable ignored) {
            // اگر استارت‌انبل خطا داد، شبیه‌سازی رفرش می‌کنیم
            System.out.println("[DEBUG] updateStartEnabled(): exception in setStartEnabled(), refreshing HUD");
            gameController.getHudController().refreshOnce();
        }
    }


    /* ────────────── Retry helpers ────────────── */
    public void retryStage() {
        gameController.getSimulation().stop();
        purgeCurrentLevelWires();
        startLevel(currentDef);
    }

    /** @deprecated – use {@link #retryStage()}. */
    @Deprecated
    public void retryLevel(LevelDefinition def) {
        gameController.getSimulation().stop();
        purgeCurrentLevelWires();
        startLevel(def);
    }

    /* ────────────── Internal helpers ────────────── */
    private void rebuildPortToBoxMap() {
        portToBox.clear();
        boxes.forEach(b -> {
            b.getInPorts().forEach(p -> portToBox.put(p, b));
            b.getOutPorts().forEach(p -> portToBox.put(p, b));
        });
    }

    private void clearTransientState() {
        Optional.ofNullable(largeRegistry).ifPresent(LargeGroupRegistry::clear);
        Optional.ofNullable(durabilityController).ifPresent(WireDurabilityController::clear);
        Optional.ofNullable(timeoutController).ifPresent(WireTimeoutController::clear);
        // confThrottleController currently has no clear(); add if/when implemented
        RouteHints.clear();
        VpnRevertHints.clear();
        KinematicsRegistry.clear();
    }

    /* ────────────── Getters / Setters ────────────── */
    public LevelDefinition getCurrentDef()                 { return currentDef; }
    public List<SystemBoxModel> getBoxes()                 { return boxes; }
    public List<WireModel> getWires()                      { return wires; }
    public Map<WireModel,SystemBoxModel> getDestMap()      { return destMap; }
    public Map<PortModel,SystemBoxModel> getPortToBoxMap() { return portToBox; }

    public WireCreationController getWireCreator() { return wireCreator; }
    public WireRemovalController  getWireRemover() { return wireRemover; }
    public WireUsageModel         getUsageModel()  { return usageModel; }
    public BehaviorRegistry       getBehaviorRegistry() { return behaviorRegistry; }

    public LargeGroupRegistry getLargeRegistry() { return largeRegistry; }

    public WireDurabilityController getDurabilityController() { return durabilityController; }
    public void setDurabilityController(WireDurabilityController ctrl) { this.durabilityController = ctrl; }

    public WireTimeoutController getTimeoutController() { return timeoutController; }
    public void setTimeoutController(WireTimeoutController ctrl) { this.timeoutController = ctrl; }

    public ConfidentialThrottleController getConfThrottleController() { return confThrottleController; }
    public void setConfThrottleController(ConfidentialThrottleController ctrl) { this.confThrottleController = ctrl; }

    public LevelBuilder getLevelBuilder() { return levelBuilder; }

    public LevelManager getLevelManager() { return levelManager; }
}

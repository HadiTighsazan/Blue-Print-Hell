package com.blueprinthell.controller.core;

import com.blueprinthell.controller.*;
import com.blueprinthell.controller.systems.BehaviorRegistry;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.WireUsageModel;
import com.blueprinthell.model.PortModel;

import java.util.*;

/**
 * G0 – FIXED VERSION
 * -------------------
 * این نسخه بر اساس کد موجود در زیپ بازنویسی شد تا:
 *  1) به متدها/فیلدهای واقعی پروژه ارجاع دهد (دیگر ارور «Cannot resolve method» ندهد).
 *  2) BehaviorRegistry تزریق شود (زیرساخت گام صفر).
 *  3) Stage حذف شد و متد extendWithSpecs جایگزین گردید.
 */
public class LevelCoreManager {

    /* ============================ خارجی ============================ */
    private final GameController   gameController;
    public final LevelManager     levelManager;
    public LevelBuilder     levelBuilder;
    private final WireUsageModel   usageModel;          // موجود در پروژه
    private final BehaviorRegistry behaviorRegistry;    // ممکن است null باشد در گام صفر

    /* ============================ وضعیت مرحله ============================ */
    private LevelDefinition              currentDef;
    private final List<SystemBoxModel>  boxes   = new ArrayList<>();
    private final List<WireModel>       wires   = new ArrayList<>();
    private final Map<WireModel, SystemBoxModel> destMap = new HashMap<>();

    // Map پورت → باکس برای Bounce در CollisionController
    private final Map<PortModel, SystemBoxModel> portToBox = new HashMap<>();

    // کنترلرهای وابسته به سیم‌کشی
    private WireCreationController wireCreator;
    private WireRemovalController  wireRemover;

    /* ---------------------------- سازنده‌ها ---------------------------- */
    public LevelCoreManager(GameController gc,
                            LevelManager lm,
                            LevelBuilder lb,
                            WireUsageModel usageModel) {
        this(gc, lm, lb, usageModel, null);
    }

    public LevelCoreManager(GameController gc,
                            LevelManager lm,
                            LevelBuilder lb,
                            WireUsageModel usageModel,
                            BehaviorRegistry registry) {
        this.gameController   = gc;
        this.levelManager     = lm;
        this.levelBuilder     = lb;
        this.usageModel       = usageModel;
        this.behaviorRegistry = registry;
    }

    /* ===================== چرخهٔ حیات مرحله ===================== */
    public void startLevel(int idx) {
        // Build the definition manually using LevelGenerator (no getDefinition in LevelManager)
        LevelDefinition def = com.blueprinthell.level.LevelGenerator.firstLevel();
        for (int i = 1; i < idx; i++) {
            def = com.blueprinthell.level.LevelGenerator.nextLevel(def);
        }
        startLevel(def);
    }

    public void startLevel(LevelDefinition def) {
        this.currentDef = def;

        // 1) توقف شبیه‌سازی هنگام بازسازی
        gameController.getSimulation().stop();

        // 2) پاکسازی وضعیت قدیمی
        boxes.clear();
        destMap.clear();
        wires.clear();
        usageModel.reset(def.totalWireLength());

        // 3) ساخت Box ها (LevelBuilder در صورت داشتن registry برای هر Box رفتار Normal ثبت می‌کند)
        boxes.addAll(levelBuilder.build(def));
        // بازسازی نگاشت پورت→باکس برای برخوردها
        rebuildPortToBoxMap();

        // 4) ساخت کنترلرهای سیم‌کشی
        buildWireControllers();

        // 5) HUD یکبار رفرش شود (HudController#refreshOnce موجود است)
        gameController.getHudController().refreshOnce();

        // 6) شروع مجدد شبیه‌سازی
        gameController.getTimeline().scrubTo(0);
        gameController.getSimulation().start();
    }

    /** سیم‌های مرحله فعلی را پاک می‌کند (برای Retry). */
    public void purgeCurrentLevelWires() {
        // فعلاً تمام سیم‌های غیر دائمی پاک می‌شوند (در آینده Flag بگذاریم)
        for (WireModel w : new ArrayList<>(wires)) {
            removeWire(w);
        }
        usageModel.reset(currentDef.totalWireLength());
        gameController.getGameView().repaint();
    }

    /** افزودن جعبه‌های جدید بدون Stage (لیست BoxSpec می‌گیرد). */
    public void extendWithSpecs(List<LevelDefinition.BoxSpec> specs) {
        List<SystemBoxModel> all = levelBuilder.extend(boxes, specs);
        boxes.clear();
        boxes.addAll(all);
        rebuildPortToBoxMap();
        // چون لیست boxes در WireCreationController به صورت reference پاس شده، اگر عوض شد باید کنترلر را دوباره بسازیم
        buildWireControllers();
    }

    /* ===================== کنترلرهای سیم‌کشی ===================== */
    public void buildWireControllers() {
        // سازنده‌ها خودشون به view گوش می‌دن؛ کافیست نمونه‌ها را بسازیم و به GC بدهیم
        wireCreator = new WireCreationController(
                gameController.getGameView(),
                gameController.getSimulation(),
                boxes,
                wires,
                destMap,
                usageModel,
                gameController.getCoinModel(),
                // هنگام تغییر شبکه HUD را به‌روزرسانی کن
                (Runnable) () -> gameController.getHudController().refreshOnce()
        );
        gameController.setWireCreator(wireCreator);

        wireRemover = new WireRemovalController(
                gameController.getGameView(),
                wires,
                destMap,
                wireCreator,              // <-- matches constructor signature
                usageModel,
                (Runnable) () -> gameController.getHudController().refreshOnce()
        );
    }

    /* ============================ کمکی‌ها ============================ */
    public void removeWire(WireModel wire) {
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

    /* ============================ Getter ها ============================ */
    public LevelDefinition getCurrentDef() { return currentDef; }
    public List<SystemBoxModel> getBoxes() { return boxes; }
    public List<WireModel> getWires() { return wires; }
    public Map<WireModel, SystemBoxModel> getDestMap() { return destMap; }
    public WireCreationController getWireCreator() { return wireCreator; }
    public WireRemovalController  getWireRemover() { return wireRemover; }
    public WireUsageModel         getUsageModel()  { return usageModel; }
    public BehaviorRegistry       getBehaviorRegistry() { return behaviorRegistry; }
    public Map<PortModel, SystemBoxModel> getPortToBoxMap() { return portToBox; }

    /** بازسازی Map پورت→باکس بر اساس لیست فعلی باکس‌ها */
    private void rebuildPortToBoxMap() {
        portToBox.clear();
        for (SystemBoxModel b : boxes) {
            for (PortModel p : b.getInPorts())  portToBox.put(p, b);
            for (PortModel p : b.getOutPorts()) portToBox.put(p, b);
        }
    }

    /* ============================ UI Sync ============================ */
    /**
     * Keeps compatibility with older code that expected this method here.
     * Currently it just delegates to GameController's implementation.
     */
    public void updateStartEnabled() {
        boolean allConnected = boxes.stream().allMatch(b ->
                b.getInPorts().stream().allMatch(gameController::isPortConnected) &&
                        b.getOutPorts().stream().allMatch(gameController::isPortConnected));
        gameController.getHudCoord().setStartEnabled(allConnected);
    }

    /* ============================ Retry ============================ */
    public void retryStage() {
        gameController.getSimulation().stop();
        purgeCurrentLevelWires();
        startLevel(currentDef);
    }

    /** @deprecated از {@link #retryStage()} استفاده کنید. */
    @Deprecated
    public void retryLevel(LevelDefinition def) {
        gameController.getSimulation().stop();
        purgeCurrentLevelWires();
        startLevel(def);
    }

    public LevelManager getLevelManager() {
        return levelManager;
    }

    public LevelBuilder getLevelBuilder() {
        return levelBuilder;
    }
}

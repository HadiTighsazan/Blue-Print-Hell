package com.blueprinthell.controller;

import com.blueprinthell.controller.core.LevelCoreManager;
import com.blueprinthell.controller.core.SimulationCoreManager;
import com.blueprinthell.controller.core.SnapshotCoreController;
import com.blueprinthell.controller.systems.TeleportTracking;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.snapshot.NetworkSnapshot;
import com.blueprinthell.view.HudView;
import com.blueprinthell.view.screens.GameScreenView;

import javax.swing.*;
import java.util.*;
import java.util.List;

public class GameController implements NetworkController {



    private final LevelCoreManager levelCoreManager = new LevelCoreManager(this);
    private final SimulationCoreManager simulationCoreManager = new SimulationCoreManager(this);
    private final SnapshotCoreController snapshotCoreController = new SnapshotCoreController();

    private ScreenController              screenController;
    private AutoSaveController autoSaveController;
    public LevelCoreManager getLevelSessionManager() {
        return levelCoreManager;
    }

    private final JFrame                  mainFrame;
    private final HudView                 hudView;
    private final GameScreenView          gameView;


    private HudController                 hudController;
    private boolean restoreInProgress = false;

    public ScreenController getScreenController() {
        return screenController;
    }

    public TimelineController getTimeline() {
        return simulationCoreManager.getTimeline();
    }

    public WireUsageModel getUsageModel() {
        return levelCoreManager.getUsageModel();
    }

    public SnapshotManager getSnapshotMgr() {
        return snapshotCoreController.getSnapshotMgr();
    }

    public HudView getHudView() {
        return hudView;
    }

    public HudCoordinator getHudCoord() {
        return hudCoord;
    }

    public ShopController getShopController() {
        return shopController;
    }

    public Map<WireModel, SystemBoxModel> getDestMap() {
        return levelCoreManager.getDestMap();
    }

    public CollisionController getCollisionCtrl() {
        return simulationCoreManager.getCollisionCtrl();
    }

    public LevelBuilder getLevelBuilder() {
        return levelCoreManager.getLevelBuilder();
    }

    public SnapshotService getSnapshotSvc() {
        return snapshotCoreController.getSnapshotSvc();
    }

    public SimulationRegistrar getRegistrar() {
        return simulationCoreManager.getRegistrar();
    }

    public List<SystemBoxModel> getBoxes() {
        return levelCoreManager.getBoxes();
    }

    public PacketRenderController getPacketRenderer() {
        return simulationCoreManager.getPacketRenderer();
    }

    public LevelManager getLevelManager() {
        return levelCoreManager.getLevelManager();
    }

    public WireCreationController getWireCreator() {
        return wireCreator;
    }

    public LevelDefinition getCurrentDef() {
        return levelCoreManager.getCurrentDef();
    }

    public JFrame getMainFrame() {
        return mainFrame;
    }

    private final HudCoordinator          hudCoord;
    private ShopController                shopController;


    private WireCreationController        wireCreator;


    public GameController(JFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.hudView   = new HudView(0, 0, 800, 50);
        this.gameView  = new GameScreenView(hudView);

        this.simulationCoreManager.collisionCtrl = new CollisionController(simulationCoreManager.getWires(), simulationCoreManager.getLossModel());
        this.levelCoreManager.levelBuilder = new LevelBuilder(gameView, simulationCoreManager.getWires(), levelCoreManager.getUsageModel());

        this.hudCoord = new HudCoordinator(hudView, simulationCoreManager.getScoreModel(), simulationCoreManager.getCoinModel(), simulationCoreManager.getLossModel(), simulationCoreManager.getSimulation(), simulationCoreManager.getTimeline());

        simulationCoreManager.getSimulation().setTimelineController(simulationCoreManager.getTimeline());

        gameView.setTemporalNavigationListener(this::onNavigateTime);
    }


    private void onNavigateTime(int dir) {
        simulationCoreManager.onNavigateTime(dir);
    }


    public void setLevelManager(LevelManager mgr) {
        this.levelCoreManager.levelManager = mgr;
    }


    public void startLevel(int idx) {
        levelCoreManager.startLevel(idx);
    }


    public void startLevel(LevelDefinition def) {

        levelCoreManager.startLevel(def);
    }


    private void buildWireControllers() {

        levelCoreManager.buildWireControllers();
    }


    public void updateStartEnabled() {
        levelCoreManager.updateStartEnabled();
    }

    public boolean isPortConnected(PortModel p) {
        return simulationCoreManager.isPortConnected(p);
    }


    private void purgeCurrentLevelWires() {

        levelCoreManager.purgeCurrentLevelWires();
    }


    public void retryStage() {
        levelCoreManager.retryStage();
    }


    @Deprecated
    private void retryLevel(LevelDefinition def) {
        levelCoreManager.retryLevel(def);
    }


    @Override
    public NetworkSnapshot captureSnapshot() {
        return snapshotCoreController.captureSnapshot();
    }


    public GameScreenView getGameView() { return gameView; }
    public List<WireModel> getWires()  {
        return simulationCoreManager.getWires();
    }
    public SimulationController getSimulation() {
        return simulationCoreManager.getSimulation();
    }
    public CoinModel getCoinModel() {
        return simulationCoreManager.getCoinModel();
    }
    public CollisionController getCollisionController() {
        return simulationCoreManager.getCollisionController();
    }
    public PacketLossModel getLossModel() {
        return simulationCoreManager.getLossModel();
    }
    public ScoreModel getScoreModel() {
        return simulationCoreManager.getScoreModel();
    }
    public HudController getHudController() { return hudController; }
    public PacketProducerController getProducerController() {
        return simulationCoreManager.getProducerController();
    }

    public void setHudController(HudController hudController) {
        this.hudController = hudController;
    }

    public void setShopController(ShopController shopController) {
        this.shopController = shopController;
    }

    public void setSnapshotSvc(SnapshotService snapshotSvc) {
        this.snapshotCoreController.snapshotSvc = snapshotSvc;
    }

    public void setRegistrar(SimulationRegistrar registrar) {
        simulationCoreManager.setRegistrar(registrar);
    }

    public void setPacketRenderer(PacketRenderController packetRenderer) {
        simulationCoreManager.setPacketRenderer(packetRenderer);
    }

    public void setProducerController(PacketProducerController producerController) {
        simulationCoreManager.setProducerController(producerController);
    }

    public void setWireCreator(WireCreationController wireCreator) {
        this.wireCreator = wireCreator;
    }

       public void restoreState(NetworkSnapshot snap) {
                SimulationRegistrar reg = getRegistrar();
               if (reg != null) reg.clearTransientState();
                TeleportTracking.clearAll();
                snapshotCoreController.restoreState(snap);
            }
    public void setScreenController(ScreenController sc) { this.screenController = sc; }
    public void startAutoSave() {
        if (autoSaveController == null) {
            autoSaveController = new AutoSaveController(
                    snapshotCoreController.getSnapshotSvc(),
                    5  // هر 5 ثانیه ذخیره شود
            );
        }
        autoSaveController.start();
        System.out.println("[GameController] AutoSave started");
    }



    public void restoreFromSavedProgress() {
        NetworkSnapshot snapshot = AutoSaveController.loadSavedProgress();
        if (snapshot == null) return;

        // 1) تعیین سطح از متای اسنپ‌شات
        int lvl = 1;
        try {
            if (snapshot.meta != null && snapshot.meta.levelNumber > 0) {
                lvl = snapshot.meta.levelNumber;
            }
        } catch (Exception ignore) { }

        // 2) load کردن level
        if (getLevelManager() != null) {
            getLevelManager().loadLevel(lvl);
        } else {
            startLevel(lvl);
        }

        // 3) توقف موقت شبیه‌سازی برای restore تمیز
        getSimulation().stop();

        // توقف موقت AutoSave (نه pause که فایل را حفظ می‌کند)
        if (autoSaveController != null && autoSaveController.isRunning()) {
            autoSaveController.stop();
        }

        // 4) پاکسازی حالت‌های گذرا
        try {
            if (getRegistrar() != null) {
                getRegistrar().clearTransientState();
            }
        } catch (Throwable ignore) {}

        // 5) اطمینان از ساخته شدن SnapshotService
        if (getSnapshotSvc() == null) {
            throw new IllegalStateException("SnapshotService not initialized after loading level " + lvl);
        }

        // 6) بازیابی state
        restoreState(snapshot);

        // 7) بازیابی وضعیت producer
        if (getProducerController() != null && snapshot.world != null
                && snapshot.world.producers != null && !snapshot.world.producers.isEmpty()) {
            NetworkSnapshot.ProducerState ps = snapshot.world.producers.get(0);
            // اگر producer قبلاً در حال اجرا بوده، وضعیت آن را حفظ کن
            if (ps.running && !getProducerController().isFinished()) {
                // این فقط flag را set می‌کند، واقعاً start نمی‌کند تا بعداً انجام شود
                getProducerController().stopProduction(); // ابتدا متوقف کن
            }
        }

        SwingUtilities.invokeLater(() -> {
            checkCompletionAfterRestore();
        });
        System.out.println("[GameController] Game restored from saved progress");
    }


    public void pauseAutoSave() {
        if (autoSaveController != null) {
            autoSaveController.pause();
        }
    }

    public void resumeAutoSave() {
        if (autoSaveController != null) {
            autoSaveController.resume();
        }
    }

    public void stopAutoSave() {
        if (autoSaveController != null) {
            autoSaveController.stop(); // DO NOT clear files here
            System.out.println("[GameController] AutoSave stopped (preserved)");
        }
    }

    // متد جدید: فقط وقتی می‌خوایم عمداً پاک کنیم (Exit منو یا New Game)
    public void stopAutoSaveAndClear() {
        if (autoSaveController != null) {
            autoSaveController.stop();
        }
        AutoSaveController.clearSavedProgress();
        System.out.println("[GameController] AutoSave stopped and cleared");
    }
    // اضافه کردن getter برای autoSaveController (اختیاری)
    public boolean isAutoSaveRunning() {
        return autoSaveController != null && autoSaveController.isRunning();
    }
    // در GameController.java اضافه کن:
    private void ensureSnapshotService() {
        if (snapshotCoreController.getSnapshotSvc() == null) {
            LargeGroupRegistry largeRegistry = (getRegistrar() != null) ? getRegistrar().getLargeGroupRegistry() : null;
            SnapshotService svc = new SnapshotService(
                    getDestMap(),
                    getBoxes(),
                    getWires(),
                    getScoreModel(),
                    getCoinModel(),
                    getLossModel(),
                    getUsageModel(),
                    getSnapshotMgr(),
                    getHudView(),
                    getGameView(),
                    getPacketRenderer(),
                    (getProducerController() != null) ? java.util.List.of(getProducerController()) : java.util.List.of(),
                    this::updateStartEnabled,
                    // تامین‌کنندهٔ شماره لول فعلی (در پچ 2 به SnapshotService اضافه می‌کنیم)
                    () -> {
                        try {
                            return getLevelManager() != null ? (getLevelManager().getLevelIndex() + 1) : 1;
                        } catch (Exception e) {
                            return 1;
                        }
                    },
                    largeRegistry
            );
            setSnapshotSvc(svc);
        }
    }
    private void checkCompletionAfterRestore() {
        // بررسی وضعیت تکمیل بلافاصله بعد از restore
        if (getProducerController() != null && getProducerController().isFinished()) {
            // بررسی که آیا همه پکت‌ها مصرف شده‌اند
            boolean allWiresEmpty = getWires().stream()
                    .allMatch(w -> w.getPackets().isEmpty());

            boolean allBoxesEmpty = getBoxes().stream()
                    .allMatch(b -> {
                        if (b.getOutPorts().isEmpty()) { // Sink
                            return !b.hasUnprocessedEntries();
                        }
                        return b.getBitBuffer().isEmpty() &&
                                b.getLargeBuffer().isEmpty() &&
                                !b.hasUnprocessedEntries();
                    });

            if (allWiresEmpty && allBoxesEmpty) {
                // بازی تمام شده - بررسی loss ratio
                getLossModel().finalizeDeferredLossNow();

                int producedUnits = getProducerController().getProducedUnits();
                double lossRatio = producedUnits > 0
                        ? (double) getLossModel().getLostCount() / producedUnits
                        : 0.0;

                double threshold = getLevelManager().getCurrentLevel().getMaxLossRatio();

                if (lossRatio < threshold) {
                    // موفقیت
                    SwingUtilities.invokeLater(() ->
                            getLevelManager().reportLevelCompleted());
                } else {
                    // شکست
                    SwingUtilities.invokeLater(() ->
                            getScreenController().showScreen(ScreenController.GAME_OVER));
                }
            }
        }
    }
}

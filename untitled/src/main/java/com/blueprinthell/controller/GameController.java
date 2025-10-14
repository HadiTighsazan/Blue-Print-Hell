package com.blueprinthell.controller;

import com.blueprinthell.controller.core.LevelCoreManager;
import com.blueprinthell.controller.core.SimulationCoreManager;
import com.blueprinthell.controller.core.SnapshotCoreController;
import com.blueprinthell.controller.gameplay.AccelerationFreezeController;
import com.blueprinthell.controller.packet.PacketProducerController;
import com.blueprinthell.controller.packet.PacketRenderController;
import com.blueprinthell.controller.persistence.AutoSaveController;
import com.blueprinthell.controller.persistence.SnapshotManager;
import com.blueprinthell.controller.persistence.SnapshotService;
import com.blueprinthell.controller.physics.CollisionController;
import com.blueprinthell.controller.simulation.NetworkController;
import com.blueprinthell.controller.simulation.SimulationController;
import com.blueprinthell.controller.simulation.SimulationRegistrar;
import com.blueprinthell.controller.simulation.TimelineController;
import com.blueprinthell.controller.systems.TeleportTracking;
import com.blueprinthell.controller.ui.ScreenController;
import com.blueprinthell.controller.ui.hud.HudController;
import com.blueprinthell.controller.ui.hud.HudCoordinator;
import com.blueprinthell.controller.wire.WireCreationController;
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

    private ScreenController screenController;
    private AutoSaveController autoSaveController;
    public LevelCoreManager getLevelSessionManager() {
        return levelCoreManager;
    }

    private final JFrame                  mainFrame;
    private final HudView                 hudView;
    private final GameScreenView          gameView;


    private AccelerationFreezeController freezeController;
    private HudController hudController;
    private ShopController shopController;
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


    private WireCreationController        wireCreator;


    public GameController(JFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.hudView   = new HudView(0, 0, 800, 50);
        this.gameView  = new GameScreenView(hudView);

        this.simulationCoreManager.collisionCtrl = new CollisionController(simulationCoreManager.getWires(), simulationCoreManager.getLossModel());
        this.levelCoreManager.levelBuilder = new LevelBuilder(gameView, simulationCoreManager.getWires(), levelCoreManager.getUsageModel());


        this.shopController = new ShopController(
                this.mainFrame,
                this.getSimulation(),
                this.getCoinModel(),
                this.getCollisionController(),
                this.getLossModel(),
                this.getWires(),
                this.hudController,
                this.gameView
        );

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

    public AccelerationFreezeController getFreezeController() {
        return freezeController;
    }

    public void setFreezeController(AccelerationFreezeController controller) {
        this.freezeController = controller;
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
            autoSaveController.stop();
        }
    }

    // متد جدید: فقط وقتی می‌خوایم عمداً پاک کنیم (Exit منو یا New Game)
    public void stopAutoSaveAndClear() {
        if (autoSaveController != null) {
            autoSaveController.stop();
        }
        AutoSaveController.clearSavedProgress();
    }
    public boolean isAutoSaveRunning() {
        return autoSaveController != null && autoSaveController.isRunning();
    }


}

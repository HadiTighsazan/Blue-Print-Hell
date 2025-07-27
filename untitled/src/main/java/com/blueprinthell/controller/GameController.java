package com.blueprinthell.controller;

import com.blueprinthell.controller.core.LevelCoreManager;
import com.blueprinthell.controller.core.SimulationCoreManager;
import com.blueprinthell.controller.core.SnapshotCoreController;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.model.*;
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

    public LevelCoreManager getLevelSessionManager() {
        return levelCoreManager;
    }

    private final JFrame                  mainFrame;
    private final HudView                 hudView;
    private final GameScreenView          gameView;


    private HudController                 hudController;

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

    @Override
    public void restoreState(NetworkSnapshot snap) {
        snapshotCoreController.restoreState(snap);
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

    public void setScreenController(ScreenController sc) { this.screenController = sc; }
}

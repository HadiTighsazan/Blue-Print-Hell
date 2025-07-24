package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.core.LevelCoreManager;
import com.blueprinthell.controller.core.SimulationCoreManager;
import com.blueprinthell.controller.core.SnapshotCoreController;
import com.blueprinthell.controller.systems.BehaviorRegistry;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.model.*;

import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.view.HudView;
import com.blueprinthell.view.screens.GameScreenView;

import javax.swing.*;
import java.util.*;
import java.util.List;

/**
 * The central façade that wires **Model–View–Controller** layers together.
 * <p>
 *  ‑ All heavy‑duty managers (Level, Simulation, Snapshot) are injected here.<br>
 *  ‑ Registries (<code>BehaviorRegistry</code>, <code>LargeGroupRegistry</code>) are global singletons for phase‑2 mechanics.<br>
 *  ‑ Construction order matters: <em>registries → levelManager → simulation → levelCore</em>.
 */
public class GameController implements NetworkController {

    /* ================================================================ */
    /*                     Core UI & immutable roots                    */
    /* ================================================================ */
    private final JFrame            mainFrame;
    private final ScreenController  screenController;
    private final HudView           hudView;
    private final GameScreenView    gameView;

    /* ================================================================ */
    /*                         Global registries                        */
    /* ================================================================ */
    private final BehaviorRegistry behaviorRegistry;
    private final LargeGroupRegistry largeRegistry;

    public void setLevelManager(LevelManager levelManager) {
        this.levelManager = levelManager;
    }

    /* ================================================================ */
    /*                    Managers (instantiated in‑order)              */
    /* ================================================================ */
    private  LevelManager          levelManager;
    private final SimulationCoreManager simulationCoreManager;
    private final LevelCoreManager      levelCoreManager;
    private final SnapshotCoreController snapshotCoreController = new SnapshotCoreController();

    /* ================================================================ */
    /*                    HUD / game‑state coordination                 */
    /* ================================================================ */
    private final HudCoordinator   hudCoord;
    private HudController          hudController;
    private ShopController         shopController;

    /* ================================================================ */
    /*                    Helpers retained for cleanup                  */
    /* ================================================================ */
    private WireCreationController wireCreator;

    /* ────────────────────────── ctor ─────────────────────────────── */
    public GameController(JFrame mainFrame) {
        /* 1) Immutable UI roots */
        this.mainFrame        = mainFrame;
        this.screenController = new ScreenController(mainFrame);
        this.hudView          = new HudView(0, 0, 800, 50);
        this.gameView         = new GameScreenView(hudView);

        /* 2) Global registries */
        this.behaviorRegistry = new BehaviorRegistry();
        this.largeRegistry    = new LargeGroupRegistry();

        /* 3) Level source */
        this.levelManager = new LevelManager(this, screenController);

        /* 4) Simulation */
        this.simulationCoreManager = new SimulationCoreManager(this, largeRegistry);

        /* Optional: override collision controller immediately */
        simulationCoreManager.setCollisionCtrl(
                new CollisionController(
                        simulationCoreManager.getWires(),
                        simulationCoreManager.getLossModel()
                )
        );

        /* 5) Level‑oriented manager (builder injected later) */
        this.levelCoreManager = new LevelCoreManager(
                this,
                levelManager,
                /* levelBuilder */ null,
                simulationCoreManager.getUsageModel(),
                behaviorRegistry,
                largeRegistry
        );

        /* 6) LevelBuilder now has all dependencies */
        LevelBuilder levelBuilder = new LevelBuilder(
                gameView,
                simulationCoreManager.getWires(),
                levelCoreManager.getUsageModel(),
                behaviorRegistry,
                largeRegistry,
                simulationCoreManager.getLossModel()
        );
        levelCoreManager.setLevelBuilder(levelBuilder);

        /* ★ 6‑bis) Packet‑producer controller */
        PacketProducerController producerCtrl = new PacketProducerController(
                levelCoreManager.getBoxes(),
                simulationCoreManager.getWires(),
                levelCoreManager.getDestMap(),
                Config.DEFAULT_PACKET_SPEED,
                Config.PACKETS_PER_PORT
        );
        simulationCoreManager.setProducerController(producerCtrl);

        /* 7) HUD coordination */
        this.hudCoord = new HudCoordinator(
                hudView,
                simulationCoreManager.getScoreModel(),
                simulationCoreManager.getCoinModel(),
                simulationCoreManager.getLossModel(),
                simulationCoreManager.getSimulation(),
                simulationCoreManager.getTimeline()
        );

        /* Instantiate HUD controller before wiring buttons */
        this.hudController = new HudController(
                simulationCoreManager.getUsageModel(),
                simulationCoreManager.getLossModel(),
                simulationCoreManager.getCoinModel(),
                this.levelManager,
                this.hudView
        );

        /* Now wire start button and enable it */
        hudCoord.wireLevel(producerCtrl);
        levelCoreManager.updateStartEnabled();

        /* ★ 7‑bis) SnapshotService – producerCtrl is non-null */
        SnapshotService snapSvc = new SnapshotService(
                levelCoreManager.getBoxes(),
                simulationCoreManager.getWires(),
                simulationCoreManager.getScoreModel(),
                simulationCoreManager.getCoinModel(),
                simulationCoreManager.getLossModel(),
                simulationCoreManager.getUsageModel(),
                getSnapshotMgr(),
                getHudView(),
                getGameView(),
                simulationCoreManager.getPacketRenderer(),
                List.of(producerCtrl)
        );
        setSnapshotSvc(snapSvc);

        /* 8) Wire game‑time navigation */
        simulationCoreManager.getSimulation()
                .setTimelineController(simulationCoreManager.getTimeline());
        gameView.setTemporalNavigationListener(this::onNavigateTime);
    }







    /* ================================================================ */
    /*                  Timeline scrubbing with keyboard                */
    /* ================================================================ */
    private void onNavigateTime(int dir) {
        simulationCoreManager.onNavigateTime(dir);
    }

    /* ================================================================ */
    /*               Public API to load a level by index                */
    /* ================================================================ */
    public void startLevel(int idx) { levelCoreManager.startLevel(idx); }
    public void startLevel(LevelDefinition def) { levelCoreManager.startLevel(def); }

    /* Build controllers for wire creation/removal */
    private void buildWireControllers() { levelCoreManager.buildWireControllers(); }

    /* Enable / disable “Start” button */
    public void updateStartEnabled() { levelCoreManager.updateStartEnabled(); }

    public boolean isPortConnected(PortModel p) { return simulationCoreManager.isPortConnected(p); }

    /* ---------------- retry helpers ---------------- */
    private void purgeCurrentLevelWires() { levelCoreManager.purgeCurrentLevelWires(); }
    public void retryStage() { levelCoreManager.retryStage(); }
    @Deprecated private void retryLevel(LevelDefinition def) { levelCoreManager.retryLevel(def); }

    /* ================================================================ */
    /*                       Snapshot interface                         */
    /* ================================================================ */
    @Override public NetworkSnapshot captureSnapshot() { return snapshotCoreController.captureSnapshot(); }
    @Override public void restoreState(NetworkSnapshot snap) { snapshotCoreController.restoreState(snap); }

    /* ================================================================ */
    /*                           Getters                                */
    /* ================================================================ */
    public JFrame getMainFrame()                 { return mainFrame; }
    public ScreenController getScreenController(){ return screenController; }
    public GameScreenView getGameView()          { return gameView; }

    public LevelCoreManager getLevelSessionManager() { return levelCoreManager; }
    public TimelineController getTimeline()          { return simulationCoreManager.getTimeline(); }
    public WireUsageModel getUsageModel()            { return levelCoreManager.getUsageModel(); }
    public SnapshotManager getSnapshotMgr()          { return snapshotCoreController.getSnapshotMgr(); }
    public HudView getHudView()                      { return hudView; }
    public HudCoordinator getHudCoord()              { return hudCoord; }
    public ShopController getShopController()        { return shopController; }
    public Map<WireModel, SystemBoxModel> getDestMap(){ return levelCoreManager.getDestMap(); }
    public CollisionController getCollisionCtrl()    { return simulationCoreManager.getCollisionCtrl(); }
    public LevelBuilder getLevelBuilder()            { return levelCoreManager.getLevelBuilder(); }
    public SnapshotService getSnapshotSvc()          { return snapshotCoreController.getSnapshotSvc(); }
    public SimulationRegistrar getRegistrar()        { return simulationCoreManager.getRegistrar(); }
    public List<SystemBoxModel> getBoxes()           { return levelCoreManager.getBoxes(); }
    public PacketRenderController getPacketRenderer(){ return simulationCoreManager.getPacketRenderer(); }
    public LevelManager getLevelManager()            { return levelManager; }
    public WireCreationController getWireCreator()   { return wireCreator; }
    public LevelDefinition getCurrentDef()           { return levelCoreManager.getCurrentDef(); }

    public List<WireModel> getWires()                { return simulationCoreManager.getWires(); }
    public SimulationController getSimulation()      { return simulationCoreManager.getSimulation(); }
    public CoinModel getCoinModel()                  { return simulationCoreManager.getCoinModel(); }
    public PacketLossModel getLossModel()            { return simulationCoreManager.getLossModel(); }
    public ScoreModel getScoreModel()                { return simulationCoreManager.getScoreModel(); }
    public HudController getHudController()          { return hudController; }
    public PacketProducerController getProducerController() { return simulationCoreManager.getProducerController(); }

    /* ================================================================ */
    /*                      Dependency injection                        */
    /* ================================================================ */
    public void setHudController(HudController hudController)                 { this.hudController = hudController; }
    public void setShopController(ShopController shopController)             { this.shopController = shopController; }
    public void setSnapshotSvc(SnapshotService snapshotSvc)                  { this.snapshotCoreController.setSnapshotSvc(snapshotSvc); }
    public void setRegistrar(SimulationRegistrar registrar)                  { simulationCoreManager.setRegistrar(registrar); }
    public void setPacketRenderer(PacketRenderController packetRenderer)     { simulationCoreManager.setPacketRenderer(packetRenderer); }
    public void setProducerController(PacketProducerController producerCtrl) { simulationCoreManager.setProducerController(producerCtrl); }
    public void setWireCreator(WireCreationController wireCreator)           { this.wireCreator = wireCreator; }
}

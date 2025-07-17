package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.level.LevelGenerator;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.model.*;
import com.blueprinthell.view.HudView;
import com.blueprinthell.view.screens.GameScreenView;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Facade class that orchestrates high‑level flow, delegating detailed work to
 * helper services extracted from the original monolithic version.
 */
public class GameController implements NetworkController {
    private ScreenController screenController;

    /* ================================================================ */
    /*                 Core, shared through entire app                  */
    /* ================================================================ */
    private final SimulationController simulation = new SimulationController(60);
    private final TimelineController   timeline   = new TimelineController(this, 1000);

    private final ScoreModel      scoreModel  = new ScoreModel();
    private final CoinModel       coinModel   = new CoinModel();
    private final PacketLossModel lossModel   = new PacketLossModel();
    private final WireUsageModel  usageModel  = new WireUsageModel(1000.0);
    private final SnapshotManager snapshotMgr = new SnapshotManager();

    /* -------------------- UI & Controllers -------------------- */
    private final JFrame         mainFrame;
    private final HudView        hudView;
    private final GameScreenView gameView;
    private HudController        hudController;
    private final HudCoordinator hudCoord;
    private ShopController       shopController;

    /* -------------------- Wiring -------------------- */
    private final List<WireModel>                 wires   = new CopyOnWriteArrayList<>();
    private final Map<WireModel, SystemBoxModel>  destMap = new HashMap<>();

    /* -------------------- Services -------------------- */
    private final CollisionController collisionCtrl;
    private final LevelBuilder        levelBuilder;
    private SnapshotService           snapshotSvc;
    private SimulationRegistrar       registrar;

    /* -------------------- Runtime per‑level -------------------- */
    private List<SystemBoxModel>   boxes = Collections.emptyList();
    private PacketRenderController packetRenderer;
    private LevelManager           levelManager;

    /* ================================================================ */
    public GameController(JFrame mainFrame) {
        this.mainFrame = mainFrame;
        /* Build HUD + Game view */
        this.hudView  = new HudView(0, 0, 800, 50);
        this.gameView = new GameScreenView(hudView);

        /* Core controllers */
        this.collisionCtrl = new CollisionController(wires, lossModel);
        this.levelBuilder  = new LevelBuilder(gameView, wires, usageModel);

        /* HUD ↔ models */
        this.hudCoord = new HudCoordinator(hudView, scoreModel, coinModel, lossModel, simulation, timeline);

        /* Timeline */
        simulation.setTimelineController(timeline);

        /* Key navigation (← / →) */
        gameView.setTemporalNavigationListener(this::onNavigateTime);
    }

    /* ---------------- Temporal navigation handler ---------------- */
    /* ---------------- Temporal navigation handler ---------------- */
    /**
     * dir = -1 ⟹ یک فریم به عقب، dir = +1 ⟹ یک فریم به جلو.
     * <p>هنگام اسکراب باید <b>حلقهٔ شبیه‌سازی</b> متوقف بماند تا تولیدکنندهٔ پکت‌ها
     * دوباره فعال نشود؛ در غیر این صورت پس از بازگشت پکت به مبدأ، Producer مثل
     * «شروع جدید» رفتار می‌کند و بسته‌های تکراری می‌سازد.</p>
     */
    private void onNavigateTime(int dir) {
        if (timeline.isPlaying()) return;           // فقط در حالت Pause مجاز است
        simulation.stop();                          // اطمینان: هیچ Updatable اجرا نشود

        int target = timeline.getCurrentOffset() + (dir < 0 ? 1 : -1);
        timeline.scrubTo(target);

        gameView.requestFocusInWindow();            // حفظ فوکوس
    }

    /* ---------------- Level management ---------------- */
    public void setLevelManager(LevelManager mgr) { this.levelManager = mgr; }
    public void startLevel(int idx) { LevelDefinition def = LevelGenerator.firstLevel(); for (int i=1;i<idx;i++) def = LevelGenerator.nextLevel(def); startLevel(def);}
    public void startLevel(LevelDefinition def) {
        if (levelManager == null) throw new IllegalStateException("LevelManager must be set before starting level");
        /* Reset */
        simulation.stop(); simulation.clearUpdatables();
        scoreModel.reset(); coinModel.reset(); lossModel.reset(); snapshotMgr.clear();
        timeline.resume(); usageModel.reset(def.totalWireLength());
        wires.clear(); destMap.clear();

        /* Build */
        boxes = levelBuilder.build(def);
        buildWireControllers();

        /* Sources & sink */
        List<SystemBoxModel> sources = new ArrayList<>(); SystemBoxModel sink=null;
        for(int i=0;i<def.boxes().size();i++){var spec=def.boxes().get(i);var box=boxes.get(i); if(spec.isSource())sources.add(box); if(spec.isSink())sink=box;}

        int count = Config.PACKETS_PER_PORT * (levelManager.getLevelIndex()+1);
        PacketProducerController producer = new PacketProducerController(sources, wires, destMap, Config.DEFAULT_PACKET_SPEED, count);
        hudCoord.wireLevel(producer);
        simulation.register(producer);
        packetRenderer = new PacketRenderController(gameView.getGameArea(), wires);

        this.hudController = new HudController(usageModel, lossModel, coinModel, levelManager, hudView);
        this.shopController = new ShopController(mainFrame, simulation, coinModel, collisionCtrl, lossModel, wires, hudController);
        hudView.getStoreButton().addActionListener(e -> shopController.openShop());

        snapshotSvc = new SnapshotService(boxes, wires, scoreModel, coinModel, lossModel, usageModel, snapshotMgr, hudView, gameView, packetRenderer, List.of(producer));
        registrar = new SimulationRegistrar(simulation, null, collisionCtrl, packetRenderer, scoreModel, coinModel, lossModel, usageModel, snapshotMgr, hudView, levelManager);

        List<Updatable> systemControllers = new ArrayList<>(); systemControllers.add(hudController);
        registrar.registerAll(boxes, wires, destMap, sources, sink, producer, systemControllers);
        updateStartEnabled();
    }

    private void buildWireControllers() {
        WireCreationController creator = new WireCreationController(gameView, simulation, boxes, wires, destMap, usageModel, coinModel, this::updateStartEnabled);
        new WireRemovalController(gameView, wires, destMap, creator, usageModel, this::updateStartEnabled);
    }

    private void updateStartEnabled() {
        boolean allConnected = boxes.stream().allMatch(b -> b.getInPorts().stream().allMatch(this::isPortConnected) && b.getOutPorts().stream().allMatch(this::isPortConnected));
        hudCoord.setStartEnabled(allConnected);
    }
    private boolean isPortConnected(PortModel p){return wires.stream().anyMatch(w->w.getSrcPort()==p||w.getDstPort()==p);}

    /* ---------------- Snapshot API ---------------- */
    @Override public NetworkSnapshot captureSnapshot(){return snapshotSvc.buildSnapshot();}
    @Override public void restoreState(NetworkSnapshot snap){snapshotSvc.restore(snap);}

    /* ---------------- Getters ---------------- */
    public GameScreenView getGameView(){return gameView;} public List<WireModel> getWires(){return wires;} public SimulationController getSimulation(){return simulation;} public CoinModel getCoinModel(){return coinModel;} public CollisionController getCollisionController(){return collisionCtrl;} public PacketLossModel getLossModel(){return lossModel;} public ScoreModel getScoreModel(){return scoreModel;} public HudController getHudController(){return hudController;} public void setScreenController(ScreenController sc){this.screenController=sc;}
}

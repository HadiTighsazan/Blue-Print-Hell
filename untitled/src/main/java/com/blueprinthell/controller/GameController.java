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

/**
 * Facade class that orchestrates high‑level flow, delegating detailed work to
 * helper services extracted from the original monolithic version:
 * <ul>
 *     <li>{@link LevelBuilder} – ایجاد مدل و ویوی جعبه‌ها</li>
 *     <li>{@link SimulationRegistrar} – ثبت همهٔ Updatable ها در شبیه‌ساز</li>
 *     <li>{@link SnapshotService} – مدیریت Capture/Restore شبکه برای Time‑Travel</li>
 *     <li>{@link HudCoordinator} – همگام‌سازی دکمه‌ها و لیبل‌های HUD</li>
 * </ul>
 */
public class GameController implements NetworkController {

    /* ================================================================ */
    /*                 Core, shared through entire app                  */
    /* ================================================================ */
    private final SimulationController simulation = new SimulationController(60);
    private final TimelineController   timeline   = new TimelineController(this, 1000);

    private final ScoreModel      scoreModel = new ScoreModel();
    private final CoinModel       coinModel  = new CoinModel();
    private final PacketLossModel lossModel  = new PacketLossModel();
    private final WireUsageModel  usageModel = new WireUsageModel(1000.0);
    private final SnapshotManager snapshotMgr= new SnapshotManager();

    /* -------------------- UI layer roots -------------------- */
    private final HudView         hudView   = new HudView(0,0,800,50);
    private final GameScreenView  gameView  = new GameScreenView(hudView);
    private final ScreenController screenCtrl;

    /* -------------------- Wiring -------------------- */
    private final List<WireModel> wires   = new ArrayList<>();
    private final Map<WireModel, SystemBoxModel> destMap = new HashMap<>();

    /* -------------------- Services -------------------- */
    private final CollisionController collisionCtrl = new CollisionController(wires, lossModel);
    private final HudCoordinator     hudCoord;
    private final LevelBuilder       levelBuilder;
    private SnapshotService          snapshotSvc;      // per‑level renderer injected later
    private SimulationRegistrar      registrar;        // per‑level because of renderer

    /* -------------------- Runtime per‑level -------------------- */
    private List<SystemBoxModel> boxes = Collections.emptyList();
    private PacketRenderController packetRenderer;
    private LevelManager levelManager;

    /* ================================================================ */
    public GameController(ScreenController sc) {
        this.screenCtrl = sc;
        sc.registerGameScreen(gameView);
        simulation.setTimelineController(timeline);

        levelBuilder = new LevelBuilder(gameView, wires, usageModel);
        hudCoord     = new HudCoordinator(hudView, scoreModel, coinModel, lossModel, simulation, timeline);
    }

    /* ================================================================ */
    /*                         Level lifecycle                          */
    /* ================================================================ */
    public void startLevel(int idx) {
        LevelDefinition def = LevelGenerator.firstLevel();
        for (int i = 1; i < idx; i++) def = LevelGenerator.nextLevel(def);
        startLevel(def);
    }

    public void startLevel(LevelDefinition def) {
        /* ---------- reset global state ---------- */
        simulation.stop();
        simulation.clearUpdatables();
        scoreModel.reset(); coinModel.reset(); lossModel.reset();
        snapshotMgr.clear();
        timeline.resume();
        usageModel.reset(def.totalWireLength());
        wires.clear(); destMap.clear();

        /* ---------- build static part ---------- */
        boxes = levelBuilder.build(def);
        buildWireControllers();

        /* ---------- dynamic controllers ---------- */
        // Identify source & sink for Producer/Consumer setup
        List<SystemBoxModel> sources = new ArrayList<>();
        SystemBoxModel sink = null;
        for (int i = 0; i < def.boxes().size(); i++) {
            var spec = def.boxes().get(i);
            var box  = boxes.get(i);
            if (spec.isSource()) sources.add(box);
            if (spec.isSink())   sink = box;
        }
        int packetsPerPort = Config.PACKETS_PER_PORT * (levelManager.getLevelIndex()+1);
        PacketProducerController producer = new PacketProducerController(sources, wires, destMap,
                Config.DEFAULT_PACKET_SPEED, packetsPerPort);

        /* Packet renderer depends on wires generated at runtime */
        packetRenderer = new PacketRenderController(gameView.getGameArea(), wires);

        List<PacketProducerController> producers = List.of(producer);

        /* Snapshot service (needs renderer for refresh) */
        snapshotSvc = new SnapshotService(boxes, wires, scoreModel, coinModel, lossModel,
                usageModel, snapshotMgr, hudView, gameView, packetRenderer,producers);

        /* Registrar for Updatable stack */
        registrar = new SimulationRegistrar(simulation, screenCtrl, collisionCtrl, packetRenderer,
                scoreModel, coinModel, lossModel, usageModel, snapshotMgr, hudView, levelManager);
        registrar.registerAll(boxes, wires, destMap, sources, sink, producer);

        /* HUD wiring */
        hudCoord.wireLevel(producer);
        updateStartEnabled();

        /* Time-scrub listener */
        gameView.setTemporalNavigationListener(dir -> {
            simulation.stop();
            if (dir < 0) timeline.scrubTo(timeline.getCurrentOffset()+1);
            else if (dir > 0) timeline.scrubTo(timeline.getCurrentOffset()-1);
            SwingUtilities.invokeLater(() -> packetRenderer.refreshAll());
            hudCoord.refresh();
        });
    }

    /* ---------------- Wire‑creation / removal enable Start button --------------- */
    private void buildWireControllers() {
        WireCreationController creator = new WireCreationController(gameView, simulation, boxes,
                wires, destMap, usageModel, this::updateStartEnabled);
        new WireRemovalController(gameView, wires, destMap, creator, usageModel, this::updateStartEnabled);
    }

    /** Enable Start only when every port has a wire. */
    private void updateStartEnabled() {
        boolean allConnected = boxes.stream().allMatch(b ->
                b.getInPorts().stream().allMatch(this::isPortConnected) &&
                        b.getOutPorts().stream().allMatch(this::isPortConnected));
        hudCoord.setStartEnabled(allConnected);
    }
    private boolean isPortConnected(PortModel p){return wires.stream().anyMatch(w->w.getSrcPort()==p || w.getDstPort()==p);}

    /* ================================================================ */
    /*             NetworkController – Snapshot passthrough             */
    /* ================================================================ */
    @Override public NetworkSnapshot captureSnapshot(){return snapshotSvc.buildSnapshot();}
    @Override public void restoreState(NetworkSnapshot snap){snapshotSvc.restore(snap);}

    /* ================================================================ */
    /*                              Getters                             */
    /* ================================================================ */
    public void setLevelManager(LevelManager mgr){this.levelManager = mgr;}
    public GameScreenView getGameView(){return gameView;}
    public List<WireModel> getWires(){return wires;}
    public SimulationController getSimulation(){return simulation;}
    public CoinModel getCoinModel(){return coinModel;}
    public CollisionController getCollisionController(){return collisionCtrl;}
    public PacketLossModel getLossModel(){return lossModel;}
    public ScoreModel getScoreModel(){return scoreModel;}
}

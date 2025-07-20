package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.level.LevelGenerator;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.model.*;
import com.blueprinthell.view.HudView;
import com.blueprinthell.view.WireView;
import com.blueprinthell.view.screens.GameScreenView;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameController implements NetworkController {

    /* ================================================================ */
    /*                      Core UI & controllers                       */
    /* ================================================================ */
    private ScreenController              screenController;

    // Main simulation loop & time navigation
    private final SimulationController    simulation   = new SimulationController(60);
    private final TimelineController      timeline     = new TimelineController(this, 1000);

    /* ================================================================ */
    /*                           Game models                            */
    /* ================================================================ */
    private final ScoreModel              scoreModel   = new ScoreModel();
    private final CoinModel               coinModel    = new CoinModel();
    private final PacketLossModel         lossModel    = new PacketLossModel();
    private final WireUsageModel          usageModel   = new WireUsageModel(1000.0);
    private final SnapshotManager         snapshotMgr  = new SnapshotManager();

    /* ================================================================ */
    /*                    Swing views (immutable roots)                 */
    /* ================================================================ */
    private final JFrame                  mainFrame;
    private final HudView                 hudView;
    private final GameScreenView          gameView;

    /* ================================================================ */
    /*                 HUD / game‑state coordination helpers            */
    /* ================================================================ */
    private HudController                 hudController;
    private final HudCoordinator          hudCoord;
    private ShopController                shopController;

    /* ================================================================ */
    /*                Runtime collections shared across layers          */
    /* ================================================================ */
    private final List<WireModel>         wires        = new CopyOnWriteArrayList<>();
    private final Map<WireModel, SystemBoxModel> destMap = new HashMap<>();

    /* ================================================================ */
    /*                   Engine‑level controllers & helpers             */
    /* ================================================================ */
    private final CollisionController     collisionCtrl;
    private final LevelBuilder            levelBuilder;
    private SnapshotService               snapshotSvc;
    private SimulationRegistrar           registrar;

    /* ================================================================ */
    /*                Per‑level transient state & controllers           */
    /* ================================================================ */
    private List<SystemBoxModel>          boxes         = new ArrayList<>();
    private PacketRenderController        packetRenderer;
    private LevelManager                  levelManager;
    private PacketProducerController      producerController;

    // *** NEW: keep reference to creator to free ports when purging ***
    private WireCreationController        wireCreator;

    /* ================================================================ */
    /*                               Ctor                               */
    /* ================================================================ */
    public GameController(JFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.hudView   = new HudView(0, 0, 800, 50);
        this.gameView  = new GameScreenView(hudView);

        this.collisionCtrl = new CollisionController(wires, lossModel);
        this.levelBuilder  = new LevelBuilder(gameView, wires, usageModel);

        this.hudCoord = new HudCoordinator(hudView, scoreModel, coinModel, lossModel, simulation, timeline);

        simulation.setTimelineController(timeline);

        gameView.setTemporalNavigationListener(this::onNavigateTime);
    }

    /* --------------------------------------------------------------- */
    /*                 Timeline scrubbing with keyboard                */
    /* --------------------------------------------------------------- */
    private void onNavigateTime(int dir) {
        if (timeline.isPlaying()) return;
        simulation.stop();
        int target = timeline.getCurrentOffset() + (dir < 0 ? 1 : -1);
        timeline.scrubTo(target);
        gameView.requestFocusInWindow();
    }

    /* --------------------------------------------------------------- */
    /*                    External setter (DI‑style)                   */
    /* --------------------------------------------------------------- */
    public void setLevelManager(LevelManager mgr) {
        this.levelManager = mgr;
    }

    /* --------------------------------------------------------------- */
    /*               Public API to load a level by index               */
    /* --------------------------------------------------------------- */
    public void startLevel(int idx) {
        LevelDefinition def = LevelGenerator.firstLevel();
        for (int i = 1; i < idx; i++) {
            def = LevelGenerator.nextLevel(def);
        }
        startLevel(def);
    }

    /* --------------------------------------------------------------- */
    /*                Core level bootstrap (fresh start)               */
    /* --------------------------------------------------------------- */
    public void startLevel(LevelDefinition def) {
        if (levelManager == null) {
            throw new IllegalStateException("LevelManager must be set before starting level");
        }

        /* ------------------------------------------------------------------ */
        /*  Fresh game (index 0) ⇒ full reset of boxes & wires collections   */
        /* ------------------------------------------------------------------ */
        // When a brand‑new game is launched from the main menu we may still
        // be holding onto boxes / wires from a previous run (e.g. after the
        // player quit to menu without restarting the JVM).  Starting level 1
        // with those leftovers causes size mismatch (ArrayIndexOutOfBounds).
        // If the LevelManager index is 0 we assume a fresh run and wipe all
        // transient collections so the engine can rebuild cleanly.
        if (levelManager.getLevelIndex() == 0) {
            boxes.clear();
            wires.clear();
            destMap.clear();
        }

        /* More defensively, ensure boxes list never exceeds definition size  */
        if (boxes.size() > def.boxes().size()) {
            boxes = new ArrayList<>(boxes.subList(0, def.boxes().size()));
        }

        /* ----- hard reset sim & HUD for new stage ----- */
        if (levelManager == null) {
            throw new IllegalStateException("LevelManager must be set before starting level");
        }

        /* ----- hard reset sim & HUD for new stage ----- */
        simulation.stop();
        simulation.clearUpdatables();

        scoreModel.reset();
        coinModel.reset();
        lossModel.reset();
        snapshotMgr.clear();
        timeline.resume();

        usageModel.reset(def.totalWireLength());

        /* ----- build / reuse system boxes ----- */
        boxes = levelBuilder.build(def, boxes);

        /* ----- mark existing wires as immutable (carry‑over) ----- */
        for (WireModel w : wires) {
            w.setForPreviousLevels(true);
        }

        /* ----- controllers that depend on boxes/wires ----- */
        buildWireControllers();

        /* ----- discover sources & sink ----- */
        List<SystemBoxModel> sources = new ArrayList<>();
        SystemBoxModel sink = null;
        for (int i = 0; i < boxes.size(); i++) {
            LevelDefinition.BoxSpec spec = def.boxes().get(i);
            SystemBoxModel          box  = boxes.get(i);
            if (spec.isSource()) { sources.add(box); }
            if (spec.isSink())   { sink = box;     }
        }

        WireModel.setSourceInputPorts(sources);
        WireModel.setSimulationController(simulation);

        /* ----- packet planning for HUD & loss monitor ----- */
        int stageIndex     = levelManager.getLevelIndex() + 1;
        int perPortCount   = Config.PACKETS_PER_PORT * stageIndex;
        int totalOutPorts  = sources.stream().mapToInt(b -> b.getOutPorts().size()).sum();
        int plannedPackets = perPortCount * totalOutPorts;

        producerController = new PacketProducerController(
                sources, wires, destMap,
                Config.DEFAULT_PACKET_SPEED,
                perPortCount);
        hudCoord.wireLevel(producerController);
        simulation.setPacketProducerController(producerController);

        /* ----- loss monitor that can restart stage on failure ----- */
        LossMonitorController lossCtrl = new LossMonitorController(
                lossModel,
                plannedPackets,
                0.5,
                simulation,
                screenController,
                () -> retryLevel(def)               // ✨ changed
        );
        simulation.register(lossCtrl);

        /* ----- view & HUD plumbing ----- */
        packetRenderer = new PacketRenderController(gameView.getGameArea(), wires);

        hudController = new HudController(usageModel, lossModel, coinModel, levelManager, hudView);
        shopController = new ShopController(
                mainFrame, simulation, coinModel, collisionCtrl, lossModel, wires, hudController);
        hudView.getStoreButton().addActionListener(e -> shopController.openShop());

        /* ----- snapshot service ----- */
        snapshotSvc = new SnapshotService(
                boxes, wires, scoreModel, coinModel, lossModel, usageModel, snapshotMgr,
                hudView, gameView, packetRenderer, List.of(producerController));

        registrar = new SimulationRegistrar(
                simulation, null, collisionCtrl, packetRenderer, scoreModel, coinModel,
                lossModel, usageModel, snapshotMgr, hudView, levelManager);

        /* ----- register everything ----- */
        List<Updatable> systemControllers = new ArrayList<>();
        systemControllers.add(hudController);
        registrar.registerAll(boxes, wires, destMap, sources, sink, producerController, systemControllers);

        updateStartEnabled();
    }

    /* --------------------------------------------------------------- */
    /*              Build controllers for wire creation/removal        */
    /* --------------------------------------------------------------- */
    private void buildWireControllers() {
        WireCreationController creator = new WireCreationController(
                gameView, simulation, boxes, wires, destMap, usageModel, coinModel, this::updateStartEnabled);
        // keep reference for port‑freeing on purge
        this.wireCreator = creator;

        new WireRemovalController(
                gameView, wires, destMap, creator, usageModel, this::updateStartEnabled);
    }

    /* --------------------------------------------------------------- */
    /*                  Enable / disable “Start” button                */
    /* --------------------------------------------------------------- */
    private void updateStartEnabled() {
        boolean allConnected = boxes.stream().allMatch(b ->
                b.getInPorts().stream().allMatch(this::isPortConnected) &&
                        b.getOutPorts().stream().allMatch(this::isPortConnected));
        hudCoord.setStartEnabled(allConnected);
    }

    private boolean isPortConnected(PortModel p) {
        return wires.stream().anyMatch(w -> w.getSrcPort() == p || w.getDstPort() == p);
    }

    /* ================================================================ */
    /*                    ***   Retry logic  ***                        */
    /* ================================================================ */

    /**
     * Remove only the wires drawn in the *current* level (those not yet
     * tagged as {@code forPreviousLevels}), free their port capacity and
     * wire‑length quota, then refresh HUD.
     */
    private void purgeCurrentLevelWires() {
        JPanel area = gameView.getGameArea();

        // Collect wires that belong to the current level
        List<WireModel> toRemove = new ArrayList<>();
        for (WireModel w : wires) {
            if (!w.isForPreviousLevels()) {
                if (wireCreator != null) {
                    wireCreator.freePortsForWire(w);
                }
                usageModel.freeWire(w.getLength());
                destMap.remove(w);
                toRemove.add(w);
            }
        }

        if (toRemove.isEmpty()) return; // nothing to do

        /* ----- detach visuals from Swing hierarchy ----- */
        Component[] comps = area.getComponents();
        for (Component c : comps) {
            if (c instanceof WireView wv) {
                if (toRemove.contains(wv.getModel())) {
                    area.remove(c);
                }
            }
        }

        // Remove models after visuals to avoid sync issues
        wires.removeAll(toRemove);

        /* ----- refresh UI ----- */
        area.revalidate();
        area.repaint();
        if (hudController != null) hudController.refreshOnce();
    }

    /**
     * Called by LossMonitor when player clicks “Retry”.
     * Stops simulation, purges current‑level wires, then restarts the same
     * LevelDefinition from scratch while preserving carry‑over circuits.
     */
    private void retryLevel(LevelDefinition def) {
        simulation.stop();
        purgeCurrentLevelWires();
        startLevel(def);
    }

    /* ================================================================ */
    /*                NetworkController interface methods               */
    /* ================================================================ */
    @Override
    public NetworkSnapshot captureSnapshot() {
        return snapshotSvc.buildSnapshot();
    }

    @Override
    public void restoreState(NetworkSnapshot snap) {
        snapshotSvc.restore(snap);
    }

    /* ================================================================ */
    /*                 Getters for other controllers                    */
    /* ================================================================ */
    public GameScreenView getGameView() { return gameView; }
    public List<WireModel> getWires()  { return wires; }
    public SimulationController getSimulation() { return simulation; }
    public CoinModel getCoinModel() { return coinModel; }
    public CollisionController getCollisionController() { return collisionCtrl; }
    public PacketLossModel getLossModel() { return lossModel; }
    public ScoreModel getScoreModel() { return scoreModel; }
    public HudController getHudController() { return hudController; }
    public PacketProducerController getProducerController() { return producerController; }

    public void setScreenController(ScreenController sc) { this.screenController = sc; }
}

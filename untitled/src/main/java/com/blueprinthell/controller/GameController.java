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

public class GameController implements NetworkController {
    private ScreenController screenController;

    private final SimulationController simulation = new SimulationController(60);
    private final TimelineController timeline = new TimelineController(this, 1000);

    private final ScoreModel scoreModel = new ScoreModel();
    private final CoinModel coinModel = new CoinModel();
    private final PacketLossModel lossModel = new PacketLossModel();
    private final WireUsageModel usageModel = new WireUsageModel(1000.0);
    private final SnapshotManager snapshotMgr = new SnapshotManager();

    private final JFrame mainFrame;
    private final HudView hudView;
    private final GameScreenView gameView;
    private HudController hudController;
    private final HudCoordinator hudCoord;
    private ShopController shopController;

    private final List<WireModel> wires = new CopyOnWriteArrayList<>();
    private final Map<WireModel, SystemBoxModel> destMap = new HashMap<>();

    private final CollisionController collisionCtrl;
    private final LevelBuilder levelBuilder;
    private SnapshotService snapshotSvc;
    private SimulationRegistrar registrar;

    private List<SystemBoxModel> boxes = new ArrayList<>();
    private PacketRenderController packetRenderer;
    private LevelManager levelManager;

    private PacketProducerController producerController;

    public GameController(JFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.hudView = new HudView(0, 0, 800, 50);
        this.gameView = new GameScreenView(hudView);

        this.collisionCtrl = new CollisionController(wires, lossModel);
        this.levelBuilder = new LevelBuilder(gameView, wires, usageModel);

        this.hudCoord = new HudCoordinator(hudView, scoreModel, coinModel, lossModel, simulation, timeline);

        simulation.setTimelineController(timeline);

        gameView.setTemporalNavigationListener(this::onNavigateTime);
    }

    private void onNavigateTime(int dir) {
        if (timeline.isPlaying()) return;
        simulation.stop();
        int target = timeline.getCurrentOffset() + (dir < 0 ? 1 : -1);
        timeline.scrubTo(target);
        gameView.requestFocusInWindow();
    }

    public void setLevelManager(LevelManager mgr) {
        this.levelManager = mgr;
    }

    public void startLevel(int idx) {
        LevelDefinition def = LevelGenerator.firstLevel();
        for (int i = 1; i < idx; i++) {
            def = LevelGenerator.nextLevel(def);
        }
        startLevel(def);
    }

    public void startLevel(LevelDefinition def) {
        if (levelManager == null) {
            throw new IllegalStateException("LevelManager must be set before starting level");
        }

        simulation.stop();
        simulation.clearUpdatables();

        scoreModel.reset();
        coinModel.reset();
        lossModel.reset();
        snapshotMgr.clear();
        timeline.resume();

        usageModel.reset(def.totalWireLength());

        // Build level preserving previous boxes
        boxes = levelBuilder.build(def, boxes);

        // Tag wires from previous levels as immutable
        for (WireModel w : wires) {
            w.setForPreviousLevels(true);
        }

        buildWireControllers();

        List<SystemBoxModel> sources = new ArrayList<>();
        SystemBoxModel sink = null;
        for (int i = 0; i < boxes.size(); i++) {
            LevelDefinition.BoxSpec spec = def.boxes().get(i);
            SystemBoxModel box = boxes.get(i);
            if (spec.isSource()) {
                sources.add(box);
            }
            if (spec.isSink()) {
                sink = box;
            }
        }

        WireModel.setSourceInputPorts(sources);
        WireModel.setSimulationController(simulation);

        int stageIndex = levelManager.getLevelIndex() + 1;
        int perPortCount = Config.PACKETS_PER_PORT * stageIndex;
        int totalOutPorts = sources.stream()
                .mapToInt(b -> b.getOutPorts().size())
                .sum();
        int plannedPackets = perPortCount * totalOutPorts;

        producerController = new PacketProducerController(
                sources, wires, destMap,
                Config.DEFAULT_PACKET_SPEED,
                perPortCount);
        hudCoord.wireLevel(producerController);
        simulation.setPacketProducerController(producerController);

        LossMonitorController lossCtrl = new LossMonitorController(
                lossModel,
                plannedPackets,
                0.5,
                simulation,
                screenController,
                () -> startLevel(def)
        );
        simulation.register(lossCtrl);

        packetRenderer = new PacketRenderController(gameView.getGameArea(), wires);

        hudController = new HudController(usageModel, lossModel, coinModel, levelManager, hudView);
        shopController = new ShopController(
                mainFrame, simulation, coinModel, collisionCtrl, lossModel, wires, hudController
        );
        hudView.getStoreButton().addActionListener(e -> shopController.openShop());

        snapshotSvc = new SnapshotService(
                boxes, wires, scoreModel, coinModel, lossModel, usageModel, snapshotMgr,
                hudView, gameView, packetRenderer, List.of(producerController)
        );

        registrar = new SimulationRegistrar(
                simulation, null, collisionCtrl, packetRenderer, scoreModel, coinModel,
                lossModel, usageModel, snapshotMgr, hudView, levelManager
        );

        List<Updatable> systemControllers = new ArrayList<>();
        systemControllers.add(hudController);
        registrar.registerAll(boxes, wires, destMap, sources, sink, producerController, systemControllers);

        updateStartEnabled();
    }

    private void buildWireControllers() {
        WireCreationController creator = new WireCreationController(
                gameView, simulation, boxes, wires, destMap, usageModel, coinModel, this::updateStartEnabled
        );
        new WireRemovalController(gameView, wires, destMap, creator, usageModel, this::updateStartEnabled);
    }

    private void updateStartEnabled() {
        boolean allConnected = boxes.stream()
                .allMatch(b -> b.getInPorts().stream().allMatch(this::isPortConnected)
                        && b.getOutPorts().stream().allMatch(this::isPortConnected));
        hudCoord.setStartEnabled(allConnected);
    }

    private boolean isPortConnected(PortModel p) {
        return wires.stream().anyMatch(w -> w.getSrcPort() == p || w.getDstPort() == p);
    }

    @Override
    public NetworkSnapshot captureSnapshot() {
        return snapshotSvc.buildSnapshot();
    }

    @Override
    public void restoreState(NetworkSnapshot snap) {
        snapshotSvc.restore(snap);
    }

    public GameScreenView getGameView() {
        return gameView;
    }

    public List<WireModel> getWires() {
        return wires;
    }

    public SimulationController getSimulation() {
        return simulation;
    }

    public CoinModel getCoinModel() {
        return coinModel;
    }

    public CollisionController getCollisionController() {
        return collisionCtrl;
    }

    public PacketLossModel getLossModel() {
        return lossModel;
    }

    public ScoreModel getScoreModel() {
        return scoreModel;
    }

    public HudController getHudController() {
        return hudController;
    }

    public void setScreenController(ScreenController sc) {
        this.screenController = sc;
    }

    public PacketProducerController getProducerController() {
        return producerController;
    }
}

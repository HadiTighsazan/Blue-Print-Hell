package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;
import com.blueprinthell.view.HudView;
import com.blueprinthell.view.SystemBoxView;
import com.blueprinthell.view.screens.GameScreenView;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.level.LevelCompletionDetector;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.level.LevelGenerator;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Core controller orchestrating game logic for each level.
 */
public class GameController implements NetworkController {

    /* ----------------- Persistent controllers & models ----------------- */
    private final SimulationController simulation = new SimulationController(60);
    private final TimelineController timelineController = new TimelineController(this, 1000);
    private final WireUsageModel usageModel = new WireUsageModel(1000.0);
    private final PacketLossModel lossModel = new PacketLossModel();
    private final SnapshotManager snapshotManager = new SnapshotManager();

    private final ScoreModel scoreModel = new ScoreModel();
    private final CoinModel  coinModel  = new CoinModel();

    private final HudView hudView = new HudView(0, 0, 800, 50);
    private final GameScreenView gameView = new GameScreenView(hudView);

    private final List<WireModel> wires = new ArrayList<>();
    private final Map<WireModel, SystemBoxModel> destMap = new HashMap<>();
    private final CollisionController collisionController = new CollisionController(wires, lossModel);

    private final ScreenController screenController;
    private PacketRenderController packetRenderController;
    private List<SystemBoxModel> boxes;
    private LevelManager levelManager;

    /* --------------------- Constructor --------------------- */
    public GameController(ScreenController screenController) {
        this.screenController = screenController;
        screenController.registerGameScreen(gameView);
        simulation.setTimelineController(timelineController);
    }

    /* =================================================================== */
    /*                          LEVEL LIFECYCLE                            */
    /* =================================================================== */

    public void startLevel(LevelDefinition def) {
        // 1. Stop and clear previous simulation
        simulation.stop();
        simulation.clearUpdatables();

        // 2. Reset runtime models
        scoreModel.reset();
        coinModel.reset();
        lossModel.reset();
        snapshotManager.clear();
        timelineController.resume();
        usageModel.reset(def.totalWireLength());

        hudView.getStartButton().setEnabled(true);
        hudView.setToggleText("Pause");

        // 3. Re‑build level content
        wires.clear();
        destMap.clear();

        buildBoxes(def);
        buildWireControllers();

        // 4. Build and register simulation controllers for this level
        registerSimulationControllers(def);

        // 5. Initial HUD refresh
        updateStartEnabled();
    }

    public void startLevel(int index) {
        LevelDefinition def = LevelGenerator.firstLevel();
        for (int i = 1; i < index; i++) def = LevelGenerator.nextLevel(def);
        startLevel(def);
    }

    /* --------------------- Helpers --------------------- */
    private void buildBoxes(LevelDefinition def) {
        boxes = new ArrayList<>();
        for (LevelDefinition.BoxSpec spec : def.boxes()) {
            boxes.add(new SystemBoxModel(spec.x(), spec.y(), spec.width(), spec.height(),
                    spec.inShapes(), spec.outShapes()));
        }
        gameView.reset(boxes, wires);

        // Attach drag controllers
        for (Component c : gameView.getGameArea().getComponents()) {
            if (c instanceof SystemBoxView sbv) {
                new SystemBoxDragController(sbv.getModel(), sbv, wires, usageModel);
            }
        }
    }

    private void buildWireControllers() {
        WireCreationController creator = new WireCreationController(
                gameView, simulation, boxes, wires, destMap, usageModel, this::updateStartEnabled);
        new WireRemovalController(gameView, wires, destMap, creator, usageModel, this::updateStartEnabled);
    }

    private void registerSimulationControllers(LevelDefinition def) {
        // Identify sources & sink
        List<SystemBoxModel> sources = new ArrayList<>();
        SystemBoxModel sink = null;
        for (int i = 0; i < def.boxes().size(); i++) {
            var spec = def.boxes().get(i);
            var box  = boxes.get(i);
            if (spec.isSource()) sources.add(box);
            if (spec.isSink())   sink = box;
        }

        int packetsPerPort = Config.PACKETS_PER_PORT * (levelManager.getLevelIndex() + 1);
        var producer = new PacketProducerController(sources, wires, destMap,
                Config.DEFAULT_PACKET_SPEED, packetsPerPort);

        // ----- register order matters -----
        simulation.register(producer);
        simulation.register(new PacketDispatcherController(wires, destMap, coinModel, lossModel));
        if (sink != null)
            simulation.register(new PacketConsumerController(sink, scoreModel, coinModel));

        boxes.stream()
                .filter(b -> !b.getInPorts().isEmpty() && !b.getOutPorts().isEmpty())
                .forEach(b -> simulation.register(new PacketRouterController(b, wires, destMap, lossModel)));

        packetRenderController = new PacketRenderController(gameView.getGameArea(), wires);
        simulation.register(packetRenderController);

        // Loss & completion monitors
        int planned = sources.stream().mapToInt(s -> s.getOutPorts().size() * packetsPerPort).sum();
        simulation.register(new LossMonitorController(lossModel, planned, 0.5,
                simulation, screenController, () -> startLevel(levelManager.getLevelIndex() + 1)));
        simulation.register(collisionController);

        simulation.register(new SnapshotController(boxes, wires, scoreModel, coinModel,
                usageModel, lossModel, snapshotManager));

        simulation.register(new HudController(scoreModel, usageModel, lossModel, coinModel,
                levelManager, hudView));

        simulation.register(new LevelCompletionDetector(wires, lossModel, producer,
                levelManager, 0.5, planned));

        // Temporal navigation listener
        gameView.setTemporalNavigationListener(dir -> {
            simulation.stop();
            if (dir < 0) timelineController.scrubTo(timelineController.getCurrentOffset() + 1);
            else if (dir > 0) timelineController.scrubTo(timelineController.getCurrentOffset() - 1);
            packetRenderController.refreshAll();
            refreshHud();
        });

        // HUD buttons (listeners cleared first)
        hudView.getStartButton().setEnabled(false); // until all ports wired
        clearButtonListeners();
        hudView.addStartListener(e -> {
            producer.startProduction();
            hudView.getStartButton().setEnabled(false);
            simulation.start();
        });
        hudView.addToggleListener(e -> {
            if (simulation.isRunning()) {
                simulation.stop();
                producer.stopProduction();
                timelineController.pause();
                hudView.setToggleText("Resume");
            } else {
                if (!producer.isFinished()) producer.startProduction();
                timelineController.resume();
                simulation.start();
                hudView.setToggleText("Pause");
            }
        });
    }

    private void clearButtonListeners() {
        for (var l : hudView.getStartButton().getActionListeners())
            hudView.getStartButton().removeActionListener(l);
        for (var l : hudView.getToggleButton().getActionListeners())
            hudView.getToggleButton().removeActionListener(l);
    }

    private void refreshHud() {
        hudView.setScore(scoreModel.getScore());
        hudView.setCoins(coinModel.getCoins());
        hudView.setPacketLoss(lossModel.getLostCount());
    }

    /* =================================================================== */
    /*                   NETWORK SNAPSHOT (Time Travel)                    */
    /* =================================================================== */

    @Override
    public NetworkSnapshot captureSnapshot() {
        List<NetworkSnapshot.SystemBoxState> bs = new ArrayList<>();
        for (SystemBoxModel b : boxes) {
            List<NetworkSnapshot.PacketState> buffer = new ArrayList<>();
            for (PacketModel p : b.getBuffer())
                buffer.add(new NetworkSnapshot.PacketState(0.0, p.getNoise(), p.getType()));
            bs.add(new NetworkSnapshot.SystemBoxState(b.getX(), b.getY(), b.getWidth(), b.getHeight(),
                    b.getInShapes(), b.getOutShapes(), buffer));
        }
        List<NetworkSnapshot.WireState> ws = new ArrayList<>();
        for (WireModel w : wires) {
            List<NetworkSnapshot.PacketState> ps = new ArrayList<>();
            for (PacketModel p : w.getPackets())
                ps.add(new NetworkSnapshot.PacketState(p.getProgress(), p.getNoise(), p.getType()));
            ws.add(new NetworkSnapshot.WireState(w.getSrcPort().getCenterX(), w.getSrcPort().getCenterY(),
                    w.getDstPort().getCenterX(), w.getDstPort().getCenterY(), ps));
        }
        return new NetworkSnapshot(scoreModel.getScore(), coinModel.getCoins(),
                lossModel.getLostCount(), bs, ws);
    }

    @Override public void restoreState(NetworkSnapshot snap) { applySnapshot(snap); }

    private void applySnapshot(NetworkSnapshot snap) {
        scoreModel.reset(); scoreModel.addPoints(snap.score());
        coinModel.reset();  coinModel.add(snap.coins());
        lossModel.reset();  for (int i = 0; i < snap.packetLoss(); i++) lossModel.increment();

        // Restore boxes & buffers
        List<NetworkSnapshot.SystemBoxState> boxStates = snap.boxStates();
        for (int i = 0; i < boxes.size() && i < boxStates.size(); i++) {
            var st = boxStates.get(i); var box = boxes.get(i);
            box.setX(st.x()); box.setY(st.y());
            box.clearBuffer();
            for (var ps : st.bufferPackets()) {
                PacketModel pkt = new PacketModel(ps.type(), Config.DEFAULT_PACKET_SPEED);
                pkt.increaseNoise(ps.noise());
                box.enqueue(pkt);
            }
        }

        // Restore wire packets
        List<NetworkSnapshot.WireState> wireStates = snap.wireStates();
        for (int i = 0; i < wires.size() && i < wireStates.size(); i++) {
            var wire = wires.get(i); var st = wireStates.get(i);
            wire.clearPackets();
            for (var ps : st.packets()) {
                PacketModel pkt = new PacketModel(ps.type(), Config.DEFAULT_PACKET_SPEED);
                pkt.increaseNoise(ps.noise());
                wire.attachPacket(pkt, ps.progress());
            }
        }

        gameView.reset(boxes, wires);
        packetRenderController.refreshAll();
        refreshHud();
    }

    /* =================================================================== */
    /*                         Utility & Getters                            */
    /* =================================================================== */

    public void setLevelManager(LevelManager mgr) { this.levelManager = mgr; }
    public List<WireModel> getWires()            { return wires; }
    public GameScreenView getGameView()          { return gameView; }




    public CoinModel getCoinModel() { return coinModel; }

    /* -------------------------------------------------- */
    /** Enables the Start button only when ALL ports have a connected wire. */
    private void updateStartEnabled() {
        boolean allConnected = true;
        for (SystemBoxModel box : boxes) {
            for (PortModel port : box.getInPorts()) {
                if (!isPortConnected(port)) { allConnected = false; break; }
            }
            if (!allConnected) break;
            for (PortModel port : box.getOutPorts()) {
                if (!isPortConnected(port)) { allConnected = false; break; }
            }
            if (!allConnected) break;
        }
        boolean finalAllConnected = allConnected;
        SwingUtilities.invokeLater(() -> hudView.getStartButton().setEnabled(finalAllConnected));
    }

    private boolean isPortConnected(PortModel port) {
        for (WireModel w : wires) {
            if (w.getSrcPort() == port || w.getDstPort() == port) return true;
        }
        return false;
    }

    public PacketLossModel getLossModel() {
        return lossModel;
    }

    public CollisionController getCollisionController() {
        return collisionController;
    }

    public SimulationController getSimulation() {
        return simulation;
    }

    public ScoreModel getScoreModel() {
        return scoreModel;
    }
}

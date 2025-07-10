package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.TimelineController;
import com.blueprinthell.model.*;
import com.blueprinthell.view.HudView;
import com.blueprinthell.view.SystemBoxView;
import com.blueprinthell.view.screens.GameScreenView;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Controller for managing game levels: setup, start, and MVC wiring,
 * including temporal navigation and snapshot application.
 */
public class GameController implements NetworkController {
    private final TimelineController timelineController;
    private final WireUsageModel usageModel;
    private final PacketLossModel lossModel;
    private final SnapshotManager snapshotManager;
    private PacketRenderController packetRenderController;

    private final SimulationController simulation;
    private final ScoreModel scoreModel;
    private final HudView hudView;
    private final GameScreenView gameView;

    private List<SystemBoxModel> boxes;
    private final List<WireModel> wires = new ArrayList<>();
    private final Map<WireModel, SystemBoxModel> destMap = new HashMap<>();

    public GameController(ScreenController screenController) {
        // initialize models
        this.usageModel = new WireUsageModel(1000.0);
        this.lossModel = new PacketLossModel();
        this.snapshotManager = new SnapshotManager();

        // initialize simulation and timeline
        this.simulation = new SimulationController(60);
        this.timelineController = new TimelineController(this, 1000);
        simulation.setTimelineController(timelineController);

        // initialize score, HUD, and game view
        this.scoreModel = new ScoreModel();
        this.hudView = new HudView(0, 0, 800, 50);
        this.gameView = new GameScreenView(hudView);
        screenController.registerGameScreen(gameView);
    }

    /**
     * Sets up and starts the specified level.
     */
    public void startLevel(int level) {
        // stop and reset previous state
        simulation.stop();
        scoreModel.reset();
        wires.clear();
        destMap.clear();
        lossModel.reset();
        snapshotManager.clear();
        timelineController.resume();

        // create system boxes
        SystemBoxModel source = new SystemBoxModel(50, 50, 80, 80,
                Collections.emptyList(), Collections.nCopies(1, PortShape.SQUARE));
        SystemBoxModel mid1 = new SystemBoxModel(200, 50, 80, 80,
                Collections.nCopies(1, PortShape.TRIANGLE), Collections.nCopies(1, PortShape.SQUARE));
        SystemBoxModel mid2 = new SystemBoxModel(50, 200, 80, 80,
                Collections.nCopies(1, PortShape.SQUARE), Collections.nCopies(1, PortShape.TRIANGLE));
        SystemBoxModel sink = new SystemBoxModel(200, 200, 80, 80,
                Collections.nCopies(1, PortShape.TRIANGLE), Collections.emptyList());
        boxes = new ArrayList<>(List.of(source, mid1, mid2, sink));

        // reset view
        gameView.reset(boxes, wires);
        gameView.showScreen();

        // enable dragging
        for (Component c : gameView.getGameArea().getComponents()) {
            if (c instanceof SystemBoxView sbv) {
                new SystemBoxDragController(sbv.getModel(), sbv, wires, usageModel);
            }
        }

        // enable wiring
        WireCreationController creator = new WireCreationController(
                gameView, simulation, boxes, wires, destMap, usageModel);
        new WireRemovalController(gameView, wires, destMap, creator, usageModel);

        // register packet logic
        PacketProducerController producer = new PacketProducerController(
                List.of(source), wires, destMap, Config.DEFAULT_PACKET_SPEED);
        simulation.register(producer);
        simulation.register(new PacketConsumerController(sink, scoreModel));
        boxes.stream()
                .filter(b -> !b.getInPorts().isEmpty() && !b.getOutPorts().isEmpty())
                .forEach(b -> simulation.register(new PacketRouterController(b, wires, destMap)));

        // dispatcher, render, collision
        simulation.register(new PacketDispatcherController(wires, destMap));
        packetRenderController = new PacketRenderController(gameView.getGameArea(), wires);
        simulation.register(packetRenderController);
        simulation.register(new CollisionController(wires, lossModel));

        // snapshot controller
        SnapshotController snapCtrl = new SnapshotController(
                boxes, wires, scoreModel, usageModel, lossModel, snapshotManager);
        simulation.register(snapCtrl);
        snapCtrl.update(0);

        // HUD controller
        simulation.register(new HudController(scoreModel, usageModel, lossModel, hudView));

        // temporal navigation
        gameView.setTemporalNavigationListener(dir -> {
            simulation.stop();
            if (dir < 0) timelineController.scrubTo(timelineController.getCurrentOffset() + 1);
            else if (dir > 0) timelineController.scrubTo(timelineController.getCurrentOffset() - 1);
            packetRenderController.refreshAll();
            hudView.setScore(scoreModel.getScore());
        });

        // start/resume
        hudView.addStartListener(e -> {
            // trigger packet production on first start
            producer.startProduction();
            if (!timelineController.isPlaying()) timelineController.resume();
            simulation.start();
        });
    }

    /**
     * Provides access to the game view panel.
     */
    public GameScreenView getGameView() {
        return gameView;
    }

    /**
     * Provides access to the simulation controller.
     */
    public SimulationController getSimulation() {
        return simulation;
    }

    @Override
    public NetworkSnapshot captureSnapshot() {
        List<NetworkSnapshot.SystemBoxState> bs = new ArrayList<>();
        for (SystemBoxModel b : boxes) {
            bs.add(new NetworkSnapshot.SystemBoxState(
                    b.getX(), b.getY(), b.getWidth(), b.getHeight(),
                    b.getInShapes(), b.getOutShapes()));
        }
        List<NetworkSnapshot.WireState> ws = new ArrayList<>();
        for (WireModel w : wires) {
            List<NetworkSnapshot.PacketState> ps = new ArrayList<>();
            for (PacketModel p : w.getPackets()) {
                ps.add(new NetworkSnapshot.PacketState(p.getProgress(), p.getNoise(), p.getType()));
            }
            ws.add(new NetworkSnapshot.WireState(
                    w.getSrcPort().getCenterX(), w.getSrcPort().getCenterY(),
                    w.getDstPort().getCenterX(), w.getDstPort().getCenterY(), ps));
        }
        return new NetworkSnapshot(
                scoreModel.getScore(),
                lossModel.getLostCount(),
                bs, ws);
    }

    @Override
    public void restoreState(NetworkSnapshot snap) {
        applySnapshot(snap);
    }

    /**
     * Applies a recorded snapshot to the current game state and view.
     */
    /**
     * Applies a recorded snapshot to the current game state and view.
     */
    private void applySnapshot(NetworkSnapshot snap) {
        // restore score
        scoreModel.reset();
        scoreModel.addPoints(snap.score());
        // restore packet loss
        lossModel.reset();
        for (int i = 0; i < snap.packetLoss(); i++) {
            lossModel.increment();
        }
        // update boxes
        List<NetworkSnapshot.SystemBoxState> boxStates = snap.boxStates();
        for (int i = 0; i < boxes.size() && i < boxStates.size(); i++) {
            var st = boxStates.get(i);
            var box = boxes.get(i);
            box.setX(st.x());
            box.setY(st.y());
        }
        // update wires and packets
        List<NetworkSnapshot.WireState> wireStates = snap.wireStates();
        for (int i = 0; i < wires.size() && i < wireStates.size(); i++) {
            var wire = wires.get(i);
            var state = wireStates.get(i);
            wire.clearPackets();
            for (var ps : state.packets()) {
                var p = new PacketModel(ps.type(), Config.DEFAULT_PACKET_SPEED);
                p.resetNoise();
                p.increaseNoise(ps.noise());
                wire.attachPacket(p, ps.progress());
            }
        }
        // refresh UI
        gameView.reset(boxes, wires);
        packetRenderController.refreshAll();
        hudView.setScore(scoreModel.getScore());
    }
}

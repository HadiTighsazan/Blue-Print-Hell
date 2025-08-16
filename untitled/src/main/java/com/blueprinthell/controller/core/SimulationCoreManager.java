package com.blueprinthell.controller.core;

import com.blueprinthell.controller.*;
import com.blueprinthell.controller.packet.PacketProducerController;
import com.blueprinthell.controller.packet.PacketRenderController;
import com.blueprinthell.controller.physics.CollisionController;
import com.blueprinthell.controller.simulation.SimulationController;
import com.blueprinthell.controller.simulation.SimulationRegistrar;
import com.blueprinthell.controller.simulation.TimelineController;
import com.blueprinthell.model.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SimulationCoreManager {
    private final GameController gameController;
    public final SimulationController simulation = new SimulationController(60);
    public final TimelineController timeline;

    public final ScoreModel scoreModel = new ScoreModel();
    public final CoinModel coinModel = new CoinModel();
    public final PacketLossModel lossModel = new PacketLossModel();
    public final List<WireModel> wires = new CopyOnWriteArrayList<WireModel>();
    public CollisionController collisionCtrl;
    public SimulationRegistrar registrar;
    public PacketRenderController packetRenderer;
    public PacketProducerController producerController;

    public SimulationCoreManager(GameController gameController) {
        this.gameController = gameController;
        this.timeline = new TimelineController(gameController, 1000);
    }

    public TimelineController getTimeline() {
        return timeline;
    }

    public CollisionController getCollisionCtrl() {
        return collisionCtrl;
    }

    public SimulationRegistrar getRegistrar() {
        return registrar;
    }

    public PacketRenderController getPacketRenderer() {
        return packetRenderer;
    }
    public void onNavigateTime(int dir) {
        if (timeline.isPlaying()) return;
        simulation.stop();
        int target = timeline.getCurrentOffset() + (dir < 0 ? 1 : -1);
        timeline.scrubTo(target);
        gameController.getGameView().requestFocusInWindow();
    }

    public boolean isPortConnected(PortModel p) {
        return wires.stream().anyMatch(w -> w.getSrcPort() == p || w.getDstPort() == p);
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

    public PacketProducerController getProducerController() {
        return producerController;
    }

    public void setRegistrar(SimulationRegistrar registrar) {
        this.registrar = registrar;
    }

    public void setPacketRenderer(PacketRenderController packetRenderer) {
        this.packetRenderer = packetRenderer;
    }

    public void setProducerController(PacketProducerController producerController) {
        this.producerController = producerController;
    }
}
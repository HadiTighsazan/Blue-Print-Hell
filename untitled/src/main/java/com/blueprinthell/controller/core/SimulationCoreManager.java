package com.blueprinthell.controller.core;

import com.blueprinthell.controller.*;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.LargeGroupRegistry;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Facade over all simulation‑time pieces:
 * <ul>
 *     <li>Main {@link SimulationController} loop &amp; play / pause logic</li>
 *     <li>Timeline scrubbing &amp; keyboard navigation</li>
 *     <li>Runtime registries (wires, packets, collisions, losses …)</li>
 * </ul>
 *
 * 2025‑07 refactor: سازندهٔ جدید (GameController, LargeGroupRegistry) ‑‑>
 *  dependency‑injection تمیز برای فاز ۲.  همچنین {@code usageModel} افزوده شد تا
 *  در بیرون (GameController → LevelCoreManager) به سهمیهٔ طول سیم دسترسی دهند.
 */
public class SimulationCoreManager {

    /* ================================================================ */
    /*                  Static configuration & dependencies             */
    /* ================================================================ */
    private final GameController     gameController;   // مرجع بالادستی
    private final LargeGroupRegistry largeRegistry;    // رجیستری گروه‌های بزرگ پکت

    /* ================================================================ */
    /*               Core simulation objects & sub‑controllers          */
    /* ================================================================ */
    private final WireUsageModel    usageModel   = new WireUsageModel(Double.MAX_VALUE);
    public  final SimulationController simulation = new SimulationController(60);
    public  final TimelineController   timeline;

    /* ---------------------- Runtime models ------------------------- */
    public final ScoreModel       scoreModel = new ScoreModel();
    public final CoinModel        coinModel  = new CoinModel();
    public final PacketLossModel  lossModel  = new PacketLossModel();
    public final List<WireModel>  wires      = new CopyOnWriteArrayList<>();



    /* ----------------- Helper controllers (late‑init) -------------- */
    public CollisionController        collisionCtrl;
    public SimulationRegistrar        registrar;
    public PacketRenderController     packetRenderer;
    public PacketProducerController   producerController;

    /* ================================================================ */
    /*                           Constructor                            */
    /* ================================================================ */
    public SimulationCoreManager(GameController gameController,
                                 LargeGroupRegistry largeRegistry) {
        this.gameController = gameController;
        this.largeRegistry  = largeRegistry;
        this.timeline       = new TimelineController(gameController, 1000);
    }

    /* ================================================================ */
    /*                        Getters / Setters                         */
    /* ================================================================ */
    public TimelineController getTimeline()        { return timeline; }
    public CollisionController getCollisionCtrl()  { return collisionCtrl; }
    public SimulationRegistrar getRegistrar()      { return registrar; }
    public PacketRenderController getPacketRenderer() { return packetRenderer; }
    public SimulationController getSimulation()    { return simulation; }
    public CoinModel getCoinModel()               { return coinModel; }
    public CollisionController getCollisionController() { return collisionCtrl; }
    public PacketLossModel getLossModel()         { return lossModel; }
    public ScoreModel getScoreModel()             { return scoreModel; }
    public PacketProducerController getProducerController() { return producerController; }
    public LargeGroupRegistry getLargeRegistry()  { return largeRegistry; }
    public WireUsageModel getUsageModel()         { return usageModel; }
    public List<WireModel> getWires()             { return wires; }

    /* ---------------------- Late‑init hooks ------------------------- */
    public void setRegistrar(SimulationRegistrar registrar)               { this.registrar = registrar; }
    public void setPacketRenderer(PacketRenderController packetRenderer)  { this.packetRenderer = packetRenderer; }
    public void setProducerController(PacketProducerController producerController) {
        this.producerController = producerController;

        // ثبت در SimulationController
        simulation.setPacketProducerController(producerController);
    }


    /* ================================================================ */
    /*                 Timeline scrubbing with keyboard                */
    /* ================================================================ */
    public void onNavigateTime(int dir) {
        if (timeline.isPlaying()) return;
        simulation.stop();
        int target = timeline.getCurrentOffset() + (dir < 0 ? 1 : -1);
        timeline.scrubTo(target);
        gameController.getGameView().requestFocusInWindow();
    }

    /* ================================================================ */
    /*                        Utility helpers                           */
    /* ================================================================ */
    public boolean isPortConnected(PortModel p) {
        return wires.stream().anyMatch(w -> w.getSrcPort() == p || w.getDstPort() == p);
    }
    public void setCollisionCtrl(CollisionController collisionCtrl) {
        this.collisionCtrl = collisionCtrl;
    }





}

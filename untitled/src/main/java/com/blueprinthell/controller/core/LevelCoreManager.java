package com.blueprinthell.controller.core;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.*;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.level.LevelGenerator;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.WireUsageModel;
import com.blueprinthell.view.WireView;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LevelCoreManager {
    private final GameController gameController;
    public final WireUsageModel usageModel = new WireUsageModel(1000.0);
    public final Map<WireModel, SystemBoxModel> destMap = new HashMap<WireModel, SystemBoxModel>();
    public LevelBuilder levelBuilder;
    /* ================================================================ */
    /*                Per‑level transient state & controllers           */
    /* ================================================================ */
    public List<SystemBoxModel> boxes = new ArrayList<SystemBoxModel>();
    public LevelManager levelManager;
    /**
     * Stores the definition of the level currently running so that we can restart (retry)
     * the exact same stage later without relying on external callers to re‑pass it.
     */
    public LevelDefinition currentDef;

    public LevelCoreManager(GameController gameController) {
        this.gameController = gameController;
    }

    public WireUsageModel getUsageModel() {
        return usageModel;
    }

    public Map<WireModel, SystemBoxModel> getDestMap() {
        return destMap;
    }

    public LevelBuilder getLevelBuilder() {
        return levelBuilder;
    }

    public List<SystemBoxModel> getBoxes() {
        return boxes;
    }

    public LevelManager getLevelManager() {
        return levelManager;
    }

    public LevelDefinition getCurrentDef() {
        return currentDef;
    }/* --------------------------------------------------------------- */

    /*               Public API to load a level by index               */
    /* --------------------------------------------------------------- */
    public void startLevel(int idx) {
        LevelDefinition def = LevelGenerator.firstLevel();
        for (int i = 1; i < idx; i++) {
            def = LevelGenerator.nextLevel(def);
        }
        startLevel(def);
    }/* --------------------------------------------------------------- */

    /*                Core level bootstrap (fresh start)               */
    /* --------------------------------------------------------------- */
    public void startLevel(LevelDefinition def) {
        // ① remember the definition we are about to run
        this.currentDef = def;

        if (levelManager == null) {
            throw new IllegalStateException("LevelManager must be set before starting level");
        }

        /* ------------------------------------------------------------------ */
        /*  Fresh game (index 0) ⇒ full reset of boxes & wires collections   */
        /* ------------------------------------------------------------------ */
        if (levelManager.getLevelIndex() == 0) {
            boxes.clear();
            gameController.getWires().clear();
            destMap.clear();
        }

        /* More defensively, ensure boxes list never exceeds definition size  */
        if (boxes.size() > def.boxes().size()) {
            boxes = new ArrayList<SystemBoxModel>(boxes.subList(0, def.boxes().size()));
        }

        /* ----- hard reset sim & HUD for new stage ----- */
        gameController.getSimulation().stop();
        gameController.getSimulation().clearUpdatables();

        gameController.getScoreModel().reset();
        gameController.getCoinModel().reset();
        gameController.getLossModel().reset();
        gameController.getSnapshotMgr().clear();
        gameController.getTimeline().resume();

        usageModel.reset(def.totalWireLength());

        /* ----- build / reuse system boxes ----- */
        boxes = levelBuilder.build(def, boxes);

        /* ----- mark existing wires as immutable (carry‑over) ----- */
        for (WireModel w : gameController.getWires()) {
            w.setForPreviousLevels(true);
        }

        /* ----- controllers that depend on boxes/wires ----- */
        buildWireControllers();

        /* ----- discover sources & sink ----- */
        List<SystemBoxModel> sources = new ArrayList<SystemBoxModel>();
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
        WireModel.setSimulationController(gameController.getSimulation());

        /* ----- packet planning for HUD & loss monitor ----- */
        int stageIndex = levelManager.getLevelIndex() + 1;
        int perPortCount = Config.PACKETS_PER_PORT * stageIndex;
        int totalOutPorts = sources.stream().mapToInt(b -> b.getOutPorts().size()).sum();
        int plannedPackets = perPortCount * totalOutPorts;

        gameController.setProducerController(new PacketProducerController(
                sources, gameController.getWires(), destMap,
                Config.DEFAULT_PACKET_SPEED,
                perPortCount));
        gameController.getHudCoord().wireLevel(gameController.getProducerController());
        gameController.getSimulation().setPacketProducerController(gameController.getProducerController());

        /* ----- loss monitor that can restart stage on failure ----- */
        LossMonitorController lossCtrl = new LossMonitorController(
                gameController.getLossModel(),
                plannedPackets,
                0.5,
                gameController.getSimulation(),
                gameController.getScreenController(),
                gameController::retryStage              // 🎯 use new retry method
        );
        gameController.getSimulation().register(lossCtrl);

        /* ----- view & HUD plumbing ----- */
        gameController.setPacketRenderer(new PacketRenderController(gameController.getGameView().getGameArea(), gameController.getWires()));

        gameController.setHudController(new HudController(usageModel, gameController.getLossModel(), gameController.getCoinModel(), levelManager, gameController.getHudView()));
        gameController.setShopController(new ShopController(
                gameController.getMainFrame(), gameController.getSimulation(), gameController.getCoinModel(), gameController.getCollisionCtrl(), gameController.getLossModel(), gameController.getWires(), gameController.getHudController()));
        gameController.getHudView().getStoreButton().addActionListener(e -> gameController.getShopController().openShop());

        /* ----- snapshot service ----- */
        gameController.setSnapshotSvc(new SnapshotService(
                boxes, gameController.getWires(), gameController.getScoreModel(), gameController.getCoinModel(), gameController.getLossModel(), usageModel, gameController.getSnapshotMgr(),
                gameController.getHudView(), gameController.getGameView(), gameController.getPacketRenderer(), List.of(gameController.getProducerController())));

        gameController.setRegistrar(new SimulationRegistrar(
                gameController.getSimulation(), null, gameController.getCollisionCtrl(), gameController.getPacketRenderer(), gameController.getScoreModel(), gameController.getCoinModel(),
                gameController.getLossModel(), usageModel, gameController.getSnapshotMgr(), gameController.getHudView(), levelManager));

        /* ----- register everything ----- */
        List<Updatable> systemControllers = new ArrayList<Updatable>();
        systemControllers.add(gameController.getHudController());
        gameController.getRegistrar().registerAll(boxes, gameController.getWires(), destMap, sources, sink, gameController.getProducerController(), systemControllers);

        updateStartEnabled();
    }/* --------------------------------------------------------------- */

    /*              Build controllers for wire creation/removal        */
    /* --------------------------------------------------------------- */
    public void buildWireControllers() {
        WireCreationController creator = new WireCreationController(
                gameController.getGameView(), gameController.getSimulation(), boxes, gameController.getWires(), destMap, usageModel, gameController.getCoinModel(), gameController::updateStartEnabled);
        // keep reference for port‑freeing on purge
        gameController.setWireCreator(creator);

        new WireRemovalController(
                gameController.getGameView(), gameController.getWires(), destMap, creator, usageModel, gameController::updateStartEnabled);
    }/* --------------------------------------------------------------- */

    /*                  Enable / disable “Start” button                */
    /* --------------------------------------------------------------- */
    public void updateStartEnabled() {
        boolean allConnected = boxes.stream().allMatch(b ->
                b.getInPorts().stream().allMatch(gameController::isPortConnected) &&
                        b.getOutPorts().stream().allMatch(gameController::isPortConnected));
        gameController.getHudCoord().setStartEnabled(allConnected);
    }

    /**
     * Remove only the wires drawn in the *current* level (those not yet
     * tagged as {@code forPreviousLevels}), free their port capacity and
     * wire‑length quota, then refresh HUD.
     */
    public void purgeCurrentLevelWires() {
        JPanel area = gameController.getGameView().getGameArea();

        // Collect wires that belong to the current level
        List<WireModel> toRemove = new ArrayList<WireModel>();
        for (WireModel w : gameController.getWires()) {
            if (!w.isForPreviousLevels()) {
                if (gameController.getWireCreator() != null) {
                    gameController.getWireCreator().freePortsForWire(w);
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
        gameController.getWires().removeAll(toRemove);

        /* ----- refresh UI ----- */
        area.revalidate();
        area.repaint();
        if (gameController.getHudController() != null) gameController.getHudController().refreshOnce();
    }

    /**
     * Public helper to retry the current stage, preserving carry‑over circuits.
     * Stops simulation, purges current‑level wires, then restarts using the
     * {@code currentDef} stored when the level was first launched.
     */
    public void retryStage() {
        if (currentDef == null) {
            throw new IllegalStateException("Cannot retry before a level has been started");
        }
        gameController.getSimulation().stop();
        purgeCurrentLevelWires();
        startLevel(currentDef);
    }

    /**
     * @deprecated Use {@link #retryStage()} instead. This variant keeps the
     * previous signature for callers that still pass the
     * definition explicitly.
     */
    @Deprecated
    public void retryLevel(LevelDefinition def) {
        gameController.getSimulation().stop();
        purgeCurrentLevelWires();
        startLevel(def);
    }
}
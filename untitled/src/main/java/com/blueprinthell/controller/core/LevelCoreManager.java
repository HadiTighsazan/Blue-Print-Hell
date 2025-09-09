package com.blueprinthell.controller.core;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.*;
import com.blueprinthell.controller.gameplay.AccelerationFreezeController;
import com.blueprinthell.controller.gameplay.EliphasCenteringController;
import com.blueprinthell.controller.packet.LossMonitorController;
import com.blueprinthell.controller.packet.PacketProducerController;
import com.blueprinthell.controller.packet.PacketRenderController;
import com.blueprinthell.controller.persistence.SnapshotService;
import com.blueprinthell.controller.simulation.SimulationRegistrar;
import com.blueprinthell.controller.systems.RouteHints;
import com.blueprinthell.controller.systems.VpnRevertHints;
import com.blueprinthell.controller.ui.editor.SystemBoxDragController;
import com.blueprinthell.controller.ui.hud.HudController;
import com.blueprinthell.controller.validation.WireIntersectionValidator;
import com.blueprinthell.controller.wire.WireCreationController;
import com.blueprinthell.controller.wire.WireRemovalController;
import com.blueprinthell.level.Level;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.level.LevelRegistry;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.WireUsageModel;
import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.view.EliphasPointRenderer;
import com.blueprinthell.view.HudView;
import com.blueprinthell.view.WireView;
import com.blueprinthell.view.screens.GameScreenView;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LevelCoreManager {
    private final GameController gameController;
    private final boolean isHeadless;
    public final WireUsageModel usageModel = new WireUsageModel(1000.0);
    public final Map<WireModel, SystemBoxModel> destMap = new HashMap<>();
    public LevelBuilder levelBuilder;

    public List<SystemBoxModel> boxes = new ArrayList<>();
    public LevelManager levelManager;

    public LevelDefinition currentDef;

    private WireRemovalController wireRemovalController;

    public LevelCoreManager(GameController gameController, boolean isHeadless) {
        this.gameController = gameController;
        this.isHeadless = isHeadless;
        // LevelBuilder now needs to handle a potentially null GameScreenView
        this.levelBuilder = new LevelBuilder(isHeadless ? null : gameController.getGameView(),
                gameController.getWires(), usageModel);
    }

    public void startLevel(int idx) {
        if (!LevelRegistry.isValidLevel(idx)) {
            throw new IllegalArgumentException("Invalid level index: " + idx);
        }
        Level level = LevelRegistry.getLevel(idx);
        startLevel(level.getDefinition());
    }

    public void startLevel(LevelDefinition def) {
        this.currentDef = def;

        if (levelManager == null) {
            throw new IllegalStateException("LevelManager must be set before starting level");
        }

        if (levelManager.getLevelIndex() == 0) {
            boxes.clear();
            gameController.getWires().clear();
            destMap.clear();
        }

        if (boxes.size() > def.boxes().size()) {
            boxes = new ArrayList<>(boxes.subList(0, def.boxes().size()));
        }

        gameController.getSimulation().stop();
        gameController.getSimulation().clearUpdatables();

        gameController.getScoreModel().reset();
        gameController.getCoinModel().reset();
        gameController.getLossModel().reset();
        gameController.getSnapshotMgr().clear();
        gameController.getTimeline().resume();

        usageModel.reset(def.totalWireLength());

        boxes = levelBuilder.build(def, boxes);

        for (WireModel w : gameController.getWires()) {
            w.setForPreviousLevels(true);
        }

        if (!isHeadless) {
            SystemBoxDragController.setDragEnabled(true);
            SystemBoxDragController.setNetworkChanged(gameController::updateStartEnabled);
            buildWireControllers();
            gameController.getHudCoord().setRightWarningMessage(null);
        }

        List<SystemBoxModel> sources = new ArrayList<>();
        SystemBoxModel sink = null;
        for (int i = 0; i < boxes.size(); i++) {
            LevelDefinition.BoxSpec spec = def.boxes().get(i);
            SystemBoxModel box = boxes.get(i);
            if (spec.isSource()) sources.add(box);
            if (spec.isSink()) sink = box;
        }

        RouteHints.clear();
        VpnRevertHints.clear();

        WireModel.setSourceInputPorts(sources);
        WireModel.setSimulationController(gameController.getSimulation());

        int stageIndex = levelManager.getLevelIndex() + 1;
        int perPortCount = Config.PACKETS_PER_PORT * stageIndex;
        int totalOutPorts = sources.stream().mapToInt(b -> b.getOutPorts().size()).sum();
        int plannedPackets = perPortCount * totalOutPorts;

        gameController.setProducerController(new PacketProducerController(
                sources, gameController.getWires(), destMap,
                Config.DEFAULT_PACKET_SPEED,
                perPortCount));

        if (!isHeadless) {
            gameController.getHudCoord().wireLevel(gameController.getProducerController());
        }

        gameController.getSimulation().setPacketProducerController(gameController.getProducerController());

        double threshold = levelManager.getCurrentLevel().getMaxLossRatio();
        LossMonitorController lossCtrl = new LossMonitorController(
                gameController.getLossModel(), plannedPackets, threshold,
                gameController.getSimulation(), gameController.getScreenController(),
                gameController::retryStage);
        gameController.getSimulation().register(lossCtrl);

        if (!isHeadless) {
            gameController.setPacketRenderer(new PacketRenderController(gameController.getGameView().getGameArea(), gameController.getWires()));

            // Setup UI-only controllers
            gameController.setHudController(new HudController(usageModel, gameController.getLossModel(), gameController.getCoinModel(), levelManager, gameController.getHudView()));
            gameController.setShopController(new ShopController(
                    gameController.getMainFrame(), gameController.getSimulation(), gameController.getCoinModel(),
                    gameController.getCollisionController(), gameController.getLossModel(), gameController.getWires(),
                    gameController.getHudController(), gameController.getGameView()));

            AccelerationFreezeController freezeController = new AccelerationFreezeController(gameController.getWires());
            EliphasCenteringController eliphas = new EliphasCenteringController(gameController.getWires());
            gameController.getShopController().setEliphasController(eliphas);

            gameController.getSimulation().register(freezeController);
            gameController.setFreezeController(freezeController);
            gameController.getShopController().setFreezeController(freezeController);
            gameController.getSimulation().register(eliphas);
        }

        gameController.setRegistrar(new SimulationRegistrar(
                gameController, gameController.getSimulation(), gameController.getScreenController(),
                gameController.getCollisionController(), gameController.getPacketRenderer(),
                gameController.getScoreModel(), gameController.getCoinModel(), gameController.getLossModel(),
                usageModel, gameController.getSnapshotMgr(), gameController.getHudView(), levelManager));
        gameController.getRegistrar().setCurrentBoxSpecs(def.boxes());

        LargeGroupRegistry largeRegistry = gameController.getRegistrar().getLargeGroupRegistry();

        gameController.setSnapshotSvc(new SnapshotService(
                gameController.getDestMap(), boxes, gameController.getWires(),
                gameController.getScoreModel(), gameController.getCoinModel(), gameController.getLossModel(),
                usageModel, gameController.getSnapshotMgr(), gameController.getHudView(),
                gameController.getGameView(), gameController.getPacketRenderer(),
                List.of(gameController.getProducerController()),
                gameController::updateStartEnabled,
                () -> levelManager.getLevelIndex() + 1,
                largeRegistry));

        List<Updatable> systemControllers = new ArrayList<>();
        if (!isHeadless) {
            systemControllers.add(gameController.getHudController());
        }

        if (wireRemovalController != null) {
            gameController.getRegistrar().setWireRemover(wireRemovalController);
        }
        gameController.getRegistrar().registerAll(boxes, gameController.getWires(), destMap, sources, sink, gameController.getProducerController(), systemControllers);

        if (!isHeadless) {
            updateStartEnabled();
            SystemBoxDragController.setNetworkChanged(this::updateStartEnabled);
            gameController.startAutoSave();
        }
    }

    public void buildWireControllers() {
        if (isHeadless) return;
        WireCreationController creator = new WireCreationController(
                gameController.getGameView(), gameController.getSimulation(), boxes, gameController.getWires(), destMap, usageModel, gameController.getCoinModel(), gameController::updateStartEnabled);
        gameController.setWireCreator(creator);

        this.wireRemovalController = new WireRemovalController(
                gameController.getGameView(), gameController.getWires(), destMap, creator, usageModel, gameController::updateStartEnabled);
    }

    public void updateStartEnabled() {
        if (isHeadless) return;
        boolean allConnected = boxes.stream().allMatch(b ->
                b.getInPorts().stream().allMatch(gameController::isPortConnected) &&
                        b.getOutPorts().stream().allMatch(gameController::isPortConnected));

        boolean wiresValid = WireIntersectionValidator.areAllWiresValid(
                gameController.getWires(), boxes);

        boolean canStart = allConnected && wiresValid;
        gameController.getHudCoord().setStartEnabled(canStart);

        String warn = wiresValid ? null : WireIntersectionValidator.getValidationMessage(gameController.getWires(), boxes);
        gameController.getHudCoord().setRightWarningMessage(warn);
    }

    public void retryStage() {
        if (currentDef == null) {
            throw new IllegalStateException("Cannot retry before a level has been started");
        }

        gameController.getSimulation().stop();
        for (WireModel w : gameController.getWires()) {
            if (w.isForPreviousLevels()) {
                w.clearPackets();
                w.resetLargePacketCounter();
            }
        }
        for (SystemBoxModel b : boxes) {
            b.clearBuffer();
        }

        if (!isHeadless) {
            if (gameController.getPacketRenderer() != null) {
                gameController.getPacketRenderer().refreshAll();
            }
            if (gameController.getHudController() != null) {
                gameController.getHudController().refreshOnce();
            }
            purgeCurrentLevelWires();
        }
        startLevel(currentDef);
    }

    private void purgeCurrentLevelWires() {
        if (isHeadless) return;
        JPanel area = gameController.getGameView().getGameArea();
        List<WireModel> toRemove = new ArrayList<>();
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
        if (toRemove.isEmpty()) return;

        for (Component c : area.getComponents()) {
            if (c instanceof WireView wv && toRemove.contains(wv.getModel())) {
                area.remove(c);
            }
        }
        gameController.getWires().removeAll(toRemove);
        area.revalidate();
        area.repaint();
        if (gameController.getHudController() != null) gameController.getHudController().refreshOnce();
    }
    // In LevelCoreManager.java, add this new public method
    public void ensureSnapshotService() {
        if (gameController.getSnapshotSvc() != null) {
            return; // Already created
        }

        LargeGroupRegistry largeRegistry = (gameController.getRegistrar() != null)
                ? gameController.getRegistrar().getLargeGroupRegistry()
                : null;

        // In headless mode, some UI components are null.
        PacketRenderController renderer = isHeadless ? null : gameController.getPacketRenderer();
        HudView hud = isHeadless ? null : gameController.getHudView();
        GameScreenView gsv = isHeadless ? null : gameController.getGameView();
        Runnable networkChangedCb = isHeadless ? () -> {} : gameController::updateStartEnabled;


        SnapshotService svc = new SnapshotService(
                gameController.getDestMap(),
                boxes,
                gameController.getWires(),
                gameController.getScoreModel(),
                gameController.getCoinModel(),
                gameController.getLossModel(),
                usageModel,
                gameController.getSnapshotMgr(),
                hud,
                gsv,
                renderer,
                (gameController.getProducerController() != null) ? java.util.List.of(gameController.getProducerController()) : java.util.List.of(),
                networkChangedCb,
                () -> (levelManager != null) ? (levelManager.getLevelIndex() + 1) : 1,
                largeRegistry
        );
        gameController.setSnapshotSvc(svc);
    }
    // GETTERS
    public WireUsageModel getUsageModel() { return usageModel; }
    public Map<WireModel, SystemBoxModel> getDestMap() { return destMap; }
    public LevelBuilder getLevelBuilder() { return levelBuilder; }
    public List<SystemBoxModel> getBoxes() { return boxes; }
    public LevelManager getLevelManager() { return levelManager; }
    public LevelDefinition getCurrentDef() { return currentDef; }
    public WireRemovalController getWireRemovalController() { return wireRemovalController; }
}
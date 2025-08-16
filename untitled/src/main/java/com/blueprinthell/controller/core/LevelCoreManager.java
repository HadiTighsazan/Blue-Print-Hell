package com.blueprinthell.controller.core;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.*;
import com.blueprinthell.controller.systems.RouteHints;
import com.blueprinthell.controller.systems.VpnRevertHints;
import com.blueprinthell.level.Level;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.level.LevelRegistry;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.WireUsageModel;
import com.blueprinthell.model.large.LargeGroupRegistry;
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

    public List<SystemBoxModel> boxes = new ArrayList<SystemBoxModel>();
    public LevelManager levelManager;

    public LevelDefinition currentDef;

    // اضافه کردن فیلد برای WireRemovalController
    private WireRemovalController wireRemovalController;

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
    }

    // متد برای دسترسی به WireRemovalController
    public WireRemovalController getWireRemovalController() {
        return wireRemovalController;
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
            boxes = new ArrayList<SystemBoxModel>(boxes.subList(0, def.boxes().size()));
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

        buildWireControllers();

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
                perPortCount));  // حذف PacketLossModel - loss فقط هنگام از دست رفتن واقعی پکت اضافه می‌شود

        gameController.getHudCoord().wireLevel(gameController.getProducerController());
        gameController.getSimulation().setPacketProducerController(gameController.getProducerController());

        double threshold = levelManager.getCurrentLevel().getMaxLossRatio();

        LossMonitorController lossCtrl = new LossMonitorController(
                gameController.getLossModel(),
                plannedPackets,
                threshold,                         // به جای 0.5
                gameController.getSimulation(),
                gameController.getScreenController(),
                gameController::retryStage
        );

        gameController.getSimulation().register(lossCtrl);

        gameController.setPacketRenderer(new PacketRenderController(gameController.getGameView().getGameArea(), gameController.getWires()));

        gameController.setHudController(new HudController(usageModel, gameController.getLossModel(), gameController.getCoinModel(), levelManager, gameController.getHudView()));
        gameController.setShopController(new ShopController(
                gameController.getMainFrame(),
                gameController.getSimulation(),
                gameController.getCoinModel(),
                gameController.getCollisionCtrl(),
                gameController.getLossModel(),
                gameController.getWires(),
                gameController.getHudController(),
                gameController.getGameView()  // اضافه شدن gameView
        ));
        AccelerationFreezeController freezeController = new AccelerationFreezeController(gameController.getWires());

        gameController.getSimulation().register(freezeController);
        gameController.setFreezeController(freezeController);
        gameController.getShopController().setFreezeController(freezeController);
        gameController.getHudView().getStoreButton().addActionListener(e -> gameController.getShopController().openShop());

        LargeGroupRegistry largeRegistry = null;
        if (gameController.getRegistrar() != null) {
            largeRegistry = gameController.getRegistrar().getLargeGroupRegistry();
        }
        gameController.setSnapshotSvc(new SnapshotService(
                gameController.getDestMap(),
                boxes,
                gameController.getWires(),
                gameController.getScoreModel(),
                gameController.getCoinModel(),
                gameController.getLossModel(),
                usageModel,
                gameController.getSnapshotMgr(),
                gameController.getHudView(),
                gameController.getGameView(),
                gameController.getPacketRenderer(),
                List.of(gameController.getProducerController()),
                gameController::updateStartEnabled,
                () -> levelManager.getLevelIndex() + 1, // ★ تامین شماره لِول
                largeRegistry
        ));

        gameController.setRegistrar(new SimulationRegistrar(gameController,
                gameController.getSimulation(), null, gameController.getCollisionCtrl(), gameController.getPacketRenderer(), gameController.getScoreModel(), gameController.getCoinModel(),
                gameController.getLossModel(), usageModel, gameController.getSnapshotMgr(), gameController.getHudView(), levelManager));
        gameController.getRegistrar().setCurrentBoxSpecs(def.boxes());

        List<Updatable> systemControllers = new ArrayList<Updatable>();
        systemControllers.add(gameController.getHudController());

        // *** تغییر مهم: پاس کردن WireRemovalController به SimulationRegistrar ***
        if (wireRemovalController != null) {
            gameController.getRegistrar().setWireRemover(wireRemovalController);
        }

        gameController.getRegistrar().registerAll(boxes, gameController.getWires(), destMap, sources, sink, gameController.getProducerController(), systemControllers);

        updateStartEnabled();


        gameController.startAutoSave();
    }

    public void buildWireControllers() {
        WireCreationController creator = new WireCreationController(
                gameController.getGameView(), gameController.getSimulation(), boxes, gameController.getWires(), destMap, usageModel, gameController.getCoinModel(), gameController::updateStartEnabled);
        gameController.setWireCreator(creator);

        // *** تغییر مهم: ذخیره کردن مرجع WireRemovalController ***
        this.wireRemovalController = new WireRemovalController(
                gameController.getGameView(), gameController.getWires(), destMap, creator, usageModel, gameController::updateStartEnabled);

        System.out.println("WireRemovalController created and stored: " + wireRemovalController);
    }

    public void updateStartEnabled() {
        boolean allConnected = boxes.stream().allMatch(b ->
                b.getInPorts().stream().allMatch(gameController::isPortConnected) &&
                        b.getOutPorts().stream().allMatch(gameController::isPortConnected));
        gameController.getHudCoord().setStartEnabled(allConnected);
    }

    public void purgeCurrentLevelWires() {
        JPanel area = gameController.getGameView().getGameArea();

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

        if (toRemove.isEmpty()) return;

        Component[] comps = area.getComponents();
        for (Component c : comps) {
            if (c instanceof WireView wv) {
                if (toRemove.contains(wv.getModel())) {
                    area.remove(c);
                }
            }
        }

        gameController.getWires().removeAll(toRemove);

        area.revalidate();
        area.repaint();
        if (gameController.getHudController() != null) gameController.getHudController().refreshOnce();
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
        if (gameController.getPacketRenderer() != null) {
            gameController.getPacketRenderer().refreshAll();
        }
        if (gameController.getHudController() != null) {
            gameController.getHudController().refreshOnce();
        }

        purgeCurrentLevelWires();
        startLevel(currentDef);
    }

    @Deprecated
    public void retryLevel(LevelDefinition def) {
        gameController.getSimulation().stop();
        purgeCurrentLevelWires();
        startLevel(def);
    }
}
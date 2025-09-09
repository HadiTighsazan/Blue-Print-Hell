package com.blueprinthell.controller;

import com.blueprinthell.controller.core.LevelCoreManager;
import com.blueprinthell.controller.core.SimulationCoreManager;
import com.blueprinthell.controller.core.SnapshotCoreController;
import com.blueprinthell.controller.gameplay.AccelerationFreezeController;
import com.blueprinthell.controller.packet.PacketProducerController;
import com.blueprinthell.controller.packet.PacketRenderController;
import com.blueprinthell.controller.persistence.AutoSaveController;
import com.blueprinthell.controller.persistence.SnapshotManager;
import com.blueprinthell.controller.persistence.SnapshotService;
import com.blueprinthell.controller.physics.CollisionController;
import com.blueprinthell.controller.simulation.NetworkController;
import com.blueprinthell.controller.simulation.SimulationController;
import com.blueprinthell.controller.simulation.SimulationRegistrar;
import com.blueprinthell.controller.simulation.TimelineController;
import com.blueprinthell.controller.systems.TeleportTracking;
import com.blueprinthell.controller.ui.ScreenController;
import com.blueprinthell.controller.ui.editor.SystemBoxDragController;
import com.blueprinthell.controller.ui.hud.HudController;
import com.blueprinthell.controller.ui.hud.HudCoordinator;
import com.blueprinthell.controller.wire.WireCreationController;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.snapshot.NetworkSnapshot;
import com.blueprinthell.view.HudView;
import com.blueprinthell.view.screens.GameScreenView;

import javax.swing.*;
import java.util.List;
import java.util.Map;

public class GameController implements NetworkController {

    // Core Managers (Logic-only)
    private final LevelCoreManager levelCoreManager;
    private final SimulationCoreManager simulationCoreManager;
    private final SnapshotCoreController snapshotCoreController = new SnapshotCoreController();

    // UI-Related Components (Nullable for headless mode)
    private final JFrame mainFrame;
    private final HudView hudView;
    private final GameScreenView gameView;
    private ScreenController screenController;
    private HudCoordinator hudCoord;
    private ShopController shopController;
    private HudController hudController;
    private WireCreationController wireCreator;

    // Controllers
    private AutoSaveController autoSaveController;
    private AccelerationFreezeController freezeController;

    // State
    private final boolean isHeadless;
    private boolean restoreInProgress = false;

    /**
     * Headless constructor for server-side simulation.
     */
    public GameController(boolean isHeadless) {
        this.isHeadless = isHeadless;
        this.mainFrame = null;
        this.hudView = null;
        this.gameView = null;

        // Initialize core logic managers
        this.simulationCoreManager = new SimulationCoreManager(this);
        this.levelCoreManager = new LevelCoreManager(this, true); // Pass headless flag

        // Initialize controllers that don't depend on UI
        this.simulationCoreManager.collisionCtrl = new CollisionController(
                simulationCoreManager.getWires(), simulationCoreManager.getLossModel());

        // UI-dependent initializations are skipped
        this.hudCoord = null;
        this.shopController = null;
    }

    /**
     * GUI constructor for client-side gameplay.
     */
    public GameController(JFrame mainFrame) {
        this.isHeadless = false;
        this.mainFrame = mainFrame;
        this.hudView = new HudView(0, 0, 800, 50);
        this.gameView = new GameScreenView(hudView);

        // Initialize core logic managers
        this.simulationCoreManager = new SimulationCoreManager(this);
        this.levelCoreManager = new LevelCoreManager(this, false); // Pass headless flag

        // Initialize controllers that don't depend on UI
        this.simulationCoreManager.collisionCtrl = new CollisionController(
                simulationCoreManager.getWires(), simulationCoreManager.getLossModel());

        // Initialize UI-dependent controllers
        this.hudCoord = new HudCoordinator(hudView, simulationCoreManager.getScoreModel(), simulationCoreManager.getCoinModel(),
                simulationCoreManager.getLossModel(), simulationCoreManager.getSimulation(), simulationCoreManager.getTimeline());

        simulationCoreManager.getSimulation().setTimelineController(simulationCoreManager.getTimeline());

        gameView.setTemporalNavigationListener(this::onNavigateTime);
    }

    private void onNavigateTime(int dir) {
        simulationCoreManager.onNavigateTime(dir);
    }

    public void startLevel(LevelDefinition def) {
        levelCoreManager.startLevel(def);
    }

    public void setLevelManager(LevelManager mgr) {
        this.levelCoreManager.levelManager = mgr;
    }

    public void startLevel(int idx) {
        levelCoreManager.startLevel(idx);
    }

    public void updateStartEnabled() {
        levelCoreManager.updateStartEnabled();
    }

    public boolean isPortConnected(PortModel p) {
        return simulationCoreManager.isPortConnected(p);
    }

    public void retryStage() {
        levelCoreManager.retryStage();
    }

    @Override
    public NetworkSnapshot captureSnapshot() {
        if (snapshotCoreController.getSnapshotSvc() == null) {
            levelCoreManager.ensureSnapshotService(); // This call is now correct
        }
        return snapshotCoreController.captureSnapshot();
    }



    @Override
    public void restoreState(NetworkSnapshot snap) {
        if (getRegistrar() != null) getRegistrar().clearTransientState();
        TeleportTracking.clearAll();
        if (snapshotCoreController.getSnapshotSvc() == null) {
            levelCoreManager.ensureSnapshotService(); // This call is now correct
        }
        snapshotCoreController.restoreState(snap);

        // <<< این خط اضافه شده است >>>
        // After any restore, check if the level is already complete.
        // This applies to both single-player resume and PvP state updates.
        checkCompletionAfterRestore();
    }

    public void startAutoSave() {
        if (isHeadless) return;
        if (autoSaveController == null) {
            autoSaveController = new AutoSaveController(
                    snapshotCoreController.getSnapshotSvc(), 5);
        }
        autoSaveController.start();
    }
    public boolean isAutoSaveRunning() {
        // Returns true if the auto-save controller exists and its timer is running.
        // This is safe to call even in headless mode where autoSaveController is null.
        return autoSaveController != null && autoSaveController.isRunning();
    }
    private void checkCompletionAfterRestore() {
        if (producerIsFinishedAndGameIsStable()) {
            getLossModel().finalizeDeferredLossNow(); // CORRECTED: Was lossModel

            int producedUnits = getProducerController().getProducedUnits();
            double lossRatio = producedUnits > 0
                    ? (double) getLossModel().getLostCount() / producedUnits // CORRECTED: Was lossModel
                    : 0.0;

            double threshold = getLevelManager().getCurrentLevel().getMaxLossRatio();

            if (lossRatio < threshold) {
                if (!isHeadless) SwingUtilities.invokeLater(() ->
                        getLevelManager().reportLevelCompleted());
            } else {
                if (!isHeadless) SwingUtilities.invokeLater(() ->
                        getScreenController().showScreen(ScreenController.GAME_OVER));
            }
        }
    }



    private boolean producerIsFinishedAndGameIsStable() {
        if (getProducerController() == null || !getProducerController().isFinished()) {
            return false;
        }

        boolean allWiresEmpty = getWires().stream()
                .allMatch(w -> w.getPackets().isEmpty());

        boolean allBoxesEmpty = getBoxes().stream()
                .allMatch(b -> {
                    if (b.getOutPorts().isEmpty()) { // Sink
                        return !b.hasUnprocessedEntries();
                    }
                    return b.getBitBuffer().isEmpty() &&
                            b.getLargeBuffer().isEmpty() &&
                            !b.hasUnprocessedEntries();
                });

        return allWiresEmpty && allBoxesEmpty;
    }

    public void pauseAutoSave() {
        if (autoSaveController != null) autoSaveController.pause();
    }

    public void resumeAutoSave() {
        if (autoSaveController != null) autoSaveController.resume();
    }

    public void stopAutoSave() {
        if (autoSaveController != null) autoSaveController.stop();
    }

    public void stopAutoSaveAndClear() {
        if (autoSaveController != null) autoSaveController.stop();
        AutoSaveController.clearSavedProgress();
    }

    // GETTERS and SETTERS
    public boolean isHeadless() { return isHeadless; }
    public ScreenController getScreenController() { return screenController; }
    public TimelineController getTimeline() { return simulationCoreManager.getTimeline(); }
    public SnapshotManager getSnapshotMgr() { return snapshotCoreController.getSnapshotMgr(); }
    public HudView getHudView() { return hudView; }
    public HudCoordinator getHudCoord() { return hudCoord; }
    public ShopController getShopController() { return shopController; }
    public CollisionController getCollisionController() { return simulationCoreManager.getCollisionController(); }
    public SnapshotService getSnapshotSvc() { return snapshotCoreController.getSnapshotSvc(); }
    public SimulationRegistrar getRegistrar() { return simulationCoreManager.getRegistrar(); }
    public PacketRenderController getPacketRenderer() { return simulationCoreManager.getPacketRenderer(); }
    public LevelManager getLevelManager() { return levelCoreManager.getLevelManager(); }
    public WireCreationController getWireCreator() { return wireCreator; } // Getter added
    public GameScreenView getGameView() { return gameView; }
    public List<WireModel> getWires() { return simulationCoreManager.getWires(); }
    public SimulationController getSimulation() { return simulationCoreManager.getSimulation(); }
    public CoinModel getCoinModel() { return simulationCoreManager.getCoinModel(); }
    public PacketLossModel getLossModel() { return simulationCoreManager.getLossModel(); }
    public ScoreModel getScoreModel() { return simulationCoreManager.getScoreModel(); }
    public HudController getHudController() { return hudController; }
    public PacketProducerController getProducerController() { return simulationCoreManager.getProducerController(); }
    public List<SystemBoxModel> getBoxes() { return levelCoreManager.getBoxes(); }
    public Map<WireModel, SystemBoxModel> getDestMap() { return levelCoreManager.getDestMap(); }
    public JFrame getMainFrame() { return mainFrame; }

    public void setScreenController(ScreenController sc) { this.screenController = sc; }
    public void setHudController(HudController hudController) { this.hudController = hudController; }
    public void setShopController(ShopController shopController) { this.shopController = shopController; }
    public void setSnapshotSvc(SnapshotService snapshotSvc) { this.snapshotCoreController.snapshotSvc = snapshotSvc; }
    public void setRegistrar(SimulationRegistrar registrar) { simulationCoreManager.setRegistrar(registrar); }
    public void setPacketRenderer(PacketRenderController packetRenderer) { simulationCoreManager.setPacketRenderer(packetRenderer); }
    public void setProducerController(PacketProducerController producerController) { simulationCoreManager.setProducerController(producerController); }
    public void setWireCreator(WireCreationController wireCreator) { this.wireCreator = wireCreator; }
    public void setFreezeController(AccelerationFreezeController controller) { this.freezeController = controller; }
}
// blueprinthell/controller/pvp/PvPClientController.java
package com.blueprinthell.controller.pvp;

import com.blueprinthell.client.network.ConnectionManager;
import com.blueprinthell.controller.GameController;
import com.blueprinthell.controller.ui.ScreenController;
import com.blueprinthell.model.*;
import com.blueprinthell.shared.protocol.NetworkProtocol.*;
import com.blueprinthell.view.pvp.*;
import com.blueprinthell.view.screens.GameScreenView;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * کنترلر PvP در سمت کلاینت
 * مدیریت ارتباط با سرور و UI های PvP
 */
public class PvPClientController {

    // Core controllers
    private final GameController gameController; //  فقط برای دسترسی به viewها و مدیریت level در فاز ساخت
    private final ScreenController screenController;
    private final ConnectionManager connectionManager;

    // PvP state
    private String currentMatchId;
    private String opponentUsername;
    private int playerSide; // 1 or 2
    private PvPPhase currentPhase = PvPPhase.IDLE;

    // Build phase
    private PvPBuildView buildView;
    private final AtomicBoolean isReady = new AtomicBoolean(false);
    private int extendStage = 0;
    private Timer layoutUpdateTimer;

    // Match phase
    private PvPMatchView matchView;
    private PvPGameController pvpGameController; // کنترلر جدید برای رندر
    private OpponentNetworkRenderer opponentRenderer;
    // ### START OF PATCH 11 ###
    private List<WireLayout> ownWires;
    private List<WireLayout> opponentWires;
    // ### END OF PATCH 11 ###

    enum PvPPhase {
        IDLE, QUEUING, BUILD, COUNTDOWN, MATCH, ENDED
    }

    /**
     * Constructor
     */
    public PvPClientController(GameController gameController,
                               ScreenController screenController,
                               ConnectionManager connectionManager) {
        this.gameController = gameController;
        this.screenController = screenController;
        this.connectionManager = connectionManager;
        // ### START OF PATCH 12 ###
        this.ownWires = new ArrayList<>();
        this.opponentWires = new ArrayList<>();
        // ### END OF PATCH 12 ###

        registerMessageHandlers();
    }

    private void registerMessageHandlers() {
        connectionManager.registerHandler(MessageType.QUEUE_STATUS, this::handleQueueStatus);
        connectionManager.registerHandler(MessageType.MATCH_FOUND, this::handleMatchFound);
        connectionManager.registerHandler(MessageType.BUILD_TICK, this::handleBuildTick);
        connectionManager.registerHandler(MessageType.EXTEND_GRANTED, this::handleExtendGranted);
        connectionManager.registerHandler(MessageType.MATCH_START, this::handleMatchStart);
        connectionManager.registerHandler(MessageType.TICK, this::handleTick);
        connectionManager.registerHandler(MessageType.MATCH_END, this::handleMatchEnd);
    }

    public void startQueue() {
        if (connectionManager.getState() != ConnectionManager.ConnectionState.CONNECTED) {
            JOptionPane.showMessageDialog(null,
                    "Not connected to server", "Connection Required",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        currentPhase = PvPPhase.QUEUING;
        QueueForMatch queue = new QueueForMatch(connectionManager.getUserId());
        connectionManager.sendMessage(queue);
        showQueueingView();
    }

    public void cancelQueue() {
        if (currentPhase != PvPPhase.QUEUING) return;
        connectionManager.sendMessage(new Message(MessageType.CANCEL_QUEUE));
        currentPhase = PvPPhase.IDLE;
        screenController.showScreen(ScreenController.MAIN_MENU);
    }

    // === Message Handlers ===

    private void handleQueueStatus(Message msg) {
        if (!(msg instanceof QueueStatus status)) return;
        SwingUtilities.invokeLater(() -> {
            if (buildView != null) {
                buildView.updateQueueStatus(status.position, status.estimatedWaitSeconds);
            }
        });
    }

    private void handleMatchFound(Message msg) {
        if (!(msg instanceof MatchFound found)) return;
        currentMatchId = found.matchId;
        opponentUsername = found.opponentUsername;
        playerSide = found.playerSide;
        currentPhase = PvPPhase.BUILD;
        SwingUtilities.invokeLater(this::startBuildPhase);
    }

    private void handleBuildTick(Message msg) {
        if (!(msg instanceof BuildTick tick)) return;
        SwingUtilities.invokeLater(() -> {
            if (buildView != null) {
                buildView.updateTimer(tick.remainingSeconds);
                buildView.updateReadyStatus(
                        playerSide == 1 ? tick.p1Ready : tick.p2Ready,
                        playerSide == 1 ? tick.p2Ready : tick.p1Ready
                );
                if (tick.activePenalty != null) {
                    buildView.showPenalty(tick.activePenalty);
                }
            }
        });
    }

    private void handleExtendGranted(Message msg) {
        if (!(msg instanceof ExtendGranted granted)) return;
        extendStage = granted.stage;
        SwingUtilities.invokeLater(() -> {
            if (buildView != null) {
                buildView.showExtendGranted(granted.stage, granted.penaltyType);
            }
        });
    }

    private void handleMatchStart(Message msg) {
        if (!(msg instanceof MatchStart start)) return;
        currentPhase = PvPPhase.COUNTDOWN;
        // ### START OF PATCH 13 ###
        this.opponentWires = start.opponentWires;
        this.ownWires = start.ownWires;
        // ### END OF PATCH 13 ###

        SwingUtilities.invokeLater(() -> {
            if (layoutUpdateTimer != null) {
                layoutUpdateTimer.stop();
            }
            gameController.getGameView().setTopControls(null); // پاک کردن UI فاز ساخت

            opponentRenderer = new OpponentNetworkRenderer(start.opponentBoxes, start.opponentWires, gameController.getGameView());
            opponentRenderer.render(); // نمایش شبکه حریف

            // نمایش شمارش معکوس
            showCountdown(start.countdownSeconds, this::startMatch);
        });
    }

    private void handleTick(Message msg) {
        if (!(msg instanceof Tick tick) || tick.state == null) return;
        if (pvpGameController != null) {
            pvpGameController.updateState(tick.state);
        }
    }

    private void handleMatchEnd(Message msg) {
        if (!(msg instanceof MatchEnd end)) return;
        currentPhase = PvPPhase.ENDED;

        SwingUtilities.invokeLater(() -> {
            boolean playerWon = (end.winnerSide == playerSide);
            PvPResultView resultView = new PvPResultView(
                    playerWon,
                    playerSide == 1 ? end.finalScoreP1 : end.finalScoreP2,
                    playerSide == 1 ? end.finalScoreP2 : end.finalScoreP1,
                    end.xpEarned,
                    opponentUsername
            );
            screenController.registerCustomView(ScreenController.PVP_RESULT, resultView);
            screenController.showScreen(ScreenController.PVP_RESULT);
            cleanup();
        });
    }

    private void startBuildPhase() {
        gameController.getSimulation().stop(); // توقف شبیه‌سازی بازی تک‌نفره
        gameController.getSimulation().clearUpdatables();

        gameController.getLevelManager().loadLevel(1); // بارگذاری یک زمین خالی برای ساخت شبکه
        gameController.startLevel(gameController.getLevelManager().getCurrentDefinition());

        buildView = new PvPBuildView(gameController, opponentUsername, playerSide);
        buildView.setReadyCallback(this::setReady);
        buildView.setExtendCallback(this::requestExtend);
        gameController.getGameView().setTopControls(buildView);
        screenController.showScreen(ScreenController.GAME_SCREEN);

        layoutUpdateTimer = new Timer(1000, e -> sendLayoutUpdate());
        layoutUpdateTimer.setRepeats(true);
        layoutUpdateTimer.start();
    }

    private void setReady(boolean ready) {
        isReady.set(ready);
        ReadyState readyMsg = new ReadyState(currentMatchId, ready);
        connectionManager.sendMessage(readyMsg);
    }

    private void requestExtend() {
        if (extendStage >= 3) return;
        ExtendRequest extend = new ExtendRequest(currentMatchId, extendStage + 1);
        connectionManager.sendMessage(extend);
    }

    private void sendLayoutUpdate() {
        if (currentPhase != PvPPhase.BUILD) return;
        SubmitLayout layout = new SubmitLayout(currentMatchId);

        for (SystemBoxModel box : gameController.getBoxes()) {
            SystemLayout sysLayout = new SystemLayout();
            sysLayout.id = box.getId();
            sysLayout.x = box.getX();
            sysLayout.y = box.getY();
            sysLayout.width = box.getWidth();
            sysLayout.height = box.getHeight();
            sysLayout.inShapes = box.getInShapes().stream().map(Enum::toString).toList();
            sysLayout.outShapes = box.getOutShapes().stream().map(Enum::toString).toList();
            sysLayout.kind = box.getPrimaryKind().toString();
            sysLayout.isSource = box.getInPorts().isEmpty();
            sysLayout.isSink = box.getOutPorts().isEmpty();
            layout.boxes.add(sysLayout);
        }

        for (WireModel wire : gameController.getWires()) {
            WireLayout wireLayout = new WireLayout();
            wireLayout.id = wire.getCanonicalId();
            wireLayout.fromBoxId = findBoxIdForPort(wire.getSrcPort());
            wireLayout.fromOutIndex = wire.getFromOutIndex();
            wireLayout.toBoxId = findBoxIdForPort(wire.getDstPort());
            wireLayout.toInIndex = wire.getToInIndex();
            wireLayout.path = new ArrayList<>();
            for (Point p : wire.getPath().getPoints()) {
                wireLayout.path.add(new WireLayout.Point2D(p.x, p.y));
            }
            layout.wires.add(wireLayout);
        }
        connectionManager.sendMessage(layout);
    }

    private String findBoxIdForPort(PortModel port) {
        for (SystemBoxModel box : gameController.getBoxes()) {
            if (box.getInPorts().contains(port) || box.getOutPorts().contains(port)) {
                return box.getId();
            }
        }
        return null;
    }

    private void startMatch() {
        currentPhase = PvPPhase.MATCH;
        matchView = new PvPMatchView(gameController.getGameView());
        matchView.setInjectCallback(this::injectPacket);

        // ### START OF PATCH 14 ###
        pvpGameController = new PvPGameController(gameController.getGameView(), matchView, ownWires, opponentWires, playerSide);
        // ### END OF PATCH 14 ###
        gameController.getGameView().setTopControls(matchView);
    }

    private void injectPacket(String systemId) {
        Inject inject = new Inject(currentMatchId, systemId);
        connectionManager.sendMessage(inject);
    }

    private void showQueueingView() {
        JPanel queuePanel = new JPanel();
        queuePanel.add(new JLabel("Searching for opponent..."));
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> cancelQueue());
        queuePanel.add(cancelButton);
        screenController.registerCustomView(ScreenController.PVP_QUEUE, queuePanel);
        screenController.showScreen(ScreenController.PVP_QUEUE);
    }

    private void showCountdown(int seconds, Runnable onFinished) {
        GameScreenView gsv = gameController.getGameView();
        JLabel countdownLabel = new JLabel(String.valueOf(seconds));
        countdownLabel.setFont(new Font("Arial", Font.BOLD, 72));
        countdownLabel.setForeground(Color.YELLOW);
        countdownLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel overlay = new JPanel(new BorderLayout());
        overlay.setOpaque(false);
        overlay.add(countdownLabel, BorderLayout.CENTER);
        overlay.setBounds(0, 0, gsv.getWidth(), gsv.getHeight());

        gsv.add(overlay);
        gsv.setComponentZOrder(overlay, 0);
        gsv.revalidate();
        gsv.repaint();

        AtomicInteger countdown = new AtomicInteger(seconds);
        Timer countdownTimer = new Timer(1000, null);
        countdownTimer.addActionListener(e -> {
            int remaining = countdown.decrementAndGet();
            if (remaining > 0) {
                countdownLabel.setText(String.valueOf(remaining));
            } else {
                ((Timer) e.getSource()).stop();
                gsv.remove(overlay);
                gsv.revalidate();
                gsv.repaint();
                onFinished.run();
            }
        });
        countdownTimer.setRepeats(true);
        countdownTimer.start();
    }

    private void cleanup() {
        currentMatchId = null;
        opponentUsername = null;
        playerSide = 0;
        currentPhase = PvPPhase.IDLE;
        isReady.set(false);
        extendStage = 0;

        if (pvpGameController != null) {
            pvpGameController.cleanup();
            pvpGameController = null;
        }
        if (opponentRenderer != null) {
            opponentRenderer.cleanup();
            opponentRenderer = null;
        }
        if(layoutUpdateTimer != null) {
            layoutUpdateTimer.stop();
            layoutUpdateTimer = null;
        }
        screenController.removeCustomView(ScreenController.PVP_QUEUE);
        screenController.removeCustomView(ScreenController.PVP_RESULT);
    }
}
package com.blueprinthell.controller.pvp;

import com.blueprinthell.client.network.ConnectionManager;
import com.blueprinthell.controller.GameController;
import com.blueprinthell.controller.ui.ScreenController;
import com.blueprinthell.model.*;
import com.blueprinthell.shared.protocol.NetworkProtocol.*;
import com.blueprinthell.view.pvp.*;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * کنترلر PvP در سمت کلاینت
 * مدیریت ارتباط با سرور و UI های PvP
 */
public class PvPClientController {

    // Core controllers
    private final GameController gameController;
    private final ScreenController screenController;
    private final ConnectionManager connectionManager;

    // PvP state
    private String currentMatchId;
    private String opponentId;
    private String opponentUsername;
    private int playerSide; // 1 or 2
    private PvPPhase currentPhase = PvPPhase.IDLE;

    // Build phase
    private PvPBuildView buildView;
    private final AtomicBoolean isReady = new AtomicBoolean(false);
    private int buildTimeRemaining = 30;
    private int extendStage = 0;


    // Match phase
    private PvPMatchView matchView;
    private OpponentNetworkRenderer opponentRenderer;
    private int playerAmmo = 3;
    private final Map<String, SystemCooldowns> systemCooldowns = new HashMap<>();

    // Score tracking
    private int playerDelivered = 0;
    private int playerLost = 0;
    private int opponentDelivered = 0;
    private int opponentLost = 0;

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

        registerMessageHandlers();
    }

    /**
     * Register message handlers for PvP messages
     */
    private void registerMessageHandlers() {
        connectionManager.registerHandler(MessageType.QUEUE_STATUS, this::handleQueueStatus);
        connectionManager.registerHandler(MessageType.MATCH_FOUND, this::handleMatchFound);
        connectionManager.registerHandler(MessageType.BUILD_TICK, this::handleBuildTick);
        connectionManager.registerHandler(MessageType.EXTEND_GRANTED, this::handleExtendGranted);
        connectionManager.registerHandler(MessageType.MATCH_START, this::handleMatchStart);
        connectionManager.registerHandler(MessageType.TICK, this::handleTick);
        connectionManager.registerHandler(MessageType.MATCH_END, this::handleMatchEnd);
    }

    /**
     * Start queueing for PvP match
     */
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

        // Show queue UI
        showQueueingView();
    }

    /**
     * Cancel queue
     */
    public void cancelQueue() {
        if (currentPhase != PvPPhase.QUEUING) return;

        connectionManager.sendMessage(new Message(MessageType.CANCEL_QUEUE));
        currentPhase = PvPPhase.IDLE;

        // Return to main menu
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
        opponentId = found.opponentId;
        opponentUsername = found.opponentUsername;
        playerSide = found.playerSide;
        currentPhase = PvPPhase.BUILD;

        SwingUtilities.invokeLater(() -> {
            startBuildPhase();
        });
    }

    private void handleBuildTick(Message msg) {
        if (!(msg instanceof BuildTick tick)) return;

        buildTimeRemaining = tick.remainingSeconds;

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

        SwingUtilities.invokeLater(() -> {
            // Create opponent renderer
            opponentRenderer = new OpponentNetworkRenderer(
                    start.opponentBoxes,
                    start.opponentWires,
                    gameController.getGameView()
            );

            // Show countdown
            showCountdown(start.countdownSeconds);

            // Start match after countdown
            Timer countdownTimer = new Timer(start.countdownSeconds * 1000, e -> {
                currentPhase = PvPPhase.MATCH;
                startMatch();
            });
            countdownTimer.setRepeats(false);
            countdownTimer.start();
        });
    }

    private void handleTick(Message msg) {
        if (!(msg instanceof Tick tick)) return;

        // Update scores
        if (playerSide == 1) {
            playerDelivered = tick.scoreP1.delivered;
            playerLost = tick.scoreP1.lost;
            playerAmmo = tick.scoreP1.ammo;
            opponentDelivered = tick.scoreP2.delivered;
            opponentLost = tick.scoreP2.lost;
        } else {
            playerDelivered = tick.scoreP2.delivered;
            playerLost = tick.scoreP2.lost;
            playerAmmo = tick.scoreP2.ammo;
            opponentDelivered = tick.scoreP1.delivered;
            opponentLost = tick.scoreP1.lost;
        }

        // Update system cooldowns
        for (SystemState state : tick.systems) {
            SystemCooldowns cooldowns = systemCooldowns.computeIfAbsent(
                    state.id, k -> new SystemCooldowns());

            cooldowns.systemCooldown = state.systemCooldownMs;
            if (playerSide == 1) {
                cooldowns.playerCooldown = state.packetCooldownMsP1;
            } else {
                cooldowns.playerCooldown = state.packetCooldownMsP2;
            }
        }

        // Update UI
        SwingUtilities.invokeLater(() -> {
            if (matchView != null) {
                matchView.updateScores(playerDelivered, playerLost,
                        opponentDelivered, opponentLost);
                matchView.updateAmmo(playerAmmo);
                matchView.updateSpeedMultiplier(tick.globalSpeedMultiplier);
            }
        });
    }

    private void handleMatchEnd(Message msg) {
        if (!(msg instanceof MatchEnd end)) return;

        currentPhase = PvPPhase.ENDED;

        SwingUtilities.invokeLater(() -> {
            // Show results
            boolean playerWon = (end.winnerSide == playerSide);

            PvPResultView resultView = new PvPResultView(
                    playerWon,
                    end.finalScoreP1,
                    end.finalScoreP2,
                    end.xpEarned,
                    opponentUsername
            );

            screenController.showCustomView(resultView);

            // Clean up
            cleanup();
        });
    }

    // === Build Phase ===

    private void startBuildPhase() {
        // Create build view
        buildView = new PvPBuildView(
                gameController,
                opponentUsername,
                playerSide
        );

        // Set callbacks
        buildView.setReadyCallback(this::setReady);
        buildView.setExtendCallback(this::requestExtend);

        // Show build view
        screenController.showCustomView(buildView);

        // Start sending layout updates
        Timer layoutTimer = new Timer(1000, e -> sendLayoutUpdate());
        layoutTimer.start();
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

        // Capture current layout
        SubmitLayout layout = new SubmitLayout(currentMatchId);

        // Convert boxes
        for (SystemBoxModel box : gameController.getBoxes()) {
            SystemLayout systemLayout = new SystemLayout();
            systemLayout.id = box.getId();
            systemLayout.x = box.getX();
            systemLayout.y = box.getY();
            systemLayout.width = box.getWidth();
            systemLayout.height = box.getHeight();
            systemLayout.inShapes = convertShapes(box.getInShapes());
            systemLayout.outShapes = convertShapes(box.getOutShapes());
            systemLayout.kind = box.getPrimaryKind().toString();
            // Set source/sink based on ports
            systemLayout.isSource = box.getInPorts().isEmpty();
            systemLayout.isSink = box.getOutPorts().isEmpty();

            layout.boxes.add(systemLayout);
        }

        // Convert wires
        for (WireModel wire : gameController.getWires()) {
            WireLayout wireLayout = new WireLayout();
            wireLayout.id = wire.getCanonicalId();
            wireLayout.fromBoxId = findBoxForPort(wire.getSrcPort());
            wireLayout.fromOutIndex = wire.getFromOutIndex();
            wireLayout.toBoxId = findBoxForPort(wire.getDstPort());
            wireLayout.toInIndex = wire.getToInIndex();

            // Convert path
            wireLayout.path = new ArrayList<>();
            for (Point p : wire.getPath().getPoints()) {
                wireLayout.path.add(new WireLayout.Point2D(p.x, p.y));
            }

            layout.wires.add(wireLayout);
        }


        connectionManager.sendMessage(layout);
    }

    private String findBoxForPort(PortModel port) {
        for (SystemBoxModel box : gameController.getBoxes()) {
            if (box.getInPorts().contains(port) || box.getOutPorts().contains(port)) {
                return box.getId();
            }
        }
        return "";
    }

    private List<String> convertShapes(List<PortShape> shapes) {
        List<String> result = new ArrayList<>();
        for (PortShape shape : shapes) {
            result.add(shape.toString());
        }
        return result;
    }

    // === Match Phase ===

    private void showCountdown(int seconds) {
        // Show countdown overlay
        JLabel countdownLabel = new JLabel(String.valueOf(seconds));
        countdownLabel.setFont(new Font("Arial", Font.BOLD, 72));
        countdownLabel.setForeground(Color.YELLOW);
        countdownLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel overlay = new JPanel(new BorderLayout());
        overlay.setOpaque(false);
        overlay.add(countdownLabel, BorderLayout.CENTER);

        gameController.getGameView().add(overlay);
        gameController.getGameView().setComponentZOrder(overlay, 0);
    }

    private void startMatch() {
        // Create match view
        matchView = new PvPMatchView(gameController.getGameView());

        // Set inject callback
        matchView.setInjectCallback(this::injectPacket);

        // Start game simulation
        gameController.getSimulation().start();

        // Render opponent network
        if (opponentRenderer != null) {
            opponentRenderer.render();
        }
    }

    private void injectPacket(String systemId) {
        // Check cooldowns
        SystemCooldowns cooldowns = systemCooldowns.get(systemId);
        if (cooldowns != null) {
            if (cooldowns.systemCooldown > 0 || cooldowns.playerCooldown > 0) {
                // Show cooldown message
                return;
            }
        }

        // Check ammo
        if (playerAmmo <= 0) {
            // Show no ammo message
            return;
        }

        // Send inject message
        Inject inject = new Inject(currentMatchId, systemId);
        connectionManager.sendMessage(inject);
    }

    // === Utility ===

    private void showQueueingView() {
        JPanel queuePanel = new JPanel();
        queuePanel.add(new JLabel("Searching for opponent..."));

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> cancelQueue());
        queuePanel.add(cancelButton);

        screenController.showCustomView(queuePanel);
    }

    private void cleanup() {
        currentMatchId = null;
        opponentId = null;
        opponentUsername = null;
        playerSide = 0;
        currentPhase = PvPPhase.IDLE;
        isReady.set(false);
        extendStage = 0;
        systemCooldowns.clear();

        if (opponentRenderer != null) {
            opponentRenderer.cleanup();
            opponentRenderer = null;
        }
    }

    // Internal classes

    static class SystemCooldowns {
        int systemCooldown = 0;
        int playerCooldown = 0;
    }
}
package com.blueprinthell.controller.pvp;

import com.blueprinthell.client.network.ConnectionManager;
import com.blueprinthell.controller.GameController;
import com.blueprinthell.controller.ui.ScreenController;
import com.blueprinthell.model.*;
import com.blueprinthell.shared.protocol.NetworkProtocol.*;
import com.blueprinthell.snapshot.NetworkSnapshot;
import com.blueprinthell.view.pvp.*;
import com.blueprinthell.view.screens.GameScreenView;
import com.google.gson.Gson;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * The primary client-side controller for PvP.
 * Manages the connection, UI state machine (queuing, build, match),
 * sends player actions to the server, and updates the local GameController
 * with authoritative state received from the server.
 */
public class PvPClientController {

    private final GameController gameController;
    private final ScreenController screenController;
    private final ConnectionManager connectionManager;
    private final Gson gson = new Gson(); // For deserializing snapshots

    private String currentMatchId;
    private String opponentUsername;
    private int playerSide;
    private PvPPhase currentPhase = PvPPhase.IDLE;

    // UI Views
    private PvPBuildView buildView;
    private PvPMatchView matchView;
    private OpponentNetworkRenderer opponentRenderer;
    private JPanel queuePanel; // For easy removal

    // State
    private final AtomicBoolean isReady = new AtomicBoolean(false);
    private int extendStage = 0;
    private Timer layoutUpdateTimer;

    enum PvPPhase {
        IDLE, QUEUING, BUILD, COUNTDOWN, MATCH, ENDED
    }

    public PvPClientController(GameController gameController,
                               ScreenController screenController,
                               ConnectionManager connectionManager) {
        this.gameController = gameController;
        this.screenController = screenController;
        this.connectionManager = connectionManager;
        registerMessageHandlers();
    }

    private void registerMessageHandlers() {
        connectionManager.registerHandler(MessageType.QUEUE_STATUS, this::handleQueueStatus);
        connectionManager.registerHandler(MessageType.MATCH_FOUND, this::handleMatchFound);
        connectionManager.registerHandler(MessageType.BUILD_TICK, this::handleBuildTick);
        connectionManager.registerHandler(MessageType.EXTEND_GRANTED, this::handleExtendGranted);
        connectionManager.registerHandler(MessageType.MATCH_START, this::handleMatchStart);
        // The most important handler: GAME_STATE_UPDATE (replaces TICK)
        connectionManager.registerHandler(MessageType.GAME_STATE_UPDATE, this::handleGameStateUpdate);
        connectionManager.registerHandler(MessageType.MATCH_END, this::handleMatchEnd);
    }

    // --- Public Methods to Send Actions to Server ---

    public void sendBuyItemAction(String itemId) {
        if (currentPhase == PvPPhase.MATCH && currentMatchId != null) {
            connectionManager.sendMessage(new PlayerAction_BuyItem(currentMatchId, itemId));
        }
    }

    // --- State Machine and Handlers ---

    public void startQueue() {
        if (connectionManager.getState() != ConnectionManager.ConnectionState.CONNECTED) {
            JOptionPane.showMessageDialog(null, "Not connected to server", "Connection Required", JOptionPane.WARNING_MESSAGE);
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

    private void handleQueueStatus(Message msg) {
        if (!(msg instanceof QueueStatus status)) return;
        SwingUtilities.invokeLater(() -> {
            // This view might be complex, so let's keep a reference to update it
            if (queuePanel != null) {
                for(Component comp : queuePanel.getComponents()) {
                    if (comp instanceof JLabel label) {
                        label.setText("Searching for opponent... Position: " + status.position);
                    }
                }
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

        SwingUtilities.invokeLater(() -> {
            if (layoutUpdateTimer != null) {
                layoutUpdateTimer.stop();
            }
            gameController.getGameView().setTopControls(null);

            // Create opponent renderer (can be enhanced later to use opponent snapshot)
            opponentRenderer = new OpponentNetworkRenderer(start.opponentBoxes, start.opponentWires, gameController.getGameView());
            opponentRenderer.render();

            showCountdown(start.countdownSeconds, this::startMatch);
        });
    }

// In PvPClientController.java, ensure this is your handleGameStateUpdate method

    private void handleGameStateUpdate(Message msg) {
        if (!(msg instanceof GameStateUpdate update)) return;

        // A try-catch block is good practice for deserialization
        try {
            update.playerState = gson.fromJson(update.playerStateJson, NetworkSnapshot.class);
            update.opponentState = gson.fromJson(update.opponentStateJson, NetworkSnapshot.class);
        } catch (Exception e) {
            System.err.println("Error deserializing snapshot from server: " + e.getMessage());
            return;
        }

        SwingUtilities.invokeLater(() -> {
            if (currentPhase != PvPPhase.MATCH || update.playerState == null) return;

            // Restore the local GameController to match the server's authoritative state
            gameController.restoreState(update.playerState);

            // Update the PvP-specific HUD (scores, ammo, etc.)
            if (matchView != null) {
                NetworkSnapshot myState = update.playerState;
                NetworkSnapshot oppState = update.opponentState;

                // Extract real score and loss from the snapshots
                int myScore = myState.world.score;
                int myLost = myState.world.packetLoss;
                int oppScore = (oppState != null) ? oppState.world.score : 0;
                int oppLost = (oppState != null) ? oppState.world.packetLoss : 0;

                matchView.updateScores(myScore, myLost, oppScore, oppLost);
                matchView.updateAmmo(myState.world.coins); // Repurpose coins as ammo for PvP
            }
        });
    }
    private void handleMatchEnd(Message msg) {
        if (!(msg instanceof MatchEnd end)) return;
        currentPhase = PvPPhase.ENDED;

        SwingUtilities.invokeLater(() -> {
            boolean playerWon = (end.winnerSide == playerSide);

            // Note: The protocol needs to send PlayerScore objects for the result view
            // We'll create dummy ones for now.
            PlayerScore dummyPlayerScore = new PlayerScore();
            PlayerScore dummyOpponentScore = new PlayerScore();

            PvPResultView resultView = new PvPResultView(
                    playerWon,
                    dummyPlayerScore,
                    dummyOpponentScore,
                    end.xpEarned,
                    opponentUsername
            );
            screenController.registerCustomView(ScreenController.PVP_RESULT, resultView);
            screenController.showScreen(ScreenController.PVP_RESULT);
            cleanup();
        });
    }

    private void startBuildPhase() {
        gameController.getSimulation().stop(); // Stop local simulation
        gameController.getSimulation().clearUpdatables();

        gameController.getLevelManager().loadLevel(1);
        gameController.startLevel(gameController.getLevelManager().getCurrentDefinition());

        buildView = new PvPBuildView(gameController, opponentUsername, playerSide);
        buildView.setReadyCallback(this::setReady);
        buildView.setExtendCallback(this::requestExtend);
        gameController.getGameView().setTopControls(buildView);
        screenController.showScreen(ScreenController.GAME_SCREEN);

        layoutUpdateTimer = new Timer(500, e -> sendLayoutUpdate()); // Send layout updates frequently
        layoutUpdateTimer.setRepeats(true);
        layoutUpdateTimer.start();
    }

    private void startMatch() {
        currentPhase = PvPPhase.MATCH;

        // Stop local simulation permanently for the match duration.
        // The game state will be driven entirely by server updates.
        gameController.getSimulation().stop();

        matchView = new PvPMatchView(gameController.getGameView());
        matchView.setInjectCallback(this::injectPacket);

        gameController.getGameView().setTopControls(matchView);
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

    private void injectPacket(String systemId) {
        String packetType = "MESSENGER";
        Inject inject = new Inject(currentMatchId, systemId, packetType);
        connectionManager.sendMessage(inject);
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

    private void showQueueingView() {
        queuePanel = new JPanel();
        queuePanel.setLayout(new BoxLayout(queuePanel, BoxLayout.Y_AXIS));
        queuePanel.setOpaque(false);

        JLabel statusLabel = new JLabel("Searching for opponent...");
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 24));
        statusLabel.setForeground(Color.WHITE);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        cancelButton.addActionListener(e -> cancelQueue());

        queuePanel.add(Box.createVerticalGlue());
        queuePanel.add(statusLabel);
        queuePanel.add(Box.createRigidArea(new Dimension(0, 20)));
        queuePanel.add(cancelButton);
        queuePanel.add(Box.createVerticalGlue());

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

        if (opponentRenderer != null) {
            opponentRenderer.cleanup();
            opponentRenderer = null;
        }
        if (layoutUpdateTimer != null) {
            layoutUpdateTimer.stop();
            layoutUpdateTimer = null;
        }
        screenController.removeCustomView(ScreenController.PVP_QUEUE);
        screenController.removeCustomView(ScreenController.PVP_RESULT);
    }
}
package com.blueprinthell.server.pvp;

import com.blueprinthell.server.pvp.PvPMatchManager.QueuedPlayer;
import com.blueprinthell.shared.protocol.NetworkProtocol.*;
import com.blueprinthell.snapshot.NetworkSnapshot;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the state machine and lifecycle of a PvP match session.
 * It delegates the actual game simulation to a ServerGameSession instance and
 * handles the flow between build, match, and end phases.
 */
public class PvPGameSession {

    // --- Constants ---
    private static final int BUILD_TIME_SECONDS = 30;
    private static final int EXTEND_TIME_SECONDS = 10;
    private static final int MAX_EXTENDS = 3;
    private static final int COUNTDOWN_SECONDS = 3;
    private static final int MATCH_DURATION_MINUTES = 3;

    // --- Session Data ---
    private final String matchId;
    private final QueuedPlayer player1;
    private final QueuedPlayer player2;
    private final PvPMatchManager.MatchEventHandler eventHandler;
    private final Gson gson = new Gson();

    // --- State Machine ---
    private volatile Phase currentPhase = Phase.BUILD;
    private final AtomicInteger buildTimer = new AtomicInteger(BUILD_TIME_SECONDS);
    private final AtomicInteger extendStage = new AtomicInteger(0);
    private final AtomicBoolean p1Ready = new AtomicBoolean(false);
    private final AtomicBoolean p2Ready = new AtomicBoolean(false);
    private final AtomicInteger frameId = new AtomicInteger(0);

    // --- Game Data ---
    private SubmitLayout layoutP1;
    private SubmitLayout layoutP2;
    private final ServerGameSession serverGameSession; // The real simulation engine

    // --- Timers and Threads ---
    private ScheduledFuture<?> gameLoopTask;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    enum Phase {
        BUILD, COUNTDOWN, MATCH, ENDED
    }

    public PvPGameSession(String matchId, QueuedPlayer p1, QueuedPlayer p2, PvPMatchManager.MatchEventHandler handler) {
        this.matchId = matchId;
        this.player1 = p1;
        this.player2 = p2;
        this.eventHandler = handler;
        this.serverGameSession = new ServerGameSession(matchId);
    }

    /**
     * Starts the build phase for the match.
     */
    public void startBuildPhase() {
        currentPhase = Phase.BUILD;
        buildTimer.set(BUILD_TIME_SECONDS);
        gameLoopTask = executor.scheduleAtFixedRate(this::buildPhaseTick, 0, 1, TimeUnit.SECONDS);
    }


    public void handlePlayerMessage(String sessionId, Message baseMessage, String jsonLine) {
        if (currentPhase == Phase.ENDED) return;

        int playerSide = sessionId.equals(player1.sessionId) ? 1 : 2;
        Message fullMessage = deserializeFullMessage(jsonLine, baseMessage.type);

        if (currentPhase == Phase.MATCH) {
            // In match phase, all actions are applied to the simulation
            if (fullMessage != null) {
                serverGameSession.applyPlayerAction(playerSide, fullMessage);
            }
        } else if (currentPhase == Phase.BUILD) {
            // In build phase, only specific messages are handled
            switch (baseMessage.type) {
                case SUBMIT_LAYOUT -> handleSubmitLayout(playerSide, (SubmitLayout) fullMessage);
                case READY_STATE -> handleReadyState(playerSide, (ReadyState) fullMessage);
                case EXTEND_REQUEST -> handleExtendRequest(sessionId);
                case PLAYER_ACTION_MOVE_BOX,
                     PLAYER_ACTION_CREATE_WIRE,
                     PLAYER_ACTION_REMOVE_WIRE,
                     PLAYER_ACTION_BUY_ITEM -> {
                    if (fullMessage != null) {
                        serverGameSession.applyPlayerAction(playerSide, fullMessage);
                    }
                }
            }
        }
    }

    private void buildPhaseTick() {
        if (currentPhase != Phase.BUILD) return;

        int remaining = buildTimer.decrementAndGet();
        if ((p1Ready.get() && p2Ready.get()) || remaining <= 0) {
            startCountdown();
            return;
        }

        BuildTick tick = new BuildTick(remaining);
        tick.p1Ready = p1Ready.get();
        tick.p2Ready = p2Ready.get();
        tick.extendStage = extendStage.get();

        broadcast(tick);
    }

    private void startCountdown() {
        if (gameLoopTask != null) gameLoopTask.cancel(false);
        currentPhase = Phase.COUNTDOWN;

        // Initialize the simulation engines with the final layouts BEFORE starting the match.
        // This is the core fix for the packet production issue.
        serverGameSession.initializeLayouts(layoutP1, layoutP2);

        MatchStart startMsg = new MatchStart(matchId);
        startMsg.opponentBoxes = layoutP2 != null ? layoutP2.boxes : new ArrayList<>();
        startMsg.opponentWires = layoutP2 != null ? layoutP2.wires : new ArrayList<>();
        startMsg.ownWires = layoutP1 != null ? layoutP1.wires : new ArrayList<>();
        eventHandler.sendMessageToPlayer(player1.sessionId, startMsg);

        startMsg.opponentBoxes = layoutP1 != null ? layoutP1.boxes : new ArrayList<>();
        startMsg.opponentWires = layoutP1 != null ? layoutP1.wires : new ArrayList<>();
        startMsg.ownWires = layoutP2 != null ? layoutP2.wires : new ArrayList<>();
        eventHandler.sendMessageToPlayer(player2.sessionId, startMsg);

        executor.schedule(this::startMatch, COUNTDOWN_SECONDS, TimeUnit.SECONDS);
    }

    private void startMatch() {
        currentPhase = Phase.MATCH;
        serverGameSession.startSimulations();

        serverGameSession.startPacketProduction();

        gameLoopTask = executor.scheduleAtFixedRate(this::matchTick, 0, 33, TimeUnit.MILLISECONDS);
    }
    private void matchTick() {
        if (currentPhase != Phase.MATCH) return;
        int frame = frameId.incrementAndGet();

        serverGameSession.tick(1.0 / 30.0);

        if (frame % 5 == 0) {
            sendTickUpdate();
        }

        if (checkEndConditions()) {
            endMatch();
        }
    }


    private void sendTickUpdate() {
        NetworkSnapshot[] snapshots = serverGameSession.captureSnapshots();

        boolean p1HasPackets = snapshots[0].world.wires.stream().anyMatch(w -> !w.packetsOnWire.isEmpty());
        boolean p2HasPackets = snapshots[1].world.wires.stream().anyMatch(w -> !w.packetsOnWire.isEmpty());





        GameStateUpdate updateP1 = new GameStateUpdate(matchId, frameId.get());
        updateP1.playerStateJson = gson.toJson(snapshots[0]);
        updateP1.opponentStateJson = gson.toJson(snapshots[1]);
        eventHandler.sendMessageToPlayer(player1.sessionId, updateP1);

        GameStateUpdate updateP2 = new GameStateUpdate(matchId, frameId.get());
        updateP2.playerStateJson = gson.toJson(snapshots[1]);
        updateP2.opponentStateJson = gson.toJson(snapshots[0]);
        eventHandler.sendMessageToPlayer(player2.sessionId, updateP2);
    }

    private boolean checkEndConditions() {
        return frameId.get() > MATCH_DURATION_MINUTES * 60 * 60;
    }

    private void endMatch() {
        if (currentPhase == Phase.ENDED) return;
        currentPhase = Phase.ENDED;
        if (gameLoopTask != null) gameLoopTask.cancel(false);
        serverGameSession.stopSimulations();

        NetworkSnapshot[] finalSnaps = serverGameSession.captureSnapshots();
        PlayerScore scoreP1 = snapshotToPlayerScore(finalSnaps[0]);
        PlayerScore scoreP2 = snapshotToPlayerScore(finalSnaps[1]);

        int winnerSide = 0;
        if (scoreP1.totalScore > scoreP2.totalScore) winnerSide = 1;
        else if (scoreP2.totalScore > scoreP1.totalScore) winnerSide = 2;

        int xpP1 = calculateXP(scoreP1, winnerSide == 1);
        int xpP2 = calculateXP(scoreP2, winnerSide == 2);

        MatchEnd endMsg = new MatchEnd(matchId);
        endMsg.finalScoreP1 = scoreP1;
        endMsg.finalScoreP2 = scoreP2;
        endMsg.winnerSide = winnerSide;
        endMsg.xpEarned = (winnerSide == 1) ? xpP1 : xpP2; // Send correct XP to each

        eventHandler.sendMessageToPlayer(player1.sessionId, endMsg);
        endMsg.xpEarned = (winnerSide == 2) ? xpP2 : xpP1;
        eventHandler.sendMessageToPlayer(player2.sessionId, endMsg);

        GameResult resultP1 = createGameResult(player1, scoreP1, winnerSide == 1, xpP1);
        GameResult resultP2 = createGameResult(player2, scoreP2, winnerSide == 2, xpP2);
        eventHandler.onMatchEnded(matchId, resultP1, resultP2);
    }

    private void handleSubmitLayout(int playerSide, SubmitLayout layout) {
        if (currentPhase != Phase.BUILD || layout == null) return;
        if (playerSide == 1) layoutP1 = layout;
        else layoutP2 = layout;
    }

    private void handleReadyState(int playerSide, ReadyState ready) {
        if (currentPhase != Phase.BUILD || ready == null) return;
        if (playerSide == 1) p1Ready.set(ready.isReady);
        else p2Ready.set(ready.isReady);
    }

    private void handleExtendRequest(String sessionId) {
        if (currentPhase != Phase.BUILD || extendStage.get() >= MAX_EXTENDS) return;

        extendStage.incrementAndGet();
        buildTimer.addAndGet(EXTEND_TIME_SECONDS);

        ExtendGranted granted = new ExtendGranted(extendStage.get(), buildTimer.get(), "PENIA");
        eventHandler.sendMessageToPlayer(sessionId, granted);
    }

    private Message deserializeFullMessage(String json, MessageType type) {
        return switch (type) {
            case SUBMIT_LAYOUT -> gson.fromJson(json, SubmitLayout.class);
            case READY_STATE -> gson.fromJson(json, ReadyState.class);
            case INJECT -> gson.fromJson(json, Inject.class);
            case PLAYER_ACTION_MOVE_BOX -> gson.fromJson(json, PlayerAction_MoveBox.class);
            case PLAYER_ACTION_CREATE_WIRE -> gson.fromJson(json, PlayerAction_CreateWire.class);
            case PLAYER_ACTION_REMOVE_WIRE -> gson.fromJson(json, PlayerAction_RemoveWire.class);
            case PLAYER_ACTION_BUY_ITEM -> gson.fromJson(json, PlayerAction_BuyItem.class);
            default -> null;
        };
    }

    private PlayerScore snapshotToPlayerScore(NetworkSnapshot snap) {
        PlayerScore ps = new PlayerScore();
        ps.totalScore = snap.world.score;
        ps.lost = snap.world.packetLoss;
        ps.delivered = (snap.meta.producedUnits - ps.lost);
        ps.ammo = snap.world.coins; // Using coins as ammo
        return ps;
    }

    private int calculateXP(PlayerScore score, boolean isWinner) {
        double xp = 10 * score.delivered - 5 * score.lost;
        if (isWinner) xp += 100;
        return Math.max(0, (int)xp);
    }

    private GameResult createGameResult(QueuedPlayer player, PlayerScore score, boolean isWinner, int xp) {
        GameResult result = new GameResult();
        result.userId = player.userId;
        result.mode = GameMode.MULTIPLAYER_PVP;
        result.ruleset = Ruleset.MP_MSG_ONLY_V1;
        result.matchId = matchId;
        result.isWinner = isWinner;
        result.delivered = score.delivered;
        result.loss = score.lost;
        result.score = score.totalScore;
        result.xp = xp;
        result.durationMs = frameId.get() * 16;
        result.playerSide = (player.sessionId.equals(player1.sessionId)) ? 1 : 2;
        result.opponentId = (player.sessionId.equals(player1.sessionId)) ? player2.userId : player1.userId;
        return result;
    }

    private void broadcast(Message message) {
        eventHandler.sendMessageToPlayer(player1.sessionId, message);
        eventHandler.sendMessageToPlayer(player2.sessionId, message);
    }

    public void stop() {
        currentPhase = Phase.ENDED;
        if (gameLoopTask != null) gameLoopTask.cancel(true);
        executor.shutdownNow();
        if (serverGameSession != null) serverGameSession.stopSimulations();
    }

    public QueuedPlayer getPlayer1() { return player1; }
    public QueuedPlayer getPlayer2() { return player2; }
}
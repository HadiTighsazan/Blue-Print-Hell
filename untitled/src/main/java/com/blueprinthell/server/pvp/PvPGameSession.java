package com.blueprinthell.server.pvp;

import com.blueprinthell.server.pvp.PvPMatchManager.QueuedPlayer;
import com.blueprinthell.shared.protocol.NetworkProtocol.*;
import com.blueprinthell.server.pvp.MessengerGameSimulation.NetworkLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * مدیریت یک session بازی PvP
 * شامل Build Phase و Match Phase
 */
public class PvPGameSession {

    private static final int BUILD_TIME_SECONDS = 30;
    private static final int EXTEND_TIME_SECONDS = 10;
    private static final int MAX_EXTENDS = 3;
    private static final int COUNTDOWN_SECONDS = 3;

    // Session data
    private final String matchId;
    private final QueuedPlayer player1;
    private final QueuedPlayer player2;
    private final PvPMatchManager.MatchEventHandler eventHandler;

    // Build phase state
    private volatile Phase currentPhase = Phase.BUILD;
    private final AtomicInteger buildTimer = new AtomicInteger(BUILD_TIME_SECONDS);
    private final AtomicInteger extendStage = new AtomicInteger(0);
    private final AtomicBoolean p1Ready = new AtomicBoolean(false);
    private final AtomicBoolean p2Ready = new AtomicBoolean(false);

    // Player layouts
    private SubmitLayout layoutP1;
    private SubmitLayout layoutP2;

    // Match phase state
    private final AtomicInteger frameId = new AtomicInteger(0);
    private PvPGameState gameState; // وضعیت مرکزی بازی

    // Penalties
    private volatile String activePenalty = null;
    private final Random peniaRandom = new Random();

    // Executor
    private ScheduledFuture<?> gameLoopTask;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    // Simulation
    private MessengerGameSimulation simulation;

    enum Phase {
        BUILD, COUNTDOWN, MATCH, ENDED
    }

    public PvPGameSession(String matchId, QueuedPlayer p1, QueuedPlayer p2, PvPMatchManager.MatchEventHandler handler) {
        this.matchId = matchId;
        this.player1 = p1;
        this.player2 = p2;
        this.eventHandler = handler;
    }

    /**
     * Start build phase
     */
    public void startBuildPhase() {
        currentPhase = Phase.BUILD;
        buildTimer.set(BUILD_TIME_SECONDS);
        gameLoopTask = executor.scheduleAtFixedRate(this::buildPhaseTick, 0, 1, TimeUnit.SECONDS);
    }

    private void buildPhaseTick() {
        if (currentPhase != Phase.BUILD) return;

        int remaining = buildTimer.decrementAndGet();

        if (p1Ready.get() && p2Ready.get()) {
            startCountdown();
            return;
        }

        if (remaining <= 0) {
            startCountdown();
            return;
        }

        if (extendStage.get() > 0) {
            applyExtendPenalty();
        }

        BuildTick tick = new BuildTick(remaining);
        tick.p1Ready = p1Ready.get();
        tick.p2Ready = p2Ready.get();
        tick.extendStage = extendStage.get();
        tick.activePenalty = activePenalty;

        broadcast(tick);
    }

    private void applyExtendPenalty() {
        int stage = extendStage.get();
        if (gameState == null) return; // اطمینان از اینکه gameState قبل از اعمال جریمه وجود دارد

        if (stage == 1) {
            if (peniaRandom.nextInt(10) < 2) {
                gameState.scoreP1.ammo = Math.min(gameState.scoreP1.ammo + 1, 10);
                gameState.scoreP2.ammo = Math.min(gameState.scoreP2.ammo + 1, 10);
            }
        } else if (stage == 2) {
            gameState.cooldownMultiplierP1 *= 1.01;
            gameState.cooldownMultiplierP2 *= 1.01;
        } else if (stage == 3) {
            gameState.globalSpeedMultiplier *= 1.03;
        }
    }

    private void startCountdown() {
        if (gameLoopTask != null) {
            gameLoopTask.cancel(false);
        }
        currentPhase = Phase.COUNTDOWN;

        MatchStart startP1 = new MatchStart(matchId);
        startP1.opponentBoxes = layoutP2 != null ? layoutP2.boxes : new ArrayList<>();
        startP1.opponentWires = layoutP2 != null ? layoutP2.wires : new ArrayList<>();
        // ### START OF CHANGE ###
        startP1.ownWires = layoutP1 != null ? layoutP1.wires : new ArrayList<>();
        // ### END OF CHANGE ###
        eventHandler.sendMessageToPlayer(player1.sessionId, startP1);

        MatchStart startP2 = new MatchStart(matchId);
        startP2.opponentBoxes = layoutP1 != null ? layoutP1.boxes : new ArrayList<>();
        startP2.opponentWires = layoutP1 != null ? layoutP1.wires : new ArrayList<>();
        // ### START OF CHANGE ###
        startP2.ownWires = layoutP2 != null ? layoutP2.wires : new ArrayList<>();
        // ### END OF CHANGE ###
        eventHandler.sendMessageToPlayer(player2.sessionId, startP2);

        executor.schedule(this::startMatch, COUNTDOWN_SECONDS, TimeUnit.SECONDS);
    }

    private void startMatch() {
        currentPhase = Phase.MATCH;
        this.gameState = new PvPGameState(matchId, new NetworkLayout(layoutP1, 1), new NetworkLayout(layoutP2, 2));
        this.simulation = new MessengerGameSimulation();
        gameLoopTask = executor.scheduleAtFixedRate(this::matchTick, 0, 16, TimeUnit.MILLISECONDS);
    }

    private void matchTick() {
        if (currentPhase != Phase.MATCH) return;
        int frame = frameId.incrementAndGet();

        if (simulation != null) {
            simulation.tick(gameState, 1.0 / 60.0);
        }

        if (frame % 6 == 0) { // ارسال آپدیت حدوداً هر ۱۰۰ میلی‌ثانیه
            sendTickUpdate();
        }

        if (checkEndConditions()) {
            endMatch();
        }
    }

    private boolean checkEndConditions() {
        return frameId.get() > 60 * 60 * 3; // پایان بازی پس از ۳ دقیقه
    }

    private void sendTickUpdate() {
        PvPStateSnapshot snapshot = new PvPStateSnapshot();
        snapshot.scoreP1 = gameState.scoreP1;
        snapshot.scoreP2 = gameState.scoreP2;
        snapshot.globalSpeedMultiplier = gameState.globalSpeedMultiplier;

        // ### START OF PATCH 10 ###
        // بسته‌بندی و ارسال وضعیت پکت‌ها
        List<PvPStateSnapshot.PacketState> packetStates = new ArrayList<>();
        for (MessengerGameSimulation.SimPacket simPacket : gameState.activePackets.values()) {
            PvPStateSnapshot.PacketState packetState = new PvPStateSnapshot.PacketState();
            packetState.id = simPacket.id;
            packetState.playerSide = simPacket.playerSide;
            packetState.type = simPacket.type;
            packetState.progress = simPacket.progress;
            packetState.wireId = simPacket.wireId; // ارسال wireId
            packetState.x = simPacket.x; // ارسال مختصات دقیق از سرور
            packetState.y = simPacket.y;
            packetStates.add(packetState);
        }
        snapshot.packets = packetStates;

        // بسته‌بندی و ارسال وضعیت سیستم‌ها (Cooldowns)
        List<SystemState> systemStates = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (PvPGameState.SystemState state : gameState.systemStates.values()) {
            SystemState ss = new SystemState();
            ss.id = state.id;
            ss.ammoP1 = state.ammoP1;
            ss.ammoP2 = state.ammoP2;
            ss.systemCooldownMs = (int) Math.max(0, state.systemCooldownUntil - now);
            ss.packetCooldownMsP1 = (int) Math.max(0, state.packetCooldownUntilP1 - now);
            ss.packetCooldownMsP2 = (int) Math.max(0, state.packetCooldownUntilP2 - now);
            systemStates.add(ss);
        }
        snapshot.systems = systemStates;
        // ### END OF PATCH 10 ###

        Tick tick = new Tick(matchId, frameId.get());
        tick.state = snapshot;
        broadcast(tick);
    }

    private void endMatch() {
        currentPhase = Phase.ENDED;
        if (gameLoopTask != null) {
            gameLoopTask.cancel(false);
        }

        int winnerSide = 0;
        if (gameState.scoreP1.totalScore > gameState.scoreP2.totalScore) {
            winnerSide = 1;
        } else if (gameState.scoreP2.totalScore > gameState.scoreP1.totalScore) {
            winnerSide = 2;
        }

        int xpP1 = calculateXP(gameState.scoreP1, winnerSide == 1);
        int xpP2 = calculateXP(gameState.scoreP2, winnerSide == 2);

        MatchEnd endP1 = new MatchEnd(matchId);
        endP1.finalScoreP1 = gameState.scoreP1;
        endP1.finalScoreP2 = gameState.scoreP2;
        endP1.winnerSide = winnerSide;
        endP1.xpEarned = xpP1;
        eventHandler.sendMessageToPlayer(player1.sessionId, endP1);

        MatchEnd endP2 = new MatchEnd(matchId);
        endP2.finalScoreP1 = gameState.scoreP1;
        endP2.finalScoreP2 = gameState.scoreP2;
        endP2.winnerSide = winnerSide;
        endP2.xpEarned = xpP2;
        eventHandler.sendMessageToPlayer(player2.sessionId, endP2);

        GameResult resultP1 = createGameResult(player1, gameState.scoreP1, winnerSide == 1, xpP1);
        GameResult resultP2 = createGameResult(player2, gameState.scoreP2, winnerSide == 2, xpP2);
        eventHandler.onMatchEnded(matchId, resultP1, resultP2);
    }

    private int calculateXP(PlayerScore score, boolean isWinner) {
        double xp = 1.0 * score.delivered - 1.5 * score.lost;
        if (isWinner) xp += 50; // پاداش پیروزی
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
        return result;
    }

    public void handlePlayerMessage(String sessionId, Message message) {
        if (currentPhase == Phase.ENDED) return;

        switch (message.type) {
            case SUBMIT_LAYOUT -> handleSubmitLayout(sessionId, (SubmitLayout) message);
            case READY_STATE -> handleReadyState(sessionId, (ReadyState) message);
            case EXTEND_REQUEST -> handleExtendRequest(sessionId, (ExtendRequest) message);
            case INJECT -> handleInject(sessionId, (Inject) message);
        }
    }

    private void handleSubmitLayout(String sessionId, SubmitLayout layout) {
        if (currentPhase != Phase.BUILD) return;
        if (sessionId.equals(player1.sessionId)) {
            layoutP1 = layout;
        } else {
            layoutP2 = layout;
        }
    }

    private void handleReadyState(String sessionId, ReadyState ready) {
        if (currentPhase != Phase.BUILD) return;
        if (sessionId.equals(player1.sessionId)) {
            p1Ready.set(ready.isReady);
        } else {
            p2Ready.set(ready.isReady);
        }
    }

    private void handleExtendRequest(String sessionId, ExtendRequest request) {
        if (currentPhase != Phase.BUILD || extendStage.get() >= MAX_EXTENDS) return;

        int newStage = extendStage.incrementAndGet();
        buildTimer.addAndGet(EXTEND_TIME_SECONDS);

        if (newStage == 1) activePenalty = "PENIA";
        else if (newStage == 2) activePenalty = "AERGIA";
        else activePenalty = "PENIA_SPEED";

        ExtendGranted granted = new ExtendGranted(newStage, buildTimer.get(), activePenalty);
        eventHandler.sendMessageToPlayer(sessionId, granted);
    }

    private void handleInject(String sessionId, Inject inject) {
        if (currentPhase != Phase.MATCH) return;

        boolean isP1 = sessionId.equals(player1.sessionId);
        PvPGameState.SystemState state = gameState.systemStates.get(inject.systemId);
        if (state == null) return;

        long now = System.currentTimeMillis();
        PlayerScore playerScore = isP1 ? gameState.scoreP1 : gameState.scoreP2;
        long playerCooldown = isP1 ? state.packetCooldownUntilP1 : state.packetCooldownUntilP2;

        if (playerScore.ammo > 0 && now >= state.systemCooldownUntil && now >= playerCooldown) {
            playerScore.ammo--;
            if (isP1) {
                state.packetCooldownUntilP1 = now + 5000;
            } else {
                state.packetCooldownUntilP2 = now + 5000;
            }
            state.systemCooldownUntil = now + 3000;

            if (simulation != null) {
                // ### START OF FIX ###
                // فراخوانی متد جدید با امضای صحیح:
                // دیگر نیازی به ارسال systemId به شبیه‌ساز نیست.
                // شناسه بازیکنی که اینجکت را انجام داده (1 یا 2) را ارسال می‌کنیم.
                simulation.injectPacket(gameState, isP1 ? 1 : 2);
                // ### END OF FIX ###
            }
        }
    }
    public void stop() {
        if (gameLoopTask != null) {
            gameLoopTask.cancel(true);
        }
        executor.shutdown();
    }

    private void broadcast(Message message) {
        eventHandler.sendMessageToPlayer(player1.sessionId, message);
        eventHandler.sendMessageToPlayer(player2.sessionId, message);
    }

    // Getters
    public QueuedPlayer getPlayer1() { return player1; }
    public QueuedPlayer getPlayer2() { return player2; }
}
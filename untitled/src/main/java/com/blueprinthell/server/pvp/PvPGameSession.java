package com.blueprinthell.server.pvp;

import com.blueprinthell.shared.protocol.NetworkProtocol.*;
import com.blueprinthell.server.pvp.PvPMatchManager.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * مدیریت یک session بازی PvP
 * شامل Build Phase و Match Phase
 */
public class PvPGameSession {

    private static final int BUILD_TIME_SECONDS = 30;
    private static final int EXTEND_TIME_SECONDS = 10;
    private static final int MAX_EXTENDS = 3;
    private static final int COUNTDOWN_SECONDS = 3;
    private static final int AMMO_CAP = 10;

    // Session data
    private final String matchId;
    private final QueuedPlayer player1;
    private final QueuedPlayer player2;
    private final MatchEventHandler eventHandler;

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
    private final Map<String, SystemStateInternal> systems = new ConcurrentHashMap<>();
    private final PlayerScoreInternal scoreP1 = new PlayerScoreInternal();
    private final PlayerScoreInternal scoreP2 = new PlayerScoreInternal();

    // Penalties
    private volatile String activePenalty = null;
    private volatile double globalSpeedMultiplier = 1.0;
    private volatile double cooldownMultiplierP1 = 1.0;
    private volatile double cooldownMultiplierP2 = 1.0;
    private final Random peniaRandom = new Random();

    // Executor
    private ScheduledFuture<?> gameLoopTask;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    // Simulation
    private MessengerGameSimulation simulation;

    enum Phase {
        BUILD, COUNTDOWN, MATCH, ENDED
    }

    public PvPGameSession(String matchId, QueuedPlayer p1, QueuedPlayer p2, MatchEventHandler handler) {
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

        // Start build timer
        gameLoopTask = executor.scheduleAtFixedRate(this::buildPhaseTick, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * Build phase tick (every second)
     */
    private void buildPhaseTick() {
        if (currentPhase != Phase.BUILD) return;

        int remaining = buildTimer.decrementAndGet();

        // Check if both ready
        if (p1Ready.get() && p2Ready.get()) {
            startCountdown();
            return;
        }

        // Check timer expiry
        if (remaining <= 0) {
            // If someone not ready, show extend option (3 seconds)
            if (!p1Ready.get() || !p2Ready.get()) {
                // Send extend option to unready players
                // Implementation would handle 3-second decision window
                // For now, auto-start
                startCountdown();
                return;
            }
        }

        // Apply penalties if in extend phase
        if (extendStage.get() > 0) {
            applyExtendPenalty();
        }

        // Send tick to both players
        BuildTick tick = new BuildTick(remaining);
        tick.p1Ready = p1Ready.get();
        tick.p2Ready = p2Ready.get();
        tick.extendStage = extendStage.get();
        tick.activePenalty = activePenalty;

        broadcast(tick);
    }

    /**
     * Apply extend penalties
     */
    private void applyExtendPenalty() {
        int stage = extendStage.get();

        if (stage == 1) {
            // Wrath of Penia (0-10s): Add random ammo to opponent
            if (peniaRandom.nextInt(10) < 2) { // 20% chance per second
                // Add to opponent of whoever requested extend
                // For simplicity, add to both
                scoreP1.ammo = Math.min(scoreP1.ammo + 1, AMMO_CAP);
                scoreP2.ammo = Math.min(scoreP2.ammo + 1, AMMO_CAP);
            }
        } else if (stage == 2) {
            // Wrath of Aergia (10-20s): Increase cooldowns
            cooldownMultiplierP1 *= 1.01; // 1% per second
            cooldownMultiplierP2 *= 1.01;
        } else if (stage == 3) {
            // Wrath of Penia Speed (20-30s): Increase global speed
            globalSpeedMultiplier *= 1.03; // 3% per second
        }
    }

    /**
     * Start countdown before match
     */
    private void startCountdown() {
        if (gameLoopTask != null) {
            gameLoopTask.cancel(false);
        }

        currentPhase = Phase.COUNTDOWN;

        // Send opponent layouts
        MatchStart startP1 = new MatchStart(matchId);
        startP1.opponentBoxes = layoutP2 != null ? layoutP2.boxes : new ArrayList<>();
        startP1.opponentWires = layoutP2 != null ? layoutP2.wires : new ArrayList<>();

        MatchStart startP2 = new MatchStart(matchId);
        startP2.opponentBoxes = layoutP1 != null ? layoutP1.boxes : new ArrayList<>();
        startP2.opponentWires = layoutP1 != null ? layoutP1.wires : new ArrayList<>();

        eventHandler.sendMessageToPlayer(player1.userId, startP1);
        eventHandler.sendMessageToPlayer(player2.userId, startP2);

        // Start countdown
        executor.schedule(this::startMatch, COUNTDOWN_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Start match phase
     */
    private void startMatch() {
        currentPhase = Phase.MATCH;

        // Initialize simulation
        initializeSimulation();

        // Start game loop (60 FPS)
        gameLoopTask = executor.scheduleAtFixedRate(this::matchTick, 0, 16, TimeUnit.MILLISECONDS);
    }

    /**
     * Initialize game simulation
     */
    private void initializeSimulation() {
        // Create simulation with both layouts
        simulation = new MessengerGameSimulation(layoutP1, layoutP2);

        // Initialize systems with ammo
        for (SystemLayout box : layoutP1.boxes) {
            if (!box.isSource && !box.isSink) {
                SystemStateInternal state = new SystemStateInternal(box.id);
                state.ammoP1 = 3; // Starting ammo
                systems.put(box.id, state);
            }
        }

        for (SystemLayout box : layoutP2.boxes) {
            if (!box.isSource && !box.isSink) {
                SystemStateInternal state = systems.get(box.id);
                if (state == null) {
                    state = new SystemStateInternal(box.id);
                    systems.put(box.id, state);
                }
                state.ammoP2 = 3; // Starting ammo
            }
        }
    }

    /**
     * Match phase tick (60 FPS)
     */
    private void matchTick() {
        if (currentPhase != Phase.MATCH) return;

        int frame = frameId.incrementAndGet();

        // Update simulation
        if (simulation != null) {
            SimulationResult result = simulation.tick(1.0/60.0, globalSpeedMultiplier);

            // Update scores
            scoreP1.delivered += result.deliveredP1;
            scoreP1.lost += result.lostP1;
            scoreP1.totalScore = scoreP1.delivered - (int)(scoreP1.lost * 1.5);

            scoreP2.delivered += result.deliveredP2;
            scoreP2.lost += result.lostP2;
            scoreP2.totalScore = scoreP2.delivered - (int)(scoreP2.lost * 1.5);

            // Add ammo for deliveries
            scoreP1.ammo = Math.min(scoreP1.ammo + result.deliveredP1, AMMO_CAP);
            scoreP2.ammo = Math.min(scoreP2.ammo + result.deliveredP2, AMMO_CAP);
        }

        // Update system cooldowns
        updateSystemCooldowns(16);

        // Send tick every 100ms (6 times per second)
        if (frame % 6 == 0) {
            sendTickUpdate();
        }

        // Check end conditions
        if (checkEndConditions()) {
            endMatch();
        }
    }

    /**
     * Update system cooldowns
     */
    private void updateSystemCooldowns(int deltaMs) {
        for (SystemStateInternal state : systems.values()) {
            state.systemCooldown = Math.max(0, state.systemCooldown - deltaMs);
            state.packetCooldownP1 = Math.max(0,
                    state.packetCooldownP1 - (int)(deltaMs * cooldownMultiplierP1));
            state.packetCooldownP2 = Math.max(0,
                    state.packetCooldownP2 - (int)(deltaMs * cooldownMultiplierP2));
        }
    }

    /**
     * Send tick update to players
     */
    private void sendTickUpdate() {
        Tick tick = new Tick(matchId, frameId.get());

        // Set scores
        tick.scoreP1 = new PlayerScore();
        tick.scoreP1.delivered = scoreP1.delivered;
        tick.scoreP1.lost = scoreP1.lost;
        tick.scoreP1.totalScore = scoreP1.totalScore;
        tick.scoreP1.ammo = scoreP1.ammo;

        tick.scoreP2 = new PlayerScore();
        tick.scoreP2.delivered = scoreP2.delivered;
        tick.scoreP2.lost = scoreP2.lost;
        tick.scoreP2.totalScore = scoreP2.totalScore;
        tick.scoreP2.ammo = scoreP2.ammo;

        // Set system states
        tick.systems = new ArrayList<>();
        for (SystemStateInternal internal : systems.values()) {
            SystemState state = new SystemState();
            state.id = internal.id;
            state.ammoP1 = internal.ammoP1;
            state.ammoP2 = internal.ammoP2;
            state.systemCooldownMs = internal.systemCooldown;
            state.packetCooldownMsP1 = internal.packetCooldownP1;
            state.packetCooldownMsP2 = internal.packetCooldownP2;
            tick.systems.add(state);
        }

        tick.globalSpeedMultiplier = globalSpeedMultiplier;

        broadcast(tick);
    }

    /**
     * Check end conditions
     */
    private boolean checkEndConditions() {
        // End after 3 minutes or if simulation is complete
        return frameId.get() > 60 * 60 * 3; // 3 minutes at 60 FPS
    }

    /**
     * End match
     */
    private void endMatch() {
        currentPhase = Phase.ENDED;

        if (gameLoopTask != null) {
            gameLoopTask.cancel(false);
        }

        // Determine winner
        int winnerSide = 0;
        if (scoreP1.totalScore > scoreP2.totalScore) {
            winnerSide = 1;
        } else if (scoreP2.totalScore > scoreP1.totalScore) {
            winnerSide = 2;
        }

        // Calculate XP
        int xpP1 = calculateXP(scoreP1, winnerSide == 1);
        int xpP2 = calculateXP(scoreP2, winnerSide == 2);

        // Send match end
        MatchEnd end = new MatchEnd(matchId);
        end.finalScoreP1 = createPlayerScore(scoreP1);
        end.finalScoreP2 = createPlayerScore(scoreP2);
        end.winnerSide = winnerSide;

        // Different XP for each player
        MatchEnd endP1 = new MatchEnd(matchId);
        endP1.finalScoreP1 = end.finalScoreP1;
        endP1.finalScoreP2 = end.finalScoreP2;
        endP1.winnerSide = winnerSide;
        endP1.xpEarned = xpP1;

        MatchEnd endP2 = new MatchEnd(matchId);
        endP2.finalScoreP1 = end.finalScoreP1;
        endP2.finalScoreP2 = end.finalScoreP2;
        endP2.winnerSide = winnerSide;
        endP2.xpEarned = xpP2;

        eventHandler.sendMessageToPlayer(player1.userId, endP1);
        eventHandler.sendMessageToPlayer(player2.userId, endP2);

        // Create game results
        GameResult resultP1 = createGameResult(player1, scoreP1, winnerSide == 1, xpP1);
        GameResult resultP2 = createGameResult(player2, scoreP2, winnerSide == 2, xpP2);

        // Notify handler
        eventHandler.onMatchEnded(matchId, resultP1, resultP2);
    }

    /**
     * Calculate XP
     */
    private int calculateXP(PlayerScoreInternal score, boolean isWinner) {
        double xp = 1.0 * score.delivered - 1.5 * score.lost;
        if (isWinner) xp += 20;
        return Math.max(0, Math.min(200, (int)xp));
    }

    /**
     * Create game result
     */
    private GameResult createGameResult(QueuedPlayer player, PlayerScoreInternal score,
                                        boolean isWinner, int xp) {
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
        result.durationMs = frameId.get() * 16; // frames * ms per frame
        return result;
    }

    /**
     * Handle player message
     */
    public void handlePlayerMessage(String userId, Message message) {
        boolean isP1 = userId.equals(player1.userId);
        boolean isP2 = userId.equals(player2.userId);

        if (!isP1 && !isP2) return;

        switch (message.type) {
            case SUBMIT_LAYOUT -> handleSubmitLayout(userId, (SubmitLayout)message);
            case READY_STATE -> handleReadyState(userId, (ReadyState)message);
            case EXTEND_REQUEST -> handleExtendRequest(userId, (ExtendRequest)message);
            case INJECT -> handleInject(userId, (Inject)message);
        }
    }

    private void handleSubmitLayout(String userId, SubmitLayout layout) {
        if (currentPhase != Phase.BUILD) return;

        if (userId.equals(player1.userId)) {
            layoutP1 = layout;
        } else {
            layoutP2 = layout;
        }
    }

    private void handleReadyState(String userId, ReadyState ready) {
        if (currentPhase != Phase.BUILD) return;

        if (userId.equals(player1.userId)) {
            p1Ready.set(ready.isReady);
        } else {
            p2Ready.set(ready.isReady);
        }
    }

    private void handleExtendRequest(String userId, ExtendRequest request) {
        if (currentPhase != Phase.BUILD) return;
        if (extendStage.get() >= MAX_EXTENDS) return;

        int newStage = extendStage.incrementAndGet();
        buildTimer.addAndGet(EXTEND_TIME_SECONDS);

        // Set penalty
        if (newStage == 1) {
            activePenalty = "PENIA";
        } else if (newStage == 2) {
            activePenalty = "AERGIA";
        } else {
            activePenalty = "PENIA_SPEED";
        }

        // Send extend granted
        ExtendGranted granted = new ExtendGranted(newStage, buildTimer.get(), activePenalty);
        eventHandler.sendMessageToPlayer(userId, granted);
    }

    private void handleInject(String userId, Inject inject) {
        if (currentPhase != Phase.MATCH) return;

        boolean isP1 = userId.equals(player1.userId);
        SystemStateInternal state = systems.get(inject.systemId);

        if (state == null) return;

        // Check conditions
        if (isP1) {
            if (scoreP1.ammo <= 0 || state.systemCooldown > 0 || state.packetCooldownP1 > 0) {
                return;
            }
            scoreP1.ammo--;
            state.packetCooldownP1 = 5000; // 5 seconds
        } else {
            if (scoreP2.ammo <= 0 || state.systemCooldown > 0 || state.packetCooldownP2 > 0) {
                return;
            }
            scoreP2.ammo--;
            state.packetCooldownP2 = 5000; // 5 seconds
        }

        state.systemCooldown = 3000; // 3 seconds

        // Inject packet in simulation
        if (simulation != null) {
            simulation.injectPacket(inject.systemId, isP1 ? 1 : 2);
        }
    }

    /**
     * Stop session
     */
    public void stop() {
        if (gameLoopTask != null) {
            gameLoopTask.cancel(false);
        }
        executor.shutdown();
    }

    /**
     * Broadcast message to both players
     */
    private void broadcast(Message message) {
        eventHandler.sendMessageToPlayer(player1.userId, message);
        eventHandler.sendMessageToPlayer(player2.userId, message);
    }

    private PlayerScore createPlayerScore(PlayerScoreInternal internal) {
        PlayerScore score = new PlayerScore();
        score.delivered = internal.delivered;
        score.lost = internal.lost;
        score.totalScore = internal.totalScore;
        score.ammo = internal.ammo;
        return score;
    }

    // Getters
    public QueuedPlayer getPlayer1() { return player1; }
    public QueuedPlayer getPlayer2() { return player2; }

    // Internal classes

    static class PlayerScoreInternal {
        int delivered = 0;
        int lost = 0;
        int totalScore = 0;
        int ammo = 3;
    }

    static class SystemStateInternal {
        final String id;
        int ammoP1 = 0;
        int ammoP2 = 0;
        int systemCooldown = 0;
        int packetCooldownP1 = 0;
        int packetCooldownP2 = 0;

        SystemStateInternal(String id) {
            this.id = id;
        }
    }

    static class SimulationResult {
        int deliveredP1 = 0;
        int lostP1 = 0;
        int deliveredP2 = 0;
        int lostP2 = 0;
    }
}
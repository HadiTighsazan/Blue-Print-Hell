package com.blueprinthell.server.pvp;

import com.blueprinthell.shared.protocol.NetworkProtocol.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * مدیریت matchmaking و session های PvP
 */
public class PvPMatchManager {

    // Queue for matchmaking
    private final Queue<QueuedPlayer> matchmakingQueue = new ConcurrentLinkedQueue<>();

    // Active matches
    private final Map<String, PvPGameSession> activeMatches = new ConcurrentHashMap<>();

    // Player to match mapping
    private final Map<String, String> playerToMatch = new ConcurrentHashMap<>();

    // Scheduled executor for game loops
    private final ScheduledExecutorService gameLoopExecutor = Executors.newScheduledThreadPool(4);

    // Match handler callback
    private final MatchEventHandler eventHandler;

    public interface MatchEventHandler {
        void sendMessageToPlayer(String sessionId, Message message);
        void onMatchEnded(String matchId, GameResult resultP1, GameResult resultP2);
    }

    public PvPMatchManager(MatchEventHandler handler) {
        this.eventHandler = handler;

        // Start matchmaking thread
        gameLoopExecutor.scheduleAtFixedRate(this::processMatchmaking, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * Add player to matchmaking queue
     */
    public void queuePlayer(String sessionId, String userId, String username) {
        // Remove from queue if already present
        matchmakingQueue.removeIf(p -> p.sessionId.equals(sessionId));

        // Add to queue
        QueuedPlayer player = new QueuedPlayer(sessionId, userId, username);        matchmakingQueue.offer(player);
        matchmakingQueue.offer(player);
        // Send queue status
        updateQueueStatus(sessionId);
    }

    /**
     * Remove player from queue
     */
    public void cancelQueue(String sessionId) {
        matchmakingQueue.removeIf(p -> p.sessionId.equals(sessionId));
    }

    /**
     * Process matchmaking queue
     */
    private void processMatchmaking() {
        while (matchmakingQueue.size() >= 2) {
            QueuedPlayer p1 = matchmakingQueue.poll();
            QueuedPlayer p2 = matchmakingQueue.poll();

            if (p1 != null && p2 != null) {
                createMatch(p1, p2);
            }
        }

        // Update queue status for remaining players
        int position = 1;
        for (QueuedPlayer player : matchmakingQueue) {
            QueueStatus status = new QueueStatus(position++, position * 5);
            eventHandler.sendMessageToPlayer(player.sessionId, status);
        }
    }

    /**
     * Create a new match
     */
    private void createMatch(QueuedPlayer p1, QueuedPlayer p2) {
        String matchId = "match-" + UUID.randomUUID().toString();

        // Create game session
        PvPGameSession session = new PvPGameSession(matchId, p1, p2, eventHandler);
        activeMatches.put(matchId, session);

        // Map players to match
        playerToMatch.put(p1.sessionId, matchId);
        playerToMatch.put(p2.sessionId, matchId);

        // Notify players
        MatchFound foundP1 = new MatchFound(matchId, p2.userId, p2.username, 1);
        MatchFound foundP2 = new MatchFound(matchId, p1.userId, p1.username, 2);

        eventHandler.sendMessageToPlayer(p1.userId, foundP1);
        eventHandler.sendMessageToPlayer(p2.userId, foundP2);

        // Start build phase
        session.startBuildPhase();

        System.out.println("Match created: " + matchId + " - " + p1.username + " vs " + p2.username);
    }

    /**
     * Handle player message
     */
    public void handlePlayerMessage(String sessionId, Message message) {
        String matchId = playerToMatch.get(sessionId);
        if (matchId == null) {
            return; // Player not in a match
        }

        PvPGameSession session = activeMatches.get(matchId);
        if (session == null) {
            return; // Match not found
        }

        // Delegate to session
        session.handlePlayerMessage(sessionId, message);
    }

    /**
     * End match
     */
    public void endMatch(String matchId) {
        PvPGameSession session = activeMatches.remove(matchId);
        if (session != null) {
            // Clean up player mappings
            playerToMatch.remove(session.getPlayer1().sessionId);
            playerToMatch.remove(session.getPlayer2().sessionId);
            // Stop session
            session.stop();
        }
    }

    /**
     * Update queue status for a player
     */
    private void updateQueueStatus(String sessionId) {
        int position = 1;
        for (QueuedPlayer player : matchmakingQueue) {
            if (player.sessionId.equals(sessionId)) {
                QueueStatus status = new QueueStatus(position, position * 5);
                eventHandler.sendMessageToPlayer(sessionId, status);
                break;
            }
            position++;
        }
    }

    /**
     * Shutdown manager
     */
    public void shutdown() {
        gameLoopExecutor.shutdown();
        activeMatches.values().forEach(PvPGameSession::stop);
        activeMatches.clear();
        matchmakingQueue.clear();
    }

    /**
     * Queued player data
     */
    static class QueuedPlayer {
        final String sessionId;
        final String userId;
        final String username;
        final long queueTime;

        QueuedPlayer(String sessionId, String userId, String username) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.username = username;
            this.queueTime = System.currentTimeMillis();
        }
    }
}
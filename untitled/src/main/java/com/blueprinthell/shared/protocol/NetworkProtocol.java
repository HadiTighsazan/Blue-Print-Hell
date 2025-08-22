package com.blueprinthell.shared.protocol;

import java.util.*;

/**
 * پروتکل ارتباطی بین Client و Server
 * Extended for PvP Messenger Mode
 */
public class NetworkProtocol {

    // Message Types
    public enum MessageType {
        // Connection
        HELLO,
        HELLO_ACK,
        HEARTBEAT,
        HEARTBEAT_ACK,
        ERROR,

        // Game Results
        SUBMIT_RESULT,
        SUBMIT_RESULT_ACK,

        // Profile
        GET_PROFILE,
        PROFILE,

        // === PvP Messages ===
        // Matchmaking
        QUEUE_FOR_MATCH,
        QUEUE_STATUS,
        MATCH_FOUND,
        CANCEL_QUEUE,

        // Build Phase
        SUBMIT_LAYOUT,
        READY_STATE,
        EXTEND_REQUEST,
        EXTEND_GRANTED,
        BUILD_TICK,

        // Match Phase
        MATCH_START,
        INJECT,
        TICK,
        STATE_SYNC,
        MATCH_END
    }

    // Base Message
    public static class Message {
        public MessageType type;
        public String messageId;
        public long timestamp;

        public Message(MessageType type) {
            this.type = type;
            this.messageId = UUID.randomUUID().toString();
            this.timestamp = System.currentTimeMillis();
        }
    }

    // Game Mode & Ruleset
    public enum GameMode {
        SOLO_OFFLINE,
        SOLO_ONLINE,
        MULTIPLAYER_PVP  // NEW
    }

    public enum Ruleset {
        SP_V1,  // Single Player Version 1
        MP_MSG_ONLY_V1  // Multiplayer Messenger Only V1
    }

    // === PvP Data Structures ===

    public static class WireLayout {
        public String id;
        public String fromBoxId;
        public int fromOutIndex;
        public String toBoxId;
        public int toInIndex;
        public List<Point2D> path;

        public static class Point2D {
            public int x, y;
            public Point2D(int x, int y) {
                this.x = x; this.y = y;
            }
        }
    }

    public static class BoxLayout {
        public String id;
        public String kind; // NORMAL, VPN, DISTRIBUTOR, etc.
        public int x, y, width, height;
        public List<String> inShapes;  // SQUARE, TRIANGLE, CIRCLE
        public List<String> outShapes;
        public boolean isSource;
        public boolean isSink;
    }

    public static class SystemState {
        public String id;
        public int ammoP1;
        public int ammoP2;
        public int systemCooldownMs;
        public int packetCooldownMsP1;
        public int packetCooldownMsP2;
    }

    public static class PlayerScore {
        public int delivered;
        public int lost;
        public int totalScore;
        public int ammo;
    }

    // === PvP Matchmaking Messages ===

    public static class QueueForMatch extends Message {
        public String userId;
        public String preferredMode; // "MSG_ONLY" for now

        public QueueForMatch(String userId) {
            super(MessageType.QUEUE_FOR_MATCH);
            this.userId = userId;
            this.preferredMode = "MSG_ONLY";
        }
    }

    public static class QueueStatus extends Message {
        public int position;
        public int estimatedWaitSeconds;

        public QueueStatus(int position, int estimatedWait) {
            super(MessageType.QUEUE_STATUS);
            this.position = position;
            this.estimatedWaitSeconds = estimatedWait;
        }
    }

    public static class MatchFound extends Message {
        public String matchId;
        public String opponentId;
        public String opponentUsername;
        public int playerSide; // 1 or 2

        public MatchFound(String matchId, String opponentId, String opponentUsername, int side) {
            super(MessageType.MATCH_FOUND);
            this.matchId = matchId;
            this.opponentId = opponentId;
            this.opponentUsername = opponentUsername;
            this.playerSide = side;
        }
    }

    // === PvP Build Phase Messages ===

    public static class SubmitLayout extends Message {
        public String matchId;
        public List<WireLayout> wires;
        public List<BoxLayout> boxes;

        public SubmitLayout(String matchId) {
            super(MessageType.SUBMIT_LAYOUT);
            this.matchId = matchId;
            this.wires = new ArrayList<>();
            this.boxes = new ArrayList<>();
        }
    }

    public static class ReadyState extends Message {
        public String matchId;
        public boolean isReady;

        public ReadyState(String matchId, boolean ready) {
            super(MessageType.READY_STATE);
            this.matchId = matchId;
            this.isReady = ready;
        }
    }

    public static class ExtendRequest extends Message {
        public String matchId;
        public int stage; // 1, 2, or 3

        public ExtendRequest(String matchId, int stage) {
            super(MessageType.EXTEND_REQUEST);
            this.matchId = matchId;
            this.stage = stage;
        }
    }

    public static class ExtendGranted extends Message {
        public int stage;
        public int newTimerSeconds;
        public String penaltyType; // "PENIA", "AERGIA", "PENIA_SPEED"

        public ExtendGranted(int stage, int newTimer, String penalty) {
            super(MessageType.EXTEND_GRANTED);
            this.stage = stage;
            this.newTimerSeconds = newTimer;
            this.penaltyType = penalty;
        }
    }

    public static class BuildTick extends Message {
        public int remainingSeconds;
        public boolean p1Ready;
        public boolean p2Ready;
        public int extendStage; // 0, 1, 2, or 3
        public String activePenalty;

        public BuildTick(int remaining) {
            super(MessageType.BUILD_TICK);
            this.remainingSeconds = remaining;
        }
    }

    // === PvP Match Phase Messages ===

    public static class MatchStart extends Message {
        public String matchId;
        public String mapId;
        public long seed;
        public List<BoxLayout> opponentBoxes;
        public List<WireLayout> opponentWires;
        public int countdownSeconds; // 3, 2, 1...

        public MatchStart(String matchId) {
            super(MessageType.MATCH_START);
            this.matchId = matchId;
            this.countdownSeconds = 3;
        }
    }

    public static class Inject extends Message {
        public String matchId;
        public String systemId;

        public Inject(String matchId, String systemId) {
            super(MessageType.INJECT);
            this.matchId = matchId;
            this.systemId = systemId;
        }
    }

    public static class Tick extends Message {
        public String matchId;
        public int frameId;
        public PlayerScore scoreP1;
        public PlayerScore scoreP2;
        public List<SystemState> systems;
        public double globalSpeedMultiplier; // for Wrath of Penia (speed)

        public Tick(String matchId, int frame) {
            super(MessageType.TICK);
            this.matchId = matchId;
            this.frameId = frame;
            this.globalSpeedMultiplier = 1.0;
        }
    }

    public static class StateSync extends Message {
        public String matchId;
        public int frameId;
        public String compressedState; // Base64 encoded game state

        public StateSync(String matchId, int frame) {
            super(MessageType.STATE_SYNC);
            this.matchId = matchId;
            this.frameId = frame;
        }
    }

    public static class MatchEnd extends Message {
        public String matchId;
        public PlayerScore finalScoreP1;
        public PlayerScore finalScoreP2;
        public int winnerSide; // 1, 2, or 0 for draw
        public int xpEarned;
        public Map<String, Object> stats;

        public MatchEnd(String matchId) {
            super(MessageType.MATCH_END);
            this.matchId = matchId;
            this.stats = new HashMap<>();
        }
    }

    // === Extended Game Result for PvP ===

    public static class GameResult {
        public String resultId;
        public String userId;
        public GameMode mode;
        public Ruleset ruleset;
        public int level;
        public long durationMs;
        public int score;
        public int delivered;
        public int loss;
        public int xp;
        public long ts;

        // PvP specific fields
        public String matchId;
        public String opponentId;
        public boolean isWinner;
        public int playerSide;

        public GameResult() {
            this.resultId = "r-" + UUID.randomUUID().toString();
            this.ts = System.currentTimeMillis();
        }
    }

    // === Existing Messages (unchanged) ===

    public static class Hello extends Message {
        public String clientVersion;
        public String userId;

        public Hello(String clientVersion, String userId) {
            super(MessageType.HELLO);
            this.clientVersion = clientVersion;
            this.userId = userId;
        }
    }

    public static class HelloAck extends Message {
        public String serverVersion;
        public String motd;

        public HelloAck(String serverVersion, String motd) {
            super(MessageType.HELLO_ACK);
            this.serverVersion = serverVersion;
            this.motd = motd;
        }
    }

    public static class Heartbeat extends Message {
        public Heartbeat() {
            super(MessageType.HEARTBEAT);
        }
    }

    public static class HeartbeatAck extends Message {
        public HeartbeatAck() {
            super(MessageType.HEARTBEAT_ACK);
        }
    }

    public static class ErrorMessage extends Message {
        public String code;
        public String msg;

        public ErrorMessage(String code, String msg) {
            super(MessageType.ERROR);
            this.code = code;
            this.msg = msg;
        }
    }

    public static class SubmitResult extends Message {
        public GameResult result;

        public SubmitResult(GameResult result) {
            super(MessageType.SUBMIT_RESULT);
            this.result = result;
        }
    }

    public static class SubmitResultAck extends Message {
        public boolean success;
        public String resultId;

        public SubmitResultAck(boolean success, String resultId) {
            super(MessageType.SUBMIT_RESULT_ACK);
            this.success = success;
            this.resultId = resultId;
        }
    }

    public static class GetProfile extends Message {
        public String userId;

        public GetProfile(String userId) {
            super(MessageType.GET_PROFILE);
            this.userId = userId;
        }
    }

    public static class Profile extends Message {
        public String userId;
        public String username;
        public int xpTotal;
        public Map<String, Integer> xpByMode;
        public Map<Integer, Long> bestTimes;
        public GameResult[] history;
        public String[] unlocks;
        public String activePerk;

        // PvP stats
        public int pvpWins;
        public int pvpLosses;
        public int pvpRating;

        public Profile() {
            super(MessageType.PROFILE);
        }
    }
}
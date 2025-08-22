package com.blueprinthell.shared.protocol;

import java.util.Map;
import java.util.UUID;

/**
 * پروتکل ارتباطی بین Client و Server
 * تمام پیام‌ها به صورت JSON Line-delimited ارسال می‌شوند
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
        PROFILE
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

    // Connection Messages
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
        public String motd; // Message of the day

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

    // Game Mode & Ruleset
    public enum GameMode {
        SOLO_OFFLINE,
        SOLO_ONLINE
        // Future: COOP, PVP
    }

    public enum Ruleset {
        SP_V1  // Single Player Version 1
        // Future: MP_V1, RANKED_V1
    }

    // Game Result Messages
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
        public long ts; // timestamp

        public GameResult() {
            this.resultId = "r-" + UUID.randomUUID().toString();
            this.ts = System.currentTimeMillis();
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

    // Profile Messages
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
        public Map<String, Integer> xpByMode; // {"SOLO": 1500, ...}
        public Map<Integer, Long> bestTimes; // level -> best time ms
        public GameResult[] history; // last 20 games
        public String[] unlocks; // unlocked features
        public String activePerk; // current active perk

        public Profile() {
            super(MessageType.PROFILE);
        }
    }
}
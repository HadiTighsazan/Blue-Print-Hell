package com.blueprinthell.server;

import com.blueprinthell.shared.protocol.NetworkProtocol.*;
import com.blueprinthell.server.pvp.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * سرور BlueprintHell با پشتیبانی از PvP
 */
public class BlueprintHellServerExtended {
    private static final int PORT = 7777;
    private static final String VERSION = "2.0.0";
    private static final String MOTD = "Welcome to BlueprintHell Server - PvP Ready!";
    private static final Path DATA_DIR = Paths.get("data");
    private static final Path USERS_DIR = DATA_DIR.resolve("users");

    private final ServerSocket serverSocket;
    private final ExecutorService clientExecutor;
    private final Map<String, ClientHandler> activeClients;
    private final ProfileManager profileManager;
    private final PvPMatchManager pvpManager;
    private final Gson gson;
    private volatile boolean running;

    public BlueprintHellServerExtended(int port) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.clientExecutor = Executors.newCachedThreadPool();
        this.activeClients = new ConcurrentHashMap<>();
        this.profileManager = new ProfileManager(USERS_DIR);
        this.gson = new GsonBuilder().create();
        this.running = true;

        // Initialize PvP manager
        this.pvpManager = new PvPMatchManager(new PvPMatchManager.MatchEventHandler() {
            @Override
            public void sendMessageToPlayer(String userId, Message message) {
                ClientHandler client = activeClients.get(userId);
                if (client != null) {
                    client.sendMessage(message);
                }
            }

            @Override
            public void onMatchEnded(String matchId, GameResult resultP1, GameResult resultP2) {
                // Save results
                profileManager.addResult(resultP1.userId, resultP1);
                profileManager.addResult(resultP2.userId, resultP2);

                // Clean up
                pvpManager.endMatch(matchId);
            }
        });

        // Create directories
        Files.createDirectories(USERS_DIR);

        System.out.println("BlueprintHell Server v" + VERSION);
        System.out.println("Listening on port " + port);
        System.out.println("PvP Mode: ENABLED");
    }

    public void start() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection from: " + clientSocket.getInetAddress());

                ClientHandler handler = new ClientHandler(clientSocket);
                clientExecutor.execute(handler);

            } catch (IOException e) {
                if (!running) break;
                System.err.println("Error accepting connection: " + e.getMessage());
            }
        }
    }

    /**
     * Enhanced client handler with PvP support
     */
    private class ClientHandler implements Runnable {
        private final Socket socket;
        private final BufferedReader reader;
        private final PrintWriter writer;
        private String userId;
        private String username;
        private volatile boolean connected;
        private long lastHeartbeat;

        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            this.connected = true;
            this.lastHeartbeat = System.currentTimeMillis();
        }

        @Override
        public void run() {
            try {
                // Heartbeat monitor thread
                Thread heartbeatMonitor = new Thread(this::monitorHeartbeat);
                heartbeatMonitor.start();

                // Read messages
                String line;
                while (connected && (line = reader.readLine()) != null) {
                    handleMessage(line);
                }

            } catch (IOException e) {
                System.err.println("Client disconnected: " + (userId != null ? userId : "unknown"));
            } finally {
                disconnect();
            }
        }

        private void handleMessage(String jsonLine) {
            try {
                Message baseMsg = gson.fromJson(jsonLine, Message.class);

                switch (baseMsg.type) {
                    // Connection messages
                    case HELLO -> handleHello(jsonLine);
                    case HEARTBEAT -> handleHeartbeat();

                    // Profile messages
                    case SUBMIT_RESULT -> handleSubmitResult(jsonLine);
                    case GET_PROFILE -> handleGetProfile(jsonLine);

                    // PvP messages
                    case QUEUE_FOR_MATCH -> handleQueueForMatch(jsonLine);
                    case CANCEL_QUEUE -> handleCancelQueue();
                    case SUBMIT_LAYOUT -> handleSubmitLayout(jsonLine);
                    case READY_STATE -> handleReadyState(jsonLine);
                    case EXTEND_REQUEST -> handleExtendRequest(jsonLine);
                    case INJECT -> handleInject(jsonLine);

                    default -> sendError("UNKNOWN_MSG", "Unknown message type: " + baseMsg.type);
                }

            } catch (Exception e) {
                System.err.println("Error handling message: " + e.getMessage());
                sendError("PARSE_ERROR", "Failed to parse message");
            }
        }

        // === Connection Handlers ===

        private void handleHello(String json) {
            Hello hello = gson.fromJson(json, Hello.class);
            this.userId = hello.userId;

            // Get username from profile
            Profile profile = profileManager.getProfile(userId);
            this.username = profile != null ? profile.username : "Player_" + userId.substring(0, 8);

            // Register active client
            activeClients.put(userId, this);

            // Send HelloAck
            HelloAck ack = new HelloAck(VERSION, MOTD);
            sendMessage(ack);

            System.out.println("User connected: " + userId + " (" + username +
                    ") - client v" + hello.clientVersion);
        }

        private void handleHeartbeat() {
            lastHeartbeat = System.currentTimeMillis();
            sendMessage(new HeartbeatAck());
        }

        // === Profile Handlers ===

        private void handleSubmitResult(String json) {
            SubmitResult submit = gson.fromJson(json, SubmitResult.class);
            GameResult result = submit.result;

            boolean success = profileManager.addResult(result.userId, result);

            SubmitResultAck ack = new SubmitResultAck(success, result.resultId);
            sendMessage(ack);

            String modeStr = result.mode != null ? result.mode.toString() : "UNKNOWN";
            System.out.println("Result submitted: " + result.resultId +
                    " (user=" + result.userId + ", mode=" + modeStr +
                    ", level=" + result.level + ", score=" + result.score +
                    ", xp=" + result.xp + ")");
        }

        private void handleGetProfile(String json) {
            GetProfile request = gson.fromJson(json, GetProfile.class);
            Profile profile = profileManager.getProfile(request.userId);

            if (profile != null) {
                sendMessage(profile);
            } else {
                sendError("PROFILE_NOT_FOUND", "Profile not found for user: " + request.userId);
            }
        }

        // === PvP Handlers ===

        private void handleQueueForMatch(String json) {
            QueueForMatch queue = gson.fromJson(json, QueueForMatch.class);

            System.out.println("User " + username + " queued for PvP match");
            pvpManager.queuePlayer(userId, username);
        }

        private void handleCancelQueue() {
            System.out.println("User " + username + " cancelled queue");
            pvpManager.cancelQueue(userId);
        }

        private void handleSubmitLayout(String json) {
            SubmitLayout layout = gson.fromJson(json, SubmitLayout.class);
            pvpManager.handlePlayerMessage(userId, layout);
        }

        private void handleReadyState(String json) {
            ReadyState ready = gson.fromJson(json, ReadyState.class);
            pvpManager.handlePlayerMessage(userId, ready);
        }

        private void handleExtendRequest(String json) {
            ExtendRequest extend = gson.fromJson(json, ExtendRequest.class);
            pvpManager.handlePlayerMessage(userId, extend);
        }

        private void handleInject(String json) {
            Inject inject = gson.fromJson(json, Inject.class);
            pvpManager.handlePlayerMessage(userId, inject);
        }

        // === Utility Methods ===

        public void sendMessage(Message msg) {
            String json = gson.toJson(msg);
            writer.println(json);
            writer.flush();
        }

        private void sendError(String code, String msg) {
            sendMessage(new ErrorMessage(code, msg));
        }

        private void monitorHeartbeat() {
            while (connected) {
                try {
                    Thread.sleep(2000);

                    long timeSinceLastHeartbeat = System.currentTimeMillis() - lastHeartbeat;
                    if (timeSinceLastHeartbeat > 6000) {
                        System.out.println("Heartbeat timeout for user: " + username);
                        disconnect();
                        break;
                    }

                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        private void disconnect() {
            connected = false;

            // Remove from PvP queue if queued
            if (userId != null) {
                pvpManager.cancelQueue(userId);
                activeClients.remove(userId);
            }

            try {
                socket.close();
            } catch (IOException ignored) {}

            System.out.println("User disconnected: " + username);
        }
    }

    /**
     * Profile manager with PvP stats
     */
    private static class ProfileManager {
        private final Path usersDir;
        private final Gson gson;

        public ProfileManager(Path usersDir) {
            this.usersDir = usersDir;
            this.gson = new GsonBuilder().setPrettyPrinting().create();
        }

        public Profile getProfile(String userId) {
            Path userFile = usersDir.resolve(userId + ".json");

            if (!Files.exists(userFile)) {
                return createNewProfile(userId);
            }

            try {
                String json = Files.readString(userFile);
                return gson.fromJson(json, Profile.class);
            } catch (IOException e) {
                System.err.println("Failed to load profile: " + userId);
                return null;
            }
        }

        public boolean addResult(String userId, GameResult result) {
            Profile profile = getProfile(userId);
            if (profile == null) {
                profile = createNewProfile(userId);
            }

            // Update XP
            profile.xpTotal += result.xp;

            // XP by mode
            String modeKey = result.mode != null ? result.mode.toString() : "UNKNOWN";
            profile.xpByMode.merge(modeKey, result.xp, Integer::sum);

            // Best times (for single player)
            if (result.mode != GameMode.MULTIPLAYER_PVP && result.durationMs > 0) {
                profile.bestTimes.merge(result.level, result.durationMs, Math::min);
            }

            // PvP stats
            if (result.mode == GameMode.MULTIPLAYER_PVP) {
                if (result.isWinner) {
                    profile.pvpWins++;
                } else {
                    profile.pvpLosses++;
                }

                // Simple rating system
                int ratingChange = result.isWinner ? 25 : -20;
                profile.pvpRating = Math.max(0, profile.pvpRating + ratingChange);
            }

            // Add to history
            List<GameResult> history = new ArrayList<>();
            if (profile.history != null) {
                history.addAll(Arrays.asList(profile.history));
            }
            history.add(0, result);
            if (history.size() > 20) {
                history = history.subList(0, 20);
            }
            profile.history = history.toArray(new GameResult[0]);

            return saveProfile(profile);
        }

        private Profile createNewProfile(String userId) {
            Profile profile = new Profile();
            profile.userId = userId;
            profile.username = "Player_" + userId.substring(0, Math.min(8, userId.length()));
            profile.xpTotal = 0;
            profile.xpByMode = new HashMap<>();
            profile.bestTimes = new HashMap<>();
            profile.history = new GameResult[0];
            profile.unlocks = new String[0];
            profile.activePerk = "none";
            profile.pvpWins = 0;
            profile.pvpLosses = 0;
            profile.pvpRating = 1000; // Starting rating

            saveProfile(profile);
            return profile;
        }

        private boolean saveProfile(Profile profile) {
            Path userFile = usersDir.resolve(profile.userId + ".json");

            try {
                String json = gson.toJson(profile);
                Files.writeString(userFile, json);
                return true;
            } catch (IOException e) {
                System.err.println("Failed to save profile: " + profile.userId);
                return false;
            }
        }
    }

    public static void main(String[] args) {
        try {
            int port = args.length > 0 ? Integer.parseInt(args[0]) : PORT;
            BlueprintHellServerExtended server = new BlueprintHellServerExtended(port);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down server...");
                server.shutdown();
            }));

            server.start();

        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void shutdown() {
        running = false;
        pvpManager.shutdown();
        try {
            serverSocket.close();
            clientExecutor.shutdown();
        } catch (IOException ignored) {}
    }
}
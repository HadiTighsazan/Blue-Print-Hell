package com.blueprinthell.server;

import com.blueprinthell.shared.protocol.NetworkProtocol.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * سرور هدلس BlueprintHell
 * پورت پیش‌فرض: 7777
 */
public class BlueprintHellServer {
    private static final int PORT = 7777;
    private static final String VERSION = "1.0.0";
    private static final String MOTD = "Welcome to BlueprintHell Server!";
    private static final Path DATA_DIR = Paths.get("data");
    private static final Path USERS_DIR = DATA_DIR.resolve("users");

    private final ServerSocket serverSocket;
    private final ExecutorService clientExecutor;
    private final Map<String, ClientHandler> activeClients;
    private final ProfileManager profileManager;
    private final Gson gson;
    private volatile boolean running;

    public BlueprintHellServer(int port) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.clientExecutor = Executors.newCachedThreadPool();
        this.activeClients = new ConcurrentHashMap<>();
        this.profileManager = new ProfileManager(USERS_DIR);
        this.gson = new GsonBuilder().create();
        this.running = true;

        // ایجاد دایرکتوری‌ها
        Files.createDirectories(USERS_DIR);

        System.out.println("BlueprintHell Server v" + VERSION);
        System.out.println("Listening on port " + port);
    }

    public void start() {
        // Thread اصلی برای پذیرش اتصالات
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
     * هندلر برای هر کلاینت متصل
     */
    private class ClientHandler implements Runnable {
        private final Socket socket;
        private final BufferedReader reader;
        private final PrintWriter writer;
        private String userId;
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

                // خواندن پیام‌ها
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
                // Parse base message to get type
                Message baseMsg = gson.fromJson(jsonLine, Message.class);

                switch (baseMsg.type) {
                    case HELLO -> handleHello(jsonLine);
                    case HEARTBEAT -> handleHeartbeat();
                    case SUBMIT_RESULT -> handleSubmitResult(jsonLine);
                    case GET_PROFILE -> handleGetProfile(jsonLine);
                    default -> sendError("UNKNOWN_MSG", "Unknown message type");
                }

            } catch (Exception e) {
                System.err.println("Error handling message: " + e.getMessage());
                sendError("PARSE_ERROR", "Failed to parse message");
            }
        }

        private void handleHello(String json) {
            Hello hello = gson.fromJson(json, Hello.class);
            this.userId = hello.userId;

            // ثبت کلاینت فعال
            activeClients.put(userId, this);

            // ارسال HelloAck
            HelloAck ack = new HelloAck(VERSION, MOTD);
            sendMessage(ack);

            System.out.println("User connected: " + userId + " (client v" + hello.clientVersion + ")");
        }

        private void handleHeartbeat() {
            lastHeartbeat = System.currentTimeMillis();
            sendMessage(new HeartbeatAck());
        }

        private void handleSubmitResult(String json) {
            SubmitResult submit = gson.fromJson(json, SubmitResult.class);
            GameResult result = submit.result;

            // ذخیره در پروفایل
            boolean success = profileManager.addResult(result.userId, result);

            // ارسال تایید
            SubmitResultAck ack = new SubmitResultAck(success, result.resultId);
            sendMessage(ack);

            System.out.println("Result submitted: " + result.resultId +
                    " (user=" + result.userId + ", level=" + result.level +
                    ", score=" + result.score + ", xp=" + result.xp + ")");
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

        private void sendMessage(Message msg) {
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
                    Thread.sleep(2000); // بررسی هر 2 ثانیه

                    long timeSinceLastHeartbeat = System.currentTimeMillis() - lastHeartbeat;
                    if (timeSinceLastHeartbeat > 6000) { // 6 ثانیه timeout
                        System.out.println("Heartbeat timeout for user: " + userId);
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

            if (userId != null) {
                activeClients.remove(userId);
            }

            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    /**
     * مدیریت پروفایل‌های کاربران
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
                // ایجاد پروفایل جدید
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

            // به‌روزرسانی XP
            profile.xpTotal += result.xp;

            // XP by mode
            String modeKey = result.mode.toString();
            profile.xpByMode.merge(modeKey, result.xp, Integer::sum);

            // بهترین زمان
            if (result.durationMs > 0) {
                profile.bestTimes.merge(result.level, result.durationMs, Math::min);
            }

            // افزودن به تاریخچه (حداکثر 20 بازی آخر)
            List<GameResult> history = new ArrayList<>();
            if (profile.history != null) {
                history.addAll(Arrays.asList(profile.history));
            }
            history.add(0, result); // اضافه در ابتدا
            if (history.size() > 20) {
                history = history.subList(0, 20);
            }
            profile.history = history.toArray(new GameResult[0]);

            // ذخیره پروفایل
            return saveProfile(profile);
        }

        private Profile createNewProfile(String userId) {
            Profile profile = new Profile();
            profile.userId = userId;
            profile.username = "Player_" + userId.substring(0, 8);
            profile.xpTotal = 0;
            profile.xpByMode = new HashMap<>();
            profile.bestTimes = new HashMap<>();
            profile.history = new GameResult[0];
            profile.unlocks = new String[0];
            profile.activePerk = "none";

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
            BlueprintHellServer server = new BlueprintHellServer(port);

            // Shutdown hook
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
        try {
            serverSocket.close();
            clientExecutor.shutdown();
        } catch (IOException ignored) {}
    }
}
package com.blueprinthell.client.network;

import com.blueprinthell.shared.protocol.NetworkProtocol.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.Timer;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * مدیریت اتصال به سرور و صف آفلاین
 */
public class ConnectionManager {
    private static final String CLIENT_VERSION = "1.0.0";
    private static final Path OFFLINE_QUEUE_FILE = Paths.get("data/offline_results.jsonl");
    private static final int HEARTBEAT_INTERVAL_MS = 2000;
    private static final int HEARTBEAT_TIMEOUT_MS = 6000;

    // Connection state
    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private final Gson gson;

    private volatile ConnectionState state;
    private String userId;
    private String serverHost;
    private int serverPort;

    private Thread receiveThread;
    private Timer heartbeatTimer;
    private long lastHeartbeatReceived;

    // Callbacks
    private final Map<MessageType, Consumer<Message>> messageHandlers;
    private Consumer<ConnectionState> stateChangeCallback;
    private Consumer<String> errorCallback;

    // Offline queue
    private final Queue<GameResult> offlineQueue;
    private final Object queueLock = new Object();

    public ConnectionManager(String userId) {
        this.userId = userId != null ? userId : generateUserId();
        this.gson = new GsonBuilder().create();
        this.state = ConnectionState.DISCONNECTED;
        this.messageHandlers = new ConcurrentHashMap<>();
        this.offlineQueue = new ConcurrentLinkedQueue<>();

        loadOfflineQueue();
    }

    /**
     * اتصال به سرور
     */
    public CompletableFuture<Boolean> connect(String host, int port) {
        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) {
            return CompletableFuture.completedFuture(false);
        }

        setState(ConnectionState.CONNECTING);
        this.serverHost = host;
        this.serverPort = port;

        return CompletableFuture.supplyAsync(() -> {
            try {
                // ایجاد اتصال TCP
                socket = new Socket(host, port);
                socket.setSoTimeout(HEARTBEAT_TIMEOUT_MS);

                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

                // ارسال Hello
                Hello hello = new Hello(CLIENT_VERSION, userId);
                sendMessage(hello);

                // شروع thread دریافت
                startReceiveThread();

                // شروع heartbeat
                startHeartbeat();

                setState(ConnectionState.CONNECTED);
                System.out.println("Connected to server: " + host + ":" + port);

                return true;

            } catch (Exception e) {
                setState(ConnectionState.ERROR);
                notifyError("Connection failed: " + e.getMessage());
                disconnect();
                return false;
            }
        });
    }

    /**
     * قطع اتصال
     */
    public void disconnect() {
        if (state == ConnectionState.DISCONNECTED) return;

        setState(ConnectionState.DISCONNECTED);

        // توقف heartbeat
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer = null;
        }

        // بستن socket
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}

        socket = null;
        reader = null;
        writer = null;

        System.out.println("Disconnected from server");
    }

    /**
     * ارسال پیام به سرور
     */
    public void sendMessage(Message message) {
        if (writer == null) {
            System.err.println("Cannot send message: not connected");
            return;
        }

        try {
            String json = gson.toJson(message);
            writer.println(json);
            writer.flush();
        } catch (Exception e) {
            notifyError("Failed to send message: " + e.getMessage());
        }
    }

    /**
     * ثبت handler برای پیام‌های دریافتی
     */
    public void registerHandler(MessageType type, Consumer<Message> handler) {
        messageHandlers.put(type, handler);
    }

    /**
     * افزودن نتیجه بازی به صف
     */
    public void submitResult(GameResult result) {
        if (state == ConnectionState.CONNECTED) {
            // ارسال مستقیم به سرور
            sendMessage(new SubmitResult(result));
        } else {
            // ذخیره در صف آفلاین
            addToOfflineQueue(result);
        }
    }

    /**
     * سینک کردن صف آفلاین با سرور
     */
    public CompletableFuture<Integer> syncOfflineQueue() {
        if (state != ConnectionState.CONNECTED) {
            return CompletableFuture.completedFuture(0);
        }

        return CompletableFuture.supplyAsync(() -> {
            int synced = 0;

            synchronized (queueLock) {
                Iterator<GameResult> iter = offlineQueue.iterator();

                while (iter.hasNext()) {
                    GameResult result = iter.next();

                    try {
                        // ارسال به سرور
                        SubmitResult submit = new SubmitResult(result);
                        sendMessage(submit);

                        // صبر برای تایید (simplified - در واقعیت باید async باشد)
                        Thread.sleep(100);

                        // حذف از صف
                        iter.remove();
                        synced++;

                    } catch (Exception e) {
                        System.err.println("Failed to sync result: " + result.resultId);
                        break; // توقف در صورت خطا
                    }
                }

                // ذخیره صف به‌روز شده
                saveOfflineQueue();
            }

            return synced;
        });
    }

    /**
     * دریافت پروفایل از سرور
     */
    public void requestProfile(String targetUserId) {
        if (state != ConnectionState.CONNECTED) {
            notifyError("Not connected to server");
            return;
        }

        sendMessage(new GetProfile(targetUserId));
    }

    // Private methods

    private void startReceiveThread() {
        receiveThread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleReceivedMessage(line);
                }
            } catch (SocketTimeoutException e) {
                notifyError("Connection timeout");
            } catch (IOException e) {
                if (state == ConnectionState.CONNECTED) {
                    notifyError("Connection lost: " + e.getMessage());
                }
            } finally {
                disconnect();
            }
        });
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    private void handleReceivedMessage(String json) {
        try {
            Message msg = gson.fromJson(json, Message.class);

            // به‌روزرسانی heartbeat
            if (msg.type == MessageType.HEARTBEAT_ACK) {
                lastHeartbeatReceived = System.currentTimeMillis();
            }

            // پیام‌های خاص
            switch (msg.type) {
                case HELLO_ACK -> {
                    HelloAck ack = gson.fromJson(json, HelloAck.class);
                    System.out.println("Server version: " + ack.serverVersion);
                    System.out.println("MOTD: " + ack.motd);
                }
                case ERROR -> {
                    ErrorMessage err = gson.fromJson(json, ErrorMessage.class);
                    notifyError("Server error: " + err.code + " - " + err.msg);
                }
                case SUBMIT_RESULT_ACK -> {
                    SubmitResultAck ack = gson.fromJson(json, SubmitResultAck.class);
                    if (ack.success) {
                        System.out.println("Result submitted successfully: " + ack.resultId);
                    }
                }
                case PROFILE -> {
                    Profile profile = gson.fromJson(json, Profile.class);
                    // اطلاع به handler
                    Consumer<Message> handler = messageHandlers.get(MessageType.PROFILE);
                    if (handler != null) {
                        handler.accept(profile);
                    }
                }
            }

            // اطلاع به handler عمومی
            Consumer<Message> handler = messageHandlers.get(msg.type);
            if (handler != null) {
                handler.accept(msg);
            }

        } catch (Exception e) {
            System.err.println("Failed to handle message: " + e.getMessage());
        }
    }

    private void startHeartbeat() {
        lastHeartbeatReceived = System.currentTimeMillis();

        heartbeatTimer = new Timer("Heartbeat", true);
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (state != ConnectionState.CONNECTED) return;

                // بررسی timeout
                long timeSinceLastHeartbeat = System.currentTimeMillis() - lastHeartbeatReceived;
                if (timeSinceLastHeartbeat > HEARTBEAT_TIMEOUT_MS) {
                    notifyError("Heartbeat timeout");
                    disconnect();
                    return;
                }

                // ارسال heartbeat
                sendMessage(new Heartbeat());
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS);
    }

    private void setState(ConnectionState newState) {
        if (state == newState) return;

        state = newState;

        if (stateChangeCallback != null) {
            SwingUtilities.invokeLater(() -> stateChangeCallback.accept(newState));
        }
    }

    private void notifyError(String error) {
        System.err.println("Connection error: " + error);

        if (errorCallback != null) {
            SwingUtilities.invokeLater(() -> errorCallback.accept(error));
        }
    }

    private void addToOfflineQueue(GameResult result) {
        synchronized (queueLock) {
            offlineQueue.offer(result);
            saveOfflineQueue();
        }
        System.out.println("Result saved to offline queue: " + result.resultId);
    }

    private void loadOfflineQueue() {
        if (!Files.exists(OFFLINE_QUEUE_FILE)) return;

        try {
            List<String> lines = Files.readAllLines(OFFLINE_QUEUE_FILE);
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    GameResult result = gson.fromJson(line, GameResult.class);
                    offlineQueue.offer(result);
                }
            }
            System.out.println("Loaded " + offlineQueue.size() + " offline results");

        } catch (IOException e) {
            System.err.println("Failed to load offline queue: " + e.getMessage());
        }
    }

    private void saveOfflineQueue() {
        try {
            Files.createDirectories(OFFLINE_QUEUE_FILE.getParent());

            try (BufferedWriter writer = Files.newBufferedWriter(OFFLINE_QUEUE_FILE)) {
                for (GameResult result : offlineQueue) {
                    writer.write(gson.toJson(result));
                    writer.newLine();
                }
            }

        } catch (IOException e) {
            System.err.println("Failed to save offline queue: " + e.getMessage());
        }
    }

    private String generateUserId() {
        // تلاش برای گرفتن MAC address
        try {
            Enumeration<NetworkInterface> networks = NetworkInterface.getNetworkInterfaces();
            while (networks.hasMoreElements()) {
                NetworkInterface network = networks.nextElement();
                byte[] mac = network.getHardwareAddress();

                if (mac != null && mac.length > 0) {
                    StringBuilder sb = new StringBuilder("u-");
                    for (byte b : mac) {
                        sb.append(String.format("%02X", b));
                    }
                    return sb.toString();
                }
            }
        } catch (Exception ignored) {}

        // Fallback به UUID
        return "u-" + UUID.randomUUID().toString();
    }

    // Getters & Setters

    public ConnectionState getState() {
        return state;
    }

    public String getUserId() {
        return userId;
    }

    public int getOfflineQueueSize() {
        return offlineQueue.size();
    }

    public void setStateChangeCallback(Consumer<ConnectionState> callback) {
        this.stateChangeCallback = callback;
    }

    public void setErrorCallback(Consumer<String> callback) {
        this.errorCallback = callback;
    }
}
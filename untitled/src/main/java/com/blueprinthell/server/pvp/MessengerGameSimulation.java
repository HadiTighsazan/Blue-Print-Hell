package com.blueprinthell.server.pvp;

import com.blueprinthell.shared.protocol.NetworkProtocol.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * شبیه‌سازی سبک بازی Messenger برای سرور
 * حالا با منطق کامل برخورد و معماری سرور-محور
 */
public class MessengerGameSimulation {

    // Constants
    private static final double MESSENGER_BASE_SPEED = 100.0; // pixels per second
    private static final double PACKET_SPAWN_INTERVAL = 1.0; // seconds between auto spawns
    private static final int WIRE_LENGTH_PIXELS = 300; // average wire length
    private static final int AMMO_CAP = 10;
    private static final int COLLISION_CELL_SIZE = 50; // اندازه هر سلول گرید فضایی
    private static final double COLLISION_RADIUS = 18.0; // شعاع برخورد پکت‌ها
    private final SpatialHashGrid<SimPacket> collisionGrid = new SpatialHashGrid<>(COLLISION_CELL_SIZE);

    private final AtomicInteger packetIdCounter = new AtomicInteger(0);

    // Automatic spawning
    private double spawnAccumulatorP1 = 0;
    private double spawnAccumulatorP2 = 0;
    private int autoSpawnTurn = 1; // Alternates between 1 and 2

    /**
     * سازنده خالی.
     */
    public MessengerGameSimulation() {
        // Constructor is now empty
    }

    /**
     * یک گام شبیه‌سازی را اجرا می‌کند و مستقیماً gameState را تغییر می‌دهد.
     * @param gameState حالت بازی که باید تغییر کند
     * @param dt Delta time in seconds
     */
    public void tick(PvPGameState gameState, double dt) {
        // فاز ۱: به‌روزرسانی مختصات و پر کردن گرید برخورد
        updatePacketCoordinatesAndGrid(gameState);

        // فاز ۲: حرکت پکت‌ها، تشخیص برخورد و تحویل
        moveAndProcessPackets(gameState, dt);

        // Handle automatic spawning from uncontrolled sources
        handleAutoSpawning(gameState, dt);

        // Update scores
        gameState.scoreP1.totalScore = gameState.scoreP1.delivered - (int)(gameState.scoreP1.lost * 1.5);
        gameState.scoreP2.totalScore = gameState.scoreP2.delivered - (int)(gameState.scoreP2.lost * 1.5);
    }

    /**
     * مختصات دقیق هر پکت را محاسبه کرده و آن را در گرید فضایی برای تشخیص برخورد درج می‌کند.
     */
    private void updatePacketCoordinatesAndGrid(PvPGameState gameState) {
        collisionGrid.clear();
        for (SimPacket packet : gameState.activePackets.values()) {
            WireLayout wire = findWire(gameState, packet.wireId, packet.playerSide);
            if (wire != null) {
                WireLayout.Point2D pos = WirePathHelper.pointAt(wire, packet.progress);
                packet.x = pos.x;
                packet.y = pos.y;
                collisionGrid.insert(packet.x, packet.y, packet);
            }
        }
    }

    /**
     * پکت‌ها را حرکت می‌دهد، برخوردها را شناسایی و پردازش می‌کند و پکت‌های رسیده را تحویل می‌دهد.
     */
    private void moveAndProcessPackets(PvPGameState gameState, double dt) {
        Set<String> toRemove = new HashSet<>();

        for (SimPacket packet : gameState.activePackets.values()) {
            if (toRemove.contains(packet.id)) continue;

            // ۱. حرکت پکت
            double speed = MESSENGER_BASE_SPEED * gameState.globalSpeedMultiplier;
            packet.progress += (speed * dt) / packet.wireLength;

            // ۲. بررسی برخورد فقط بین بازیکنان مختلف
            List<SimPacket> neighbors = collisionGrid.retrieve(packet.x, packet.y);
            for (SimPacket other : neighbors) {
                if (other == packet || toRemove.contains(other.id) || packet.playerSide == other.playerSide) continue;

                double dx = packet.x - other.x;
                double dy = packet.y - other.y;
                if (Math.sqrt(dx * dx + dy * dy) <= COLLISION_RADIUS) {
                    // برخورد رخ داد! هر دو پکت حذف می‌شوند.
                    toRemove.add(packet.id);
                    toRemove.add(other.id);

                    // ثبت امتیاز از دست رفته برای هر بازیکن
                    if (packet.playerSide == 1) gameState.scoreP1.lost++; else gameState.scoreP2.lost++;
                    if (other.playerSide == 1) gameState.scoreP1.lost++; else gameState.scoreP2.lost++;
                    break;
                }
            }

            if (toRemove.contains(packet.id)) continue;

            // ۳. بررسی رسیدن به مقصد
            if (packet.progress >= 1.0) {
                WireLayout wire = findWire(gameState, packet.wireId, packet.playerSide);
                if (wire != null) {
                    SystemLayout destBox = findBox(gameState, wire.toBoxId, packet.playerSide);
                    if (destBox != null && destBox.isSink) {
                        // تحویل موفق
                        if (packet.playerSide == 1) {
                            gameState.scoreP1.delivered++;
                            gameState.scoreP1.ammo = Math.min(gameState.scoreP1.ammo + 1, AMMO_CAP);
                        } else {
                            gameState.scoreP2.delivered++;
                            gameState.scoreP2.ammo = Math.min(gameState.scoreP2.ammo + 1, AMMO_CAP);
                        }
                    }
                }
                toRemove.add(packet.id);
            }
        }

        // حذف پکت‌های از بین رفته یا تحویل داده شده
        for (String id : toRemove) {
            gameState.activePackets.remove(id);
        }
    }

    private void handleAutoSpawning(PvPGameState gameState, double dt) {
        spawnAccumulatorP1 += dt;
        spawnAccumulatorP2 += dt;

        if (autoSpawnTurn == 1 && spawnAccumulatorP1 >= PACKET_SPAWN_INTERVAL) {
            spawnFromSources(gameState, 1);
            spawnAccumulatorP1 = 0;
            autoSpawnTurn = 2;
        }

        if (autoSpawnTurn == 2 && spawnAccumulatorP2 >= PACKET_SPAWN_INTERVAL) {
            spawnFromSources(gameState, 2);
            spawnAccumulatorP2 = 0;
            autoSpawnTurn = 1;
        }
    }

    private void spawnFromSources(PvPGameState gameState, int playerSide) {
        NetworkLayout layout = (playerSide == 1) ? gameState.layoutP1 : gameState.layoutP2;
        if (layout == null || layout.layout == null || layout.layout.boxes == null) return;

        for (SystemLayout box : layout.layout.boxes) {
            if (box.isSource && box.outShapes != null) {
                for (int i = 0; i < box.outShapes.size(); i++) {
                    String wireId = findWireFromSource(gameState, box.id, i, playerSide);
                    if (wireId != null) {
                        spawnPacket(gameState, wireId, playerSide, "MESSENGER");
                    }
                }
            }
        }
    }

    // ### START OF FIX ###
    /**
     * یک پکت جدید در شبکه حریف از یکی از سیستم‌های Source او ایجاد می‌کند.
     * @param gameState وضعیت فعلی بازی
     * @param injectorSide بازیکنی که این عمل را انجام داده (1 یا 2)
     */
    public void injectPacket(PvPGameState gameState, int injectorSide) {
        int opponentSide = (injectorSide == 1) ? 2 : 1;

        // ۱. تمام سیستم‌های Source حریف را پیدا کن
        NetworkLayout opponentLayout = (opponentSide == 1) ? gameState.layoutP1 : gameState.layoutP2;
        if (opponentLayout == null || opponentLayout.layout == null || opponentLayout.layout.boxes == null) return;

        List<SystemLayout> opponentSources = new ArrayList<>();
        for (SystemLayout box : opponentLayout.layout.boxes) {
            if (box.isSource) {
                opponentSources.add(box);
            }
        }

        if (opponentSources.isEmpty()) return;

        // ۲. یک سیستم Source و یک پورت خروجی تصادفی از آن انتخاب کن
        SystemLayout sourceBox = opponentSources.get(ThreadLocalRandom.current().nextInt(opponentSources.size()));

        if (sourceBox.outShapes == null || sourceBox.outShapes.isEmpty()) return;

        int outIndex = ThreadLocalRandom.current().nextInt(sourceBox.outShapes.size());
        String wireId = findWireFromSource(gameState, sourceBox.id, outIndex, opponentSide);

        // ۳. پکت را در شبکه حریف ایجاد کن
        if (wireId != null) {
            spawnPacket(gameState, wireId, opponentSide, "MESSENGER");
        }
    }
    // ### END OF FIX ###

    private void spawnPacket(PvPGameState gameState, String wireId, int playerSide, String type) {
        String packetId = "pkt-" + packetIdCounter.incrementAndGet();

        SimPacket packet = new SimPacket();
        packet.id = packetId;
        packet.wireId = wireId;
        packet.playerSide = playerSide;
        packet.progress = 0.0;
        packet.wireLength = WIRE_LENGTH_PIXELS;
        packet.noise = 0;
        packet.type = type;

        gameState.activePackets.put(packetId, packet);
    }

    private String findWireFromSource(PvPGameState gameState, String boxId, int outIndex, int playerSide) {
        NetworkLayout layout = (playerSide == 1) ? gameState.layoutP1 : gameState.layoutP2;
        if (layout == null || layout.layout == null) return null;

        for (WireLayout wire : layout.layout.wires) {
            if (wire.fromBoxId.equals(boxId) && wire.fromOutIndex == outIndex) {
                return wire.id;
            }
        }
        return null;
    }

    private WireLayout findWire(PvPGameState gameState, String wireId, int playerSide) {
        NetworkLayout layout = (playerSide == 1) ? gameState.layoutP1 : gameState.layoutP2;
        if (layout == null || layout.layout == null) return null;

        for (WireLayout wire : layout.layout.wires) {
            if (wire.id.equals(wireId)) {
                return wire;
            }
        }
        return null;
    }

    private SystemLayout findBox(PvPGameState gameState, String boxId, int playerSide) {
        NetworkLayout layout = (playerSide == 1) ? gameState.layoutP1 : gameState.layoutP2;
        if (layout == null || layout.layout == null) return null;

        for (SystemLayout box : layout.layout.boxes) {
            if (box.id.equals(boxId)) {
                return box;
            }
        }
        return null;
    }

    // Internal classes
    static class NetworkLayout {
        public final SubmitLayout layout;
        public final int playerSide;

        NetworkLayout(SubmitLayout layout, int side) {
            this.layout = layout;
            this.playerSide = side;
        }
    }

    static class SimPacket {
        String id;
        String wireId;
        int playerSide;
        double progress;
        double wireLength;
        double noise;
        String type;
        int x;
        int y;
    }
}
package com.blueprinthell.server.pvp;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.GameController;
import com.blueprinthell.controller.packet.PacketProducerController;
import com.blueprinthell.level.LevelManager;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.shared.protocol.NetworkProtocol.*;
import com.blueprinthell.snapshot.NetworkSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ServerGameSession {

    private final String matchId;
    private final GameController gameControllerP1;
    private final GameController gameControllerP2;


    public ServerGameSession(String matchId) {
        this.matchId = matchId;

        // 1. Create GameControllers in headless mode
        this.gameControllerP1 = new GameController(true);
        this.gameControllerP2 = new GameController(true);

        // 2. Create and set a LevelManager for each controller.
        // DO NOT load the level here anymore. This will be done after restoring state.
        LevelManager levelManagerP1 = new LevelManager(gameControllerP1, null);
        gameControllerP1.setLevelManager(levelManagerP1);

        LevelManager levelManagerP2 = new LevelManager(gameControllerP2, null);
        gameControllerP2.setLevelManager(levelManagerP2);
    }


// In blueprinthell/server/pvp/ServerGameSession.java

    public void initializeLayouts(SubmitLayout layoutP1, SubmitLayout layoutP2) {
        System.out.println(">>> Initializing layouts for match: " + matchId);

        // Player 1
        if (layoutP1 != null) {
            // 1. ابتدا level را load کن (برای ساختار اولیه)
            gameControllerP1.getLevelManager().loadLevel(1);

            // 2. تبدیل layout به snapshot
            NetworkSnapshot snapshotP1 = LayoutConverter.convertLayoutToSnapshot(layoutP1, 1);

            // 3. بازیابی state (این boxes و wires را پر می‌کند)
            gameControllerP1.restoreState(snapshotP1);

            // 4. ساخت Producer با wires بازیابی شده
            List<SystemBoxModel> sourcesP1 = gameControllerP1.getBoxes().stream()
                    .filter(b -> b.getInPorts().isEmpty() && !b.getOutPorts().isEmpty())
                    .collect(Collectors.toList());

            int packetsPerPortP1 = gameControllerP1.getLevelManager()
                    .getCurrentLevel()
                    .getPacketsPerPort();

            PacketProducerController producerP1 = new PacketProducerController(
                    sourcesP1,
                    gameControllerP1.getWires(),
                    gameControllerP1.getDestMap(),
                    Config.DEFAULT_PACKET_SPEED * 0.8,
                    packetsPerPortP1,
                    gameControllerP1.getLossModel()
            ) {
                // Override INTERVAL_SEC برای PvP
                private static final double INTERVAL_SEC = 0.6;  // به جای 0.4
            };
            gameControllerP1.setProducerController(producerP1);
            gameControllerP1.getSimulation().setPacketProducerController(producerP1);
        }
        if (layoutP2 != null) {
            // 1. ابتدا level را load کن (برای ساختار اولیه)
            gameControllerP2.getLevelManager().loadLevel(1);

            // 2. تبدیل layout به snapshot
            NetworkSnapshot snapshotP2 = LayoutConverter.convertLayoutToSnapshot(layoutP2, 2);

            // 3. بازیابی state (این boxes و wires را پر می‌کند)
            gameControllerP2.restoreState(snapshotP2);

            // 4. ساخت Producer با wires بازیابی شده
            List<SystemBoxModel> sourcesP2 = gameControllerP2.getBoxes().stream()
                    .filter(b -> b.getInPorts().isEmpty() && !b.getOutPorts().isEmpty())
                    .collect(Collectors.toList());

            int packetsPerPortP2 = gameControllerP2.getLevelManager()
                    .getCurrentLevel()
                    .getPacketsPerPort();

            PacketProducerController producerP2 = new PacketProducerController(
                    sourcesP2,
                    gameControllerP2.getWires(),
                    gameControllerP2.getDestMap(),
                    Config.DEFAULT_PACKET_SPEED * 0.8, // مثل پلیر 1
                    packetsPerPortP2,
                    gameControllerP2.getLossModel()
            ) {
                // Override INTERVAL_SEC برای PvP (مانند پلیر 1)
                private static final double INTERVAL_SEC = 0.6;
            };

            gameControllerP2.setProducerController(producerP2);
            gameControllerP2.getSimulation().setPacketProducerController(producerP2);
        }

    }


// اضافه کردن debug logging به ServerGameSession.java

// در متد captureSnapshots، برای اطمینان از اینکه packets واقعاً capture می‌شوند:

    public NetworkSnapshot[] captureSnapshots() {
        NetworkSnapshot[] snapshots = new NetworkSnapshot[]{
                gameControllerP1.captureSnapshot(),
                gameControllerP2.captureSnapshot()
        };

        // ===== Debug logging =====
        // بررسی تعداد packets در snapshot هر بازیکن
        for (int i = 0; i < snapshots.length; i++) {
            if (snapshots[i] != null && snapshots[i].world != null && snapshots[i].world.wires != null) {
                int totalPackets = 0;
                for (NetworkSnapshot.WireState wire : snapshots[i].world.wires) {
                    if (wire.packetsOnWire != null) {
                        totalPackets += wire.packetsOnWire.size();
                    }
                }
                System.out.println("[SERVER DEBUG] Player " + (i+1) + " snapshot has " +
                        totalPackets + " packets on wires");
            }
        }
        // ===== End debug logging =====

        return snapshots;
    }

// همچنین در tick متد، برای اطمینان از اینکه simulation واقعاً update می‌شود:

    public void tick(double dt) {
        // بررسی که simulation در حال اجرا است
        if (!gameControllerP1.getSimulation().isRunning()) {
            System.out.println("[SERVER DEBUG] Starting P1 simulation in tick");
            gameControllerP1.getSimulation().start();
        }
        if (!gameControllerP2.getSimulation().isRunning()) {
            System.out.println("[SERVER DEBUG] Starting P2 simulation in tick");
            gameControllerP2.getSimulation().start();
        }

        // آپدیت معمولی
        gameControllerP1.getSimulation().update(dt);
        gameControllerP2.getSimulation().update(dt);

        // ===== Debug: بررسی وضعیت producers =====
        if (gameControllerP1.getProducerController() != null) {
            PacketProducerController p1Producer = gameControllerP1.getProducerController();
            if (p1Producer.isRunning() && p1Producer.getProducedCount() > 0) {
                System.out.println("[SERVER DEBUG] P1 Producer: produced=" +
                        p1Producer.getProducedCount() +
                        ", inFlight=" + p1Producer.getInFlight());
            }
        }

        if (gameControllerP2.getProducerController() != null) {
            PacketProducerController p2Producer = gameControllerP2.getProducerController();
            if (p2Producer.isRunning() && p2Producer.getProducedCount() > 0) {
                System.out.println("[SERVER DEBUG] P2 Producer: produced=" +
                        p2Producer.getProducedCount() +
                        ", inFlight=" + p2Producer.getInFlight());
            }
        }
        // ===== End debug =====
    }
    public void startPacketProduction() {
        // بررسی وضعیت قبل از شروع
        System.out.println(">>> Starting packet production for match " + matchId);

        // Player 1
        if (gameControllerP1 != null) {
            // بررسی simulation state
            if (!gameControllerP1.getSimulation().isRunning()) {
                System.out.println(">>> Starting simulation for Player 1");
                gameControllerP1.getSimulation().start();
            }

            PacketProducerController producer = gameControllerP1.getProducerController();
            if (producer != null) {
                // بررسی که آیا wires موجود هستند
                List<WireModel> wires = gameControllerP1.getWires();
                System.out.println(">>> P1 has " + wires.size() + " wires");

                // شروع تولید
                producer.startProduction();
                System.out.println(">>> Production started for Player 1");
            } else {
                System.err.println(">>> ERROR: No producer for Player 1!");
            }
        }

        // Player 2
        if (gameControllerP2 != null) {
            // بررسی simulation state
            if (!gameControllerP2.getSimulation().isRunning()) {
                System.out.println(">>> Starting simulation for Player 2");
                gameControllerP2.getSimulation().start();
            }

            PacketProducerController producer = gameControllerP2.getProducerController();
            if (producer != null) {
                // بررسی که آیا wires موجود هستند
                List<WireModel> wires = gameControllerP2.getWires();
                System.out.println(">>> P2 has " + wires.size() + " wires");

                // شروع تولید
                producer.startProduction();
                System.out.println(">>> Production started for Player 2");
            } else {
                System.err.println(">>> ERROR: No producer for Player 2!");
            }
        }
    }



    public void applyPlayerAction(int playerSide, Message action) {
        GameController gc = (playerSide == 1) ? gameControllerP1 : gameControllerP2;

        if (action instanceof PlayerAction_MoveBox moveAction) {
            findBoxById(gc, moveAction.boxId).ifPresent(box -> {
                box.setX(moveAction.newX);
                box.setY(moveAction.newY);
            });
        } else if (action instanceof PlayerAction_CreateWire createAction) {
            // This logic is complex because we need to find boxes and ports
            // For now, we'll keep it simple. It can be expanded later.
            System.out.println("Server: Received request to create a wire.");
        } else if (action instanceof PlayerAction_BuyItem buyAction) {
            // Server-side validation and execution of shop purchases
            // Example:
            // if (gc.getCoinModel().getCoins() >= getItemCost(buyAction.itemId)) {
            //     gc.getCoinModel().spend(getItemCost(buyAction.itemId));
            //     applyItemEffect(gc, buyAction.itemId);
            // }
            System.out.println("Server: Player " + playerSide + " requested to buy " + buyAction.itemId);
        } else if (action instanceof Inject injectAction) {
            // This is where you'd inject a packet into the opponent's network
            GameController opponentGc = (playerSide == 1) ? gameControllerP2 : gameControllerP1;
            // TODO: Implement logic to find a source box on opponent's grid and spawn a packet
            System.out.println("Server: Player " + playerSide + " injected a packet into the opponent's network.");
        }
    }

    private Optional<SystemBoxModel> findBoxById(GameController gc, String boxId) {
        return gc.getBoxes().stream()
                .filter(box -> box.getId().equals(boxId))
                .findFirst();
    }

    private Optional<WireModel> findWireById(GameController gc, String wireId) {
        return gc.getWires().stream()
                .filter(wire -> wire.getCanonicalId().equals(wireId))
                .findFirst();
    }



    public void startSimulations() {
        gameControllerP1.getSimulation().start();
        gameControllerP2.getSimulation().start();
    }

    public void stopSimulations() {
        gameControllerP1.getSimulation().stop();
        gameControllerP2.getSimulation().stop();
    }
}
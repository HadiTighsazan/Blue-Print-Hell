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



// In file: blueprinthell/server/pvp/ServerGameSession.java

    public void initializeLayouts(SubmitLayout layoutP1, SubmitLayout layoutP2) {
        System.out.println("Initializing layouts for match: " + matchId);

        // Player 1
        if (layoutP1 != null) {
            // 1. Load the level structure first
            gameControllerP1.getLevelManager().loadLevel(1);

            // 2. Convert the player's layout into a snapshot
            NetworkSnapshot snapshotP1 = LayoutConverter.convertLayoutToSnapshot(layoutP1, 1);

            // 3. Restore the state to apply the player's layout
            gameControllerP1.restoreState(snapshotP1);

            // 4. *** THE FIX: Re-create the PacketProducerController with the now-populated lists ***
            List<SystemBoxModel> sourcesP1 = gameControllerP1.getBoxes().stream()
                    .filter(b -> b.getInPorts().isEmpty() && !b.getOutPorts().isEmpty())
                    .collect(Collectors.toList());

            int packetsPerPortP1 = gameControllerP1.getLevelManager().getCurrentLevel().getPacketsPerPort();

            PacketProducerController producerP1 = new PacketProducerController(
                    sourcesP1,
                    gameControllerP1.getWires(),
                    gameControllerP1.getDestMap(),
                    Config.DEFAULT_PACKET_SPEED,
                    packetsPerPortP1,
                    gameControllerP1.getLossModel()
            );
            gameControllerP1.setProducerController(producerP1);
            gameControllerP1.getSimulation().setPacketProducerController(producerP1);
            System.out.println("Player 1 layout and producer re-configured on server.");

        } else {
            System.err.println("WARNING: Player 1 layout was null for match " + matchId);
        }

        // Player 2 (Apply the same logic)
        if (layoutP2 != null) {
            gameControllerP2.getLevelManager().loadLevel(1);
            NetworkSnapshot snapshotP2 = LayoutConverter.convertLayoutToSnapshot(layoutP2, 2); // Use a different level number for clarity if needed, though 1 is fine
            gameControllerP2.restoreState(snapshotP2);

            // *** THE FIX: Re-create the PacketProducerController for Player 2 ***
            List<SystemBoxModel> sourcesP2 = gameControllerP2.getBoxes().stream()
                    .filter(b -> b.getInPorts().isEmpty() && !b.getOutPorts().isEmpty())
                    .collect(Collectors.toList());

            int packetsPerPortP2 = gameControllerP2.getLevelManager().getCurrentLevel().getPacketsPerPort();

            PacketProducerController producerP2 = new PacketProducerController(
                    sourcesP2,
                    gameControllerP2.getWires(),
                    gameControllerP2.getDestMap(),
                    Config.DEFAULT_PACKET_SPEED,
                    packetsPerPortP2,
                    gameControllerP2.getLossModel()
            );
            gameControllerP2.setProducerController(producerP2);
            gameControllerP2.getSimulation().setPacketProducerController(producerP2);
            System.out.println("Player 2 layout and producer re-configured on server.");
        } else {
            System.err.println("WARNING: Player 2 layout was null for match " + matchId);
        }
    }

    public void tick(double dt) {
        gameControllerP1.getSimulation().update(dt);
        gameControllerP2.getSimulation().update(dt);
    }

// in blueprinthell/server/pvp/ServerGameSession.java

    public void startPacketProduction() {
        // Start production for player 1
        if (gameControllerP1 != null && gameControllerP1.getProducerController() != null) {
            gameControllerP1.getProducerController().startProduction();
            // *** ADDING DEBUG MESSAGE ***
            System.out.println(">>> [SERVER] Packet production STARTED for Player 1 in match " + matchId);
        } else {
            System.err.println(">>> [SERVER ERROR] PacketProducerController for Player 1 is NULL in match " + matchId + ". CANNOT START PRODUCTION.");
        }

        // Start production for player 2
        if (gameControllerP2 != null && gameControllerP2.getProducerController() != null) {
            gameControllerP2.getProducerController().startProduction();
            // *** ADDING DEBUG MESSAGE ***
            System.out.println(">>> [SERVER] Packet production STARTED for Player 2 in match " + matchId);
        } else {
            System.err.println(">>> [SERVER ERROR] PacketProducerController for Player 2 is NULL in match " + matchId + ". CANNOT START PRODUCTION.");
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

    public NetworkSnapshot[] captureSnapshots() {
        return new NetworkSnapshot[]{
                gameControllerP1.captureSnapshot(),
                gameControllerP2.captureSnapshot()
        };
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
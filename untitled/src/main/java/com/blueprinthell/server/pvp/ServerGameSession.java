package com.blueprinthell.server.pvp;

import com.blueprinthell.controller.GameController;
import com.blueprinthell.level.LevelRegistry;
import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.shared.protocol.NetworkProtocol.*;
import com.blueprinthell.snapshot.NetworkSnapshot;

import java.util.Optional;

public class ServerGameSession {

    private final String matchId;
    private final GameController gameControllerP1;
    private final GameController gameControllerP2;

    public ServerGameSession(String matchId) {
        this.matchId = matchId;
        this.gameControllerP1 = new GameController(true);
        this.gameControllerP2 = new GameController(true);
        gameControllerP1.startLevel(LevelRegistry.getLevel(1).getDefinition());
        gameControllerP2.startLevel(LevelRegistry.getLevel(1).getDefinition());
    }

    public void tick(double dt) {
        gameControllerP1.getSimulation().update(dt);
        gameControllerP2.getSimulation().update(dt);
    }

    /**
     * Applies a fully deserialized player action to the correct GameController.
     */
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
package com.blueprinthell.server.pvp;

import com.blueprinthell.model.PortShape;
import com.blueprinthell.shared.protocol.NetworkProtocol.SystemLayout;
import com.blueprinthell.shared.protocol.NetworkProtocol.WireLayout;
import com.blueprinthell.shared.protocol.NetworkProtocol.SubmitLayout;
import com.blueprinthell.snapshot.NetworkSnapshot;
import com.blueprinthell.snapshot.NetworkSnapshot.*;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class to convert player-submitted layouts into a full NetworkSnapshot
 * that can be restored on the headless server-side GameController.
 */
public final class LayoutConverter {

    private LayoutConverter() {}

    /**
     * Converts a player's submitted layout into a valid initial NetworkSnapshot.
     * @param layout The layout received from the client.
     * @param levelNumber The level number for this match (usually 1 for PvP).
     * @return A complete NetworkSnapshot representing the initial state of the game.
     */
    public static NetworkSnapshot convertLayoutToSnapshot(SubmitLayout layout, int levelNumber) {
        NetworkSnapshot snap = new NetworkSnapshot();

        // 1. Fill Meta
        snap.meta = new Meta();
        snap.meta.levelNumber = levelNumber;
        snap.meta.schemaVersion = NetworkSnapshot.SCHEMA_VERSION;

        // 2. Fill initial WorldState
        snap.world = new WorldState();
        snap.world.score = 0;
        snap.world.coins = 3; // Initial coins for PvP
        snap.world.packetLoss = 0;
        snap.world.wireUsageUsed = 0;
        snap.world.wireUsageTotal = 99999.0; // Effectively infinite for PvP build phase

        // 3. Convert Boxes
        for (SystemLayout bl : layout.boxes) {
            BoxState bs = new BoxState();
            bs.id = bl.id;
            bs.x = bl.x;
            bs.y = bl.y;
            bs.width = bl.width;
            bs.height = bl.height;
            bs.enabled = true;
            bs.primaryKind = com.blueprinthell.controller.systems.SystemKind.valueOf(bl.kind);
            bs.inShapes = bl.inShapes.stream().map(PortShape::valueOf).collect(Collectors.toList());
            bs.outShapes = bl.outShapes.stream().map(PortShape::valueOf).collect(Collectors.toList());
            snap.world.boxes.add(bs);
        }

        // 4. Convert Wires
        double totalWireLength = 0;
        for (WireLayout wl : layout.wires) {
            WireState ws = new WireState();
            ws.id = wl.id;
            ws.fromBoxId = wl.fromBoxId;
            ws.fromOutIndex = wl.fromOutIndex;
            ws.toBoxId = wl.toBoxId;
            ws.toInIndex = wl.toInIndex;

            // Convert path and calculate length
            double wireLen = 0;
            Point prevPoint = null;
            if (wl.path != null) {
                for (WireLayout.Point2D p2d : wl.path) {
                    Point currentPoint = new Point(p2d.x, p2d.y);
                    ws.path.add(new IntPoint(currentPoint.x, currentPoint.y));
                    if (prevPoint != null) {
                        wireLen += prevPoint.distance(currentPoint);
                    }
                    prevPoint = currentPoint;
                }
            }
            totalWireLength += wireLen;
            snap.world.wires.add(ws);
        }
        snap.world.wireUsageUsed = totalWireLength;

        return snap;
    }
}
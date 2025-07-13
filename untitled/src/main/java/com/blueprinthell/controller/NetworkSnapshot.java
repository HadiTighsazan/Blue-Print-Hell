package com.blueprinthell.controller;

import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.PortShape;
import java.util.List;

/**
 * Snapshot of the network state at a given time, for temporal navigation.
 */
public record NetworkSnapshot(
        int score,
        int coins,      // NEW: total coins at snapshot time
        int packetLoss,
        List<SystemBoxState> boxStates,
        List<WireState> wireStates
) {
    /**
     * Immutable state of a SystemBoxModel **including its internal buffer**.
     */
    public record SystemBoxState(
            int x,
            int y,
            int width,
            int height,
            List<PortShape> inShapes,
            List<PortShape> outShapes,
            List<PacketState> bufferPackets // NEW: packets waiting inside the box
    ) {}

    /**
     * Immutable state of a WireModel and its packets.
     */
    public record WireState(
            int srcPortX,
            int srcPortY,
            int dstPortX,
            int dstPortY,
            List<PacketState> packets
    ) {}

    /**
     * Immutable state of a PacketModel (either on a wire or in a buffer).
     */
    public record PacketState(
            double progress,
            double noise,
            PacketType type
    ) {}
}

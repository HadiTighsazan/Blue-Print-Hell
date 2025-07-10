package com.blueprinthell.controller;

import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.PortShape;

import java.util.List;

/**
 * Snapshot of the network state at a given time, for temporal navigation.
 */
public record NetworkSnapshot(
        int score,
        int packetLoss,
        List<SystemBoxState> boxStates,
        List<WireState> wireStates
) {
    /**
     * Immutable state of a SystemBoxModel.
     */
    public record SystemBoxState(
            int x,
            int y,
            int width,
            int height,
            List<PortShape> inShapes,
            List<PortShape> outShapes
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
     * Immutable state of a PacketModel on a wire.
     */
    public record PacketState(
            double progress,
            double noise,
            PacketType type
    ) {}
}

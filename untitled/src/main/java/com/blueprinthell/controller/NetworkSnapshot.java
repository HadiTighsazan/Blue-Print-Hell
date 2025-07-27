package com.blueprinthell.controller;

import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.PortShape;
import java.util.List;


public record NetworkSnapshot(
        int score,
        int coins,      // NEW: total coins at snapshot time
        int packetLoss,
        List<SystemBoxState> boxStates,
        List<WireState> wireStates
) {

    public record SystemBoxState(
            int x,
            int y,
            int width,
            int height,
            List<PortShape> inShapes,
            List<PortShape> outShapes,
            List<PacketState> bufferPackets
    ) {}


    public record WireState(
            int srcPortX,
            int srcPortY,
            int dstPortX,
            int dstPortY,
            List<PacketState> packets
    ) {}


    public record PacketState(
            double progress,
            double noise,
            PacketType type
    ) {}
}

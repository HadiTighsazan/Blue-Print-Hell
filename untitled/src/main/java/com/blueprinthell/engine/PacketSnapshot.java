package com.blueprinthell.engine;

import com.blueprinthell.model.PacketType;

public record PacketSnapshot(
        PacketType type,
        double baseSpeed,
        double speed,
        double noise,
        double progress,
        int wireIndex
) {}

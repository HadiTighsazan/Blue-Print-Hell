package com.blueprinthell.engine;

import java.util.List;
import java.util.Map;

public record NetworkSnapshot(
        List<PacketSnapshot> packets,
        Map<Integer, List<PacketSnapshot>> buffers,
        int coins,
        int packetLoss
) {}

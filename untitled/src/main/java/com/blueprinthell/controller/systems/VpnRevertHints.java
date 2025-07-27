package com.blueprinthell.controller.systems;

import com.blueprinthell.model.PacketModel;

import java.util.Map;
import java.util.WeakHashMap;


public final class VpnRevertHints {
    private static final Map<PacketModel, PacketModel> REVERT = new WeakHashMap<>();

    private VpnRevertHints() {}

    public static void mark(PacketModel protectedPkt, PacketModel original) {
        if (protectedPkt == null || original == null) return;
        REVERT.put(protectedPkt, original);
    }


    public static PacketModel consume(PacketModel maybeProtected) {
        return REVERT.remove(maybeProtected);
    }


    public static void clear() {
        REVERT.clear();
    }
}

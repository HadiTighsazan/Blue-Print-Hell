package com.blueprinthell.controller.systems;

import com.blueprinthell.model.PacketModel;
import java.util.Map;
import java.util.WeakHashMap;

public final class VpnRevertHints {

    private static final VpnRevertHints GLOBAL = new VpnRevertHints();

    public static void markGlobal(PacketModel protectedPkt, PacketModel original) {
        GLOBAL.mark(protectedPkt, original);
    }

    public static PacketModel consumeGlobal(PacketModel maybeProtected) {
        return GLOBAL.consume(maybeProtected);
    }

    public static void clearAllGlobal() {
        GLOBAL.clear();
    }

    private static final Map<PacketModel, PacketModel> map = new WeakHashMap<>();

    public void mark(PacketModel protectedPkt, PacketModel original) {
        if (protectedPkt != null && original != null) {
            map.put(protectedPkt, original);
        }
    }

    public static PacketModel consume(PacketModel maybeProtected) {
        if (maybeProtected == null) return null;
        return map.remove(maybeProtected);
    }

    public static void clear() {
        map.clear();
    }
}

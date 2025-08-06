package com.blueprinthell.controller.systems;

import com.blueprinthell.model.PacketModel;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;


public final class TeleportTracking {
    private static final Set<PacketModel> TELEPORTED_PACKETS =
            Collections.newSetFromMap(new WeakHashMap<>());

    private TeleportTracking() {}


    public static void markTeleported(PacketModel packet) {
        if (packet != null) {
            TELEPORTED_PACKETS.add(packet);
        }
    }


    public static boolean isTeleported(PacketModel packet) {
        return packet != null && TELEPORTED_PACKETS.contains(packet);
    }


    public static void clearTeleported(PacketModel packet) {
        if (packet != null) {
            TELEPORTED_PACKETS.remove(packet);
        }
    }


    public static void clearAll() {
        TELEPORTED_PACKETS.clear();
    }


}
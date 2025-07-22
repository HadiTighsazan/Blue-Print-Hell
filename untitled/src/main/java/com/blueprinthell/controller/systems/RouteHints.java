package com.blueprinthell.controller.systems;

import com.blueprinthell.model.PacketModel;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Lightweight global hint store for routing decisions without modifying PacketModel.
 * MaliciousBehavior can mark a packet to be routed through an incompatible port.
 * PacketRouterController reads this flag and then clears it after use.
 */
public final class RouteHints {
    private static final Map<PacketModel, Boolean> FORCE_INCOMPATIBLE = new WeakHashMap<>();

    private RouteHints() {}

    public static void setForceIncompatible(PacketModel p, boolean v) {
        if (v) FORCE_INCOMPATIBLE.put(p, Boolean.TRUE);
        else   FORCE_INCOMPATIBLE.remove(p);
    }

    public static boolean consumeForceIncompatible(PacketModel p) {
        // read-once semantics: remove after read so it doesn't affect future hops
        return FORCE_INCOMPATIBLE.remove(p) != null;
    }
}

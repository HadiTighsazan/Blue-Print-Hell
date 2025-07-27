package com.blueprinthell.controller.systems;

import com.blueprinthell.model.PacketModel;

import java.util.Map;
import java.util.WeakHashMap;


public final class RouteHints {
    private static final Map<PacketModel, Boolean> FORCE_INCOMPATIBLE = new WeakHashMap<>();

    private RouteHints() {}

    public static void setForceIncompatible(PacketModel p, boolean v) {
        if (v) FORCE_INCOMPATIBLE.put(p, Boolean.TRUE);
        else   FORCE_INCOMPATIBLE.remove(p);
    }

    public static boolean consumeForceIncompatible(PacketModel p) {
        return FORCE_INCOMPATIBLE.remove(p) != null;
    }


    public static void clear() {
        FORCE_INCOMPATIBLE.clear();
    }
}

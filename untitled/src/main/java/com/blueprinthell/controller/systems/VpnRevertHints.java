package com.blueprinthell.controller.systems;

import com.blueprinthell.model.PacketModel;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Stores one-shot mappings for reverting VPN-protected packets back to their originals
 * without mutating core packet classes. When a VPN is disabled, it can mark each protected packet
 * here. The first time that packet arrives at a system (PacketDispatcher), the mapping is consumed
 * and the original packet is used instead.
 */
public final class VpnRevertHints {
    private static final Map<PacketModel, PacketModel> REVERT = new WeakHashMap<>();

    private VpnRevertHints() {}

    /** Mark a protected packet to be reverted to original at the next dispatch. */
    public static void mark(PacketModel protectedPkt, PacketModel original) {
        if (protectedPkt == null || original == null) return;
        REVERT.put(protectedPkt, original);
    }

    /**
     * If a mapping exists for this packet, return and remove it. Otherwise returns null.
     */
    public static PacketModel consume(PacketModel maybeProtected) {
        return REVERT.remove(maybeProtected);
    }

    /**
     * Clears all VPN revert mappings (used on level reset).
     */
    public static void clear() {
        REVERT.clear();
    }
}

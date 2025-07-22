package com.blueprinthell.controller.systems;

import com.blueprinthell.model.PacketModel;

public interface SystemBehavior {

    /**
     * Called once per simulation tick.
     * @param dt delta time in seconds
     */
    void update(double dt);

    /**
     * Called right after a packet is delivered to this system's input buffer.
     * Default: do nothing.
     */
    default void onPacketEnqueued(PacketModel packet) {
        // no-op
    }

    /**
     * Some systems can be temporarily disabled (cooldown). This callback lets
     * them know their enabled state flipped.
     */
    default void onEnabledChanged(boolean enabled) {
        // no-op
    }
}


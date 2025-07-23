package com.blueprinthell.controller.systems;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PortModel;

public interface SystemBehavior {

    /**
     * Called once per simulation tick.
     * @param dt delta time in seconds
     */
    void update(double dt);

    /**
     * Called right after a packet is delivered to this system's input buffer.
     * <p>Legacy signature without port info. Prefer using the 2-arg overload that also provides the entered port.</p>
     * Default: do nothing.
     */
    default void onPacketEnqueued(PacketModel packet) {
        // legacy no-op
    }

    /**
     * New overload that also provides the <b>port that the packet entered from</b>.
     * Default implementation delegates to the legacy 1-arg version so old behaviours keep working.
     */
    default void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        onPacketEnqueued(packet);
    }

    /**
     * Some systems can be temporarily disabled (cooldown). This callback lets
     * them know their enabled state flipped.
     */
    default void onEnabledChanged(boolean enabled) {
        // no-op
    }
}


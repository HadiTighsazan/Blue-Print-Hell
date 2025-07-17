package com.blueprinthell.motion;

import com.blueprinthell.model.PacketModel;

/**
 * Strategy interface governing how a {@link PacketModel} advances along its current wire on each
 * simulation tick. Implementations are fully stateless; any per‑packet state must live inside the
 * {@code PacketModel} itself.
 */
public interface MotionStrategy {

    /**
     * Mutates the supplied packet's progress/speed/... according to the concrete behaviour.
     *
     * @param packet the packet being updated
     * @param dt     simulation delta‑time in seconds
     */
    void update(PacketModel packet, double dt);
}

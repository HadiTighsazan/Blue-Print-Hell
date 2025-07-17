package com.blueprinthell.motion;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.WireModel;

/**
 * <h2>ConstantSpeedStrategy</h2>
 * Moves a packet along its current {@link WireModel} at a constant
 * pixel‑per‑second velocity, ignoring noise, acceleration flags or curvature.
 * <p>Two usage modes:</p>
 * <ul>
 *   <li><b>Implicit speed</b> – call the zero‑arg constructor. The strategy will read
 *       {@link PacketModel#getBaseSpeed()} on every tick so any dynamic changes to the
 *       packet's base‑speed are respected.</li>
 *   <li><b>Fixed speed</b> – supply a positive&nbsp;value in the constructor; the same
 *       value is used for the lifetime of the strategy regardless of packet fields.</li>
 * </ul>
 */
public class ConstantSpeedStrategy implements MotionStrategy {

    /** Value &lt; 0 signals «use packet.getBaseSpeed()». */
    private final double fixedSpeed;

    /** Zero‑arg constructor → defer speed decision to each packet's baseSpeed. */
    public ConstantSpeedStrategy() { this.fixedSpeed = -1; }

    /**
     * @param pxPerSec a strictly positive constant speed in pixel / second.
     */
    public ConstantSpeedStrategy(double pxPerSec) {
        if (pxPerSec <= 0)
            throw new IllegalArgumentException("Speed must be > 0");
        this.fixedSpeed = pxPerSec;
    }

    @Override
    public void update(PacketModel packet, double dt) {
        WireModel wire = packet.getCurrentWire();
        if (wire == null) return;               // no movement if not attached
        double speed = (fixedSpeed > 0) ? fixedSpeed : packet.getBaseSpeed();
        double length = wire.getLength();
        if (length <= 0) return;                // degenerate wire (avoid div‑by‑zero)

        double deltaP = (speed * dt) / length;  // normalised advance
        double next   = packet.getProgress() + deltaP;
        if (next > 1.0) next = 1.0;
        packet.setProgress(next);
    }
}

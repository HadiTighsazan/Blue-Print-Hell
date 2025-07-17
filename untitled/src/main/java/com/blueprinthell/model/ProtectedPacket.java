package com.blueprinthell.model;

import com.blueprinthell.config.Config;

/**
 * <h2>Protected Packet — decorator with a noise‑shield</h2>
 * <p>Wraps the behaviour of {@link PacketModel} but adds a finite shield that
 * absorbs a configurable amount of noise.  As long as the shield still has
 * capacity the packet is immune to removal by the {@code CollisionController}
 * (because its <em>effective</em> noise never reaches the {@code MAX_NOISE}
 * threshold).</p>
 *
 * <p>The class extends {@code PacketModel} so that it can be dropped into the
 * existing code‑base wherever a {@code PacketModel} is expected.  Two factory
 * helpers are provided:</p>
 * <ul>
 *   <li>{@link #wrap(PacketModel,double)} – clones an existing packet and adds
 *       protection (used by <em>VPNSystem</em> / store effects).</li>
 *   <li>Public constructors that create a fresh protected packet from scratch
 *       (used by future specialised factories).</li>
 * </ul>
 */
public class ProtectedPacket extends PacketModel {

    /** Remaining shield capacity; measured in the same units as <code>noise</code>. */
    private double shield;

    /* ====================================================================== */
    /*                        Factory / Constructors                          */
    /* ====================================================================== */

    /**
     * Clones the supplied packet and returns a <i>new</i> protected instance.
     * The original packet is left untouched so callers must remove it from its
     * wire/buffer manually.
     */
    public static ProtectedPacket wrap(PacketModel original, double shieldCapacity) {
        ProtectedPacket pp = new ProtectedPacket(original.getType(),
                original.getBaseSpeed(),
                shieldCapacity);
        // Copy over dynamic runtime state
        pp.setProgress(original.getProgress());
        pp.setSpeed(original.getSpeed());
        pp.setAcceleration(original.getAcceleration());
        pp.increaseNoise(original.getNoise());
        if (original.getCurrentWire() != null)
            pp.attachToWire(original.getCurrentWire(), original.getProgress());
        return pp;
    }

    /** Creates a brand‑new protected packet. */
    public ProtectedPacket(PacketType type, double baseSpeed, double shieldCapacity) {
        super(type, baseSpeed);
        this.shield = Math.max(0.0, shieldCapacity);
    }

    /* ====================================================================== */
    /*                       Overridden mutator logic                         */
    /* ====================================================================== */

    /**
     * Adds noise to the packet, but first deducts from the remaining shield.
     * Only any <em>excess</em> noise above the shield is delegated to the
     * superclass, ensuring that <code>noise</code> rises visibly only after
     * the shield is depleted.
     */
    @Override
    public void increaseNoise(double value) {
        if (value <= 0) return;
        if (shield > 0) {
            double absorbed = Math.min(value, shield);
            shield -= absorbed;
            double remaining = value - absorbed;
            if (remaining > 0) {
                super.increaseNoise(remaining);
            }
        } else {
            super.increaseNoise(value);
        }
    }

    /** Resetting noise also fully recharges the shield. */
    @Override
    public void resetNoise() {
        super.resetNoise();
        shield = Config.DEFAULT_SHIELD_CAPACITY; // requires new constant in Config
    }

    /* ====================================================================== */
    /*                          Additional getters                            */
    /* ====================================================================== */

    /** Remaining shield capacity (≥ 0). */
    public double getShield() { return shield; }

    /** @return <code>true</code> when the shield has been exhausted. */
    public boolean isShieldDepleted() { return shield <= 0.0; }


}

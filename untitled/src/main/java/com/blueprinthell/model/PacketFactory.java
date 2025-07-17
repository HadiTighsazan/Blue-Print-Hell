package com.blueprinthell.model;

import com.blueprinthell.config.Config;
import com.blueprinthell.motion.ConstantSpeedStrategy;
import com.blueprinthell.motion.MotionStrategy;
import java.util.Objects;

/**
 * <h2>PacketFactory – single entry‑point for building packets</h2>
 * <p>Hides the growing decision logic for choosing the concrete
 * {@link MotionStrategy} **and** possible decorators (e.g. <i>ProtectedPacket</i>)
 * from the rest of the code‑base.  New rules can be added in a single place
 * without touching callers.</p>
 *
 * <pre>
 * PacketModel pkt = PacketFactory.create(type, srcPort, dstPort);
 * wire.attachPacket(pkt, 0.0);
 * </pre>
 */
public final class PacketFactory {

    private PacketFactory() { /* static‑only */ }

    /* ------------------------------------------------------------------ */
    /**
     * Creates a new {@link PacketModel} (or decorator) of the given {@code type} and assigns
     * the most suitable {@link MotionStrategy} based on source/destination compatibility.
     *
     * @param type   logical packet type to spawn
     * @param src    source port (must be output)
     * @param dst    destination port (must be input)
     */
    public static PacketModel create(PacketType type,
                                     PortModel src,
                                     PortModel dst) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(src,  "src port");
        Objects.requireNonNull(dst,  "dst port");

        /* -------- base packet instance -------- */
        PacketModel packet = new PacketModel(type, Config.DEFAULT_PACKET_SPEED);

        /* -------- strategy selection v1 -------- */
        MotionStrategy strategy;
        boolean compatible = dst.getShape() == type.toPortShape();
        // Phase‑2 rule‑set (to be expanded later):
        //  – compatible  → constant speed
        //  – incompatible→ constant speed but with initial acceleration penalty handled in WirePhysics
        strategy = new ConstantSpeedStrategy();

        packet.setMotionStrategy(strategy);
        return packet;
    }
}

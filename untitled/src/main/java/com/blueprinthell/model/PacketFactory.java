package com.blueprinthell.model;

import com.blueprinthell.config.Config;
import com.blueprinthell.motion.ConstantSpeedStrategy;
import com.blueprinthell.motion.MotionStrategy;
import java.util.Objects;


public final class PacketFactory {

    private PacketFactory() { /* static‑only */ }


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

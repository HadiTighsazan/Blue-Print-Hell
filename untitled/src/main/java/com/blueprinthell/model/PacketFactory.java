package com.blueprinthell.model;

import com.blueprinthell.config.Config;
import com.blueprinthell.motion.ConstantSpeedStrategy;
import com.blueprinthell.motion.MotionStrategy;
import java.util.Objects;


public final class PacketFactory {

    private PacketFactory() {  }


    public static PacketModel create(PacketType type,
                                     PortModel src,
                                     PortModel dst) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(src,  "src port");
        Objects.requireNonNull(dst,  "dst port");

        PacketModel packet = new PacketModel(type, Config.DEFAULT_PACKET_SPEED);

        MotionStrategy strategy;
        boolean compatible = dst.getShape() == type.toPortShape();

        strategy = new ConstantSpeedStrategy();

        packet.setMotionStrategy(strategy);
        return packet;
    }
}

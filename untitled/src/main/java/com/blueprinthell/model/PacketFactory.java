package com.blueprinthell.model;

import com.blueprinthell.config.Config;
import com.blueprinthell.motion.ConstantSpeedStrategy;
import com.blueprinthell.motion.MotionStrategy;
import com.blueprinthell.motion.MotionStrategyFactory;

import java.util.Objects;


public final class PacketFactory {

    private PacketFactory() {  }


    public static PacketModel create(PacketType type,
                                     PortModel src,
                                     PortModel dst) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(src,  "src port");
        Objects.requireNonNull(dst,  "dst port");

        PacketModel pm = new PacketModel(type, Config.DEFAULT_PACKET_SPEED);

        boolean comp = src != null && src.isCompatible(pm);

        pm.setStartSpeedMul(1.0);

        pm.setMotionStrategy(MotionStrategyFactory.create(pm, comp));

        return pm;
    }


}

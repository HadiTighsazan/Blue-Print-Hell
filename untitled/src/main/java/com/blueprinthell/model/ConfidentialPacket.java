package com.blueprinthell.model;

import java.io.Serializable;
import java.util.Objects;


public class ConfidentialPacket extends PacketModel implements Serializable {
    private static final long serialVersionUID = 3L;


    public ConfidentialPacket(PacketType type, double baseSpeed) {
        super(type, baseSpeed);
    }


    public static ConfidentialPacket wrap(PacketModel original) {
        Objects.requireNonNull(original, "original packet");
        ConfidentialPacket cp = new ConfidentialPacket(original.getType(), original.getBaseSpeed());
        copyRuntimeState(original, cp);
        return cp;
    }


    public boolean isConfidential() {
        return true;
    }


    private static void copyRuntimeState(PacketModel src, PacketModel dst) {
        if (src.getCurrentWire() != null) {
            dst.attachToWire(src.getCurrentWire(), src.getProgress());
        } else {
            dst.setProgress(src.getProgress());
        }
        dst.setSpeed(src.getSpeed());
        dst.setAcceleration(src.getAcceleration());
        if (src.getNoise() > 0) {
            dst.increaseNoise(src.getNoise());
        }
    }
}

package com.blueprinthell.motion;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.WireModel;


public class ConstantSpeedStrategy implements MotionStrategy {

    private final double fixedSpeed;

    public ConstantSpeedStrategy() { this.fixedSpeed = -1; }


    public ConstantSpeedStrategy(double pxPerSec) {
        if (pxPerSec <= 0)
            throw new IllegalArgumentException("Speed must be > 0");
        this.fixedSpeed = pxPerSec;
    }

    @Override
    public void update(PacketModel packet, double dt) {
        WireModel wire = packet.getCurrentWire();
        if (wire == null) return;
        double speed = (fixedSpeed > 0) ? fixedSpeed : packet.getBaseSpeed();
        double length = wire.getLength();
        if (length <= 0) return;

        double deltaP = (speed * dt) / length;
        double next   = packet.getProgress() + deltaP;
        if (next > 1.0) next = 1.0;
        packet.setProgress(next);
    }
}

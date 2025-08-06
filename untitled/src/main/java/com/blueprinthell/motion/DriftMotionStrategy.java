package com.blueprinthell.motion;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.WireModel;

public class DriftMotionStrategy implements MotionStrategy {
    private final double baseSpeed;
    private final double driftStepPx;
    private final double driftOffsetPx;
    private double distanceTraveled = 0;
    private boolean driftDirection = false;

    public DriftMotionStrategy(double baseSpeed, double driftStepPx, double driftOffsetPx) {
        this.baseSpeed = baseSpeed;
        this.driftStepPx = driftStepPx;
        this.driftOffsetPx = driftOffsetPx;
    }

    @Override
    public void update(PacketModel packet, double dt) {
        WireModel wire = packet.getCurrentWire();
        if (wire == null) return;

        double length = wire.getLength();
        if (length <= 0) return;

        // حرکت با سرعت ثابت
        double distance = baseSpeed * dt;
        double deltaProgress = distance / length;
        packet.setProgress(Math.min(1.0, packet.getProgress() + deltaProgress));

        // اعمال drift
        distanceTraveled += distance;
        if (distanceTraveled >= driftStepPx) {
            distanceTraveled = 0;
            driftDirection = !driftDirection;

            // انحراف مرکز پکت
            int offsetX = (int)(driftDirection ? driftOffsetPx : -driftOffsetPx);
            int offsetY = (int)(driftDirection ? driftOffsetPx * 0.3 : -driftOffsetPx * 0.3);
            packet.setX(packet.getX() + offsetX);
            packet.setY(packet.getY() + offsetY);
        }
    }
}
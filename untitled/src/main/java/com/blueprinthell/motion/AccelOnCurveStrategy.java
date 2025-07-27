package com.blueprinthell.motion;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.WireModel;
import java.awt.Point;


public class AccelOnCurveStrategy implements MotionStrategy {
    private final double curvatureAcceleration;
    private final double maxSpeedMultiplier;

    public AccelOnCurveStrategy(double curvatureAcceleration, double maxSpeedMultiplier) {
        this.curvatureAcceleration = curvatureAcceleration;
        this.maxSpeedMultiplier = maxSpeedMultiplier;
    }

    @Override
    public void update(PacketModel packet, double dt) {
        WireModel wire = packet.getCurrentWire();
        if (wire == null) return;
        double curvature = estimateCurvature(wire, packet.getProgress());
        double accel = curvatureAcceleration * curvature;
        double newSpeed = packet.getSpeed() + accel * dt;
        double maxSpeed = packet.getBaseSpeed() * maxSpeedMultiplier;
        newSpeed = Math.min(Math.max(newSpeed, packet.getBaseSpeed()), maxSpeed);
        packet.setSpeed(newSpeed);
        double distance = packet.getSpeed() * dt;
        double deltaProg = distance / wire.getLength();
        packet.setProgress(packet.getProgress() + deltaProg);
    }


    private double estimateCurvature(WireModel wire, double progress) {
        double t0 = Math.max(0.0, progress - 0.01);
        double t2 = Math.min(1.0, progress + 0.01);
        Point p0 = wire.pointAt(t0);
        Point p1 = wire.pointAt(progress);
        Point p2 = wire.pointAt(t2);
        double dx1 = p1.x - p0.x, dy1 = p1.y - p0.y;
        double dx2 = p2.x - p1.x, dy2 = p2.y - p1.y;
        double dot = dx1*dx2 + dy1*dy2;
        double mag1 = Math.hypot(dx1, dy1);
        double mag2 = Math.hypot(dx2, dy2);
        if (mag1 == 0 || mag2 == 0) return 0.0;
        double cos = dot / (mag1 * mag2);
        return 1.0 - cos;
    }
}

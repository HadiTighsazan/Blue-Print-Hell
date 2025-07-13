package com.blueprinthell.model;

import com.blueprinthell.config.Config;
import java.awt.Point;
import java.io.Serializable;

/**
 * Domain model for Packet, containing movement logic without UI dependencies.
 * Advances based on speed, acceleration, and noise-driven speed scaling.
 */
public class PacketModel extends GameObjectModel implements Serializable {
    private static final long serialVersionUID = 2L;

    private final PacketType type;
    private final double baseSpeed;
    private double progress;
    private double speed;
    private double noise;
    private double acceleration;

    private WireModel currentWire;

    public PacketModel(PacketType type, double speed) {
        super(0, 0,
                type.sizeUnits * Config.PACKET_SIZE_MULTIPLIER,
                type.sizeUnits * Config.PACKET_SIZE_MULTIPLIER);
        this.type = type;
        this.baseSpeed = speed;
        this.speed = speed;
        this.noise = 0.0;
        this.acceleration = 0.0;
    }

    public PacketType getType() {
        return type;
    }

    public double getBaseSpeed() {
        return baseSpeed;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
        updatePosition();
    }

    public double getNoise() {
        return noise;
    }

    public void increaseNoise(double v) {
        this.noise += v;
    }

    public void resetNoise() {
        this.noise = 0.0;
    }

    public double getAcceleration() {
        return acceleration;
    }

    public void setAcceleration(double acceleration) {
        this.acceleration = acceleration;
    }

    /**
     * Advances the packet along its current wire by dt seconds,
     * scaling speed linearly with noise and smoothing the change.
     */
    public void advance(double dt) {
        // Compute noise ratio [0..1]
        double noiseRatio = Math.min(1.0, noise / Config.MAX_NOISE_CAPACITY);
        // Determine target speed based on noise
        double targetSpeed = baseSpeed * (1.0 + noiseRatio);
        // Smoothly adjust current speed toward target
        speed += (targetSpeed - speed) * Config.NOISE_SPEED_SMOOTHING;
        // Apply additional acceleration
        speed += acceleration * dt;

        if (currentWire != null) {
            double distance = speed * dt;
            double deltaProgress = distance / currentWire.getLength();
            progress += deltaProgress;
            updatePosition();
        }
    }

    /**
     * Attaches the packet to a wire at the given initial progress fraction.
     */
    public void attachToWire(WireModel wire, double initProgress) {
        this.currentWire = wire;
        this.progress = initProgress;
        updatePosition();
    }

    private void updatePosition() {
        if (currentWire == null) return;
        Point p = currentWire.pointAt(progress);
        // center packet on wire point
        setX(p.x - getWidth() / 2);
        setY(p.y - getHeight() / 2);
    }

    public WireModel getCurrentWire() {
        return currentWire;
    }
}

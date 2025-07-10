package com.blueprinthell.model;

import com.blueprinthell.config.Config;
import java.awt.Point;
import java.io.Serializable;

/**
 * Domain model for Packet, containing movement logic without UI dependencies.
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

    public void advance(double dt) {
        speed += acceleration * dt;
        if (currentWire != null) {
            double distance = speed * dt;
            double deltaProgress = distance / currentWire.getLength();
            progress += deltaProgress;
            updatePosition();
        }
    }

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

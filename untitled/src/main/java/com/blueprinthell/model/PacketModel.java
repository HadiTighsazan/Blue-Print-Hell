package com.blueprinthell.model;

import com.blueprinthell.config.Config;
import com.blueprinthell.motion.ConstantSpeedStrategy;
import com.blueprinthell.motion.MotionStrategy;

import java.awt.*;
import java.io.Serializable;


public class PacketModel extends GameObjectModel implements Serializable {
    private static final long serialVersionUID = 2L;

    private final PacketType        type;
    private final double            baseSpeed;

    private double progress;
    private double speed;
    private double noise;

    private WireModel       currentWire;
    private MotionStrategy  motion;

    private double acceleration = 0.0;

    public PacketModel(PacketType type, double baseSpeed) {
        super(0, 0,
                type.sizeUnits * Config.PACKET_SIZE_MULTIPLIER,
                type.sizeUnits * Config.PACKET_SIZE_MULTIPLIER);
        this.type      = type;
        this.baseSpeed = baseSpeed;
        this.speed     = baseSpeed;
        this.motion    = new ConstantSpeedStrategy(baseSpeed);
    }

    public PacketType getType()            { return type; }
    public double     getBaseSpeed()       { return baseSpeed; }
    public double     getSpeed()           { return speed; }
    public void       setSpeed(double s)   { this.speed = s; }

    public double getProgress()            { return progress; }
    public void   setProgress(double p)    { this.progress = p; updatePosition(); }

    public double getNoise()               { return noise; }
    public void   increaseNoise(double v)  { noise += v; recomputeSpeedFromNoise(); }
    public void   resetNoise()             { noise = 0.0; recomputeSpeedFromNoise(); }

    public void setMotionStrategy(MotionStrategy m) { this.motion = m; }
    public MotionStrategy getMotionStrategy()       { return motion; }

    public void advance(double dt) {
        if (motion != null) motion.update(this, dt);
    }

    private void recomputeSpeedFromNoise() {
        double ratio = Math.min(1.0, noise / Config.MAX_NOISE_CAPACITY);
        speed = Math.min(Config.MAX_SPEED, baseSpeed * (1 + ratio));
    }

    public void attachToWire(WireModel wire, double initProgress) {
        this.currentWire = wire;
        this.progress    = initProgress;
        updatePosition();
    }

    public WireModel getCurrentWire() { return currentWire; }

    private void updatePosition() {
        if (currentWire == null) return;
        Point p = currentWire.pointAt(progress);
        setX(p.x - getWidth() / 2);
        setY(p.y - getHeight() / 2);
    }

    public double getAcceleration() {
        return acceleration;
    }
    public void setAcceleration(double acceleration) {
        this.acceleration = acceleration;
    }

    public void setNoise(double v) {
        this.noise=v;
    }
}

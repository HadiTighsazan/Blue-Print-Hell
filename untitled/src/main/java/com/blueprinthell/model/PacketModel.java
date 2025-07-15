package com.blueprinthell.model;

import com.blueprinthell.config.Config;
import java.awt.Point;
import java.io.Serializable;

/**
 * Domain model for Packet, containing movement logic without UI dependencies.
 * اکنون شامل شتاب افزایشی/کاهشی و اثر نویز بر سرعت است.
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

    /* ---------------- Accessors ---------------- */
    public PacketType getType()       { return type; }
    public double getBaseSpeed()      { return baseSpeed; }
    public double getSpeed()          { return speed; }
    public void   setSpeed(double s)  { this.speed = s; }
    public double getProgress()       { return progress; }
    public void   setProgress(double p){ this.progress = p; updatePosition(); }
    public double getNoise()          { return noise; }
    public double getAcceleration()   { return acceleration; }
    public void   setAcceleration(double a){ this.acceleration = a; }

    public void increaseNoise(double v) {
        this.noise += v;
        recomputeSpeedFromNoise();
    }
    public void resetNoise() {
        this.noise = 0.0;
        recomputeSpeedFromNoise();
    }

    /* ---------------- Simulation step ---------------- */

    public void advance(double dt) {
        // 1) dynamic acceleration toward noise‑scaled target speed
        double noiseRatio   = Math.min(1.0, noise / Config.MAX_NOISE_CAPACITY);
        double targetSpeed  = baseSpeed * (1.0 + noiseRatio);
        speed += (targetSpeed - speed) * Config.NOISE_SPEED_SMOOTHING;

        // 2) explicit acceleration from WireModel (acc field)
        speed += acceleration * dt;

        // 3) deceleration in last 20% of the wire for smoother stop
        if (currentWire != null && progress > 0.8) {
            speed += Config.ACC_DECEL * dt;
        }

        // clamp speed
        if (speed < 10) speed = 10; // حداقل برای جلوگیری از سکون کامل
        if (speed > Config.MAX_SPEED) speed = Config.MAX_SPEED;

        // 4) move along the wire
        if (currentWire != null) {
            double distance      = speed * dt;
            double deltaProgress = distance / currentWire.getLength();
            progress += deltaProgress;
            updatePosition();
        }
    }

    /* ---------------- Helpers ---------------- */
    /** Re‑evaluates speed solely from noise (linear scaling) without touching acceleration. */
    public void recomputeSpeedFromNoise() {
        double noiseRatio  = Math.min(1.0, noise / Config.MAX_NOISE_CAPACITY);
        double newSpeed    = baseSpeed * (1.0 + noiseRatio);
        if (newSpeed > Config.MAX_SPEED) newSpeed = Config.MAX_SPEED;
        this.speed = newSpeed;
    }

    public void attachToWire(WireModel wire, double initProgress) {
        this.currentWire = wire;
        this.progress    = initProgress;
        updatePosition();
    }

    private void updatePosition() {
        if (currentWire == null) return;
        Point p = currentWire.pointAt(progress);
        setX(p.x - getWidth() / 2);
        setY(p.y - getHeight() / 2);
    }

    public WireModel getCurrentWire() { return currentWire; }
}

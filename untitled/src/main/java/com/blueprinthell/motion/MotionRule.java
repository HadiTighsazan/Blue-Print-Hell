package com.blueprinthell.motion;


/**
 * Small immutable value object describing how a packet should move on a single port-compatibility branch.
 */
public final class MotionRule {
    public final MotionMode mode;          // CONST / ACCEL / DECEL / ...
    public final double     speedStart;    // starting speed (px/s) or constant speed for CONST
    public final double     accel;         // px/s^2 (can be negative). Ignored for CONST/KEEP_DISTANCE/DRIFT/etc.
    public final double     minMul;        // min multiplier for speed caps (relative to baseSpeed)
    public final double     maxMul;        // max multiplier for speed caps (relative to baseSpeed)

    public MotionRule(MotionMode mode,
                      double speedStart,
                      double accel,
                      double minMul,
                      double maxMul) {
        this.mode       = mode;
        this.speedStart = speedStart;
        this.accel      = accel;
        this.minMul     = minMul;
        this.maxMul     = maxMul;
    }

    public static MotionRule constSpeed(double speed) {
        return new MotionRule(MotionMode.CONST, speed, 0, 1.0, 1.0);
    }

    public static MotionRule accel(double startSpeed, double accel, double minMul, double maxMul) {
        return new MotionRule(MotionMode.ACCEL, startSpeed, accel, minMul, maxMul);
    }

    public static MotionRule decel(double startSpeed, double accelNeg, double minMul, double maxMul) {
        return new MotionRule(MotionMode.DECEL, startSpeed, accelNeg, minMul, maxMul);
    }

    public static MotionRule curveAccel(double baseSpeed, double curveFactor, double maxMul) {
        return new MotionRule(MotionMode.CURVE_ACCEL, baseSpeed, curveFactor, 1.0, maxMul);
    }

    public static MotionRule keepDistance(double baseSpeed) {
        return new MotionRule(MotionMode.KEEP_DISTANCE, baseSpeed, 0, 1.0, 1.0);
    }

    public static MotionRule drift(double baseSpeed) {
        return new MotionRule(MotionMode.DRIFT, baseSpeed, 0, 1.0, 1.0);
    }
}
package com.blueprinthell.motion;



public final class MotionRule {
    public final MotionMode mode;
    public final double     speedStart;
    public final double     accel;
    public final double     minMul;
    public final double     maxMul;

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
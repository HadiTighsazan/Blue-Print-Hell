package com.blueprinthell.motion;


public enum MotionMode {
    CONST,
    ACCEL,
    DECEL,
    CURVE_ACCEL,
    KEEP_DISTANCE,
    DRIFT,
    RANDOM_OF_MESSENGER;

    public boolean usesLinearAccel() {
        return this == ACCEL || this == DECEL;
    }

    public boolean isCurveBased() {
        return this == CURVE_ACCEL;
    }

    public boolean needsNeighbourScan() {
        return this == KEEP_DISTANCE;
    }

    public boolean needsRandomChoice() {
        return this == RANDOM_OF_MESSENGER;
    }
}

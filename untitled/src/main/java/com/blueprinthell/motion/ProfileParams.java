package com.blueprinthell.motion;

public final class ProfileParams {

    public final MotionRule compatRule;
    public final MotionRule incompatRule;

    // Optional behaviour flags
    public final boolean bounceOnImpact;

    public final boolean keepDistance;     // (redundant if rule mode == KEEP_DISTANCE, kept for clarity)
    public final double  keepDistancePx;

    public final boolean curveAccel;       // redundant if rule mode == CURVE_ACCEL
    public final double  curveAccelFactor;
    public final double  curveMaxMul;

    public final boolean driftOffWire;     // redundant if rule mode == DRIFT
    public final double  driftStepDistancePx;
    public final double  driftOffsetPx;

    public final boolean randomMessengerProfile;

    public final boolean slowDownBeforeBusyBox;
    public final double  slowDownSpeed;

    private ProfileParams(Builder b) {
        this.compatRule              = b.compatRule;
        this.incompatRule            = b.incompatRule;
        this.bounceOnImpact          = b.bounceOnImpact;
        this.keepDistance            = b.keepDistance;
        this.keepDistancePx          = b.keepDistancePx;
        this.curveAccel              = b.curveAccel;
        this.curveAccelFactor        = b.curveAccelFactor;
        this.curveMaxMul             = b.curveMaxMul;
        this.driftOffWire            = b.driftOffWire;
        this.driftStepDistancePx     = b.driftStepDistancePx;
        this.driftOffsetPx           = b.driftOffsetPx;
        this.randomMessengerProfile  = b.randomMessengerProfile;
        this.slowDownBeforeBusyBox   = b.slowDownBeforeBusyBox;
        this.slowDownSpeed           = b.slowDownSpeed;
    }

    public static Builder builder(MotionRule compatRule, MotionRule incompatRule) {
        return new Builder(compatRule, incompatRule);
    }



    /* ------------------------------ Builder ------------------------------ */
    public static final class Builder {
        private final MotionRule compatRule;
        private final MotionRule incompatRule;

        private boolean bounceOnImpact;

        private boolean keepDistance;
        private double  keepDistancePx;

        private boolean curveAccel;
        private double  curveAccelFactor;
        private double  curveMaxMul;

        private boolean driftOffWire;
        private double  driftStepDistancePx;
        private double  driftOffsetPx;

        private boolean randomMessengerProfile;

        private boolean slowDownBeforeBusyBox;
        private double  slowDownSpeed;

        private Builder(MotionRule compatRule, MotionRule incompatRule) {
            this.compatRule   = compatRule;
            this.incompatRule = incompatRule;
        }

        public Builder bounceOnImpact() { this.bounceOnImpact = true; return this; }

        public Builder keepDistance(double px) {
            this.keepDistance = true;
            this.keepDistancePx = px;
            return this;
        }

        public Builder curveAccel(double factor, double maxMul) {
            this.curveAccel = true;
            this.curveAccelFactor = factor;
            this.curveMaxMul = maxMul;
            return this;
        }

        public Builder drift(double stepDistPx, double offsetPx) {
            this.driftOffWire = true;
            this.driftStepDistancePx = stepDistPx;
            this.driftOffsetPx = offsetPx;
            return this;
        }

        public Builder randomMessengerProfile() {
            this.randomMessengerProfile = true;
            return this;
        }

        public Builder slowDownBeforeBusyBox(double slowSpeed) {
            this.slowDownBeforeBusyBox = true;
            this.slowDownSpeed = slowSpeed;
            return this;
        }

        public ProfileParams build() {
            return new ProfileParams(this);
        }
    }
}

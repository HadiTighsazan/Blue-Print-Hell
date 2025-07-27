package com.blueprinthell.motion;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static com.blueprinthell.config.Config.*;


public enum KinematicsProfile {


    MSG1(
            ProfileParams.builder(
                    MotionRule.accel(MSG1_COMPAT_V0, MSG1_COMPAT_A, MSG1_COMPAT_MIN_MUL, MSG1_COMPAT_MAX_MUL),
                    MotionRule.decel(MSG1_INCOMPAT_V0, MSG1_INCOMPAT_A, MSG1_INCOMPAT_MIN_MUL, MSG1_INCOMPAT_MAX_MUL)
            ).bounceOnImpact().build()
    ),

    MSG2(
            ProfileParams.builder(
                    MotionRule.constSpeed(MSG2_COMPAT_SPEED),
                    MotionRule.constSpeed(MSG2_INCOMPAT_SPEED)
            ).build()
    ),

    MSG3(
            ProfileParams.builder(
                    MotionRule.constSpeed(MSG3_COMPAT_SPEED),
                    MotionRule.accel(MSG3_INCOMPAT_V0, MSG3_INCOMPAT_A, MSG3_INCOMPAT_MIN_MUL, MSG3_INCOMPAT_MAX_MUL)
            ).build()
    ),


    PROTECTED_SHADOW(
            ProfileParams.builder(
                    MotionRule.constSpeed(0),
                    MotionRule.constSpeed(0)
            ).randomMessengerProfile().build()
    ),


    CONFIDENTIAL(
            ProfileParams.builder(
                    MotionRule.constSpeed(CONF_SPEED),
                    MotionRule.constSpeed(CONF_SPEED)
            ).slowDownBeforeBusyBox(CONF_SLOW_SPEED).build()
    ),

    CONFIDENTIAL_VPN(
            ProfileParams.builder(
                    MotionRule.keepDistance(CONF_VPN_SPEED),
                    MotionRule.keepDistance(CONF_VPN_SPEED)
            ).keepDistance(CONF_VPN_KEEP_DIST_PX).build()
    ),


    LARGE_8(
            ProfileParams.builder(
                    MotionRule.curveAccel(L8_BASE_SPEED, L8_CURVE_ACCEL, L8_MAX_MUL),
                    MotionRule.curveAccel(L8_BASE_SPEED, L8_CURVE_ACCEL, L8_MAX_MUL)
            ).curveAccel(L8_CURVE_ACCEL, L8_MAX_MUL).build()
    ),

    LARGE_10(
            ProfileParams.builder(
                    MotionRule.drift(L10_BASE_SPEED),
                    MotionRule.drift(L10_BASE_SPEED)
            ).drift(L10_DRIFT_STEP_PX, L10_DRIFT_OFFSET_PX).build()
    );

    private final ProfileParams params;

    KinematicsProfile(ProfileParams params) {
        this.params = Objects.requireNonNull(params);
    }

    public ProfileParams getParams() { return params; }

    public boolean isMessenger() { return this == MSG1 || this == MSG2 || this == MSG3; }
    public boolean isLarge()     { return this == LARGE_8 || this == LARGE_10; }

    public static KinematicsProfile randomMessenger(Random rnd) {
        KinematicsProfile[] arr = {MSG1, MSG2, MSG3};
        return arr[rnd.nextInt(arr.length)];
    }
    public static KinematicsProfile randomMessenger() {
        return randomMessenger(ThreadLocalRandom.current());
    }
    public static EnumSet<KinematicsProfile> messengerSet() {
        return EnumSet.of(MSG1, MSG2, MSG3);
    }


}

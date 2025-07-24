package com.blueprinthell.motion;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static com.blueprinthell.config.Config.*;

/**
 * <h2>KinematicsProfile</h2>
 * هر پروفایل یک {@link ProfileParams} دارد؛ اعداد و فلگ‌ها در همان {@code ProfileParams} ست می‌شوند و
 * ساخت استراتژی نهایی در {@link MotionStrategyFactory} انجام می‌شود.
 */
public enum KinematicsProfile {


    /* ===================== Messenger packets ===================== */
    /** پیام‌رسان سایز 1 – سازگار: شتاب مثبت؛ ناسازگار: شتاب منفی. به‌علاوه Bounce-on-impact. */
    MSG1(
            ProfileParams.builder(
                    MotionRule.accel(MSG1_COMPAT_V0, MSG1_COMPAT_A, MSG1_COMPAT_MIN_MUL, MSG1_COMPAT_MAX_MUL),
                    MotionRule.decel(MSG1_INCOMPAT_V0, MSG1_INCOMPAT_A, MSG1_INCOMPAT_MIN_MUL, MSG1_INCOMPAT_MAX_MUL)
            ).bounceOnImpact().build()
    ),

    /** پیام‌رسان سایز 2 – سازگار: سرعت ثابت نصف ناسازگار؛ ناسازگار: سرعت ثابت. */
    MSG2(
            ProfileParams.builder(
                    MotionRule.constSpeed(MSG2_COMPAT_SPEED),
                    MotionRule.constSpeed(MSG2_INCOMPAT_SPEED)
            ).build()
    ),

    /** پیام‌رسان سایز 3 – سازگار: سرعت ثابت؛ ناسازگار: شتاب افزایشی. */
    MSG3(
            ProfileParams.builder(
                    MotionRule.constSpeed(MSG3_COMPAT_SPEED),
                    MotionRule.accel(MSG3_INCOMPAT_V0, MSG3_INCOMPAT_A, MSG3_INCOMPAT_MIN_MUL, MSG3_INCOMPAT_MAX_MUL)
            ).build()
    ),

    /* ===================== Protected packets ===================== */
    /** Protected – یکی از پروفایل‌های پیام‌رسان به‌صورت تصادفی انتخاب می‌شود. */
    PROTECTED_SHADOW(
            ProfileParams.builder(
                    MotionRule.constSpeed(0),
                    MotionRule.constSpeed(0)
            ).randomMessengerProfile().build()
    ),

    /* ===================== Confidential packets ===================== */
    /** محرمانه معمولی – سرعت ثابت؛ ولی قبل از باکس شلوغ کند می‌شود (توسط کنترلر جدا). */
    CONFIDENTIAL(
            ProfileParams.builder(
                    MotionRule.constSpeed(CONF_SPEED),
                    MotionRule.constSpeed(CONF_SPEED)
            ).slowDownBeforeBusyBox(CONF_SLOW_SPEED).build()
    ),

    /** محرمانه VPN – باید فاصلهٔ ثابتی با بقیه حفظ کند. */
    CONFIDENTIAL_VPN(
            ProfileParams.builder(
                    MotionRule.keepDistance(CONF_VPN_SPEED),
                    MotionRule.keepDistance(CONF_VPN_SPEED)
            ).keepDistance(CONF_VPN_KEEP_DIST_PX).build()
    ),

    /* ===================== Large packets ===================== */
    /** Large 8 – سرعت پایه ثابت؛ روی انحنا شتاب متناسب با curvature اضافه می‌شود. */
    LARGE_8(
            ProfileParams.builder(
                    MotionRule.curveAccel(L8_BASE_SPEED, L8_CURVE_ACCEL, L8_MAX_MUL),
                    MotionRule.curveAccel(L8_BASE_SPEED, L8_CURVE_ACCEL, L8_MAX_MUL)
            ).curveAccel(L8_CURVE_ACCEL, L8_MAX_MUL).build()
    ),

    /** Large 10 – سرعت ثابت و Drift دوره‌ای از سیم. */
    LARGE_10(
            ProfileParams.builder(
                    MotionRule.drift(L10_BASE_SPEED),
                    MotionRule.drift(L10_BASE_SPEED)
            ).drift(L10_DRIFT_STEP_PX, L10_DRIFT_OFFSET_PX).build()
    );

    /* --------------------------- fields --------------------------- */
    private final ProfileParams params;

    KinematicsProfile(ProfileParams params) {
        this.params = Objects.requireNonNull(params);
    }

    public ProfileParams getParams() { return params; }

    /* --------------------------- helpers --------------------------- */
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

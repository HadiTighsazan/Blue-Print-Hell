package com.blueprinthell.motion;

import java.util.EnumSet;
import java.util.Random;

/**
 * <h2>KinematicsProfile (refactored)</h2>
 * هر مقدار فقط یک {@link ProfileParams} نگه می‌دارد و دیگر خبری از ۲۰+ آرگومان نا‌خوانا نیست.
 * منطق اجرای واقعی در {@code MotionStrategyFactory} ترجمه خواهد شد.
 */
public enum KinematicsProfile {

    /* ======================= Messenger packets ======================= */
    /** پیام‌رسان سایز 1 (coins=1) – سازگار: شتاب ثابت مثبت؛ ناسازگار: شتاب نزولی. Bounce روی برخورد. */
    MSG1(
            ProfileParams.builder(
                    MotionRule.accel(60, 140, 1.0, 1.5),       // compat
                    MotionRule.decel(160, -120, 0.4, 1.0)       // incompat
            ).bounceOnImpact().build()
    ),

    /** پیام‌رسان سایز 2 (coins=2) – سازگار: سرعت ثابت نصف ناسازگار؛ ناسازگار: سرعت ثابت. */
    MSG2(
            ProfileParams.builder(
                    MotionRule.constSpeed(110),                 // compat
                    MotionRule.constSpeed(220)                  // incompat
            ).build()
    ),

    /** پیام‌رسان سایز 3 (coins=3) – سازگار: سرعت ثابت؛ ناسازگار: شتاب افزایشی. */
    MSG3(
            ProfileParams.builder(
                    MotionRule.constSpeed(180),                 // compat
                    MotionRule.accel(120, 90, 1.0, 1.6)         // incompat
            ).build()
    ),

    /* ======================= Protected packets ======================= */
    /** ProtectedPacket – یکی از پروفایل‌های پیام‌رسان را تصادفی انتخاب می‌کند. */
    PROTECTED_SHADOW(
            ProfileParams.builder(
                    MotionRule.constSpeed(0),                   // placeholder
                    MotionRule.constSpeed(0)
            ).randomMessengerProfile().build()
    ),

    /* ======================= Confidential packets ======================= */
    /** محرمانه معمولی (size=4, coins=3) – سرعت ثابت؛ نزدیک سیستمِ پر، کند می‌شود. */
    CONFIDENTIAL(
            ProfileParams.builder(
                    MotionRule.constSpeed(170),
                    MotionRule.constSpeed(170)
            ).slowDownBeforeBusyBox(60).build()
    ),

    /** محرمانه ساخته‌شده توسط VPN (size=6, coins=4) – حفظ فاصله ثابت با سایر پکت‌ها. */
    CONFIDENTIAL_VPN(
            ProfileParams.builder(
                    MotionRule.keepDistance(170),
                    MotionRule.keepDistance(170)
            ).keepDistance(60).build()
    ),

    /* ======================= Large packets (گام بعد) ======================= */
    /** Large size=8 – ثابت روی خط مستقیم، شتاب روی منحنی. */
    LARGE_8(
            ProfileParams.builder(
                    MotionRule.curveAccel(140, 100, 1.8),
                    MotionRule.curveAccel(140, 100, 1.8)
            ).curveAccel(100, 1.8).build()
    ),

    /** Large size=10 – سرعت ثابت + Drift دوره‌ای از سیم. */
    LARGE_10(
            ProfileParams.builder(
                    MotionRule.drift(160),
                    MotionRule.drift(160)
            ).drift(200, 6).build()
    );

    /* ------------------------------------------------------------------ */
    /*                               FIELDS                               */
    /* ------------------------------------------------------------------ */
    private final ProfileParams params;

    KinematicsProfile(ProfileParams params) {
        this.params = params;
    }

    public ProfileParams getParams() {
        return params;
    }

    /* --------------------- Helper methods --------------------- */

    /** آیا این پروفایل یکی از پیام‌رسان‌هاست؟ */
    public boolean isMessenger() {
        return this == MSG1 || this == MSG2 || this == MSG3;
    }

    /** آیا این پروفایل Large است؟ */
    public boolean isLarge() {
        return this == LARGE_8 || this == LARGE_10;
    }

    /** یک پروفایل تصادفی از بین سه پیام‌رسان برمی‌گرداند. */
    public static KinematicsProfile randomMessenger(Random rnd) {
        KinematicsProfile[] arr = {MSG1, MSG2, MSG3};
        return arr[rnd.nextInt(arr.length)];
    }

    /** مجموعهٔ پیام‌رسان‌ها برای دسترسی سریع. */
    public static EnumSet<KinematicsProfile> messengerSet() {
        return EnumSet.of(MSG1, MSG2, MSG3);
    }
}

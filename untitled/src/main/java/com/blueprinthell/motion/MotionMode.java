package com.blueprinthell.motion;

/**
 * <h2>MotionMode</h2>
 * نوع رفتار حرکتی که برای یک پکت روی یک شاخهٔ خاص (سازگار/ناسازگار) استفاده می‌شود.
 */
public enum MotionMode {
    /** سرعت ثابت؛ شتاب صفر؛ فقط پیشروی با سرعت مشخص. */
    CONST,
    /** شتاب مثبت خطی تا سقف مشخص. */
    ACCEL,
    /** شتاب منفی خطی تا کف مشخص. */
    DECEL,
    /** شتاب متناسب با خمیدگی مسیر سیم. */
    CURVE_ACCEL,
    /** تلاش برای حفظ فاصلهٔ ثابت با سایر پکت‌ها روی همان سیم. */
    KEEP_DISTANCE,
    /** هر چند وقت یکبار از مسیر سیم کمی منحرف می‌شود (Drift). */
    DRIFT,
    /** به‌صورت تصادفی یکی از پروفایل‌های پیام‌رسان را انتخاب می‌کند. */
    RANDOM_OF_MESSENGER;

    /** آیا این حالت به پارامتر شتاب احتیاج دارد؟ (ACCEL/DECEL) */
    public boolean usesLinearAccel() {
        return this == ACCEL || this == DECEL;
    }

    /** آیا این حالت بر پایهٔ خمیدگی مسیر است؟ */
    public boolean isCurveBased() {
        return this == CURVE_ACCEL;
    }

    /** آیا این حالت نیازمند بررسی فاصله با سایر پکت‌هاست؟ */
    public boolean needsNeighbourScan() {
        return this == KEEP_DISTANCE;
    }

    /** آیا این حالت نیازمند ران‌تایم تصادفی است؟ */
    public boolean needsRandomChoice() {
        return this == RANDOM_OF_MESSENGER;
    }
}

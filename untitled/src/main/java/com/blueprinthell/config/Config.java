package com.blueprinthell.config;

import com.blueprinthell.model.PortShape;

import java.awt.Color;

/**
 * Centralized configuration constants for BlueprintHell application.
 */
public final class Config {
    public static final int SYSTEM_WIDTH = 96;
    public static final int SYSTEM_HEIGHT = 96;
    public static final int MAX_OUTPUT_PORTS = 3;

    //**********************حذف کنش
    public static final PortShape DEFAULT_PORT_SHAPE = PortShape.SQUARE;
    public static final double MAX_TIME_ON_WIRE_SEC = 8.0;
    public static final double SYSTEM_DISABLE_DURATION =3.0 ;
    public static final double MAX_ALLOWED_SPEED = 180.0;


    private Config() { /* Prevent instantiation */ }

    /* ===================== Model constants ===================== */
    public static final int    PACKET_SIZE_MULTIPLIER   = 8; // بزرگ‌تر برای تمایز بصری
    public static final int    MAX_BUFFER_CAPACITY      = 5;
    public static final int    PORT_SIZE                = 14;

    /* ---------------- Packet types ---------------- */
    public static final int PACKET_SIZE_UNITS_SQUARE   = 2;
    public static final int PACKET_COINS_SQUARE        = 1;
    public static final int PACKET_SIZE_UNITS_TRIANGLE = 3;
    public static final int PACKET_COINS_TRIANGLE      = 2;

    /* ===================== Visual palette ===================== */
    public static final Color COLOR_PACKET_SQUARE   = new Color(0x1E90FF);
    public static final Color COLOR_PACKET_TRIANGLE = new Color(0xF4A742);
    public static final Color COLOR_PORT_INPUT      = Color.GREEN.darker();
    public static final Color COLOR_PORT_OUTPUT     = Color.BLUE.darker();
    public static final Color COLOR_WIRE            = Color.GREEN;
    public static final Color COLOR_BOX_BG          = new Color(0x444444);
    public static final Color COLOR_BOX_FILL        = new Color(0x888888);
    public static final Color COLOR_BOX_BORDER      = Color.WHITE;

    public static final float STROKE_WIDTH_WIRE = 2f;

    /* ===================== Gameplay numbers ===================== */
    // تعداد پکت‌ تولیدی در مرحلهٔ ۱ به ازای هر پورت خروجی سیستم مبدا (Level × this)
    public static final int PACKETS_PER_PORT = 3;

    /* ===================== Physics / Movement ===================== */
    // سرعت پایهٔ هر پکت هنگام ورود به اولین سیم (px/s)
    public static final double DEFAULT_PACKET_SPEED = 100.0;

    // شتاب افزایشی مثلث روی پورت ناسازگار (px/s²)
    public static final double ACC_TRIANGLE         = 50.0;
    // شتاب کاهشی عمومی در ۲۰٪ انتهایی سیم (px/s² – مقدار منفی)
    public static final double ACC_DECEL            = -60.0;
    // حداکثر سرعت مجاز برای پکت‌ها (px/s)
    public static final double MAX_SPEED            = 300.0;

    // حداکثر نویز تا حذف پکت
    public static final double MAX_NOISE_CAPACITY   = 100.0;
    // ضریب نرم‌سازی اثر نویز روی سرعت
    public static final double NOISE_SPEED_SMOOTHING = 0.1;

    // فاصلهٔ زمانی تولید پکت روی هر سیم در منوی Sandbox (فعلاً بلااستفاده)
    public static final double PRODUCTION_INTERVAL_SECONDS = 1.0;

    /** پایهٔ ظرفیت سپرِ پکت‌های محافظت-شده (noise units). */
    public static final double DEFAULT_SHIELD_CAPACITY = 8.0;  // ← هر عدد منطقی ۵-۱۵ خوب است

    public static final double ANTI_TROJAN_RADIUS_PX   = 140.0;
    public static final double ANTI_TROJAN_COOLDOWN_S  = 2.0;

    public static final int MAX_HEAVY_PASSES_PER_WIRE = 3;

    public static final double TROJAN_PROBABILITY_EASY   = 0.08;
    public static final double TROJAN_PROBABILITY_NORMAL = 0.15;
    public static final double TROJAN_PROBABILITY_HARD   = 0.25;

    // یا سوییچ‌پذیر:
    public static double TROJAN_PROBABILITY = TROJAN_PROBABILITY_NORMAL;



    /* --------------------- tunable constants ---------------------- */
    // MSG1 compat accel
    public static final double MSG1_COMPAT_V0       = 80;
    public static final double MSG1_COMPAT_A        = 140;
    public static final double MSG1_COMPAT_MIN_MUL  = 1.0;
    public static final double MSG1_COMPAT_MAX_MUL  = 1.6;
    // MSG1 incompat decel
    public static final double MSG1_INCOMPAT_V0      = 160;
    public static final double MSG1_INCOMPAT_A       = -120;
    public static final double MSG1_INCOMPAT_MIN_MUL = 0.4;
    public static final double MSG1_INCOMPAT_MAX_MUL = 1.0;

    // MSG2 const speeds
    public static final double MSG2_COMPAT_SPEED   = 110;
    public static final double MSG2_INCOMPAT_SPEED = 220;

    // MSG3
    public static final double MSG3_COMPAT_SPEED     = 180;
    public static final double MSG3_INCOMPAT_V0      = 120;
    public static final double MSG3_INCOMPAT_A       = 90;
    public static final double MSG3_INCOMPAT_MIN_MUL = 1.0;
    public static final double MSG3_INCOMPAT_MAX_MUL = 1.6;

    // Confidential
    public static final double CONF_SPEED      = 170;
    public static final double CONF_SLOW_SPEED = 60;

    // Confidential VPN
    public static final double CONF_VPN_SPEED        = 170;
    public static final double CONF_VPN_KEEP_DIST_PX = 60;

    // Large 8
    public static final double L8_BASE_SPEED   = 140;
    public static final double L8_CURVE_ACCEL  = 100;
    public static final double L8_MAX_MUL      = 1.8;

    // Large 10
    public static final double L10_BASE_SPEED      = 160;
    public static final double L10_DRIFT_STEP_PX   = 200;
    public static final double L10_DRIFT_OFFSET_PX = 6;
}

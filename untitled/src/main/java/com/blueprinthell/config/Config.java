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

}

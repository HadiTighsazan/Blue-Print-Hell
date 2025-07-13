package com.blueprinthell.config;

import java.awt.Color;

/**
 * Centralized configuration constants for BlueprintHell application.
 */
public final class Config {
    private Config() { /* Prevent instantiation */ }

    // Model constants
    public static final int PACKET_SIZE_MULTIPLIER = 6;
    public static final int MAX_BUFFER_CAPACITY = 5;
    public static final int PORT_SIZE = 14;

    // PacketType constants (extracted from PacketType enum to avoid hardcoded values)
    public static final int PACKET_SIZE_UNITS_SQUARE = 2;
    public static final int PACKET_COINS_SQUARE = 1;
    public static final int PACKET_SIZE_UNITS_TRIANGLE = 3;
    public static final int PACKET_COINS_TRIANGLE = 2;

    // Color constants for views
    public static final Color COLOR_PACKET_SQUARE   = new Color(0x1E90FF);
    public static final Color COLOR_PACKET_TRIANGLE = new Color(0xF4A742);
    public static final Color COLOR_PORT_INPUT      = Color.GREEN.darker();
    public static final Color COLOR_PORT_OUTPUT     = Color.BLUE.darker();
    public static final Color COLOR_WIRE            = Color.GREEN;
    public static final Color COLOR_BOX_BG          = new Color(0x444444);
    public static final Color COLOR_BOX_FILL        = new Color(0x888888);
    public static final Color COLOR_BOX_BORDER      = Color.WHITE;

    // Stroke widths
    public static final float STROKE_WIDTH_WIRE = 2f;

    // تعداد پکت‌ بر اساس هر پورت خروجی برای سیستم‌های مبدا
    public static final int PACKETS_PER_PORT = 3;

    // سرعت اولیه‌ی هر پکت (پیکسل بر ثانیه)
    public static final double DEFAULT_PACKET_SPEED = 100.0;

    // فاصله‌ی زمانی (ثانیه) بین تولید پکت‌ها روی هر سیم
    public static final double PRODUCTION_INTERVAL_SECONDS = 1.0;

    // حداکثر مقدار نویز تا حذف پکت
    public static final double MAX_NOISE_CAPACITY = 100.0;

    // ضریب هموارسازی تغییر سرعت بر اساس نویز (0..1)
    public static final double NOISE_SPEED_SMOOTHING = 0.1;

    public static final int SYSTEM_WIDTH = 96;
    public static final int SYSTEM_HEIGHT = 96;
}

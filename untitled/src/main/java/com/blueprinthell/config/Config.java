package com.blueprinthell.config;

import com.blueprinthell.model.PortShape;

import java.awt.Color;


public final class Config {
    public static final int SYSTEM_WIDTH = 96;
    public static final int SYSTEM_HEIGHT = 96;
    public static final int MAX_OUTPUT_PORTS = 3;

    public static final PortShape DEFAULT_PORT_SHAPE = PortShape.SQUARE;
    public static final double MAX_TIME_ON_WIRE_SEC = 8.0;
    public static final double SYSTEM_DISABLE_DURATION =2.0 ;
    public static final double MAX_ALLOWED_SPEED = 150.0;


    private Config() { /* Prevent instantiation */ }

    public static final int    PACKET_SIZE_MULTIPLIER   = 8;
    public static final int    MAX_BUFFER_CAPACITY      = 6;
    public static final int    PORT_SIZE                = 14;

    public static final int PACKET_SIZE_UNITS_SQUARE   = 2;
    public static final int PACKET_COINS_SQUARE        = 2;
    public static final int PACKET_SIZE_UNITS_TRIANGLE = 3;
    public static final int PACKET_COINS_TRIANGLE      = 3;


    public static final Color COLOR_PACKET_SQUARE   = new Color(0x1E90FF);
    public static final Color COLOR_PACKET_TRIANGLE = new Color(0xF4A742);
    public static final Color COLOR_PORT_INPUT      = Color.GREEN.darker();
    public static final Color COLOR_PORT_OUTPUT     = Color.BLUE.darker();
    public static final Color COLOR_WIRE            = Color.GREEN;
    public static final Color COLOR_BOX_BG          = new Color(0x444444);
    public static final Color COLOR_BOX_FILL        = new Color(0x888888);
    public static final Color COLOR_BOX_BORDER      = Color.WHITE;

    public static final int BOX_SIZE = 120;
    public static final float STROKE_WIDTH_WIRE = 2f;


    public static final int PACKETS_PER_PORT = 3;


    public static final double DEFAULT_PACKET_SPEED = 100.0;

    public static final double MAX_SPEED            = 300.0;

    public static final double MAX_NOISE_CAPACITY   = 100.0;


    public static final double DEFAULT_SHIELD_CAPACITY = 8.0;

    public static final double ANTI_TROJAN_RADIUS_PX   = 140.0;
    public static final double ANTI_TROJAN_COOLDOWN_S  = 2.0;


    public static final double TROJAN_PROBABILITY_NORMAL = 0.15;


    public static final int LARGE_PACKET_SIZE_8 = 8;
    public static final int LARGE_PACKET_SIZE_10 = 10;

    public static final double L10_DRIFT_INTERVAL_MS = 200.0; // هر 200ms یک انحراف
    public static final double L10_DRIFT_AMOUNT_PX = 5.0;     // مقدار انحراف


    public static final double MSG1_COMPAT_V0       = 80;
    public static final double MSG1_COMPAT_A        = 120;
    public static final double MSG1_COMPAT_MIN_MUL  = 1.0;
    public static final double MSG1_COMPAT_MAX_MUL  = 1.6;

    public static final double MSG1_INCOMPAT_V0      = 160;
    public static final double MSG1_INCOMPAT_A       = -100;
    public static final double MSG1_INCOMPAT_MIN_MUL = 0.4;
    public static final double MSG1_INCOMPAT_MAX_MUL = 1.0;

    public static final double MSG2_COMPAT_SPEED   = 110;
    public static final double MSG2_INCOMPAT_SPEED = 220;
    public static final double MSG3_COMPAT_SPEED     = 180;
    public static final double MSG3_INCOMPAT_V0      = 120;

    public static final double MSG3_INCOMPAT_A       = 90;
    public static final double MSG3_INCOMPAT_MIN_MUL = 1.0;


    public static final double MSG3_INCOMPAT_MAX_MUL = 2;

    public static final double CONF_SPEED      = 170;
    public static final double CONF_SLOW_SPEED = 60;

    public static final double CONF_VPN_SPEED        = 170;
    public static final double CONF_VPN_KEEP_DIST_PX = 60;

    public static final double L8_BASE_SPEED   = 140;
    public static final double L8_CURVE_ACCEL  = 80;
    public static final double L8_MAX_MUL      = 1.8;

    public static final double L10_BASE_SPEED      = 160;
    public static final double L10_DRIFT_STEP_PX   = 200;
    public static final double L10_DRIFT_OFFSET_PX = 6;


    public static final int   PACKET_SIZE_UNITS_CIRCLE = 1;
    public static final int   PACKET_COINS_CIRCLE      = 1;
    public static final Color COLOR_PACKET_CIRCLE      = new Color(0x39B54A);

    public static final int BIT_PACKET_SIZE = 2;
    public static final java.awt.Color COLOR_PACKET_LARGE = new java.awt.Color(0x8E44AD);

    public static final java.awt.Color COLOR_BADGE_BG = new java.awt.Color(0x111111);
    public static final java.awt.Color COLOR_BADGE_FG = java.awt.Color.WHITE;

    public static final java.awt.Font FONT_BADGE = new java.awt.Font("Dialog", java.awt.Font.BOLD, 11);
    public static final java.awt.Font FONT_SYSTEM_LABEL = new java.awt.Font("Dialog", java.awt.Font.BOLD, 13);

    public static final int   BADGE_CORNER_RADIUS = 8;
    public static final int   BADGE_PADDING       = 3;
    public static final int   BADGE_MARGIN_X      = 2;
    public static final int   BADGE_MARGIN_Y      = 2;
    public static final double POLY_INSET         = 0.03;

    public static final int    LONG_WIRE_THRESHOLD_PX = 250;
    public static final double LONG_WIRE_ACCEL        = 80.0;
    public static final double LONG_WIRE_MAX_MUL      = 1.8;

    public static final int LARGE_MAX_PASSES_PER_WIRE = 3;


    public static final double LONG_WIRE_MAX_SPEED_MUL= 3.5;
    public static final double APPROACH_ZONE_START   = 0.85;
    public static final double APPROACH_MAX_MUL     = 1.50;
    public static final double LONG_WIRE_ACCEL_BLUE  = 60.0;
    public static final double LONG_WIRE_MAX_MUL_BLUE= 1.50;

    public static final double CIRCLE_YIELD_WAIT = 0.30;

    public static final int MAX_LARGE_BUFFER_CAPACITY = 8;
    public static final int MAX_LP_SPLIT_PER_FRAME = 1;
}

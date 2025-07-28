package com.blueprinthell.model;

import com.blueprinthell.config.Config;


public enum PacketType {
    SQUARE  (Config.PACKET_SIZE_UNITS_SQUARE,   Config.PACKET_COINS_SQUARE),
    TRIANGLE(Config.PACKET_SIZE_UNITS_TRIANGLE, Config.PACKET_COINS_TRIANGLE),
    CIRCLE  (Config.PACKET_SIZE_UNITS_CIRCLE,   Config.PACKET_COINS_CIRCLE); // ← جدید

    public final int sizeUnits;
    public final int coins;

    PacketType(int sizeUnits, int coins) {
        this.sizeUnits = sizeUnits;
        this.coins     = coins;
    }
}


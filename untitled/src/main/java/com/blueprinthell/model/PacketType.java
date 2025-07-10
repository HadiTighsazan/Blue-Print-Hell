package com.blueprinthell.model;

import com.blueprinthell.config.Config;

/**
 * Enumeration of packet types in the domain model, without UI dependencies.
 */
public enum PacketType {
    SQUARE(Config.PACKET_SIZE_UNITS_SQUARE, Config.PACKET_COINS_SQUARE),
    TRIANGLE(Config.PACKET_SIZE_UNITS_TRIANGLE, Config.PACKET_COINS_TRIANGLE);

    public final int sizeUnits;
    public final int coins;

    PacketType(int sizeUnits, int coins) {
        this.sizeUnits = sizeUnits;
        this.coins = coins;
    }

    /**
     * Maps this packet type to the corresponding port shape.
     */
    public PortShape toPortShape() {
        return this == SQUARE ? PortShape.SQUARE : PortShape.TRIANGLE;
    }
}

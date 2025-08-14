package com.blueprinthell.model.large;

import com.blueprinthell.model.PacketType;


public class MergedPacket extends LargePacket {


    public MergedPacket(PacketType type,
                        double baseSpeed,
                        int sizeUnits,
                        int groupId,
                        int expectedBits,
                        int colorId) {
        super(type, baseSpeed, sizeUnits, groupId, expectedBits, colorId, /*rebuiltFromBits=*/true);
    }


}

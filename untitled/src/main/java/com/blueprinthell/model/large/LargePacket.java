package com.blueprinthell.model.large;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.WireModel;

import java.awt.*;
import java.io.Serializable;
import java.util.Objects;


public class LargePacket extends PacketModel implements Serializable {
    private static final long serialVersionUID = 11L;

    private final int originalSizeUnits;

    private int groupId = -1;

    private int expectedBits;

    private int colorId;

    private boolean rebuiltFromBits;
    private Color customColor;



    public LargePacket(PacketType type, double baseSpeed, int originalSizeUnits) {
        super(type, baseSpeed);
        this.originalSizeUnits = originalSizeUnits;
        this.expectedBits = originalSizeUnits;
    }
    public LargePacket(PacketType type, double baseSpeed, int originalSizeUnits, Color customColor) {
        super(type, baseSpeed);
        this.originalSizeUnits = originalSizeUnits;
        this.expectedBits = originalSizeUnits;
        this.customColor = customColor;
    }

    public LargePacket(PacketType type, double baseSpeed,
                       int originalSizeUnits,
                       int groupId,
                       int expectedBits,
                       int colorId,
                       boolean rebuiltFromBits) {
        super(type, baseSpeed);
        this.originalSizeUnits = originalSizeUnits;
        this.groupId = groupId;
        this.expectedBits = expectedBits;
        this.colorId = colorId;
        this.rebuiltFromBits = rebuiltFromBits;
        this.customColor = Color.getHSBColor(colorId / 360.0f, 0.8f, 0.9f);
    }

    public static LargePacket fromSample(PacketModel sample,
                                         int originalSizeUnits,
                                         int groupId,
                                         int expectedBits,
                                         int colorId,
                                         boolean rebuiltFromBits) {
        Objects.requireNonNull(sample, "sample");
        LargePacket lp = new LargePacket(sample.getType(), sample.getBaseSpeed(), originalSizeUnits,
                groupId, expectedBits, colorId, rebuiltFromBits);
        copyRuntimeState(sample, lp);
        return lp;
    }


    public static LargePacket createRaw(PacketType type, double baseSpeed, int sizeUnits) {
        return new LargePacket(type, baseSpeed, sizeUnits);
    }



    public int getOriginalSizeUnits() { return originalSizeUnits; }
    public int getGroupId()           { return groupId; }
    public int getExpectedBits()      { return expectedBits; }
    public int getColorId()           { return colorId; }
    public boolean isRebuiltFromBits(){ return rebuiltFromBits; }

    public boolean hasGroup()         { return groupId >= 0; }

    public void setGroupInfo(int groupId, int expectedBits, int colorId) {
        if (this.groupId >= 0) return; // already set
        this.groupId = groupId;
        this.expectedBits = expectedBits;
        this.colorId = colorId;
    }

    public void markRebuilt() { this.rebuiltFromBits = true; }



    private static void copyRuntimeState(PacketModel src, PacketModel dst) {
        WireModel w = src.getCurrentWire();
        if (w != null) {
            dst.attachToWire(w, src.getProgress());
        } else {
            dst.setProgress(src.getProgress());
        }
        dst.setSpeed(src.getSpeed());
        dst.setAcceleration(src.getAcceleration());
        if (src.getNoise() > 0) {
            dst.increaseNoise(src.getNoise());
        }
    }
    public Color getCustomColor() {
        if (customColor != null) return customColor;
        if (colorId > 0) return Color.getHSBColor(colorId / 360.0f, 0.8f, 0.9f);
        return Config.COLOR_PACKET_LARGE;
    }

    public void setCustomColor(Color color) {
        this.customColor = color;
    }
}

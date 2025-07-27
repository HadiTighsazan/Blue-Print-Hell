package com.blueprinthell.model.large;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.WireModel;

import java.io.Serializable;
import java.util.Objects;


public class BitPacket extends PacketModel implements Serializable {
    private static final long serialVersionUID = 12L;

    private final int groupId;
    private final int parentSizeUnits;
    private final int indexInGroup;
    private final int colorId;

    private boolean registeredAtMerger = false;



    public BitPacket(PacketType type,
                     double baseSpeed,
                     int groupId,
                     int parentSizeUnits,
                     int indexInGroup,
                     int colorId) {
        super(type, baseSpeed);
        this.groupId = groupId;
        this.parentSizeUnits = parentSizeUnits;
        this.indexInGroup = indexInGroup;
        this.colorId = colorId;
    }


    public static BitPacket fromSample(PacketModel sample,
                                       int groupId,
                                       int parentSizeUnits,
                                       int indexInGroup,
                                       int colorId) {
        Objects.requireNonNull(sample, "sample");
        BitPacket bp = new BitPacket(sample.getType(), sample.getBaseSpeed(),
                groupId, parentSizeUnits, indexInGroup, colorId);
        copyRuntimeState(sample, bp);
        return bp;
    }



    public int getGroupId()            { return groupId; }
    public int getParentSizeUnits()    { return parentSizeUnits; }
    public int getIndexInGroup()       { return indexInGroup; }
    public int getColorId()            { return colorId; }

    public boolean isRegisteredAtMerger() { return registeredAtMerger; }
    public void markRegisteredAtMerger()  { this.registeredAtMerger = true; }



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
}

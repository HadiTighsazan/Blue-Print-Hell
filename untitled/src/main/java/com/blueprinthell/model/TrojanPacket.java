package com.blueprinthell.model;


public class TrojanPacket extends PacketModel {
    private final PacketModel original;


    public static TrojanPacket wrap(PacketModel original) {
        return new TrojanPacket(original);
    }

    private TrojanPacket(PacketModel original) {
        super(original.getType(), original.getBaseSpeed());
        this.original = original;
        setProgress(original.getProgress());
        setSpeed(original.getSpeed());
        increaseNoise(original.getNoise());
        if (original.getCurrentWire() != null) {
            attachToWire(original.getCurrentWire(), original.getProgress());
        }
    }

    public PacketModel getOriginal() {
        return original;
    }


    public boolean isTrojan() {
        return true;
    }

}

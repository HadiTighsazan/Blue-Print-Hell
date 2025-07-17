package com.blueprinthell.model;

/**
 * TrojanPacket — decorator marking a packet as malicious.
 * Can be recognized and removed by AntiTrojanSystem.
 */
public class TrojanPacket extends PacketModel {
    private final PacketModel original;

    /**
     * Wraps an existing PacketModel into a new TrojanPacket.
     * The original must be removed from its wire or buffer by the caller.
     */
    public static TrojanPacket wrap(PacketModel original) {
        return new TrojanPacket(original);
    }

    private TrojanPacket(PacketModel original) {
        super(original.getType(), original.getBaseSpeed());
        this.original = original;
        // Copy dynamic state
        setProgress(original.getProgress());
        setSpeed(original.getSpeed());
        increaseNoise(original.getNoise());
        if (original.getCurrentWire() != null) {
            attachToWire(original.getCurrentWire(), original.getProgress());
        }
    }

    /**
     * Returns the wrapped original packet.
     */
    public PacketModel getOriginal() {
        return original;
    }

    /**
     * Always true for this class to indicate malicious status.
     */
    public boolean isTrojan() {
        return true;
    }

    // Additional malicious behaviors can be added here (e.g., spread noise on collision)
}

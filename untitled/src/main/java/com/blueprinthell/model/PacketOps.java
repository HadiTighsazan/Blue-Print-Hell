package com.blueprinthell.model;

import com.blueprinthell.motion.KinematicsProfile;
import com.blueprinthell.motion.KinematicsRegistry;

import java.util.Objects;


public final class PacketOps {

    private PacketOps() { /* static only */ }


    public static boolean isTrojan(PacketModel p)        { return p instanceof TrojanPacket; }
    public static boolean isProtected(PacketModel p)     { return p instanceof ProtectedPacket; }
    public static boolean isConfidential(PacketModel p)  { return p instanceof ConfidentialPacket; }


    public static PacketModel toTrojan(PacketModel original) {
        Objects.requireNonNull(original, "packet");
        if (original instanceof TrojanPacket) return original;
        PacketModel trojan = TrojanPacket.wrap(original);
        KinematicsRegistry.copyProfile(original, trojan);
        return trojan;
    }

    public static PacketModel unwrapTrojan(PacketModel packet) {
        Objects.requireNonNull(packet, "packet");
        if (packet instanceof TrojanPacket tp) {
            PacketModel inner = unwrapTrojan(tp.getOriginal());
            return clonePlain(inner);
        }
        return packet;
    }


    public static PacketModel clonePlain(PacketModel src) {
        Objects.requireNonNull(src, "src");
        PacketModel dst = new PacketModel(src.getType(), src.getBaseSpeed());
        if (src.getCurrentWire() != null) {
            dst.attachToWire(src.getCurrentWire(), src.getProgress());
        } else {
            dst.setProgress(src.getProgress());
        }
        dst.setSpeed(src.getSpeed());
        dst.setAcceleration(src.getAcceleration());
        dst.setNoise(src.getNoise());
        KinematicsRegistry.copyProfile(src, dst);
        return dst;
    }

    private static void copyRuntimeState(PacketModel src, PacketModel dst) {
        if (src.getCurrentWire() != null) {
            dst.attachToWire(src.getCurrentWire(), src.getProgress());
        } else {
            dst.setProgress(src.getProgress());
        }
        dst.setSpeed(src.getSpeed());
        dst.setAcceleration(src.getAcceleration());
        if (src.getNoise() > 0) {
            dst.increaseNoise(src.getNoise());
        }
    }



    public static PacketModel toProtected(PacketModel original, double shieldCapacity) {
        Objects.requireNonNull(original, "packet");
        if (original instanceof ProtectedPacket) return original;
        PacketModel prot = ProtectedPacket.wrap(original, shieldCapacity);
        KinematicsRegistry.setProfile(prot, KinematicsProfile.PROTECTED_SHADOW);
        return prot;
    }



    public static PacketModel toConfidential(PacketModel original) {
        Objects.requireNonNull(original, "packet");
        if (original instanceof ConfidentialPacket) return original;
        PacketModel conf = ConfidentialPacket.wrap(original);
        KinematicsRegistry.setProfile(conf, KinematicsProfile.CONFIDENTIAL);
        return conf;
    }




}

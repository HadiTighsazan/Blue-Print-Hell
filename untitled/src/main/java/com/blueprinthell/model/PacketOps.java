package com.blueprinthell.model;

import com.blueprinthell.motion.KinematicsProfile;
import com.blueprinthell.motion.KinematicsRegistry;

import java.util.Objects;

/**
 * Utility helpers for transforming {@link PacketModel} instances between different logical roles
 * (Trojan / Protected / Confidential / plain) while preserving runtime state (progress, speed, noise, wire binding …)
 * <br>
 * از این نسخه به بعد، پروفایل حرکتی هر پکت نیز از طریق {@link KinematicsRegistry} حفظ/تنظیم می‌شود.
 */
public final class PacketOps {

    private PacketOps() { /* static only */ }

    /* --------------------------------------------------------------- */
    /*                         Type checks                              */
    /* --------------------------------------------------------------- */

    public static boolean isTrojan(PacketModel p)        { return p instanceof TrojanPacket; }
    public static boolean isProtected(PacketModel p)     { return p instanceof ProtectedPacket; }
    public static boolean isConfidential(PacketModel p)  { return p instanceof ConfidentialPacket; }

    /* --------------------------------------------------------------- */
    /*                        Trojan helpers                            */
    /* --------------------------------------------------------------- */

    /** Wraps packet into a {@link TrojanPacket} if it isn't already one. */
    public static PacketModel toTrojan(PacketModel original) {
        Objects.requireNonNull(original, "packet");
        if (original instanceof TrojanPacket) return original;
        PacketModel trojan = TrojanPacket.wrap(original);
        // کپی پروفایل حرکتی
        KinematicsRegistry.copyProfile(original, trojan);
        return trojan;
    }

    /** If packet is a TrojanPacket, returns its original; otherwise returns the packet itself. */
    public static PacketModel unwrapTrojan(PacketModel packet) {
        Objects.requireNonNull(packet, "packet");
        if (packet instanceof TrojanPacket tp) {
            // 1) بازکردن بازگشتی
            PacketModel inner = unwrapTrojan(tp.getOriginal());
            // 2) بازسازی نسخهٔ Plain با حفظ state
            return clonePlain(inner);
        }
        return packet;
    }

    /**
     * یک PacketModel ساده می‌سازد (بدون هیچ decorator)
     * و وضعیت runtime (progress, speed, acceleration, noise) و پروفایل جنبشی را از src کپی می‌کند.
     */
    public static PacketModel clonePlain(PacketModel src) {
        Objects.requireNonNull(src, "src");
        // ۱) ساخت نمونهٔ جدید با نوع و سرعت پایهٔ src
        PacketModel dst = new PacketModel(src.getType(), src.getBaseSpeed());
        // ۲) حفظ موقعیت روی سیم یا progress
        if (src.getCurrentWire() != null) {
            dst.attachToWire(src.getCurrentWire(), src.getProgress());
        } else {
            dst.setProgress(src.getProgress());
        }
        // ۳) حفظ سرعت، شتاب و نویز
        dst.setSpeed(src.getSpeed());
        dst.setAcceleration(src.getAcceleration());
        dst.setNoise(src.getNoise());
        // ۴) حفظ پروفایل جنبشی (compatible/incompatible settings)
        KinematicsRegistry.copyProfile(src, dst);
        return dst;
    }

    /** Copies progress/speed/noise/acceleration/wire-binding. */
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

    /* --------------------------------------------------------------- */
    /*                       Protected helpers                          */
    /* --------------------------------------------------------------- */

    /** Wraps packet into a {@link ProtectedPacket} with the given shield, unless already protected. */
    public static PacketModel toProtected(PacketModel original, double shieldCapacity) {
        Objects.requireNonNull(original, "packet");
        if (original instanceof ProtectedPacket) return original;
        PacketModel prot = ProtectedPacket.wrap(original, shieldCapacity);
        // پروفایل Protected → PROTECTED_SHADOW
        KinematicsRegistry.setProfile(prot, KinematicsProfile.PROTECTED_SHADOW);
        return prot;
    }

    /* --------------------------------------------------------------- */
    /*                     Confidential helpers                         */
    /* --------------------------------------------------------------- */

    /** Wraps packet into a {@link ConfidentialPacket}; if already confidential, returns as is. */
    public static PacketModel toConfidential(PacketModel original) {
        Objects.requireNonNull(original, "packet");
        if (original instanceof ConfidentialPacket) return original;
        PacketModel conf = ConfidentialPacket.wrap(original);
        KinematicsRegistry.setProfile(conf, KinematicsProfile.CONFIDENTIAL);
        return conf;
    }

    /* --------------------------------------------------------------- */
    /*                         Generic clone                            */
    /* --------------------------------------------------------------- */



}

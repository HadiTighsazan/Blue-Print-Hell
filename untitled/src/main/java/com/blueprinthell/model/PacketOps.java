package com.blueprinthell.model;

import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.motion.KinematicsProfile;
import com.blueprinthell.motion.KinematicsRegistry;

import java.util.Objects;
/* ADD START */
import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

import static com.blueprinthell.motion.KinematicsProfile.*;
/* ADD END */

public final class PacketOps {

    private PacketOps() { /* static only */ }

    /* ---------------- Existing helpers ---------------- */

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

    public static boolean isMessenger(PacketModel p) {
        KinematicsProfile prof = KinematicsRegistry.getOrDefault(p, null);
        return prof == MSG1
                || prof == MSG2
                || prof == MSG3;
    }

    /* ---------------- ADDITIONS (Phase-1) ---------------- */

    /** Lightweight tagging so VPN can mark confidential variants without new fields on models. */
    public enum PacketTag {
        CONFIDENTIAL_VPN // Confidential with VPN semantics (keep-distance), coin=4
    }

    private static final Map<PacketModel, EnumSet<PacketTag>> TAGS = new WeakHashMap<>();

    /** Attach a logical tag to a packet (kept weakly; no lifecycle coupling). */
    public static void tag(PacketModel p, PacketTag tag) {
        if (p == null || tag == null) return;
        TAGS.computeIfAbsent(p, k -> EnumSet.noneOf(PacketTag.class)).add(tag);
    }

    /** Check if a packet carries a tag. */
    public static boolean hasTag(PacketModel p, PacketTag tag) {
        EnumSet<PacketTag> set = TAGS.get(p);
        return set != null && set.contains(tag);
    }

    /** Convenience checks for other packet categories used in logic. */
    public static boolean isBit(PacketModel p)   { return p instanceof BitPacket; }
    public static boolean isLarge(PacketModel p) { return p instanceof LargePacket; }

    /** Is a confidential packet specifically tagged as VPN-variant. */
    public static boolean isConfidentialVpn(PacketModel p) {
        return isConfidential(p) && hasTag(p, PacketTag.CONFIDENTIAL_VPN);
    }

    /**
     * Coin value logic according to design:
     * - Protected: 5
     * - Confidential (normal): 3
     * - Confidential (VPN-tagged): 4
     * - Large: originalSizeUnits (e.g., 8 or 10)
     * - Messengers by type: MSG1→1, MSG2→2, MSG3→3
     * - Otherwise: 0
     */
    public static int coinValue(PacketModel p) {
        if (p == null) return 0;

        if (isProtected(p)) {
            return 5;
        }

        if (isConfidential(p)) {
            if (isConfidentialVpn(p)) {
                return 4;
            }
            return 3;
        }

        if (isLarge(p)) {
            int size = Math.max(0, ((LargePacket) p).getOriginalSizeUnits());
            return size; // e.g., 8 or 10
        }

        PacketType t = p.getType();
        KinematicsProfile prof = KinematicsRegistry.getOrDefault(p, null);
        if (prof != null) {
            switch (prof) {
                case MSG1: return 1;
                case MSG2: return 2;
                case MSG3: return 3;
                default:   return 0;
            }
        }


        return 0;
    }
}

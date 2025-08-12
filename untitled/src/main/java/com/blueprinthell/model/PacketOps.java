package com.blueprinthell.model;

import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.motion.KinematicsProfile;
import com.blueprinthell.motion.KinematicsRegistry;

import java.util.Objects;

import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

import static com.blueprinthell.motion.KinematicsProfile.*;

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

    static void copyRuntimeState(PacketModel src, PacketModel dst) {
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

    public static PacketModel toConfidentialVpn(PacketModel original) {
        Objects.requireNonNull(original, "packet");

        // اگر از قبل Confidential + VPN-tag بود، همان را برگردان
        if (isConfidentialVpn(original)) return original;

        // اگر از قبل محرمانه (۴ واحدی) است: حتماً یک ConfidentialPacket بساز و 1.5× کن
        if (isConfidential(original)) {
            ConfidentialPacket conf6 = ConfidentialPacket.wrap(original); // حفظ کلاسِ Confidential
            conf6.setWidth( (int)Math.round(conf6.getWidth()  * (6.0/4.0)) );
            conf6.setHeight((int)Math.round(conf6.getHeight() * (6.0/4.0)) );
            tag(conf6, PacketTag.CONFIDENTIAL_VPN);
            KinematicsRegistry.setProfile(conf6, KinematicsProfile.CONFIDENTIAL_VPN);
            return conf6;
        }

        // در غیر این صورت، از روی پکت ورودی، محرمانه ۶ واحدی بساز
        ConfidentialPacket conf = ConfidentialPacket.wrap(original);
        int suOrig = Math.max(1, original.getType().sizeUnits);
        int w = original.getWidth();
        int h = original.getHeight();
        if (w > 0 && h > 0) {
            double pxPerUnitW = (double) w / suOrig;
            double pxPerUnitH = (double) h / suOrig;
            conf.setWidth( (int)Math.round(pxPerUnitW * 6) );
            conf.setHeight((int)Math.round(pxPerUnitH * 6) );
        }
        tag(conf, PacketTag.CONFIDENTIAL_VPN);
        KinematicsRegistry.setProfile(conf, KinematicsProfile.CONFIDENTIAL_VPN);
        return conf;
    }
    public static boolean isMessenger(PacketModel p) {
        if (p == null) return false;

        if (p instanceof com.blueprinthell.model.large.BitPacket) return false;

        if (p instanceof ProtectedPacket) return true;

        KinematicsProfile prof = KinematicsRegistry.getOrDefault(p, null);
        if (prof != null) {
            return prof == MSG1 || prof == MSG2 || prof == MSG3;
        }

        int su = Math.max(1, p.getType().sizeUnits);
        return su <= 3 && !(p instanceof ConfidentialPacket) && !(p instanceof com.blueprinthell.model.large.LargePacket);
    }

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


    public static int coinValueOnEntry(PacketModel p) {
        if (isProtected(p)) return 5;
        if (isConfidentialVpn(p)) return 4;
        if (isConfidential(p)) return 3;

        KinematicsProfile prof = KinematicsRegistry.getOrDefault(p, null);
        if (prof == KinematicsProfile.MSG1) return 1;  // دایره - تصحیح شد
        if (prof == KinematicsProfile.MSG2) return 2;  // مربع - تصحیح شد
        if (prof == KinematicsProfile.MSG3) return 3;  // مثلث - تصحیح شد

        // برای پکت‌های حجیم هنگام ورود سکه‌ای اضافه نمی‌شود
        if (isLarge(p)) {
            LargePacket lp = (LargePacket) p;
            return lp.getOriginalSizeUnits(); // 8 یا 10 سکه
        }

        return 0;
    }


    public static int coinValueOnConsume(PacketModel p) {
        // فقط پکت‌های حجیم هنگام مصرف نهایی سکه می‌دهند
        if (isLarge(p)) {
            return Math.max(0, ((LargePacket) p).getOriginalSizeUnits());
        }


        return 0;
    }

}
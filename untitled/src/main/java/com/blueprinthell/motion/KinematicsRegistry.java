package com.blueprinthell.motion;

import com.blueprinthell.model.PacketModel;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * <h2>KinematicsRegistry</h2>
 * نگهدارندهٔ جانبی (Sidecar) برای اتصال هر {@link PacketModel} به یک {@link KinematicsProfile}
 * بدون دست‌کاری کلاس PacketModel. از WeakHashMap استفاده شده تا با جمع‌آوری اشیای آزاد شده،
 * نشت حافظه ایجاد نشود.
 *
 * <p>تمام متدها static هستند چون این رجیستری سراسری است و در کل بازی یک نمونه کافی‌ست.</p>
 */
public final class KinematicsRegistry {

    private static final Map<PacketModel, KinematicsProfile> MAP = new WeakHashMap<>();

    private KinematicsRegistry() { /* no instantiation */ }

    /* --------------------------------------------------------------- */
    /*                              API                                 */
    /* --------------------------------------------------------------- */

    /** ثبت/به‌روزرسانی پروفایل پکت. */
    public static void setProfile(PacketModel packet, KinematicsProfile profile) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(profile, "profile");
        MAP.put(packet, profile);
    }

    /**
     * اگر پروفایل ثبت شده باشد برمی‌گرداند، در غیر این صورت null.
     */
    public static KinematicsProfile getProfile(PacketModel packet) {
        return MAP.get(packet);
    }

    /**
     * پروفایل یا مقدار پیش‌فرض را برمی‌گرداند (و اگر absent بود، ثبت نمی‌کند).
     */
    public static KinematicsProfile getOrDefault(PacketModel packet, KinematicsProfile deflt) {
        KinematicsProfile p = MAP.get(packet);
        return (p != null) ? p : deflt;
    }

    /**
     * اگر پروفایل وجود نداشت، مقدار def را ثبت می‌کند و همان را برمی‌گرداند.
     */
    public static KinematicsProfile ensure(PacketModel packet, KinematicsProfile deflt) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(deflt, "default profile");
        return MAP.computeIfAbsent(packet, k -> deflt);
    }

    /** آیا برای این پکت قبلاً پروفایل ثبت شده است؟ */
    public static boolean has(PacketModel packet) {
        return MAP.containsKey(packet);
    }

    /** حذف پروفایل یک پکت (مثلاً هنگام Destroy). */
    public static void remove(PacketModel packet) {
        MAP.remove(packet);
    }

    /** پاک‌سازی کامل رجیستری (برای ریست مرحله). */
    public static void clear() {
        MAP.clear();
    }

    /**
     * هنگام wrap/clone کردن پکت (Trojan/Protected/Confidential) می‌توان پروفایل را کپی کرد.
     */
    public static void copyProfile(PacketModel from, PacketModel to) {
        KinematicsProfile p = MAP.get(from);
        if (p != null) MAP.put(to, p);
    }

    /** دید فقط خواندنی (برای دیباگ/تست). */
    public static Map<PacketModel, KinematicsProfile> view() {
        return Collections.unmodifiableMap(MAP);

    }
}

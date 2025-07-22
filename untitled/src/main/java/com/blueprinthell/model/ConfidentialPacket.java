package com.blueprinthell.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * <h2>ConfidentialPacket – پکت محرمانه</h2>
 * <p>
 *  این کلاس یک دکوراتور ساده روی {@link PacketModel} نیست (original را نگه نمی‌دارد) اما مانند
 *  {@link TrojanPacket} و {@link ProtectedPacket} برای تشخیص سادهٔ «محرمانه بودن» از طریق instanceof استفاده می‌شود.
 *  هدف فعلی: سیستم‌های Spy / Malicious بتوانند تشخیص دهند و رفتار مناسب اعمال کنند.
 * </p>
 *
 * <p>
 *  ویژگی‌های حرکت/کاهش سرعت هنگام نزدیک شدن به سیستمی که بافر پر دارد، در گام‌های بعدی از طریق
 *  {@link com.blueprinthell.motion.MotionStrategy} یا کنترلرهای حرکتی اعمال خواهد شد. این کلاس فقط کانتینر داده و
 *  هویت منطقی پکت محرمانه است.
 * </p>
 */
public class ConfidentialPacket extends PacketModel implements Serializable {
    private static final long serialVersionUID = 3L;

    /**
     * سازندهٔ مستقیم – برای مواقعی که بخواهیم یک پکت محرمانهٔ جدید بسازیم.
     */
    public ConfidentialPacket(PacketType type, double baseSpeed) {
        super(type, baseSpeed);
    }

    /**
     * پکت داده‌شده را به یک ConfidentialPacket تبدیل می‌کند (instance جدید)،
     * و state زمان اجرا را کپی می‌نماید. مسئولیت حذف original از بافر/سیم با Caller است.
     */
    public static ConfidentialPacket wrap(PacketModel original) {
        Objects.requireNonNull(original, "original packet");
        ConfidentialPacket cp = new ConfidentialPacket(original.getType(), original.getBaseSpeed());
        copyRuntimeState(original, cp);
        return cp;
    }

    /**
     * @return همیشه true برای اینکه بتوانیم به سادگی تشخیص دهیم این پکت محرمانه است.
     */
    public boolean isConfidential() {
        return true;
    }

    /* --------------------------------------------------------------- */
    /*                      Internal copy helper                        */
    /* --------------------------------------------------------------- */
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
}

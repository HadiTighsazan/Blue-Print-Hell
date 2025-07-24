package com.blueprinthell.motion;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.ProtectedPacket;
import com.blueprinthell.model.ConfidentialPacket;
import com.blueprinthell.model.WireModel;

import java.util.*;

/**
 * <h2>MotionStrategyFactory</h2>
 * نقطهٔ مرکزی ساخت {@link MotionStrategy} براساس:
 * <ul>
 *   <li>پروفایل ثبت‌شده در {@link KinematicsRegistry}</li>
 *   <li>سازگار/ناسازگار بودن پورت انتخاب‌شده</li>
 * </ul>
 * استراتژی‌های داخلی (LinearAccelStrategy، KeepDistanceStrategy، DriftStrategy، ...) در همین کلاس به‌صورت
 * private static پیاده‌سازی شده‌اند تا وابستگی اضافه ایجاد نشود.
 */
public final class MotionStrategyFactory {

    private static final Random RAND = new Random();

    private MotionStrategyFactory() {}

    /**
     * استراتژی مناسب را می‌سازد. اگر پروفایل قبلاً تعیین نشده باشد، با {@link #ensureProfile(PacketModel)} آن را تعیین می‌کنیم.
     * @param packet     پکت موردنظر
     * @param compatible آیا پورت خروجی سازگار بوده است؟
     */
    public static MotionStrategy create(PacketModel packet, boolean compatible) {
        Objects.requireNonNull(packet, "packet");

        // 1) پروفایل را داشته باشیم
        KinematicsProfile profile = ensureProfile(packet);
        ProfileParams params      = profile.getParams();

        // 2) انتخاب Rule براساس سازگاری
        MotionRule rule = compatible ? params.compatRule : params.incompatRule;

        // 3) اگر پروفایل Protected است و flag تصادفی روشن، یکی از پیام‌رسان‌ها را انتخاب کن
        if (profile == KinematicsProfile.PROTECTED_SHADOW && params.randomMessengerProfile) {
            KinematicsProfile rndProf = KinematicsProfile.randomMessenger(RAND);
            KinematicsRegistry.setProfile(packet, rndProf);
            profile = rndProf;
            params  = rndProf.getParams();
            rule    = compatible ? params.compatRule : params.incompatRule;
        }

        // 4) ساخت استراتژی براساس mode
        return switch (rule.mode) {
            case CONST           -> new ConstFromRuleStrategy(rule);
            case ACCEL           -> new LinearAccelStrategy(rule);
            case DECEL           -> new LinearAccelStrategy(rule); // شتاب منفی در خود rule.accel هست
            case CURVE_ACCEL     -> new CurveAccelWrapper(rule);
            case KEEP_DISTANCE   -> new KeepDistanceStrategy(rule, params.keepDistancePx);
            case DRIFT           -> new DriftStrategy(rule, params.driftStepDistancePx, params.driftOffsetPx);
            case RANDOM_OF_MESSENGER -> new ConstFromRuleStrategy(rule); // نباید بعد از random بماند؛ fallback
        };
    }

    /* --------------------------------------------------------------- */
    /*                   Profile resolution helpers                     */
    /* --------------------------------------------------------------- */

    private static KinematicsProfile ensureProfile(PacketModel p) {
        KinematicsProfile existing = KinematicsRegistry.getProfile(p);
        if (existing != null) return existing;

        // اگر Protected
        if (p instanceof ProtectedPacket) {
            existing = KinematicsProfile.PROTECTED_SHADOW;
        }
        // اگر Confidential (دو نوع: عادی و VPN که شاید به‌صورت جداگانه set شوند)
        else if (p instanceof ConfidentialPacket conf) {
            // اگر سایز 6 (VPN) یا فلگ خاصی داشتی، می‌توانی تشخیص بدهی؛ فعلاً پیش‌فرض CONFIDENTIAL
            existing = KinematicsProfile.CONFIDENTIAL;
        } else {
            // براساس اندازه پکت (sizeUnits) از PacketType
            PacketType t = p.getType();
            int su = t.sizeUnits;
            if (su == 1)      existing = KinematicsProfile.MSG1;
            else if (su == 2) existing = KinematicsProfile.MSG2;
            else if (su == 3) existing = KinematicsProfile.MSG3;
            else               existing = KinematicsProfile.MSG2; // fallback
        }
        KinematicsRegistry.setProfile(p, existing);
        return existing;
    }

    /* =============================================================== */
    /*                   Internal Strategy Classes                     */
    /* =============================================================== */

    /** سرعت ثابت براساس rule.speedStart. */
    private static final class ConstFromRuleStrategy implements MotionStrategy {
        private final double speed;
        ConstFromRuleStrategy(MotionRule rule) {
            this.speed = Math.max(1.0, rule.speedStart);
        }
        @Override public void update(PacketModel packet, double dt) {
            WireModel wire = packet.getCurrentWire();
            if (wire == null) return;
            double len = wire.getLength();
            if (len <= 0) return;
            double dp = (speed * dt) / len;
            double next = packet.getProgress() + dp;
            if (next > 1.0) next = 1.0;
            packet.setProgress(next);
            packet.setSpeed(speed);
        }
    }

    /** شتاب خطی (rule.accel می‌تواند منفی باشد) با محدودیت ضریب سرعت. */
    private static final class LinearAccelStrategy implements MotionStrategy {
        private final double startSpeed;
        private final double accel;   // منفی یا مثبت
        private final double minMul;
        private final double maxMul;
        private boolean init = false;

        LinearAccelStrategy(MotionRule rule) {
            this.startSpeed = rule.speedStart;
            this.accel      = rule.accel;
            this.minMul     = rule.minMul;
            this.maxMul     = rule.maxMul;
        }

        @Override public void update(PacketModel packet, double dt) {
            WireModel wire = packet.getCurrentWire();
            if (wire == null) return;
            if (!init) {
                packet.setSpeed(startSpeed);
                init = true;
            }
            double base = packet.getBaseSpeed();
            double spd  = packet.getSpeed() + accel * dt;
            double minV = base * minMul;
            double maxV = base * maxMul;
            if (spd < minV) spd = minV;
            if (spd > maxV) spd = maxV;
            packet.setSpeed(spd);

            double dp = (spd * dt) / wire.getLength();
            double next = packet.getProgress() + dp;
            if (next > 1.0) next = 1.0;
            packet.setProgress(next);
        }
    }

    /** Wrapper روی AccelOnCurveStrategy موجود */
    private static final class CurveAccelWrapper implements MotionStrategy {
        private final AccelOnCurveStrategy delegate;
        CurveAccelWrapper(MotionRule rule) {
            this.delegate = new AccelOnCurveStrategy(rule.accel, rule.maxMul);
        }
        @Override public void update(PacketModel packet, double dt) {
            delegate.update(packet, dt);
        }
    }

    /** حفظ فاصله حداقلی با پکت‌های دیگر روی همان سیم. */
    private static final class KeepDistanceStrategy implements MotionStrategy {
        private final double baseSpeed;
        private final double minGapPx;

        KeepDistanceStrategy(MotionRule rule, double gap) {
            this.baseSpeed = rule.speedStart;
            this.minGapPx  = gap;
        }

        @Override public void update(PacketModel packet, double dt) {
            WireModel wire = packet.getCurrentWire();
            if (wire == null) return;
            double speed = baseSpeed;
            double myProg = packet.getProgress();
            double len = wire.getLength();

            for (PacketModel other : wire.getPackets()) {
                if (other == packet) continue;
                double dProg = other.getProgress() - myProg;
                if (dProg > 0) { // جلوتر است
                    double pxDist = dProg * len;
                    if (pxDist < minGapPx) {
                        speed *= pxDist / minGapPx; // کاهش سرعت
                    }
                }
            }
            if (speed < 10) speed = 10; // حداقل سرعت

            double dp = (speed * dt) / len;
            double next = myProg + dp;
            if (next > 1.0) next = 1.0;
            packet.setSpeed(speed);
            packet.setProgress(next);
        }
    }

    /** Drift: هر مسافت مشخص، offset کوچکی به موقعیت بصری پکت اعمال می‌کنیم. */
    private static final class DriftStrategy implements MotionStrategy {
        private final double baseSpeed;
        private final double stepDist;
        private final double offsetPx;
        private double traveledPx = 0;
        private boolean offsetSide = false;

        DriftStrategy(MotionRule rule, double stepDist, double offsetPx) {
            this.baseSpeed = rule.speedStart;
            this.stepDist  = stepDist;
            this.offsetPx  = offsetPx;
        }

        @Override public void update(PacketModel packet, double dt) {
            WireModel wire = packet.getCurrentWire();
            if (wire == null) return;
            double len = wire.getLength();
            double dp = (baseSpeed * dt) / len;
            double next = packet.getProgress() + dp;
            if (next > 1.0) next = 1.0;

            // track travelled distance
            double deltaPx = baseSpeed * dt;
            traveledPx += deltaPx;
            if (traveledPx >= stepDist) {
                traveledPx = 0;
                offsetSide = !offsetSide; // flip
            }

            packet.setSpeed(baseSpeed);
            packet.setProgress(next); // این خودش موقعیت را آپدیت می‌کند

            // offset بصری کوچک
            int sign = offsetSide ? 1 : -1;
            packet.setX(packet.getX() + (int) (sign * offsetPx));
            packet.setY(packet.getY() + (int) (sign * offsetPx * 0.2));
        }
    }
}

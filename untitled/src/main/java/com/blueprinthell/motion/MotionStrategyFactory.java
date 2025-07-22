package com.blueprinthell.motion;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.ProtectedPacket;
import com.blueprinthell.model.ConfidentialPacket;
import com.blueprinthell.model.WireModel;

import java.util.Objects;
import java.util.Random;

/**
 * <h2>MotionStrategyFactory</h2>
 * نقطهٔ مرکزی ساخت {@link com.blueprinthell.motion.MotionStrategy} بر اساس:
 * <ul>
 *   <li>پروفایل حرکتی ثبت‌شده در {@link KinematicsRegistry}</li>
 *   <li>سازگار/ناسازگار بودن پورت انتخاب شده</li>
 * </ul>
 *
 * <p>برای ساده‌سازی، چند استراتژی داخلی (LinearAccelStrategy، KeepDistanceStrategy، DriftStrategy)
 * در همین فایل به صورت کلاس‌های private static پیاده شده‌اند تا وابستگی اضافی ایجاد نشود.</p>
 */
public final class MotionStrategyFactory {

    private static final Random RAND = new Random();

    private MotionStrategyFactory() {}

    /**
     * می‌سازد و برمی‌گرداند Strategy مناسب. اگر پروفایل تعریف نشده باشد، تعیین و ثبت می‌کند.
     * @param packet      پکت
     * @param compatible  آیا پورت خروجی سازگار بود؟
     */
    public static MotionStrategy create(PacketModel packet, boolean compatible) {
        Objects.requireNonNull(packet, "packet");

        // 1) Ensure profile exists
        KinematicsProfile profile = ensureProfile(packet);
        ProfileParams params = profile.getParams();

        // 2) Resolve rule by compatibility
        MotionRule rule = compatible ? params.compatRule : params.incompatRule;

        // 3) Handle random messenger for protected shadow
        if (profile == KinematicsProfile.PROTECTED_SHADOW && params.randomMessengerProfile) {
            KinematicsProfile rndProf = KinematicsProfile.randomMessenger(RAND);
            KinematicsRegistry.setProfile(packet, rndProf);
            profile = rndProf;
            params = profile.getParams();
            rule = compatible ? params.compatRule : params.incompatRule;
        }

        // 4) Map rule.mode to concrete strategy
        return switch (rule.mode) {
            case CONST -> new ConstFromRuleStrategy(rule);
            case ACCEL -> new LinearAccelStrategy(rule, /*positive?*/true);
            case DECEL -> new LinearAccelStrategy(rule, /*positive?*/false);
            case CURVE_ACCEL -> new CurveAccelWrapper(rule);
            case KEEP_DISTANCE -> new KeepDistanceStrategy(rule, params.keepDistancePx);
            case DRIFT -> new DriftStrategy(rule, params.driftStepDistancePx, params.driftOffsetPx);
            case RANDOM_OF_MESSENGER -> new ConstFromRuleStrategy(rule); // shouldn't happen post-random, fallback
        };
    }

    /* --------------------------------------------------------------- */
    /*                   Profile resolution helpers                     */
    /* --------------------------------------------------------------- */

    private static KinematicsProfile ensureProfile(PacketModel p) {
        KinematicsProfile existing = KinematicsRegistry.getProfile(p);
        if (existing != null) return existing;

        // infer default by packet characteristics
        if (p instanceof ProtectedPacket) {
            existing = KinematicsProfile.PROTECTED_SHADOW;
        } else if (p instanceof ConfidentialPacket) {
            existing = KinematicsProfile.CONFIDENTIAL; // VPN نوع ۶ را هنوز جدا نکرده‌ایم
        } else {
            // Map by PacketType sizeUnits (1/2/3) – fallback MSG2
            PacketType t = p.getType();
            int su = t.sizeUnits;
            if (su == 1) existing = KinematicsProfile.MSG1;
            else if (su == 2) existing = KinematicsProfile.MSG2;
            else if (su == 3) existing = KinematicsProfile.MSG3;
            else existing = KinematicsProfile.MSG2;
        }
        KinematicsRegistry.setProfile(p, existing);
        return existing;
    }

    /* =============================================================== */
    /*                   Internal Strategy Classes                     */
    /* =============================================================== */

    /** ثابت با استفاده از speedStart در Rule. */
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

    /** شتاب خطی (مثبت یا منفی) با محدودیت سرعت نسبی. */
    private static final class LinearAccelStrategy implements MotionStrategy {
        private final double startSpeed;
        private final double accel; // can be negative
        private final double minMul;
        private final double maxMul;
        private boolean init = false;

        LinearAccelStrategy(MotionRule rule, boolean positiveBranch) {
            this.startSpeed = rule.speedStart;
            this.accel      = rule.accel; // rule.accel already sign-correct for ACCEL/DECEL
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

    /** Wrapper روی AccelOnCurveStrategy موجود، تنظیم پارامترها از Rule/Params. */
    private static final class CurveAccelWrapper implements MotionStrategy {
        private final AccelOnCurveStrategy delegate;
        CurveAccelWrapper(MotionRule rule) {
            // rule.accel = factor, rule.maxMul = max multiplier
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
            // Scan neighbours ahead on the same wire to keep distance
            double myProg = packet.getProgress();
            double len = wire.getLength();
            for (PacketModel other : wire.getPackets()) {
                if (other == packet) continue;
                double dProg = other.getProgress() - myProg;
                if (dProg > 0) { // ahead
                    double pxDist = dProg * len;
                    if (pxDist < minGapPx) {
                        // slowdown proportionally
                        speed *= pxDist / minGapPx;
                    }
                }
            }
            if (speed < 10) speed = 10; // clamp
            double dp = (speed * dt) / len;
            double next = myProg + dp;
            if (next > 1.0) next = 1.0;
            packet.setSpeed(speed);
            packet.setProgress(next);
        }
    }

    /** Drift: هر فاصلهٔ معین روی سیم یک offset کوچک از مسیر اعمال می‌کند (فقط بصری). */
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

            // track travelled pixels
            double deltaPx = baseSpeed * dt;
            traveledPx += deltaPx;
            if (traveledPx >= stepDist) {
                traveledPx = 0;
                offsetSide = !offsetSide; // flip side
            }

            packet.setSpeed(baseSpeed);
            packet.setProgress(next);

            // Apply visual drift by adjusting X/Y after position update
            // NOTE: PacketModel updates its position in setProgress; we need manual offset:
            int sign = offsetSide ? 1 : -1;
            packet.setX(packet.getX() + (int)(sign * offsetPx));
            packet.setY(packet.getY() + (int)(sign * offsetPx * 0.2)); // tiny vertical tweak
        }
    }
}

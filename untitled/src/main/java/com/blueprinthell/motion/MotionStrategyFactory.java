package com.blueprinthell.motion;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.ProtectedPacket;
import com.blueprinthell.model.ConfidentialPacket;
import com.blueprinthell.model.PacketOps;
import com.blueprinthell.model.WireModel;

import java.util.*;
import com.blueprinthell.config.Config;
import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargePacket;

public final class MotionStrategyFactory {

    private static final Random RAND = new Random();

    private MotionStrategyFactory() {}

    public static MotionStrategy create(PacketModel packet, boolean compatible) {
        Objects.requireNonNull(packet, "packet");

        if (PacketOps.isConfidentialVpn(packet)) {
            KinematicsRegistry.setProfile(packet, KinematicsProfile.CONFIDENTIAL_VPN);
            return new KeepDistanceStrategy(
                    new MotionRule(MotionMode.KEEP_DISTANCE, Config.CONF_VPN_SPEED, 0, 1.0, 1.0),
                    Config.CONF_VPN_KEEP_DIST_PX
            );
        }

        // برای پکت‌های محرمانه عادی
        if (packet instanceof ConfidentialPacket && !PacketOps.isConfidentialVpn(packet)) {
            KinematicsRegistry.setProfile(packet, KinematicsProfile.CONFIDENTIAL);
            return new ConstantSpeedStrategy(Config.CONF_SPEED);
        }

        if (packet instanceof ProtectedPacket) {
            KinematicsProfile randomProfile = KinematicsProfile.randomMessenger(RAND);
            MotionRule rule = profileToRule(randomProfile, compatible);
            double startMul = packet.consumeStartSpeedMul();
            if (startMul != 1.0) {
                rule = scaleStartSpeed(rule, startMul);
            }
            MotionStrategy base = buildStrategyFromRule(rule, randomProfile.getParams());
            return new ApproachLimiterWrapper(overrideForLongWire(packet, rule, base));
        }

        if (packet instanceof LargePacket lp) {
            if (lp.getOriginalSizeUnits() == 10) {
                // حرکت drift برای پکت 10
                return new DriftMotionStrategy(Config.L10_BASE_SPEED,
                        Config.L10_DRIFT_STEP_PX,
                        Config.L10_DRIFT_OFFSET_PX);
            } else if (lp.getOriginalSizeUnits() == 8) {
                // حرکت با شتاب در انحنا برای پکت 8
                return new AccelOnCurveStrategy(Config.L8_CURVE_ACCEL, Config.L8_MAX_MUL);
            }
        }

        KinematicsProfile profile = ensureProfile(packet);
        ProfileParams params = profile.getParams();
        MotionRule rule = compatible ? params.compatRule : params.incompatRule;

        if (profile == KinematicsProfile.PROTECTED_SHADOW && params.randomMessengerProfile) {
            KinematicsProfile rndProf = KinematicsProfile.randomMessenger(RAND);
            KinematicsRegistry.setProfile(packet, rndProf);
            profile = rndProf;
            params = rndProf.getParams();
            rule = compatible ? params.compatRule : params.incompatRule;
        }

        double startMul = packet.consumeStartSpeedMul();
        if (startMul != 1.0) {
            rule = scaleStartSpeed(rule, startMul);
        }

        MotionStrategy base = buildStrategyFromRule(rule, params);
        return new ApproachLimiterWrapper(overrideForLongWire(packet, rule, base));
    }

    private static MotionStrategy overrideForLongWire(PacketModel packet, MotionRule rule, MotionStrategy base) {
        WireModel wire = packet.getCurrentWire();
        boolean isLongWire = (wire != null) && (wire.getLength() >= Config.LONG_WIRE_THRESHOLD_PX);
        if (!isLongWire) {
            return base;
        }
        if (packet instanceof ConfidentialPacket && PacketOps.isConfidentialVpn(packet)) {
            return base;
        }
        switch (rule.mode) {
            case KEEP_DISTANCE:
            case DRIFT:
                return base;
            default:
                // continue
        }
        if (packet instanceof BitPacket) {
            return base;
        }
        // Type-aware: for blue (SQUARE => MSG2) use gentler accel/ceiling
        KinematicsProfile prof = ensureProfile(packet);
        double accel = Config.LONG_WIRE_ACCEL;
        double maxMul = Config.LONG_WIRE_MAX_MUL;
        if (prof == KinematicsProfile.MSG2) {
            accel = Config.LONG_WIRE_ACCEL_BLUE;
            maxMul = Config.LONG_WIRE_MAX_MUL_BLUE;
        }
        return new AccelOnCurveStrategy(accel, maxMul);
    }

    private static MotionRule scaleStartSpeed(MotionRule rule, double mul) {
        if (mul == 1.0) return rule;
        double s = rule.speedStart * mul;

        return switch (rule.mode) {
            case CONST -> MotionRule.constSpeed(s);
            case ACCEL -> MotionRule.accel(s, rule.accel, rule.minMul, rule.maxMul);
            case DECEL -> MotionRule.decel(s, Math.abs(rule.accel), rule.minMul, rule.maxMul);
            case CURVE_ACCEL -> MotionRule.curveAccel(s, rule.accel, rule.maxMul);
            case KEEP_DISTANCE -> MotionRule.keepDistance(s);
            case DRIFT -> MotionRule.drift(s);
            case RANDOM_OF_MESSENGER -> rule;
        };
    }

    private static MotionRule profileToRule(KinematicsProfile profile, boolean compatible) {
        ProfileParams params = profile.getParams();
        return compatible ? params.compatRule : params.incompatRule;
    }

    private static MotionStrategy buildStrategyFromRule(MotionRule rule, ProfileParams params) {
        return switch (rule.mode) {
            case CONST -> new ConstFromRuleStrategy(rule);
            case ACCEL -> new LinearAccelStrategy(rule);
            case DECEL -> new LinearAccelStrategy(rule);
            case CURVE_ACCEL -> new CurveAccelWrapper(rule);
            case KEEP_DISTANCE -> new KeepDistanceStrategy(rule, params.keepDistancePx);
            case DRIFT -> new DriftStrategy(rule, params.driftStepDistancePx, params.driftOffsetPx);
            case RANDOM_OF_MESSENGER -> new ConstFromRuleStrategy(rule);
        };
    }

    private static KinematicsProfile ensureProfile(PacketModel p) {
        KinematicsProfile existing = KinematicsRegistry.getProfile(p);
        if (existing != null) {
            return existing;
        }

        if (p instanceof ProtectedPacket) {
            existing = KinematicsProfile.PROTECTED_SHADOW;
        } else if (p instanceof ConfidentialPacket) {
            existing = KinematicsProfile.CONFIDENTIAL;
        } else {
            int su = p.getType().sizeUnits;
            if (su == 1) {
                existing = KinematicsProfile.MSG1;
            } else if (su == 2) {
                existing = KinematicsProfile.MSG2;
            } else if (su == 3) {
                existing = KinematicsProfile.MSG3;
            } else {
                existing = KinematicsProfile.MSG2;
            }
        }

        KinematicsRegistry.setProfile(p, existing);
        return existing;
    }

    private static final class ConstFromRuleStrategy implements MotionStrategy {
        private final double speed;

        ConstFromRuleStrategy(MotionRule rule) {
            this.speed = Math.max(1.0, rule.speedStart);
        }

        @Override
        public void update(PacketModel packet, double dt) {
            WireModel wire = packet.getCurrentWire();
            if (wire == null) return;
            double len = wire.getLength();
            if (len <= 0) return;

            double effective = speed;

            // Long-wire accel فقط برای پیام‌رسان‌ها
            if (PacketOps.isMessenger(packet) && len >= Config.LONG_WIRE_THRESHOLD_PX) {
                double v0   = Math.max(packet.getSpeed(), speed);
                double vmax = packet.getBaseSpeed() * Config.LONG_WIRE_MAX_SPEED_MUL;
                double v1   = Math.min(v0 + Config.LONG_WIRE_ACCEL * dt, vmax);
                effective   = Math.max(speed, v1);
                packet.setSpeed(effective);
            } else {
                // *** نکته‌ی مهم: برای پکت محرمانه سرعت از بیرون قابل تحمیل باشد (throttle) ***
                if (packet instanceof com.blueprinthell.model.ConfidentialPacket) {
                    // اگر قبلاً throttled شده بود همان را نگه دار، وگرنه روی سرعت قاعده بیفت
                    double ext = packet.getSpeed();
                    effective  = (ext > 0) ? ext : speed;
                    packet.setSpeed(effective);
                } else {
                    packet.setSpeed(speed);
                }
            }

            double dp = (effective * dt) / len;
            double next = packet.getProgress() + dp;
            if (next > 1.0) next = 1.0;
            packet.setProgress(next);

            // همخوانی state داخلی مدل با سرعتی که واقعاً استفاده شد
            packet.setSpeed(effective);
        }

    }

    private static final class LinearAccelStrategy implements MotionStrategy {
        private final double startSpeed;
        private final double accel;
        private final double minMul;
        private final double maxMul;
        private boolean init = false;

        LinearAccelStrategy(MotionRule rule) {
            this.startSpeed = rule.speedStart;
            this.accel = rule.accel;
            this.minMul = rule.minMul;
            this.maxMul = rule.maxMul;
        }

        @Override
        public void update(PacketModel packet, double dt) {
            WireModel wire = packet.getCurrentWire();
            if (wire == null) return;
            if (!init) {
                packet.setSpeed(startSpeed);
                init = true;
            }
            double base = packet.getBaseSpeed();
            double spd = packet.getSpeed() + accel * dt;
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

    private static final class CurveAccelWrapper implements MotionStrategy {
        private final AccelOnCurveStrategy delegate;

        CurveAccelWrapper(MotionRule rule) {
            this.delegate = new AccelOnCurveStrategy(rule.accel, rule.maxMul);
        }

        @Override
        public void update(PacketModel packet, double dt) {
            delegate.update(packet, dt);
        }
    }


    private static final class KeepDistanceStrategy implements MotionStrategy {
        private final double baseSpeed;   // px/s
        private final double minGapPx;    // حداقل فاصله‌ی مطلوب روی سیم (px)

        private static final double K_FWD  = 2.0;   // وقتی پشت سر ما خیلی نزدیک است → جلو رفتن
        private static final double K_BACK = 2.0;   // وقتی جلوی ما خیلی نزدیک است → عقب‌رفتن
        private static final double EPS    = 2.0;   // هیسترزیس کوچک برای جلوگیری از نوسان
        private static final double VMAX_MUL = 2.0; // سقف |سرعت| نسبت به baseSpeed

        KeepDistanceStrategy(MotionRule rule, double gap) {
            this.baseSpeed = rule.speedStart;
            this.minGapPx  = gap;
        }

        @Override
        public void update(PacketModel packet, double dt) {
            WireModel wire = packet.getCurrentWire();
            if (wire == null) return;

            final double len    = wire.getLength();
            if (len <= 0) return;

            final double myProg = packet.getProgress();
            double speed = baseSpeed; // پیش‌فرض: حرکت رو به جلو با سرعت پایه

            // فقط روی همان سیم بررسی می‌کنیم
            double nearestFrontPx = Double.POSITIVE_INFINITY; // نزدیک‌ترین پکت جلوی ما
            double nearestBackPx  = Double.POSITIVE_INFINITY; // نزدیک‌ترین پکت پشت سر ما

            // اسنپ‌شات برای جلوگیری از ConcurrentModification
            List<PacketModel> snapshot = new ArrayList<>(wire.getPackets());
            for (PacketModel other : snapshot) {
                if (other == packet) continue;

                double dProg  = other.getProgress() - myProg;
                double pxDist = Math.abs(dProg) * len;

                if (dProg > 0) { // جلوتر از ما
                    if (pxDist < nearestFrontPx) nearestFrontPx = pxDist;
                } else if (dProg < 0) { // عقب‌تر از ما
                    if (pxDist < nearestBackPx) nearestBackPx = pxDist;
                }
            }

            // اگر پکت در حالت returning است، مانند قبل هیچ تنظیم فاصله‌ای اعمال نکنیم
            boolean active = !packet.isReturning();

            if (active) {
                boolean tooCloseFront = nearestFrontPx < (minGapPx - EPS);
                boolean tooCloseBack  = nearestBackPx  < (minGapPx - EPS);

                if (tooCloseFront && tooCloseBack) {
                    // بین دو پکت گیر افتاده‌ایم → بهترین کار توقف موقت است
                    speed = 0.0;
                } else if (tooCloseFront) {
                    // پکت جلویی زیادی نزدیک است → کمی عقب برو
                    double err = (minGapPx - nearestFrontPx); // px
                    speed = -K_BACK * err; // منفی یعنی عقب‌رفتن
                } else if (tooCloseBack) {
                    // پکت پشتی زیادی نزدیک است → کمی جلو برو
                    double err = (minGapPx - nearestBackPx); // px
                    speed =  K_FWD * err; // مثبت یعنی جلو رفتن
                } else {
                    // فاصله‌ها مناسب‌اند → با سرعت پایه به جلو حرکت کن
                    speed = baseSpeed;
                }
            } else {
                // returning: رفتار قبلی را حفظ می‌کنیم (حرکت با سرعت پایه به جلو)
                speed = baseSpeed;
            }

            // کلمپ سرعت به بازه‌ی مجاز
            double vmax = Math.max(0.0, baseSpeed * VMAX_MUL);
            if (Math.abs(speed) > vmax) {
                speed = Math.copySign(vmax, speed);
            }

            // پیشروی روی سیم؛ جهت می‌تواند منفی باشد (عقب‌گرد)
            double dp   = (speed * dt) / len;
            double next = myProg + dp;

            if (next < 0.0) next = 0.0;
            if (next > 1.0) next = 1.0;

            // برای نمایش/HUD سرعت را قدرمطلق می‌گذاریم؛ جهت را با dp/پیشروی کنترل می‌کنیم
            packet.setSpeed(Math.abs(speed));
            packet.setProgress(next);
        }
    }

    private static final class DriftStrategy implements MotionStrategy {
        private final double baseSpeed;
        private final double stepDist;
        private final double offsetPx;
        private double traveledPx = 0;
        private boolean offsetSide = false;

        DriftStrategy(MotionRule rule, double stepDist, double offsetPx) {
            this.baseSpeed = rule.speedStart;
            this.stepDist = stepDist;
            this.offsetPx = offsetPx;
        }

        @Override
        public void update(PacketModel packet, double dt) {
            WireModel wire = packet.getCurrentWire();
            if (wire == null) return;
            double len = wire.getLength();
            double dp = (baseSpeed * dt) / len;
            double next = packet.getProgress() + dp;
            if (next > 1.0) next = 1.0;

            double deltaPx = baseSpeed * dt;
            traveledPx += deltaPx;
            if (traveledPx >= stepDist) {
                traveledPx = 0;
                offsetSide = !offsetSide; // flip
            }

            packet.setProgress(next);

            int sign = offsetSide ? 1 : -1;
            packet.setX(packet.getX() + (int) (sign * offsetPx));
            packet.setY(packet.getY() + (int) (sign * offsetPx * 0.2));
        }
    }

    private static final class ApproachLimiterWrapper implements MotionStrategy {
        private final MotionStrategy delegate;
        ApproachLimiterWrapper(MotionStrategy d) { this.delegate = d; }
        @Override
        public void update(PacketModel packet, double dt) {
            if (delegate != null) delegate.update(packet, dt);
            WireModel wire = packet.getCurrentWire();
            if (wire == null) return;
            double prog = packet.getProgress();
            if (prog >= Config.APPROACH_ZONE_START) {
                WireModel w = packet.getCurrentWire();
                boolean isLong = (w != null) && (w.getLength() >= Config.LONG_WIRE_THRESHOLD_PX);

                double base  = packet.getBaseSpeed();
                double capMul = isLong ? Config.LONG_WIRE_MAX_SPEED_MUL   // مثلاً 3.5
                        : Config.APPROACH_MAX_MUL;         // مثلاً 1.5
                double cap = base * capMul;

                if (packet.getSpeed() > cap) {
                    packet.setSpeed(cap);
                }
            }

        }
    }
}

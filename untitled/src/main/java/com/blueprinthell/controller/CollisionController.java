package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.media.ResourceManager;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.motion.KinematicsProfile;
import com.blueprinthell.motion.KinematicsRegistry;

import javax.sound.sampled.Clip;
import java.awt.*;
import java.util.*;
import java.util.List;


public class CollisionController implements Updatable {
    private final List<WireModel> wires;
    private final SpatialHashGrid<PacketModel> grid;
    private final PacketLossModel lossModel;

    private Map<PortModel, SystemBoxModel> portToBox = null;

    private static final int    CELL_SIZE        = 50;
    private static final double COLLISION_RADIUS = 18.0;
    static final double SPAWN_COLLISION_GUARD = 0.12;


    private static final double NOISE_INCREMENT = 0.5;
    private static final double MAX_NOISE       = 5.0;

    private static final double GREEN_GREEN_COLLISION_NOISE = 0.15;
    private static final double GREEN_COLLISION_NOISE_PERCENT = 0.10;
    private static final double MIN_GREEN_COLLISION_NOISE    = 0.10;
    // Tamed wave
    private static final double IMPACT_RADIUS   = 45.0;
    private static final double IMPACT_STRENGTH = 0.35;

    private boolean collisionsEnabled = true;
    private boolean impactWaveEnabled = true;

    // بالای کلاس
    private static final long RETURN_COLLISION_COOLDOWN_MS = 100; // مثلاً 100 میلی‌ثانیه کول‌داون
    private final Map<PacketModel, Long> returnCollisionCooldowns = new WeakHashMap<>();

    public CollisionController(List<WireModel> wires, PacketLossModel lossModel) {
        this(wires, lossModel, null);
    }

    public CollisionController(List<WireModel> wires,
                               PacketLossModel lossModel,
                               Map<PortModel, SystemBoxModel> portToBox) {
        this.wires     = wires;
        this.lossModel = lossModel;
        this.grid      = new SpatialHashGrid<>(CELL_SIZE);
        if (portToBox != null) {
            this.portToBox = portToBox;
        }
    }

    public void setPortToBoxMap(Map<PortModel, SystemBoxModel> map) {
        this.portToBox = (map != null) ? map : Collections.emptyMap();
    }

    @Override
    public void update(double dt) {
        List<Point> impacts = Collections.emptyList();
        if (collisionsEnabled) {
            impacts = performCollisionPass();
        }
        if (impactWaveEnabled && !impacts.isEmpty()) {
            propagateImpactWaves(impacts);
        }
        handleNoiseRemovalAndSound();
    }


    private List<Point> performCollisionPass() {
        List<Point> impactPoints = new ArrayList<>();
        grid.clear();

        long now = System.currentTimeMillis();

        for (WireModel w : wires) {
            for (PacketModel p : w.getPackets()) {
                if (isShielded(p)) continue;
                Point pos = w.pointAt(p.getProgress());
                grid.insert(pos.x, pos.y, p);
            }
        }

        Set<PacketModel> processed = new HashSet<>();

        for (WireModel w : wires) {
            for (PacketModel p : new ArrayList<>(w.getPackets())) {
                if (processed.contains(p) || p.getProgress() <= 0) continue;
                if (isShielded(p)) continue;

                // کول‌داونِ برگشت: اگر پکت در حال برگشت است و هنوز کول‌داون دارد، نادیده‌اش بگیر
                if (p.isReturning()) {
                    Long cooldownUntil = returnCollisionCooldowns.get(p);
                    if (cooldownUntil != null && now < cooldownUntil) {
                        continue;
                    } else if (cooldownUntil != null && now >= cooldownUntil) {
                        returnCollisionCooldowns.remove(p);
                    }
                }

                Point pPos = w.pointAt(p.getProgress());
                for (PacketModel other : grid.retrieve(pPos.x, pPos.y)) {
                    if (other == p || processed.contains(other)) continue;
                    if (isShielded(other)) continue;







                    if (p instanceof BitPacket || p instanceof LargePacket
                            || other instanceof BitPacket || other instanceof LargePacket) {
                        continue;
                    }









                    // کول‌داونِ برگشت برای other
                    if (other.isReturning()) {
                        Long cooldownUntilOther = returnCollisionCooldowns.get(other);
                        if (cooldownUntilOther != null && now < cooldownUntilOther) {
                            continue;
                        } else if (cooldownUntilOther != null && now >= cooldownUntilOther) {
                            returnCollisionCooldowns.remove(other);
                        }
                    }

                    // اگر هر کدام کول‌دان دارند، برخورد را نادیده بگیر
                    if (p.getCollisionCooldown() > 0 || other.getCollisionCooldown() > 0) continue;

                    WireModel ow = other.getCurrentWire();
                    Point oPos = ow.pointAt(other.getProgress());
                    double dx = pPos.x - oPos.x;
                    double dy = pPos.y - oPos.y;

                    boolean sameWire      = (w == ow);
                    boolean bothOutward   = !p.isReturning() && !other.isReturning();
                    boolean bothReturning =  p.isReturning() &&  other.isReturning();

                    boolean nearSrcOutTogether = sameWire && bothOutward &&
                            (p.getProgress() <= SPAWN_COLLISION_GUARD && other.getProgress() <= SPAWN_COLLISION_GUARD);

                    boolean nearDstRetTogether = sameWire && bothReturning &&
                            (p.getProgress() >= 1.0 - SPAWN_COLLISION_GUARD && other.getProgress() >= 1.0 - SPAWN_COLLISION_GUARD);

                    // دو حالت متقارنِ جاافتاده:
                    boolean nearSrcRetTogether = sameWire && bothReturning &&
                            (p.getProgress() <= SPAWN_COLLISION_GUARD && other.getProgress() <= SPAWN_COLLISION_GUARD);

                    boolean nearDstOutTogether = sameWire && bothOutward &&
                            (p.getProgress() >= 1.0 - SPAWN_COLLISION_GUARD && other.getProgress() >= 1.0 - SPAWN_COLLISION_GUARD);

                    if (nearSrcOutTogether || nearDstRetTogether || nearSrcRetTogether || nearDstOutTogether) {
                        continue; // اصلاً بررسی برخورد را رد کن
                    }

                    if (Math.hypot(dx, dy) <= COLLISION_RADIUS) {
                        boolean pIsMsg1     = isMsg1(p);
                        boolean otherIsMsg1 = isMsg1(other);

                        boolean lossSfxPlayed = false;

                        if (pIsMsg1 && !otherIsMsg1) {
                            bounceToSource(p, w);
                            // اگر پکت درحال برگشت شد، کول‌داون بگذار
                            if (p.isReturning()) {
                                returnCollisionCooldowns.put(p, now + RETURN_COLLISION_COOLDOWN_MS);
                            }
                            double inc = Math.max(MIN_GREEN_COLLISION_NOISE, other.getNoise() * GREEN_COLLISION_NOISE_PERCENT);
                            other.increaseNoise(inc);
                            processed.add(p);
                            processed.add(other);

                        } else if (!pIsMsg1 && otherIsMsg1) {
                            bounceToSource(other, ow);
                            if (other.isReturning()) {
                                returnCollisionCooldowns.put(other, now + RETURN_COLLISION_COOLDOWN_MS);
                            }
                            double inc = Math.max(MIN_GREEN_COLLISION_NOISE, p.getNoise() * GREEN_COLLISION_NOISE_PERCENT);
                            p.increaseNoise(inc);
                            processed.add(p);
                            processed.add(other);

                        } else if (pIsMsg1 && otherIsMsg1) {

                            if (!sameWire) {
                                // روی دو سیم متفاوت: هر دو به مبدا برگردند
                                bounceToSource(p, w);
                                if (p.isReturning()) {
                                    returnCollisionCooldowns.put(p, now + RETURN_COLLISION_COOLDOWN_MS);
                                }
                                bounceToSource(other, ow);
                                if (other.isReturning()) {
                                    returnCollisionCooldowns.put(other, now + RETURN_COLLISION_COOLDOWN_MS);
                                }
                            } else {
                                // روی یک سیم: فقط نویز + یکی مکث کند تا دیگری جلو بیفتد
                                p.increaseNoise(GREEN_GREEN_COLLISION_NOISE);
                                other.increaseNoise(GREEN_GREEN_COLLISION_NOISE);

                                // رهبر/دنباله‌دار را نسبت به جهت حرکت تعیین کن
                                double ppPos = p.isReturning() ? (1.0 - p.getProgress()) : p.getProgress();
                                double ooPos = other.isReturning() ? (1.0 - other.getProgress()) : other.getProgress();

                                PacketModel leader   = (ppPos >= ooPos) ? p     : other;
                                PacketModel follower = (ppPos >= ooPos) ? other : p;

                                // دنباله‌دار کمی صبر کند
                                follower.setHoldWhileCooldown(true);
                                follower.setCollisionCooldown(Config.CIRCLE_YIELD_WAIT); // ~0.30s

                                // نیشگونِ پیشروی برای رفع هم‌پوشانی و جلوگیری از برخورد فوری
                                double delta = follower.isReturning() ? +0.003 : -0.003;
                                double np = Math.max(0.0, Math.min(1.0, follower.getProgress() + delta));
                                follower.setProgress(np);
                            }

                            // این دو خط برای جلوگیری از رسیدگی دوباره به همین جفت در همین پاس
                            processed.add(p);
                            processed.add(other);

                        } else {
                            w.removePacket(p);
                            ow.removePacket(other);
                            lossModel.incrementBy(2);
                            if (!lossSfxPlayed) { playLossSfxOnce(); lossSfxPlayed = true; }
                            processed.add(p);
                            processed.add(other);
                        }

                        int ix = (pPos.x + oPos.x) / 2;
                        int iy = (pPos.y + oPos.y) / 2;
                        impactPoints.add(new Point(ix, iy));
                    }
                }
            }
        }

        return impactPoints;
    }


    private boolean isShielded(PacketModel p) {
        return (p instanceof ProtectedPacket pp) && pp.getShield() > 0;
    }

    private boolean isMsg1(PacketModel p) {
        KinematicsProfile prof = KinematicsRegistry.getProfile(p);
        return prof == KinematicsProfile.MSG1;
    }

    public void bounceToSource(PacketModel p, WireModel w) {
        if (w == null || p == null) return;
        p.setReturning(true);
        p.setAcceleration(0.0);
        p.setCollisionCooldown(0.20);      // ~200ms
        // کمی فاصلهٔ موقعیتی ایجاد کن تا هم‌پوشانی پیکسل-به-پیکسل حذف شود
        p.setProgress(Math.min(1.0, p.getProgress() + 0.002));
        // Optional: reset noise so it won't get removed while returning
        // p.resetNoise();
    }


    private void propagateImpactWaves(List<Point> impacts) {
        for (Point pt : impacts) {
            for (PacketModel p : grid.retrieve(pt.x, pt.y)) {
                Point pPos = p.getCurrentWire().pointAt(p.getProgress());
                double dx = pPos.x - pt.x;
                double dy = pPos.y - pt.y;
                double dist = Math.hypot(dx, dy);
                if (dist <= IMPACT_RADIUS) {
                    if (isMsg1(p) && p.isReturning()) continue;
                    double waveNoise = IMPACT_STRENGTH * (1.0 - dist / IMPACT_RADIUS);
                    p.increaseNoise(waveNoise);
                }
            }
        }
    }


    private void handleNoiseRemovalAndSound() {
        boolean played = false;
        for (WireModel w : wires) {
            List<PacketModel> doomed = new ArrayList<>();
            for (PacketModel p : w.getPackets()) {
                if (!(p instanceof ProtectedPacket) && p.getNoise() >= MAX_NOISE) {
                    if (isMsg1(p) && p.isReturning()) {
                        continue;
                    }
                    doomed.add(p);
                }
            }
            for (PacketModel p : doomed) {
                w.removePacket(p);
                lossModel.increment();
                played = true;
            }
        }
        if (played) playImpactSound();
    }


    private void playImpactSound() {
        try {
            Clip c = ResourceManager.INSTANCE.getClip("impact_thud.wav");
            c.stop(); c.setFramePosition(0); c.start();
        } catch (Exception ignored) {}
    }

    public void pauseCollisions()  { this.collisionsEnabled = false; }
    public void resumeCollisions() { this.collisionsEnabled = true;  }

    public void setImpactWaveEnabled(boolean enabled) { this.impactWaveEnabled = enabled; }

    private void playLossSfxOnce() {
        try {
            Clip c = ResourceManager.INSTANCE.getClip("impact_thud.wav");
            if (c != null) {
                c.stop();
                c.setFramePosition(0);
                c.start();
            }
        } catch (Exception ignore) {}
    }
}

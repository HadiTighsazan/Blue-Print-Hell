package com.blueprinthell.controller;

import com.blueprinthell.media.ResourceManager;
import com.blueprinthell.model.*;
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

    private static final double NOISE_INCREMENT = 0.5;
    private static final double MAX_NOISE       = 5.0;

    private static final double IMPACT_RADIUS    = 100.0;
    private static final double IMPACT_STRENGTH  = 1.0;

    private boolean collisionsEnabled = true;
    private boolean impactWaveEnabled = true;

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

        // Broad phase: همهٔ بسته‌ها رو وارد گرید می‌کنیم (به‌جز محافظت‌شده‌ها)
        for (WireModel w : wires) {
            for (PacketModel p : w.getPackets()) {
                if (isShielded(p)) continue;
                Point pos = w.pointAt(p.getProgress());
                grid.insert(pos.x, pos.y, p);
            }
        }

        Set<PacketModel> processed = new HashSet<>();

        // Narrow phase: برخوردها
        for (WireModel w : wires) {
            for (PacketModel p : new ArrayList<>(w.getPackets())) {
                if (processed.contains(p) || p.getProgress() <= 0) continue;
                if (isShielded(p)) continue;

                Point pPos = w.pointAt(p.getProgress());
                for (PacketModel other : grid.retrieve(pPos.x, pPos.y)) {
                    if (other == p || processed.contains(other)) continue;
                    if (isShielded(other)) continue;

                    WireModel ow = other.getCurrentWire();
                    Point oPos = ow.pointAt(other.getProgress());
                    double dx = pPos.x - oPos.x;
                    double dy = pPos.y - oPos.y;

                    if (Math.hypot(dx, dy) <= COLLISION_RADIUS) {
                        boolean pIsMsg1     = isMsg1(p);
                        boolean otherIsMsg1 = isMsg1(other);

                        // برای MSG1 (سبز): برگشت واقعی روی سیم
                        if (pIsMsg1)     bounceToSource(p, w);
                        if (otherIsMsg1) bounceToSource(other, ow);

                        // به غیر از MSG1، نویز برخورد را اعمال کن
                        if (!pIsMsg1)     p.increaseNoise(NOISE_INCREMENT);
                        if (!otherIsMsg1) other.increaseNoise(NOISE_INCREMENT);

                        processed.add(p);
                        processed.add(other);

                        // نقطهٔ اثر برای موج ضربه
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
        // برگشت روی همان سیم: از سیم حذف نمی‌کنیم؛ progress رو به عقب می‌رود
        p.setReturning(true);
        // کنترل صاف‌تر برگشت
        p.setAcceleration(0.0);
        // اگر لازم داری، نویز را هم ریست کن تا در مسیر برگشت حذف نشود:
        // p.resetNoise();  // اختیاری
    }


    private void propagateImpactWaves(List<Point> impacts) {
        for (Point pt : impacts) {
            for (PacketModel p : grid.retrieve(pt.x, pt.y)) {
                Point pPos = p.getCurrentWire().pointAt(p.getProgress());
                double dx = pPos.x - pt.x;
                double dy = pPos.y - pt.y;
                double dist = Math.hypot(dx, dy);
                if (dist <= IMPACT_RADIUS) {
                    // --- NEW: موجِ برخورد به MSG1 که در حال برگشت است اعمال نشود ---
                    if (isMsg1(p) && p.isReturning()) continue;  // جلوگیری از حذف ناخواسته
                    // -------------------------------------------------------------------
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
                    // --- NEW: MSG1 در حال برگشت را حذف نکن ---
                    if (isMsg1(p) && p.isReturning()) {
                        continue; // معاف از حذف تا به مبدأ برسد
                    }
                    // -----------------------------------------
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
}

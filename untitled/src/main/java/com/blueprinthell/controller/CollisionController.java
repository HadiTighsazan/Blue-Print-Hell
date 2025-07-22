package com.blueprinthell.controller;

import com.blueprinthell.media.ResourceManager;
import com.blueprinthell.model.*;
import com.blueprinthell.motion.KinematicsProfile;
import com.blueprinthell.motion.KinematicsRegistry;

import javax.sound.sampled.Clip;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Detects collisions between packets using a spatial-hash grid; increases noise,
 * propagates impact waves, removes over‑noised packets, **و** پکت‌های MSG1 را پس از برخورد
 * به باکس مبدأ بازمی‌گرداند (Bounce).
 */
public class CollisionController implements Updatable {
    private final List<WireModel> wires;
    private final SpatialHashGrid<PacketModel> grid;
    private final PacketLossModel lossModel;

    /** Map پورت → باکس برای بازگرداندن پکت‌ها */
    private Map<PortModel, SystemBoxModel> portToBox = Collections.emptyMap();

    /* ---- Tunables ---- */
    private static final int    CELL_SIZE        = 50;
    private static final double COLLISION_RADIUS = 18.0;
    private static final double NOISE_INCREMENT  = 0.5;
    private static final double MAX_NOISE        = 5.0;
    private static final double IMPACT_RADIUS    = 100.0;
    private static final double IMPACT_STRENGTH  = 1.0;

    /* ---- Runtime Flags ---- */
    private boolean collisionsEnabled = true;
    private boolean impactWaveEnabled = true;

    /* ------------------------- Ctors ------------------------- */
    public CollisionController(List<WireModel> wires, PacketLossModel lossModel) {
        this(wires, lossModel, Collections.emptyMap());
    }

    public CollisionController(List<WireModel> wires,
                               PacketLossModel lossModel,
                               Map<PortModel, SystemBoxModel> portToBox) {
        this.wires     = wires;
        this.lossModel = lossModel;
        this.grid      = new SpatialHashGrid<>(CELL_SIZE);
        if (portToBox != null) this.portToBox = portToBox;
    }

    /** تزریق/به‌روزرسانی نقشهٔ پورت→باکس در زمان اجرا (مثلاً پس از rebuild). */
    public void setPortToBoxMap(Map<PortModel, SystemBoxModel> map) {
        this.portToBox = (map != null) ? map : Collections.emptyMap();
    }

    /* ------------------------- Update ------------------------- */
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

    /* -------------------- Collision pass --------------------- */
    private List<Point> performCollisionPass() {
        List<Point> impactPoints = new ArrayList<>();
        grid.clear();
        // Broad phase
        for (WireModel w : wires) {
            for (PacketModel p : w.getPackets()) {
                if (isShielded(p)) continue;
                Point pos = w.pointAt(p.getProgress());
                grid.insert(pos.x, pos.y, p);
            }
        }
        // Narrow phase
        Set<PacketModel> processed = new HashSet<>();
        for (WireModel w : wires) {
            for (PacketModel p : new ArrayList<>(w.getPackets())) {
                if (processed.contains(p) || p.getProgress() <= 0) continue;
                if (isShielded(p)) continue;
                Point pPos = w.pointAt(p.getProgress());
                for (PacketModel other : grid.retrieve(pPos.x, pPos.y)) {
                    if (other == p || processed.contains(other)) continue;
                    if (isShielded(other)) continue;
                    Point oPos = other.getCurrentWire().pointAt(other.getProgress());
                    double dx = pPos.x - oPos.x;
                    double dy = pPos.y - oPos.y;
                    if (Math.hypot(dx, dy) <= COLLISION_RADIUS) {
                        // Noise
                        p.increaseNoise(NOISE_INCREMENT);
                        other.increaseNoise(NOISE_INCREMENT);
                        processed.add(p);
                        processed.add(other);
                        int ix = (pPos.x + oPos.x) / 2;
                        int iy = (pPos.y + oPos.y) / 2;
                        impactPoints.add(new Point(ix, iy));

                        // Bounce logic for MSG1 profile
                        if (isMsg1(p))     bounceToSource(p, w);
                        if (isMsg1(other)) bounceToSource(other, other.getCurrentWire());
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

    /** Remove packet from wire and try to enqueue into its source box. */
    private void bounceToSource(PacketModel p, WireModel w) {
        if (w == null) return;
        w.removePacket(p);
        SystemBoxModel srcBox = (portToBox != null) ? portToBox.get(w.getSrcPort()) : null;
        if (srcBox != null) {
            if (!srcBox.enqueue(p)) {
                // Buffer full → packet loss
                lossModel.increment();
            }
        } else {
            lossModel.increment();
        }
    }

    /* ---------------- Impact wave & noise cleanup -------------- */
    private void propagateImpactWaves(List<Point> impacts) {
        for (Point pt : impacts) {
            for (PacketModel p : grid.retrieve(pt.x, pt.y)) {
                Point pPos = p.getCurrentWire().pointAt(p.getProgress());
                double dx = pPos.x - pt.x;
                double dy = pPos.y - pt.y;
                double dist = Math.hypot(dx, dy);
                if (dist <= IMPACT_RADIUS) {
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

    /* ------------------------ Controls ------------------------ */
    public void pauseCollisions()  { this.collisionsEnabled = false; }
    public void resumeCollisions() { this.collisionsEnabled = true; }
    public void setImpactWaveEnabled(boolean enabled) { this.impactWaveEnabled = enabled; }
}

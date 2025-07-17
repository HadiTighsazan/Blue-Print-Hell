package com.blueprinthell.controller;

import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.ProtectedPacket;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.controller.SpatialHashGrid;

import javax.sound.sampled.Clip;
import com.blueprinthell.media.ResourceManager;
import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects collisions between packets using a spatial-hash grid; increases noise,
 * propagates impact waves to nearby packets, and removes packets whose noise exceeds MAX_NOISE.
 * Ignores collisions for ProtectedPacket while its shield remains.
 */
public class CollisionController implements com.blueprinthell.model.Updatable {
    private final List<WireModel> wires;
    private final SpatialHashGrid<PacketModel> grid;
    private final PacketLossModel lossModel;

    /* ---- Tunables ---- */
    private static final int    CELL_SIZE       = 50;
    private static final double COLLISION_RADIUS= 18.0;
    private static final double NOISE_INCREMENT = 0.5;
    private static final double MAX_NOISE       = 5.0;
    private static final double IMPACT_RADIUS   = 100.0;
    private static final double IMPACT_STRENGTH = 1.0;

    /* ---- Runtime Flags ---- */
    private boolean collisionsEnabled  = true;
    private boolean impactWaveEnabled  = true;

    public CollisionController(List<WireModel> wires, PacketLossModel lossModel) {
        this.wires     = wires;
        this.lossModel = lossModel;
        this.grid      = new SpatialHashGrid<>(CELL_SIZE);
    }

    @Override
    public void update(double dt) {
        List<Point> impacts = new ArrayList<>();
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
        // Broad phase: insert positions on wire path
        for (WireModel w : wires) {
            for (PacketModel p : w.getPackets()) {
                if (p instanceof ProtectedPacket pp && pp.getShield() > 0) continue;
                Point pos = w.pointAt(p.getProgress());
                grid.insert(pos.x, pos.y, p);
            }
        }
        // Narrow phase
        Set<PacketModel> processed = new HashSet<>();
        for (WireModel w : wires) {
            for (PacketModel p : new ArrayList<>(w.getPackets())) {
                if (processed.contains(p) || p.getProgress() <= 0) continue;
                if (p instanceof ProtectedPacket pp && pp.getShield() > 0) continue;
                Point pPos = w.pointAt(p.getProgress());
                for (PacketModel other : grid.retrieve(pPos.x, pPos.y)) {
                    if (other == p || processed.contains(other)) continue;
                    if (other instanceof ProtectedPacket op && op.getShield() > 0) continue;
                    Point oPos = other.getCurrentWire().pointAt(other.getProgress());
                    double dx = pPos.x - oPos.x;
                    double dy = pPos.y - oPos.y;
                    if (Math.hypot(dx, dy) <= COLLISION_RADIUS) {
                        p.increaseNoise(NOISE_INCREMENT);
                        other.increaseNoise(NOISE_INCREMENT);
                        processed.add(p);
                        processed.add(other);
                        int ix = (pPos.x + oPos.x) / 2;
                        int iy = (pPos.y + oPos.y) / 2;
                        impactPoints.add(new Point(ix, iy));
                    }
                }
            }
        }
        return impactPoints;
    }

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

    public void pauseCollisions()  { this.collisionsEnabled = false; }
    public void resumeCollisions() { this.collisionsEnabled = true; }
    public void setImpactWaveEnabled(boolean enabled) { this.impactWaveEnabled = enabled; }
}

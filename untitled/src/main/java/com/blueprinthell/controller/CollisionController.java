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
 * CollisionController — revised on 2025-07-29
 *
 * Summary of changes (per discussion):
 * 1) Immediate removals on collision (per design doc):
 *    - Non-green vs non-green  => both are removed immediately (loss += 2).
 *    - Green (MSG1) vs non-green => non-green is removed immediately (loss += 1),
 *      green is bounced back to source (setReturning=true, no removal).
 *    - Green vs Green => both bounce back; no removals.
 *    - Shielded packets remain unaffected as before.
 *    - Removed the old behavior of adding NOISE_INCREMENT to colliding packets at the moment of collision.
 *
 * 2) Tamed impact-wave spillover between wires:
 *    - IMPACT_RADIUS reduced from 100.0 to 45.0
 *    - IMPACT_STRENGTH reduced from 1.0 to 0.35
 *    - Still skips impact noise for green packets that are returning.
 *
 * 3) Kept protections:
 *    - ProtectedPacket (shield>0) is not inserted into broad phase and is not affected by waves.
 *    - Returning green packets are not removed on noise pass.
 */
public class CollisionController implements Updatable {
    private final List<WireModel> wires;
    private final SpatialHashGrid<PacketModel> grid;
    private final PacketLossModel lossModel;

    private Map<PortModel, SystemBoxModel> portToBox = null;

    private static final int    CELL_SIZE        = 50;
    private static final double COLLISION_RADIUS = 18.0;

    // Removed usage at collision time; retained constants for possible future use
    private static final double NOISE_INCREMENT = 0.5;
    private static final double MAX_NOISE       = 5.0;

    private static final double GREEN_GREEN_COLLISION_NOISE = 0.15;
    // Tamed wave
    private static final double IMPACT_RADIUS   = 45.0;   // was 100.0
    private static final double IMPACT_STRENGTH = 0.35;   // was 1.0

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

        // Broad phase: insert all unshielded packets into the grid
        for (WireModel w : wires) {
            for (PacketModel p : w.getPackets()) {
                if (isShielded(p)) continue;
                Point pos = w.pointAt(p.getProgress());
                grid.insert(pos.x, pos.y, p);
            }
        }

        Set<PacketModel> processed = new HashSet<>();

        // Narrow phase: detect and resolve collisions
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

                        // قبل از if اصلی برای همین برخورد:
                        boolean lossSfxPlayed = false;

                        if (pIsMsg1 && !otherIsMsg1) {
                            // Green vs non-green: bounce green, remove other
                            bounceToSource(p, w);
                            ow.removePacket(other);
                            lossModel.increment();
                            if (!lossSfxPlayed) { playLossSfxOnce(); lossSfxPlayed = true; } // اضافه
                            processed.add(p);
                            processed.add(other);

                        } else if (!pIsMsg1 && otherIsMsg1) {
                            // Non-green vs green
                            bounceToSource(other, ow);
                            w.removePacket(p);
                            lossModel.increment();
                            if (!lossSfxPlayed) { playLossSfxOnce(); lossSfxPlayed = true; } // اضافه
                            processed.add(p);
                            processed.add(other);

                        } else if (pIsMsg1 /*&&*/ && otherIsMsg1) {

                            // Green–Green: فقط نویز کم اضافه شود؛ هیچ بازگشتی انجام نشود
                            p.increaseNoise(GREEN_GREEN_COLLISION_NOISE);
                            other.increaseNoise(GREEN_GREEN_COLLISION_NOISE);
                            processed.add(p);
                            processed.add(other);

                        } else {
                            // Non-green vs non-green: remove both immediately
                            w.removePacket(p);
                            ow.removePacket(other);
                            lossModel.incrementBy(2);
                            if (!lossSfxPlayed) { playLossSfxOnce(); lossSfxPlayed = true; } // اضافه
                            processed.add(p);
                            processed.add(other);
                        }


                        // NOTE: we DO NOT add NOISE_INCREMENT to colliders anymore.

                        // Impact point for wave (still used to give nearby packets mild noise)
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
        // Bounce on the same wire: do not detach; let progress run backward via motion strategy
        p.setReturning(true);
        p.setAcceleration(0.0);
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
                    // Do not apply wave to returning green packets
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
                    // Do not remove returning green packets due to noise
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

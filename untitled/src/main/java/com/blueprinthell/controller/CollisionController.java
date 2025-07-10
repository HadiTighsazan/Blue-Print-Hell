package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;

import java.util.*;

/**
 * Controller to detect packet collisions via a spatial hash grid and increase noise.
 * Packets whose noise exceeds a threshold are dropped and counted as losses.
 */
public class CollisionController implements Updatable {
    private final List<WireModel> wires;
    private final SpatialHashGrid<PacketModel> grid;
    private final PacketLossModel lossModel;
    private static final int CELL_SIZE = 50;
    private static final double COLLISION_RADIUS = 20.0;
    private static final double NOISE_INCREMENT = 0.5;
    private static final double MAX_NOISE = 5.0;

    /**
     * @param wires list of wires whose packets may collide
     * @param lossModel model to track dropped packets
     */
    public CollisionController(List<WireModel> wires, PacketLossModel lossModel) {
        this.wires = wires;
        this.lossModel = lossModel;
        this.grid = new SpatialHashGrid<>(CELL_SIZE);
    }

    @Override
    public void update(double dt) {
        grid.clear();
        // Broad-phase: insert all packets into spatial grid
        for (WireModel wire : wires) {
            for (PacketModel p : wire.getPackets()) {
                int x = p.getCenterX();
                int y = p.getCenterY();
                grid.insert(x, y, p);
            }
        }

        // Narrow-phase: for each packet, check neighbors, ignoring fresh attachments
        for (WireModel wire : wires) {
            for (PacketModel p : new ArrayList<>(wire.getPackets())) {
                // only consider packets that have progressed beyond start to avoid initial collision
                if (p.getProgress() <= 0) continue;
                int x = p.getCenterX();
                int y = p.getCenterY();
                for (PacketModel other : grid.retrieve(x, y)) {
                    if (other != p && other.getProgress() > 0) {
                        int dx = p.getCenterX() - other.getCenterX();
                        int dy = p.getCenterY() - other.getCenterY();
                        double dist = Math.hypot(dx, dy);
                        if (dist <= COLLISION_RADIUS
                                && p.getCurrentWire() != other.getCurrentWire()) {
                            // collision only between packets on different wires
                            p.increaseNoise(NOISE_INCREMENT);
                            other.increaseNoise(NOISE_INCREMENT);
                        }
                    }
                }
            }
        }

        // Remove and count packets exceeding noise threshold
        for (WireModel wire : wires) {
            List<PacketModel> toRemove = new ArrayList<>();
            for (PacketModel p : wire.getPackets()) {
                if (p.getNoise() >= MAX_NOISE) {
                    toRemove.add(p);
                }
            }
            for (PacketModel p : toRemove) {
                wire.removePacket(p);
                lossModel.increment();
            }
        }
    }
}

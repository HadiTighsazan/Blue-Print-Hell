package com.blueprinthell.controller;

import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.TrojanPacket;
import com.blueprinthell.model.Updatable;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * AntiTrojanSystem — periodically scans around this system and removes any TrojanPacket
 * within a given radius, with a cooldown between sweeps.
 */
public class AntiTrojanSystem implements Updatable {
    private final SystemBoxModel box;
    private final List<WireModel> wires;
    private final PacketLossModel lossModel;
    private final double radius;
    private final double cooldown;
    private double timer = 0.0;

    /**
     * @param box           the system box center for sweeping
     * @param wires         all wires in the network
     * @param lossModel     model to count removed trojans as loss
     * @param radius        sweep radius in pixels
     * @param cooldown      minimum interval between sweeps in seconds
     */
    public AntiTrojanSystem(SystemBoxModel box,
                            List<WireModel> wires,
                            PacketLossModel lossModel,
                            double radius,
                            double cooldown) {
        this.box = box;
        this.wires = wires;
        this.lossModel = lossModel;
        this.radius = radius;
        this.cooldown = cooldown;
    }

    @Override
    public void update(double dt) {
        timer += dt;
        if (timer < cooldown) {
            return;
        }
        timer = 0.0;
        // compute center point of the box
        Point center = new Point(
                box.getX() + box.getWidth()/2,
                box.getY() + box.getHeight()/2
        );
        // collect trojans to remove
        for (WireModel wire : wires) {
            List<PacketModel> toRemove = new ArrayList<>();
            for (PacketModel p : wire.getPackets()) {
                if (p instanceof TrojanPacket) {
                    Point pos = wire.pointAt(p.getProgress());
                    if (center.distance(pos) <= radius) {
                        toRemove.add(p);
                    }
                }
            }
            // remove and count losses
            for (PacketModel t : toRemove) {
                wire.removePacket(t);
                lossModel.increment();
            }
        }
    }
}

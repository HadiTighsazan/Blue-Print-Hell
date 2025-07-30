package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.ConfidentialPacket;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;

import java.util.*;

public class ConfidentialThrottleController implements Updatable {

    private final List<WireModel> wires;
    private Map<WireModel, SystemBoxModel> destMap;

    private boolean enabled = true;

    // Phase-4 additions: remember original/base speeds to restore after congestion clears
    private final Map<PacketModel, Double> baseSpeed = new WeakHashMap<>();

    public ConfidentialThrottleController(List<WireModel> wires,
                                          Map<WireModel, SystemBoxModel> destMap) {
        this.wires = Objects.requireNonNull(wires, "wires");
        this.destMap = Objects.requireNonNull(destMap, "destMap");
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }

    public void setDestMap(Map<WireModel, SystemBoxModel> destMap) {
        this.destMap = Objects.requireNonNull(destMap, "destMap");
    }

    @Override
    public void update(double dt) {
        if (!enabled) return;

        for (WireModel w : wires) {
            SystemBoxModel dest = destMap.get(w);
            if (dest == null) continue;

            boolean congested = !dest.getBuffer().isEmpty();

            for (PacketModel p : w.getPackets()) {
                if (!(p instanceof ConfidentialPacket)) continue;

                if (congested) {
                    // Slow down to configured min; remember base speed once
                    baseSpeed.putIfAbsent(p, p.getSpeed());
                    double slow = Config.CONF_SLOW_SPEED;
                    if (p.getSpeed() > slow) {
                        p.setSpeed(slow);
                    }
                } else {
                    // Restore original/base speed if we had slowed it before
                    Double orig = baseSpeed.remove(p);
                    if (orig != null && orig > 0) {
                        p.setSpeed(orig);
                    } else {
                        // Fallback: if no remembered speed, at least ensure not below base
                        double min = Math.max(0, p.getBaseSpeed());
                        if (p.getSpeed() < min) {
                            p.setSpeed(min);
                        }
                    }
                }
            }
        }
    }
}

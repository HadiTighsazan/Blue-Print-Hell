package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.ConfidentialPacket;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;

import java.util.List;
import java.util.Map;
import java.util.Objects;


public class ConfidentialThrottleController implements Updatable {

    private final List<WireModel> wires;
    private Map<WireModel, SystemBoxModel> destMap;

    private boolean enabled = true;

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

            if (!dest.getBuffer().isEmpty()) {
                for (PacketModel p : w.getPackets()) {
                    if (p instanceof ConfidentialPacket) {
                        double slow = Config.CONF_SLOW_SPEED;
                        if (p.getSpeed() > slow) {
                            p.setSpeed(slow);
                        }
                    }
                }
            }
        }
    }
}

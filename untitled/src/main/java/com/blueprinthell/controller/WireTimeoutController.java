package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;

import java.util.*;


public final class WireTimeoutController implements Updatable {

    private final List<WireModel>   wires;
    private final PacketLossModel   lossModel;
    private final double            maxTimeOnWire;

    private final Map<PacketModel, Double> elapsed = new HashMap<>();
    private final Map<PacketModel, WireModel> lastWire = new HashMap<>();

    public WireTimeoutController(List<WireModel> wires,
                                 PacketLossModel lossModel) {
        this(wires, lossModel, Config.MAX_TIME_ON_WIRE_SEC);
    }

    public WireTimeoutController(List<WireModel> wires,
                                 PacketLossModel lossModel,
                                 double maxTimeOnWireSec) {
        this.wires = Objects.requireNonNull(wires, "wires");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
        if (maxTimeOnWireSec <= 0) throw new IllegalArgumentException("maxTimeOnWireSec must be > 0");
        this.maxTimeOnWire = maxTimeOnWireSec;
    }

    @Override
    public void update(double dt) {
        if (dt <= 0) return;
        Set<PacketModel> alivePackets = new HashSet<>();
        List<Removal> toRemove = new ArrayList<>();

        for (WireModel w : wires) {
            for (PacketModel p : w.getPackets()) {
                alivePackets.add(p);
                WireModel prevWire = lastWire.get(p);
                if (prevWire != w) {
                    elapsed.put(p, 0.0);
                    lastWire.put(p, w);
                } else {
                    double t = elapsed.getOrDefault(p, 0.0) + dt;
                    elapsed.put(p, t);
                    if (t >= maxTimeOnWire) {
                        toRemove.add(new Removal(w, p));
                    }
                }
            }
        }

        for (Removal r : toRemove) {
            if (r.wire.removePacket(r.packet)) {
                lossModel.increment();
            }
            elapsed.remove(r.packet);
            lastWire.remove(r.packet);
        }

        if (elapsed.size() != alivePackets.size()) {
            Iterator<PacketModel> it = elapsed.keySet().iterator();
            while (it.hasNext()) {
                PacketModel p = it.next();
                if (!alivePackets.contains(p)) {
                    it.remove();
                    lastWire.remove(p);
                }
            }
        }
    }

    public void clear() {
        elapsed.clear();
        lastWire.clear();
    }

    public void resetTimer(PacketModel p) {
        if (p == null) return;
        elapsed.put(p, 0.0);
        lastWire.put(p, p.getCurrentWire());
    }

    private static final class Removal {
        final WireModel wire;
        final PacketModel packet;
        Removal(WireModel w, PacketModel p) { this.wire = w; this.packet = p; }
    }
}

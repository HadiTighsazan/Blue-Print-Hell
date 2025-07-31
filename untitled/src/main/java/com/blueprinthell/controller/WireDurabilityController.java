package com.blueprinthell.controller;

import com.blueprinthell.model.*;

import java.util.*;

public class WireDurabilityController implements Updatable {

    private final List<WireModel> wires;
    private final PacketLossModel lossModel;
    private final int maxPasses;

    private final Map<WireModel, Integer> passCount = new IdentityHashMap<>();
    private final Deque<WireModel> toRemove = new ArrayDeque<>();
    private final Set<WireModel> removed = Collections.newSetFromMap(new IdentityHashMap<>());

    private WireRemovalController remover;

    public WireDurabilityController(List<WireModel> wires, PacketLossModel lossModel, int maxPasses) {
        this.wires = Objects.requireNonNull(wires);
        this.lossModel = Objects.requireNonNull(lossModel);
        if (maxPasses <= 0) throw new IllegalArgumentException("maxPasses must be > 0");
        this.maxPasses = maxPasses;
    }

    public void setWireRemover(WireRemovalController remover) {
        this.remover = remover;
    }

    public void onPacketArrived(PacketModel packet, WireModel wire) {
        if (packet == null || wire == null) return;
        if (PacketOps.isLarge(packet)) {
            recordHeavyPass(wire);
        }
    }

    public void recordHeavyPass(WireModel wire) {
        if (wire == null || removed.contains(wire)) return;
        int c = passCount.getOrDefault(wire, 0) + 1;
        passCount.put(wire, c);
        if (c >= maxPasses) {
            toRemove.add(wire);
        }
    }

    public int getPasses(WireModel wire) {
        return passCount.getOrDefault(wire, 0);
    }

    public boolean isAlive(WireModel wire) {
        return !removed.contains(wire);
    }

    public void destroyWire(WireModel wire) {
        if (wire != null) {
            toRemove.add(wire);
        }
    }

    public void clear() {
        passCount.clear();
        toRemove.clear();
        removed.clear();
    }

    @Override
    public void update(double dt) {
        while (!toRemove.isEmpty()) {
            WireModel w = toRemove.poll();
            if (w == null || removed.contains(w)) continue;
            removed.add(w);
            if (remover != null) {
                remover.removeWire(w);
            } else {
            }
        }
    }
}

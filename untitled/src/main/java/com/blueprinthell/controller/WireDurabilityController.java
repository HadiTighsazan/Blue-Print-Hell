package com.blueprinthell.controller;

import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.model.Updatable;

import java.util.*;


public final class WireDurabilityController implements Updatable {

    private final List<WireModel> wires;
    private final PacketLossModel lossModel;
    private final Map<WireModel, Integer> passCount = new HashMap<>();
    private final int maxPasses;

    private WireRemovalController wireRemover;

    public WireDurabilityController(List<WireModel> wires,
                                    PacketLossModel lossModel,
                                    int maxPasses) {
        this.wires = Objects.requireNonNull(wires, "wires");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
        this.maxPasses = maxPasses;
    }

    public void setWireRemover(WireRemovalController remover) {
        this.wireRemover = remover;
    }

    public void recordHeavyPass(WireModel wire) {
        int cnt = passCount.getOrDefault(wire, 0) + 1;
        passCount.put(wire, cnt);
        if (cnt >= maxPasses) {
            destroyWire(wire);
        }
    }

    public int getPasses(WireModel wire) {
        return passCount.getOrDefault(wire, 0);
    }

    public boolean isAlive(WireModel wire) {
        return wires.contains(wire) && passCount.getOrDefault(wire, 0) < maxPasses;
    }

    private void destroyWire(WireModel wire) {
        for (PacketModel p : new ArrayList<>(wire.getPackets())) {
            wire.removePacket(p);
            lossModel.increment();
        }

        if (wireRemover != null) {
            wireRemover.removeWire(wire);
        } else {
            wires.remove(wire);
        }

        passCount.remove(wire);
    }

    public void clear() {
        passCount.clear();
    }

    @Override
    public void update(double dt) {
    }


    public void onPacketArrived(PacketModel packet, WireModel wire) {
        if (packet instanceof LargePacket) {
            recordHeavyPass(wire);
        }
    }
}

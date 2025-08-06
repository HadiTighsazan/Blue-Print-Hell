package com.blueprinthell.controller;

import com.blueprinthell.model.*;
import com.blueprinthell.model.large.LargePacket;

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

        // فقط پکت‌های حجیم اصلی را بشمار، نه بیت‌پکت‌ها
        if (packet instanceof LargePacket lp) {
            // فقط پکت‌های حجیم با سایز 8 یا 10 را بشمار
            // پکت‌های سایز 4 (که از ادغام بیت‌ها ساخته می‌شوند) را نادیده بگیر
            if (lp.getOriginalSizeUnits() >= 8) {
                recordHeavyPass(wire);
                System.out.println("Wire durability: Large packet size " +
                        lp.getOriginalSizeUnits() + " passed. Wire passes: " +
                        getPasses(wire) + "/" + maxPasses);
            }
        }
    }

    public void recordHeavyPass(WireModel wire) {
        if (wire == null || removed.contains(wire)) return;

        int c = passCount.getOrDefault(wire, 0) + 1;
        passCount.put(wire, c);

        if (c >= maxPasses) {
            // قبل از حذف سیم، پکت‌های روی آن را ذخیره کن
            savePacketsBeforeRemoval(wire);
            toRemove.add(wire);
        }
    }

    private void savePacketsBeforeRemoval(WireModel wire) {
        if (wire == null) return;

        List<PacketModel> packetsOnWire = new ArrayList<>(wire.getPackets());
        if (!packetsOnWire.isEmpty()) {
            System.out.println("Warning: Removing wire with " + packetsOnWire.size() +
                    " packets on it. Moving them to loss.");

            // پکت‌ها را به عنوان loss ثبت کن
            for (PacketModel p : packetsOnWire) {
                lossModel.increment();
            }

            // پاک کردن پکت‌ها از سیم
            wire.clearPackets();
        }
    }

    public int getPasses(WireModel wire) {
        return passCount.getOrDefault(wire, 0);
    }

    public boolean isAlive(WireModel wire) {
        return !removed.contains(wire);
    }

    public void destroyWire(WireModel wire) {
        if (wire != null && !removed.contains(wire)) {
            savePacketsBeforeRemoval(wire);
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
                System.err.println("WireRemovalController not set!");
            }
        }
    }
}
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
        System.out.println("WireRemovalController set: " + (remover != null ? "OK" : "NULL"));
    }

    public void onPacketArrived(PacketModel packet, WireModel wire) {
        if (packet == null || wire == null) return;

        // فقط پکت‌های حجیم اصلی را بشمار، نه بیت‌پکت‌ها
        if (packet instanceof LargePacket lp) {
            // فقط پکت‌های حجیم با سایز 8 یا 10 را بشمار
            // پکت‌های سایز 4 (که از ادغام بیت‌ها ساخته می‌شوند) را نادیده بگیر
            if (lp.getOriginalSizeUnits() >= 8) {
                recordHeavyPass(wire);
                System.out.println("Large packet passed through wire. Count: " + passCount.getOrDefault(wire, 0) + "/" + maxPasses);
            }
        }
    }

    public void recordHeavyPass(WireModel wire) {
        if (wire == null || removed.contains(wire)) return;

        int c = passCount.getOrDefault(wire, 0) + 1;
        passCount.put(wire, c);

        System.out.println("Wire " + wire.hashCode() + " heavy pass count: " + c + "/" + maxPasses);

        if (c >= maxPasses) {
            System.out.println("Wire " + wire.hashCode() + " marked for destruction!");
            // قبل از حذف سیم، پکت‌های روی آن را ذخیره کن
            savePacketsBeforeRemoval(wire);
            toRemove.add(wire);
        }
    }

    private void savePacketsBeforeRemoval(WireModel wire) {
        if (wire == null) return;

        List<PacketModel> packetsOnWire = new ArrayList<>(wire.getPackets());
        if (!packetsOnWire.isEmpty()) {
            System.out.println("Saving " + packetsOnWire.size() + " packets before wire removal");

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
            System.out.println("Manually destroying wire: " + wire.hashCode());
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

            System.out.println("Processing wire removal: " + w.hashCode());
            removed.add(w);

            if (remover != null) {
                System.out.println("Using WireRemovalController to remove wire");
                remover.removeWire(w);
            } else {
                System.out.println("WARNING: No WireRemovalController available! Wire not removed from UI.");

                // فالبک: حذف مستقیم از لیست wires
                boolean removedFromList = wires.remove(w);
                System.out.println("Fallback removal from wires list: " + removedFromList);
            }
        }
    }
}
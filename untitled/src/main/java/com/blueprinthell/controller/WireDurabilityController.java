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

        if (packet instanceof LargePacket lp) {
            if (lp.isOriginal() && lp.getOriginalSizeUnits() > 5) {
                recordHeavyPass(wire);
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
            Iterator<PacketModel> it = packetsOnWire.iterator();

            while (it.hasNext()) {
                PacketModel p = it.next();

                // اگر پکت حجیم باشد، صبر کن تا پردازش شود (فرصت بده در فریم‌های بعد برسد)
                if (p instanceof LargePacket lp && lp.isOriginal() && lp.getOriginalSizeUnits() > 5) {
                    // سیم را هنوز حذف نکن! defer کن
                    return;
                }

                lossModel.increment();  // ثبت loss فقط برای پکت‌های غیر حجیم
            }

            wire.clearPackets();  // پاک‌سازی فقط اگر safe بود
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

                // فالبک: حذف مستقیم از لیست wires
                boolean removedFromList = wires.remove(w);
            }
        }
    }
}
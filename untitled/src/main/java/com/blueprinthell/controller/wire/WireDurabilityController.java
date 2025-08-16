package com.blueprinthell.controller.wire;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.simulation.SimulationController;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.BitPacket;
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
        // شمارش عبورها از اینجا انجام نمی‌شود؛ PacketDispatcher + WireModel منبع واحد هستند
        // این متد عمداً no-op می‌ماند تا از دوباره‌شماری جلوگیری شود.
    }






    public int getPasses(WireModel wire) {
        return (wire != null) ? wire.getLargePacketPassCount() : 0;
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
    private void savePacketsBeforeRemoval(WireModel wire) {
        if (wire == null) return;

        List<PacketModel> packetsOnWire = new ArrayList<>(wire.getPackets());
        if (!packetsOnWire.isEmpty()) {
            Iterator<PacketModel> it = packetsOnWire.iterator();

            while (it.hasNext()) {
                PacketModel p = it.next();

                if (p instanceof LargePacket lp &&
                        lp.isOriginal() && lp.getGroupId() < 0 &&
                        (lp.getOriginalSizeUnits() == Config.LARGE_PACKET_SIZE_8 ||
                                lp.getOriginalSizeUnits() == Config.LARGE_PACKET_SIZE_10)) {
                }

                if (DBG_LOSS) System.out.println("[LOSS][WireDurability +1 bare] پکت بدون نوع دقیق (increment) ثبت شد.(incrementPacket in savePacketsBeforeRemoval)");
                lossModel.incrementPacket(p);

                // اطلاع به producer
                SimulationController sim = WireModel.getSimulationController();
                if (sim != null && sim.getPacketProducerController() != null) {
                    sim.getPacketProducerController().onPacketLost();
                }
            }

            wire.clearPackets();
        }
    }
        private static final boolean DBG_LOSS = true;
    private static String dbg(PacketModel p) {
                if (p instanceof LargePacket lp) {
                        return "Large{orig=" + lp.isOriginal() + ",group=" + lp.getGroupId() + ",size=" + lp.getOriginalSizeUnits() + "}";
                    } else if (p instanceof BitPacket bp) {
                        return "Bit{group=" + bp.getGroupId() + "}";
                    } else {
                        return p.getClass().getSimpleName();
                    }
            }
}
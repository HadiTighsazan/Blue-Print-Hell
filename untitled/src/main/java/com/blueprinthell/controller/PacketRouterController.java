package com.blueprinthell.controller;

import com.blueprinthell.controller.systems.RouteHints;
import com.blueprinthell.model.*;
import com.blueprinthell.motion.MotionStrategy;
import com.blueprinthell.motion.MotionStrategyFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * G1 – مرحلهٔ ۴ (Malicious): نسخهٔ به‌روزشدهٔ Router با پشتیبانی از "اجبار پورت ناسازگار".
 *
 * رفتار اصلی تغییری نکرده، فقط اگر MaliciousBehavior روی پکت فلگ بگذارد، این‌جا اولویت انتخاب پورت
 * معکوس می‌شود (اول ناسازگار، سپس سایرین). پس در حالت عادی (بدون فلگ) همان منطق قبلی اجرا می‌شود.
 */
public class PacketRouterController implements Updatable {
    private final SystemBoxModel box;
    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destMap; // فعلاً استفاده نمی‌شود ولی برای سازگاری نگه داشته‌ایم
    private final PacketLossModel lossModel;
    private final Random rnd = new Random();

    public PacketRouterController(SystemBoxModel box,
                                  List<WireModel> wires,
                                  Map<WireModel, SystemBoxModel> destMap,
                                  PacketLossModel lossModel) {
        this.box = box;
        this.wires = wires;
        this.destMap = destMap;
        this.lossModel = lossModel;
    }

    @Override
    public void update(double dt) {
        // 1) Drain buffer
        List<PacketModel> toRoute = new ArrayList<>();
        PacketModel p;
        while ((p = box.pollPacket()) != null) toRoute.add(p);

        // 2) Route each
        for (PacketModel packet : toRoute) {
            List<PortModel> outs = box.getOutPorts();
            if (outs.isEmpty()) {
                drop(packet);
                continue;
            }

            // --- NEW: did a Malicious system force incompatible routing? ---
            boolean forceIncompat = RouteHints.consumeForceIncompatible(packet);

            // 3) classify ports
            List<PortModel> compat = outs.stream()
                    .filter(port -> port.isCompatible(packet))
                    .collect(Collectors.toList());
            List<PortModel> incompat = outs.stream()
                    .filter(port -> !port.isCompatible(packet))
                    .collect(Collectors.toList());

            List<PortModel> emptyCompat = compat.stream()
                    .filter(this::isWireEmpty)
                    .collect(Collectors.toList());
            List<PortModel> emptyIncompat = incompat.stream()
                    .filter(this::isWireEmpty)
                    .collect(Collectors.toList());
            List<PortModel> emptyAny = outs.stream()
                    .filter(this::isWireEmpty)
                    .collect(Collectors.toList());

            PortModel chosen = null;
            if (forceIncompat) {
                if (!emptyIncompat.isEmpty())      chosen = emptyIncompat.get(0);
                else if (!incompat.isEmpty())      chosen = incompat.get(rnd.nextInt(incompat.size()));
                else if (!emptyCompat.isEmpty())   chosen = emptyCompat.get(0);
                else if (!compat.isEmpty())        chosen = compat.get(rnd.nextInt(compat.size()));
                else if (!emptyAny.isEmpty())      chosen = emptyAny.get(0);
            } else {
                if (!emptyCompat.isEmpty())        chosen = emptyCompat.get(0);
                else if (!compat.isEmpty())        chosen = compat.get(rnd.nextInt(compat.size()));
                else if (!emptyAny.isEmpty())      chosen = emptyAny.get(0);
            }

            if (chosen == null) {
                if (!box.enqueue(packet)) drop(packet);
                continue;
            }

            WireModel wire = findWire(chosen);
            if (wire == null) {
                if (!box.enqueue(packet)) drop(packet);
                continue;
            }

            // 4) assign motion strategy using MotionStrategyFactory
            boolean comp = chosen.isCompatible(packet);
            MotionStrategy ms = MotionStrategyFactory.create(packet, comp);
            packet.setMotionStrategy(ms);

            // 5) attach to wire
            wire.attachPacket(packet, 0.0);
        }
    }

    /* ---------------- helpers ---------------- */
    private boolean isWireEmpty(PortModel port) {
        WireModel w = findWire(port);
        return w != null && w.getPackets().isEmpty();
    }

    private WireModel findWire(PortModel port) {
        for (WireModel w : wires) if (w.getSrcPort() == port) return w;
        return null;
    }

    private void drop(PacketModel packet) {
        lossModel.increment();
    }
}

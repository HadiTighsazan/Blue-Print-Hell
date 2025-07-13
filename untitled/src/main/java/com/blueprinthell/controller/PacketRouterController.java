package com.blueprinthell.controller;

import com.blueprinthell.model.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Routes packets from a system‑box buffer onto outgoing wires.
 * ‑ اولویت با پورت سازگار و خالی
 * ‑ سپس پورت سازگار تصادفی
 * ‑ سپس هر پورت خالی
 * اگر هیچ پورتی موجود نباشد:
 *   • تلاش می‌کند پکت را مجدد در بافر قرار دهد؛ اگر بافر پر بود -> Packet‑Loss ++
 * همچنین سرعت و شتاب پکت را بر اساس سازگاری پورت و نوع پکت تنظیم می‌کند.
 */
public class PacketRouterController implements Updatable {
    private final SystemBoxModel box;
    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destMap;
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
        // 1) drain buffer to avoid infinite re‑enqueue loops
        List<PacketModel> toRoute = new ArrayList<>();
        PacketModel p;
        while ((p = box.pollPacket()) != null) toRoute.add(p);

        // 2) attempt routing each packet once this tick
        for (PacketModel packet : toRoute) {
            List<PortModel> outs = box.getOutPorts();
            if (outs.isEmpty()) {
                drop(packet);
                continue;
            }

            // compatible ports
            List<PortModel> compat = outs.stream().filter(port -> port.isCompatible(packet)).collect(Collectors.toList());
            // empty compatible
            List<PortModel> emptyCompat = compat.stream().filter(port -> isWireEmpty(port)).collect(Collectors.toList());
            // empty any
            List<PortModel> emptyAny = outs.stream().filter(this::isWireEmpty).collect(Collectors.toList());

            PortModel chosen = null;
            if (!emptyCompat.isEmpty()) chosen = emptyCompat.get(0);
            else if (!compat.isEmpty())  chosen = compat.get(rnd.nextInt(compat.size()));
            else if (!emptyAny.isEmpty()) chosen = emptyAny.get(0);

            if (chosen == null) {
                // هیچ پورتی در دسترس نیست → سعی در برگرداندن به بافر
                if (!box.enqueue(packet)) drop(packet);
                continue;
            }

            WireModel wire = findWire(chosen);
            if (wire == null) {
                if (!box.enqueue(packet)) drop(packet);
                continue;
            }

            // تنظیم سرعت / شتاب
            double base = packet.getBaseSpeed();
            boolean comp = chosen.isCompatible(packet);
            switch (packet.getType()) {
                case SQUARE -> packet.setSpeed(comp ? base / 2 : base);
                case TRIANGLE -> packet.setAcceleration(comp ? 0 : base);
            }

            // اتصال به سیم
            wire.attachPacket(packet, 0.0);
            destMap.putIfAbsent(wire, destMap.get(wire));
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
        // simply count as loss; packet will be GC‑ed because no references held
        lossModel.increment();
    }
}

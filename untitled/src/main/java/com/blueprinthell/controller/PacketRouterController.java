package com.blueprinthell.controller;

import com.blueprinthell.model.*;
import com.blueprinthell.motion.ConstantSpeedStrategy;
import com.blueprinthell.motion.MotionStrategy;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Routes packets from a system‑box buffer onto outgoing wires.
 * <ul>
 *   <li>اولویت با پورت خالی و سازگار</li>
 *   <li>در غیر این صورت پورت سازگار تصادفی</li>
 *   <li>در غیر این صورت پورت خالی</li>
 *   <li>در صورت عدم امکان، تلاش برای بازگرداندن به بافر یا شمارش Packet Loss</li>
 * </ul>
 * Kinematics now driven by MotionStrategy instead of direct speed/acc.
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

            // 3) select candidate port
            List<PortModel> compat = outs.stream()
                    .filter(port -> port.isCompatible(packet))
                    .collect(Collectors.toList());
            List<PortModel> emptyCompat = compat.stream()
                    .filter(this::isWireEmpty)
                    .collect(Collectors.toList());
            List<PortModel> emptyAny = outs.stream()
                    .filter(this::isWireEmpty)
                    .collect(Collectors.toList());

            PortModel chosen = null;
            if (!emptyCompat.isEmpty()) chosen = emptyCompat.get(0);
            else if (!compat.isEmpty()) chosen = compat.get(rnd.nextInt(compat.size()));
            else if (!emptyAny.isEmpty()) chosen = emptyAny.get(0);

            if (chosen == null) {
                if (!box.enqueue(packet)) drop(packet);
                continue;
            }

            WireModel wire = findWire(chosen);
            if (wire == null) {
                if (!box.enqueue(packet)) drop(packet);
                continue;
            }

            // 4) assign motion strategy based on compatibility
            boolean comp = chosen.isCompatible(packet);
            double base = packet.getBaseSpeed();
            MotionStrategy ms = new ConstantSpeedStrategy(comp ? base / 2 : base);
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

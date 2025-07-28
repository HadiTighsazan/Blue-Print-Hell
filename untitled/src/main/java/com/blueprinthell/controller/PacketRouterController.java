package com.blueprinthell.controller;

import com.blueprinthell.model.*;
import com.blueprinthell.motion.MotionStrategy;
import com.blueprinthell.motion.MotionStrategyFactory;

import java.util.*;
import java.util.stream.Collectors;


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
        List<PacketModel> toRoute = new ArrayList<>();
        PacketModel p;
        while ((p = box.pollPacket()) != null) toRoute.add(p);

        for (PacketModel packet : toRoute) {
            // CHANGED: Null-guard برای destMap.get(w)
            List<PortModel> outs = box.getOutPorts().stream()
                    .filter(port -> {
                        WireModel w = findWire(port);
                        if (w == null) return false;
                        SystemBoxModel d = destMap.get(w);
                        return d != null && d.isEnabled();
                    })
                    .collect(Collectors.toList());

            // CHANGED: در نبود خروجی مجاز، بازصف‌گذاری و فقط در صورت پر بودن صف Drop
            if (outs.isEmpty()) {
                if (!box.enqueue(packet)) {
                    drop(packet);
                }
                continue;
            }

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

            boolean comp = chosen.isCompatible(packet);

            double mul = packet.consumeExitBoostMultiplier();

            packet.setStartSpeedMul(mul);

            MotionStrategy ms = MotionStrategyFactory.create(packet, comp);

            packet.setMotionStrategy(ms);
            wire.attachPacket(packet, 0.0);
        }
    }

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

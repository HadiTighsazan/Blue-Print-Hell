package com.blueprinthell.controller;

import com.blueprinthell.controller.systems.RouteHints;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.motion.MotionStrategy;
import com.blueprinthell.motion.MotionStrategyFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Router به‌روزشده برای گام ۲ – مرحله ۵:
 * <ul>
 *   <li>پشتیبانی از پرچم «اجبار ناسازگار» (Malicious).</li>
 *   <li>نادیده گرفتن سازگاری برای {@link LargePacket} و {@link BitPacket} (سازگاری برایشان بی‌معناست).</li>
 * </ul>
 */
public class PacketRouterController implements Updatable {
    private final SystemBoxModel box;
    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destMap; // برای سازگاری با کد قبلی نگه داشته شده
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
        // 1) تخلیه بافر
        List<PacketModel> toRoute = new ArrayList<>();
        PacketModel p;
        while ((p = box.pollPacket()) != null) toRoute.add(p);

        // 2) مسیریابی هر پکت
        for (PacketModel packet : toRoute) {
            List<PortModel> outs = box.getOutPorts();
            if (outs.isEmpty()) { drop(packet); continue; }

            boolean forceIncompat = RouteHints.consumeForceIncompatible(packet);
            boolean ignoreCompat  = (packet instanceof LargePacket) || (packet instanceof BitPacket);

            // طبقه‌بندی پورت‌ها
            List<PortModel> compat = outs.stream().filter(port -> port.isCompatible(packet)).collect(Collectors.toList());
            List<PortModel> incompat = outs.stream().filter(port -> !port.isCompatible(packet)).collect(Collectors.toList());

            List<PortModel> emptyCompat   = compat.stream().filter(this::isWireEmpty).collect(Collectors.toList());
            List<PortModel> emptyIncompat = incompat.stream().filter(this::isWireEmpty).collect(Collectors.toList());
            List<PortModel> emptyAny      = outs.stream().filter(this::isWireEmpty).collect(Collectors.toList());

            PortModel chosen = null;
            if (ignoreCompat) {
                // برای Large/Bit فقط سعی می‌کنیم پورت خالی بگیریم، وگرنه رندوم هر پورت
                if (!emptyAny.isEmpty()) chosen = emptyAny.get(0);
                else chosen = outs.get(rnd.nextInt(outs.size()));
            } else if (forceIncompat) {
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

            // 4) استراتژی حرکتی
            boolean comp = chosen.isCompatible(packet); // حتی اگر ignoreCompat باشد، برای انتخاب rule ضرری ندارد
            MotionStrategy ms = MotionStrategyFactory.create(packet, comp);
            packet.setMotionStrategy(ms);

            // 5) الصاق به سیم
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

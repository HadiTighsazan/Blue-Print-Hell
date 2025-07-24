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
 * <h2>PacketRouterController</h2>
 * مسئول بیرون کشیدن پکت‌ها از بافر یک SystemBox و الصاق آن‌ها به سیم‌های خروجی است.
 * ویژگی‌های گام ۴/۲ اعمال شده‌اند:
 * <ul>
 *   <li>استفاده از {@link MotionStrategyFactory} برای تعیین استراتژی حرکت.</li>
 *   <li>پشتیبانی از پرچم «اجبار ناسازگار» از طریق {@link RouteHints}.</li>
 *   <li>نادیده‌گرفتن سازگاری برای {@link LargePacket} و {@link BitPacket} (سازگاری برایشان معنی ندارد).</li>
 *   <li>fallbackهای معقول زمانی که هیچ پورتی انتخاب نشود (بازگشت به بافر یا Drop).</li>
 * </ul>
 */
public class PacketRouterController implements Updatable {

    private final SystemBoxModel box;
    private final List<WireModel> wires;
    /** نگه‌داشت برای سازگاری با کد قبلی؛ فعلاً فقط ممکن است در آینده جهت چک مقصد استفاده شود. */
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
            routeOne(packet);
        }
    }

    /* --------------------------------------------------------------- */
    /*                             Core                                 */
    /* --------------------------------------------------------------- */

    private void routeOne(PacketModel packet) {
        List<PortModel> outs = box.getOutPorts();
        if (outs.isEmpty()) { drop(packet); return; }

        boolean forceIncompat = RouteHints.consumeForceIncompatible(packet);
        boolean ignoreCompat  = (packet instanceof LargePacket) || (packet instanceof BitPacket);

        // دسته‌بندی خروجی‌ها
        List<PortModel> compat   = outs.stream().filter(port -> port.isCompatible(packet)).collect(Collectors.toList());
        List<PortModel> incompat = outs.stream().filter(port -> !port.isCompatible(packet)).collect(Collectors.toList());

        List<PortModel> emptyCompat   = compat.stream().filter(this::isWireEmpty).collect(Collectors.toList());
        List<PortModel> emptyIncompat = incompat.stream().filter(this::isWireEmpty).collect(Collectors.toList());
        List<PortModel> emptyAny      = outs.stream().filter(this::isWireEmpty).collect(Collectors.toList());

        PortModel chosen = choosePort(packet, ignoreCompat, forceIncompat,
                emptyCompat, emptyIncompat, emptyAny,
                compat, incompat, outs);

        if (chosen == null) {
            // تلاشی برای برگرداندن به بافر
            if (!box.enqueue(packet)) drop(packet);
            return;
        }

        WireModel wire = findWire(chosen);
        if (wire == null) {
            if (!box.enqueue(packet)) drop(packet);
            return;
        }

        // 4) استراتژی حرکتی
        boolean effectiveCompat = !forceIncompat && !ignoreCompat && chosen.isCompatible(packet);
        MotionStrategy ms = MotionStrategyFactory.create(packet, effectiveCompat);
        packet.setMotionStrategy(ms);

        // 5) attach
        wire.attachPacket(packet, 0.0);
    }

    /** الگوریتم انتخاب پورت با توجه به سناریوهای مختلف */
    private PortModel choosePort(PacketModel packet,
                                 boolean ignoreCompat,
                                 boolean forceIncompat,
                                 List<PortModel> emptyCompat,
                                 List<PortModel> emptyIncompat,
                                 List<PortModel> emptyAny,
                                 List<PortModel> compat,
                                 List<PortModel> incompat,
                                 List<PortModel> outs) {
        if (ignoreCompat) {
            if (!emptyAny.isEmpty()) return emptyAny.get(0);
            return outs.get(rnd.nextInt(outs.size()));
        }
        if (forceIncompat) {
            if (!emptyIncompat.isEmpty()) return emptyIncompat.get(0);
            if (!incompat.isEmpty())      return incompat.get(rnd.nextInt(incompat.size()));
            if (!emptyCompat.isEmpty())   return emptyCompat.get(0);
            if (!compat.isEmpty())        return compat.get(rnd.nextInt(compat.size()));
            if (!emptyAny.isEmpty())      return emptyAny.get(0);
            return null;
        }
        // حالت عادی: اول empty+compat، بعد compat، بعد emptyAny، بعد هرچیز
        if (!emptyCompat.isEmpty()) return emptyCompat.get(0);
        if (!compat.isEmpty())      return compat.get(rnd.nextInt(compat.size()));
        if (!emptyAny.isEmpty())    return emptyAny.get(0);
        // آخرین fallback: یک پورت تصادفی
        return outs.get(rnd.nextInt(outs.size()));
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

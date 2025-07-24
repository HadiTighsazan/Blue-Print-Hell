package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;

import java.util.*;

/**
 * <h2>WireTimeoutController</h2>
 * اگر یک پکت بیش از مدت زمان مجاز روی یک سیم بماند (بدون رسیدن به مقصد)، حذف می‌شود و در PacketLoss ثبت می‌گردد.
 * <p>
 *  این کنترلر هر تیک، همهٔ سیم‌ها را اسکن می‌کند و برای هر پکت زمان حضور روی سیم را افزایش می‌دهد.
 *  هرگاه پکت به سیم جدیدی وارد شود، تایمرش ریست می‌شود. اگر پکت دیگر روی هیچ سیمی نباشد، از جدول پاک می‌شود.
 * </p>
 */
public final class WireTimeoutController implements Updatable {

    private final List<WireModel>   wires;          // مرجع اصلی سیم‌ها
    private final PacketLossModel   lossModel;
    private final double            maxTimeOnWire;  // ثانیه

    /** زمان سپری‌شده روی سیم فعلی برای هر پکت */
    private final Map<PacketModel, Double> elapsed = new HashMap<>();
    /** آخرین سیمی که پکت روی آن بوده (برای تشخیص ورود به سیم جدید) */
    private final Map<PacketModel, WireModel> lastWire = new HashMap<>();

    public WireTimeoutController(List<WireModel> wires,
                                 PacketLossModel lossModel) {
        this(wires, lossModel, Config.MAX_TIME_ON_WIRE_SEC);
    }

    public WireTimeoutController(List<WireModel> wires,
                                 PacketLossModel lossModel,
                                 double maxTimeOnWireSec) {
        this.wires = Objects.requireNonNull(wires, "wires");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
        if (maxTimeOnWireSec <= 0) throw new IllegalArgumentException("maxTimeOnWireSec must be > 0");
        this.maxTimeOnWire = maxTimeOnWireSec;
    }

    @Override
    public void update(double dt) {
        if (dt <= 0) return;
        // جمع آوری تمام پکت‌های فعلی روی همهٔ سیم‌ها
        Set<PacketModel> alivePackets = new HashSet<>();
        List<Removal> toRemove = new ArrayList<>();

        for (WireModel w : wires) {
            for (PacketModel p : w.getPackets()) {
                alivePackets.add(p);
                WireModel prevWire = lastWire.get(p);
                if (prevWire != w) {
                    // وارد سیم جدید شده است → تایمر صفر
                    elapsed.put(p, 0.0);
                    lastWire.put(p, w);
                } else {
                    double t = elapsed.getOrDefault(p, 0.0) + dt;
                    elapsed.put(p, t);
                    if (t >= maxTimeOnWire) {
                        toRemove.add(new Removal(w, p));
                    }
                }
            }
        }

        // حذف پکت‌های تایم‌اوت شده
        for (Removal r : toRemove) {
            if (r.wire.removePacket(r.packet)) {
                lossModel.increment();
            }
            elapsed.remove(r.packet);
            lastWire.remove(r.packet);
        }

        // پاکسازی رکورد پکت‌هایی که دیگر روی هیچ سیمی نیستند
        if (elapsed.size() != alivePackets.size()) {
            // remove stale
            Iterator<PacketModel> it = elapsed.keySet().iterator();
            while (it.hasNext()) {
                PacketModel p = it.next();
                if (!alivePackets.contains(p)) {
                    it.remove();
                    lastWire.remove(p);
                }
            }
        }
    }

    /** ریست کامل هنگام ریست مرحله */
    public void clear() {
        elapsed.clear();
        lastWire.clear();
    }

    /** امکان ریست دستی تایمر برای یک پکت (مثلاً هنگام ورود به باکس) */
    public void resetTimer(PacketModel p) {
        if (p == null) return;
        elapsed.put(p, 0.0);
        lastWire.put(p, p.getCurrentWire());
    }

    private static final class Removal {
        final WireModel wire;
        final PacketModel packet;
        Removal(WireModel w, PacketModel p) { this.wire = w; this.packet = p; }
    }
}

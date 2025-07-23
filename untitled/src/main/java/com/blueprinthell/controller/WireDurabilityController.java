package com.blueprinthell.controller;

import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.model.Updatable;

import java.util.*;

/**
 * <h2>WireDurabilityController</h2>
 * مسئول شمارش عبور پکت‌های حجیم از هر سیم و نابود کردن سیم پس از رسیدن به سقف مجاز.
 * <ul>
 *   <li>هر بار که یک {@link LargePacket} از سیمی «عبور کامل» کند (به مقصد برسد)،
 *       متد {@link #recordHeavyPass(WireModel)} را صدا بزنید.</li>
 *   <li>پس از رسیدن شمارنده به حد آستانه، سیم حذف می‌شود و تمام پکت‌های روی آن Drop شده و در {@link PacketLossModel} ثبت می‌شوند.</li>
 *   <li>در صورت وجود {@link WireRemovalController} از آن برای حذف UI/مدل استفاده می‌شود؛ در غیر این صورت سیم از لیست محلی حذف می‌گردد.</li>
 * </ul>
 */
public final class WireDurabilityController implements Updatable {

    private final List<WireModel> wires;                 // مرجع لیست اصلی سیم‌ها
    private final PacketLossModel lossModel;
    private final Map<WireModel, Integer> passCount = new HashMap<>();
    private final int maxPasses;

    private WireRemovalController wireRemover;           // اختیاری

    public WireDurabilityController(List<WireModel> wires,
                                    PacketLossModel lossModel,
                                    int maxPasses) {
        this.wires = Objects.requireNonNull(wires, "wires");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
        this.maxPasses = maxPasses;
    }

    /** برای تزریق بعدی (وقتی LevelCoreManager آماده شد). */
    public void setWireRemover(WireRemovalController remover) {
        this.wireRemover = remover;
    }

    /** ثبت عبور پکت حجیم از یک سیم. */
    public void recordHeavyPass(WireModel wire) {
        int cnt = passCount.getOrDefault(wire, 0) + 1;
        passCount.put(wire, cnt);
        if (cnt >= maxPasses) {
            destroyWire(wire);
        }
    }

    /** بررسی شمارندهٔ فعلی یک سیم. */
    public int getPasses(WireModel wire) {
        return passCount.getOrDefault(wire, 0);
    }

    /** آیا سیم هنوز قابل استفاده است؟ */
    public boolean isAlive(WireModel wire) {
        return wires.contains(wire) && passCount.getOrDefault(wire, 0) < maxPasses;
    }

    /** حذف سیم و ثبت Loss برای پکت‌های روی آن. */
    private void destroyWire(WireModel wire) {
        // Drop packets currently on the wire
        for (PacketModel p : new ArrayList<>(wire.getPackets())) {
            // پاک کردن از سیم
            wire.removePacket(p);
            // ثبت Loss
            lossModel.increment();
        }

        // حذف خود سیم
        if (wireRemover != null) {
            wireRemover.removeWire(wire); // فرض بر اینکه این متد وجود دارد؛ در غیر این صورت دستی حذف می‌کنیم
        } else {
            wires.remove(wire);
        }

        passCount.remove(wire);
    }

    /** پاکسازی کامل هنگام ریست مرحله. */
    public void clear() {
        passCount.clear();
    }

    @Override
    public void update(double dt) {
        // نیازی به منطق زمان‌محور نیست؛ همه‌چیز event-driven است
    }

    /* --------------------------------------------------------------- */
    /*               Helper for Dispatcher integration                  */
    /* --------------------------------------------------------------- */
    /**
     * یک کمک‌کنندهٔ ایمن: وقتی پکتی به مقصد رسید، اگر Large بود، ثبت کن.
     * می‌توان این متد را از PacketDispatcherController صدا زد.
     */
    public void onPacketArrived(PacketModel packet, WireModel wire) {
        if (packet instanceof LargePacket) {
            recordHeavyPass(wire);
        }
    }
}

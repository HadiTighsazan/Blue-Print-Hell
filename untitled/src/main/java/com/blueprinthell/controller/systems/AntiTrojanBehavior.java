package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.TrojanPacket;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.PortModel;

import java.util.*;

/**
 * <h2>AntiTrojanBehavior – سیستم آنتی‌تروجان</h2>
 * <ul>
 *   <li>در هر بروزرسانی، اگر پکت تروجان در شعاع مشخصی از مرکز این باکس روی هر سیمی باشد، آن را به پکت عادی تبدیل می‌کند.</li>
 *   <li>بعد از هر موفقیت، به مدت مشخصی (cooldown) غیرفعال می‌شود.</li>
 *   <li>اگر تروجان مستقیماً وارد بافر همین باکس شود، فوراً پاک‌سازی می‌شود.</li>
 * </ul>
 */
public final class AntiTrojanBehavior implements SystemBehavior {

    private final SystemBoxModel box;
    private final List<WireModel> wires;   // برای اسکن روی سیم‌ها
    private final double radiusPx;
    private final double cooldownSec;

    private double cooldownLeft = 0.0;

    public AntiTrojanBehavior(SystemBoxModel box, List<WireModel> wires) {
        this(box, wires, Config.ANTI_TROJAN_RADIUS_PX, Config.ANTI_TROJAN_COOLDOWN_S);
    }

    public AntiTrojanBehavior(SystemBoxModel box,
                              List<WireModel> wires,
                              double radiusPx,
                              double cooldownSec) {
        this.box = Objects.requireNonNull(box, "box");
        this.wires = Objects.requireNonNull(wires, "wires");
        this.radiusPx = radiusPx;
        this.cooldownSec = cooldownSec;
    }

    /* --------------------------- Tick --------------------------- */
    @Override
    public void update(double dt) {
        if (cooldownLeft > 0) {
            cooldownLeft -= dt;
            if (cooldownLeft < 0) cooldownLeft = 0;
            return;
        }

        // 1) اسکن سیم‌ها برای اولین تروجان در شعاع
        double r2 = radiusPx * radiusPx;
        for (WireModel w : wires) {
            for (PacketModel pkt : w.getPackets()) {
                if (!(pkt instanceof TrojanPacket)) continue;
                if (distanceSquared(pkt, box) <= r2) {
                    // تبدیل تروجان به پکت سالم
                    PacketModel clean = unTrojan(pkt);
                    double prog = pkt.getProgress();
                    if (w.removePacket(pkt)) {
                        w.attachPacket(clean, prog);
                        startCooldown();
                        return; // فقط یکی در هر فعال‌سازی
                    }
                }
            }
        }

        // 2) fallback: بررسی بافر خود باکس (اگر به هر دلیل در onPacketEnqueued جا افتاده باشد)
        replaceFirstTrojanInBuffer();
    }

    /* -------------------- Packet arrival hooks -------------------- */
    @Override
    public void onPacketEnqueued(PacketModel packet) {
        onPacketEnqueued(packet, null);
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (cooldownLeft > 0) return; // غیرفعال است
        if (packet instanceof TrojanPacket) {
            PacketModel clean = unTrojan(packet);
            if (replaceInBuffer(packet, clean)) {
                startCooldown();
            }
        }
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        // کنترل فعال/غیرفعال شدن کلی در این فاز نیاز نیست؛ cooldown مدیریت می‌کند
    }

    /* --------------------------- Helpers --------------------------- */

    private void startCooldown() {
        cooldownLeft = cooldownSec;
    }

    /** تبدیل TrojanPacket به نسخهٔ سالم. در حال حاضر از original درون TrojanPacket استفاده می‌کنیم. */
    private PacketModel unTrojan(PacketModel pkt) {
        if (pkt instanceof TrojanPacket tp) {
            PacketModel orig = tp.getOriginal();
            // اگر original null بود یا نمی‌خواهیم همان را استفاده کنیم، می‌توانیم clone جدید بسازیم
            return (orig != null) ? orig : clonePlain(tp);
        }
        return pkt;
    }

    /**
     * یک کپی ساده از PacketModel (بدون Trojan) می‌سازد.
     * اینجا فقط در شرایط اضطراری استفاده می‌شود.
     */
    private PacketModel clonePlain(PacketModel src) {
        PacketModel c = new PacketModel(src.getType(), src.getBaseSpeed());
        c.setProgress(src.getProgress());
        c.setSpeed(src.getSpeed());
        c.setAcceleration(src.getAcceleration());
        c.resetNoise();
        return c;
    }

    private boolean replaceInBuffer(PacketModel oldPkt, PacketModel newPkt) {
        Deque<PacketModel> temp = new ArrayDeque<>();
        boolean replaced = false;
        PacketModel p;
        while ((p = box.pollPacket()) != null) {
            if (!replaced && p == oldPkt) {
                temp.addLast(newPkt);
                replaced = true;
            } else {
                temp.addLast(p);
            }
        }
        for (PacketModel q : temp) box.enqueue(q);
        return replaced;
    }

    private void replaceFirstTrojanInBuffer() {
        Deque<PacketModel> temp = new ArrayDeque<>();
        boolean converted = false;
        PacketModel p;
        while ((p = box.pollPacket()) != null) {
            if (!converted && p instanceof TrojanPacket) {
                temp.addLast(unTrojan(p));
                converted = true;
            } else {
                temp.addLast(p);
            }
        }
        for (PacketModel q : temp) box.enqueue(q);
        if (converted) startCooldown();
    }

    private static double distanceSquared(PacketModel pkt, SystemBoxModel box) {
        int dx = pkt.getCenterX() - box.getCenterX();
        int dy = pkt.getCenterY() - box.getCenterY();
        return (double) dx * dx + (double) dy * dy;
    }

    /** پاکسازی داخلی هنگام ریست مرحله */
    public void clear() {
        cooldownLeft = 0.0;
    }
}

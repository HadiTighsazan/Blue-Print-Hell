package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.TrojanPacket;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.PacketOps;

import java.util.*;

/**
 * AntiTrojanBehavior – رفتار سیستم آنتی‌تروجان
 *
 * <ul>
 *   <li>در هر به‌روزرسانی، اگر در شعاع مشخصی از مرکز این سیستم، پکت تروجان موجود باشد، آن را به یک پکت پیام‌رسان
 *       (نسخهٔ پاک شده از تروجان) تبدیل می‌کند.</li>
 *   <li>بعد از هر تبدیل موفق، سیستم برای مدت مشخصی غیرفعال (cooldown) می‌شود.</li>
 *   <li>اگر پکت تروجان مستقیماً وارد بافر همین سیستم شود، در {@link #onPacketEnqueued(PacketModel)} همان‌جا پاک‌سازی می‌گردد.</li>
 * </ul>
 */
public final class AntiTrojanBehavior implements SystemBehavior {

    private final SystemBoxModel box;
    private final List<WireModel> wires;          // برای اسکن پکت‌ها روی سیم‌ها
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
        this.box = Objects.requireNonNull(box);
        this.wires = Objects.requireNonNull(wires);
        this.radiusPx = radiusPx;
        this.cooldownSec = cooldownSec;
    }

    @Override
    public void update(double dt) {
        if (cooldownLeft > 0) {
            cooldownLeft -= dt;
            if (cooldownLeft < 0) cooldownLeft = 0;
            return;
        }

        // 1) اسکن سیم‌ها برای پیداکردن اولین تروجان در شعاع
        for (WireModel w : wires) {
            List<PacketModel> list = w.getPackets(); // unmodifiable
            for (PacketModel pkt : list) {
                if (!(pkt instanceof TrojanPacket)) continue;
                if (distanceSquared(pkt, box) <= radiusPx * radiusPx) {
                    // Convert trojan → plain
                    PacketModel clean = PacketOps.clonePlain(pkt);
                    double prog = pkt.getProgress();
                    if (w.removePacket(pkt)) {
                        w.attachPacket(clean, prog);
                        startCooldown();
                        return; // تنها یک تبدیل در هر فعال‌سازی
                    }
                }
            }
        }

        // 2) اسکن بافر خود سیستم (اگر Trojan وارد بافر شد و هنوز cooldown نیست)
        //    (در صورتیکه در onPacketEnqueued missed شده باشد)
        replaceFirstTrojanInBuffer();
    }

    @Override
    public void onPacketEnqueued(PacketModel packet) {
        if (cooldownLeft > 0) return; // غیرفعال
        if (packet instanceof TrojanPacket) {
            // تبدیل فوری در بافر
            PacketModel clean = PacketOps.clonePlain(packet);
            if (replaceInBuffer(packet, clean)) {
                startCooldown();
            }
        }
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        // AntiTrojan فقط با cooldown مدیریت می‌شود؛ این متد لازم نیست فعلاً
    }

    /* --------------------------------------------------------------- */
    /*                          Helpers                                 */
    /* --------------------------------------------------------------- */

    private void startCooldown() {
        cooldownLeft = cooldownSec;
    }

    private boolean replaceInBuffer(PacketModel oldPkt, PacketModel newPkt) {
        Deque<PacketModel> temp = new ArrayDeque<>();
        PacketModel p;
        boolean replaced = false;
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
        PacketModel p;
        boolean converted = false;
        while ((p = box.pollPacket()) != null) {
            if (!converted && p instanceof TrojanPacket) {
                PacketModel clean = PacketOps.clonePlain(p);
                temp.addLast(clean);
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
}

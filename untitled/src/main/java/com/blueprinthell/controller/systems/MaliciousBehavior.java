package com.blueprinthell.controller.systems;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.ProtectedPacket;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.PacketOps;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Random;

/**
 * <h2>MaliciousBehavior – سیستم خراب‌کار</h2>
 * قواعد فاز:
 * <ul>
 *   <li>روی پکت‌های محافظت‌شده (ProtectedPacket) اثری ندارد.</li>
 *   <li>اگر نویز پکت صفر است، ۱ واحد نویز القا می‌کند.</li>
 *   <li>با احتمال مشخص، پکت را به Trojan تبدیل می‌کند.</li>
 *   <li>پکت را مجبور می‌کند از پورت ناسازگار عبور کند (از طریق RouteHints).</li>
 * </ul>
 * این رفتار در لحظهٔ ورود پکت به بافر سیستم (onPacketEnqueued) اعمال می‌شود.
 */
public final class MaliciousBehavior implements SystemBehavior {

    private final SystemBoxModel box;
    private final Random rnd = new Random();
    private final double trojanProbability; // 0..1

    public MaliciousBehavior(SystemBoxModel box, double trojanProbability) {
        this.box = Objects.requireNonNull(box, "box");
        this.trojanProbability = trojanProbability;
    }

    @Override
    public void update(double dt) {
        // رفتار زمان‌محور ندارد
    }

    /** نسخهٔ جدید با پورت ورودی. */
    @Override
    public void onPacketEnqueued(PacketModel packet, com.blueprinthell.model.PortModel enteredPort) {
        applyRules(packet);
    }

    /** سازگاری با امضای قدیمی */
    @Override
    public void onPacketEnqueued(PacketModel packet) {
        applyRules(packet);
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        // فعلاً نیازی نیست
    }

    /* --------------------------------------------------------------- */
    /*                           Core logic                             */
    /* --------------------------------------------------------------- */
    private void applyRules(PacketModel packet) {
        // 1) Protected → untouched
        if (packet instanceof ProtectedPacket || PacketOps.isProtected(packet)) {
            return;
        }

        // 2) نویز صفر؟ یک واحد افزایش
        if (packet.getNoise() == 0.0) {
            packet.increaseNoise(1.0);
        }

        // 3) احتمال تبدیل به تروجان
        if (rnd.nextDouble() < trojanProbability) {
            PacketModel trojan = PacketOps.toTrojan(packet);
            if (trojan != packet) {
                replaceInBuffer(packet, trojan);
                packet = trojan;
            }
        }

        // 4) اجبار به پورت ناسازگار
        RouteHints.setForceIncompatible(packet, true);
    }

    /* جایگزینی امن پکت در بافر بدون تغییر ترتیب سایر پکت‌ها */
    private void replaceInBuffer(PacketModel oldPkt, PacketModel newPkt) {
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
        for (PacketModel q : temp) {
            box.enqueue(q);
        }
    }
}

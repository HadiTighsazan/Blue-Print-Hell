package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config; // only if probability is defined there; else remove
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.ProtectedPacket;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.PacketOps;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Random;

/**
 * MaliciousBehavior – رفتار سیستم خراب‌کار
 *
 * قواعد فاز:
 *  - روی پکت‌های Protected اثری ندارد.
 *  - اگر نویز پکت صفر است، یک واحد نویز القا می‌کند.
 *  - با احتمال مشخص، پکت را به TrojanPacket تبدیل می‌کند.
 *  - پکت‌ها را به «پورت ناسازگار» ارسال می‌کند؛ برای این کار، یک Hint روی پکت می‌گذاریم تا
 *    PacketRouterController پورت ناسازگار را انتخاب کند.
 */
public final class MaliciousBehavior implements SystemBehavior {

    private final SystemBoxModel box;
    private final Random rnd = new Random();
    private final double trojanProbability;

    /**
     * @param trojanProbability احتمال تبدیل به تروجان بین 0..1
     */
    public MaliciousBehavior(SystemBoxModel box, double trojanProbability) {
        this.box = Objects.requireNonNull(box);
        this.trojanProbability = trojanProbability;
    }

    @Override
    public void update(double dt) {
        // رفتار زمان‌محور خاصی ندارد
    }

    @Override
    public void onPacketEnqueued(PacketModel packet) {
        // محافظت شده: بی‌اثر
        if (packet instanceof ProtectedPacket) return;

        // 1) نویز صفر؟ یک واحد اضافه کن
        if (packet.getNoise() == 0) {
            packet.increaseNoise(1);
        }

        // 2) احتمال تبدیل به تروجان
        if (rnd.nextDouble() < trojanProbability) {
            PacketModel trojan = PacketOps.toTrojan(packet);
            if (trojan != packet) {
                // جایگزینی در بافر
                replaceInBuffer(packet, trojan);
                packet = trojan; // برای ادامه کار
            }
        }

        // 3) علامت زدن برای روت ناسازگار
        RouteHints.setForceIncompatible(packet, true);
    }

    /* --------------------------------------------------------------- */
    /*                        helper: replace packet                    */
    /* --------------------------------------------------------------- */
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
        for (PacketModel q : temp) box.enqueue(q);
    }
}

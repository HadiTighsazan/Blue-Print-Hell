package com.blueprinthell;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.PacketDispatcherController;
import com.blueprinthell.model.*;
import com.blueprinthell.motion.MotionStrategyFactory;

import java.util.*;

public final class KeepDistanceManualTest {

    public static void main(String[] args) throws Exception {
        // 1) تنظیم صحنه ساده: یک منبع با 1 خروجی، یک مقصد با 1 ورودی
        SystemBoxModel src  = new SystemBoxModel("SRC", 100, 200, 80, 80,
                List.of(), List.of(PortShape.CIRCLE));
        SystemBoxModel dst  = new SystemBoxModel("DST", 900, 200, 80, 80,
                List.of(PortShape.CIRCLE), List.of()); // سینک/بدون خروجی

        // یک سیم بین SRC(out0) -> DST(in0)
        PortModel out0 = src.getOutPorts().get(0);
        PortModel in0  = dst.getInPorts().get(0);
        WireModel w = new WireModel(out0, in0);

        List<WireModel> wires = List.of(w);
        Map<WireModel, SystemBoxModel> destMap = Map.of(w, dst);

        // 2) Dispatcher فقط برای ورود/خروج از سیم ← با Adapterها کاری نداریم
        PacketDispatcherController dispatcher =
                new PacketDispatcherController(wires, destMap, new CoinModel(), new PacketLossModel());

        // فرض: out0 و in0 همون پورت‌های سیمِ تست هستن
        PacketModel p1 = PacketFactory.create(PacketType.SQUARE, out0, in0);
        p1 = PacketOps.toConfidentialVpn(p1); // تبدیل مستقیم به نوع دوم (۶ واحدی)
        p1.setMotionStrategy(MotionStrategyFactory.create(p1, out0.isCompatible(p1)));

        PacketModel p2 = PacketFactory.create(PacketType.SQUARE, out0, in0);
        p2 = PacketOps.toConfidentialVpn(p2);
        p2.setMotionStrategy(MotionStrategyFactory.create(p2, out0.isCompatible(p2)));


        // 4) روی سیم attach کن با فاصله کمتر از keep-dist
        double L = w.getLength();
        double keep = Config.CONF_VPN_KEEP_DIST_PX;     // مثلاً 120
        double gap  = Math.max(keep * 0.5, 20);         // فاصله‌ی اولیه کمتر از keep
        double pos1 = 0.55;                             // 55% مسیر
        double pos2 = pos1 - (gap / L);                 // عقب‌تر به اندازه gap

        w.attachPacket(p1, pos1);
        w.attachPacket(p2, pos2);

        // 5) حلقه‌ی به‌روزرسانی ساده
        double t = 0.0;
        final double dt = 1.0 / 60.0;
        double minDist = Double.POSITIVE_INFINITY;

        for (int i = 0; i < 360; i++) { // 6 ثانیه
            t += dt;

            // به‌روزرسانی رفتارهای حرکتیِ خود پکت‌ها (اگر پکت‌ها Updatable هستند)
            safeUpdate(p1, dt);
            safeUpdate(p2, dt);

            // Dispatcher مسئول ورود/خروج در سینه‌ی تست ما
            dispatcher.update(dt);


            double dist =  w.getAlongDistance(p1, p2);
            minDist = Math.min(minDist, dist);

            // فقط چند فریم اول و وقتی فاصله کمتر از keep است را لاگ کن
            if (i < 30 || dist < keep + 5) {
                System.out.printf(Locale.US,
                        "t=%.2fs  dist=%.1f  v2=%.1f  v1=%.1f%n",
                        t, dist, p2.getSpeed(), p1.getSpeed());
            }
        }

        System.out.printf("Min distance observed: %.1f (keep=%.1f)%n", minDist, keep);
    }

    private static void safeUpdate(Object o, double dt) {
        if (o instanceof Updatable u) {
            try { u.update(dt); } catch (Throwable ignore) {}
        }
    }
}

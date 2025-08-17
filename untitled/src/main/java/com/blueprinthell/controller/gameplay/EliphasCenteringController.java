package com.blueprinthell.controller.gameplay;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.motion.MotionStrategy;

import java.awt.Point;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scroll of Eliphas (نسخهٔ نقطه‌ای):
 * - با هر خرید، یک نقطه روی سنترلاین یک سیم را برای 30s فعال می‌کنیم.
 * - هر پکتی که از شعاع اثر عبور کند، با «کشش پیوسته» به سنترلاین بازگردانده می‌شود.
 * - هیچ پرش/اسنپ ناگهانی وجود ندارد → تونلینگ رخ نمی‌دهد.
 * - چند خرید = چند نقطهٔ هم‌زمان.
 */
public class EliphasCenteringController implements Updatable {

    // تنظیمات (در صورت لزوم از Config بخوانید)
    private static final double EFFECT_RADIUS_PX     = 100.0;
    private static final double EFFECT_DURATION_SEC  = 30.0;
    private static final double PULL_RATE_PER_SEC    = 16.0; // 8–16 خوبه

    private final List<WireModel> wires;

    // نقاط فعال و زمان باقی‌مانده هر کدام
    private final Map<Point, Double> activePoints = new ConcurrentHashMap<>();

    // پکت‌هایی که الان تحتِ اثر هستند + استراتژی اصلی‌شان
    private final Set<PacketModel> affected = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<PacketModel, MotionStrategy> original = new WeakHashMap<>();

    public EliphasCenteringController(List<WireModel> wires) {
        this.wires = Objects.requireNonNull(wires, "wires");
    }

    /** هر خرید = فعال‌سازی یک نقطهٔ جدید (30 ثانیه) */
    public boolean activateAt(Point p) {
        if (p == null) return false;
        activePoints.put(new Point(p), EFFECT_DURATION_SEC);
        return true;
    }

    /** برای رندر HUD: کپیِ امن از نقاط و زمان باقی‌مانده */
    public Map<Point, Double> getActivePoints() {
        return new HashMap<>(activePoints);
    }
    public double getEffectRadiusPixels() { return EFFECT_RADIUS_PX; }
    public double getEffectDurationSec()  { return EFFECT_DURATION_SEC; }

    @Override
    public void update(double dt) {
        // 1) کم‌کردن تایمر نقاط
        Iterator<Map.Entry<Point, Double>> it = activePoints.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Point, Double> e = it.next();
            double left = e.getValue() - dt;
            if (left <= 0) it.remove();
            else           e.setValue(left);
        }

        // 2) بررسی پکت‌ها روی هر سیم
        for (WireModel w : wires) {
            if (w == null) continue;
            if (w.getPackets().isEmpty()) continue;

            for (PacketModel p : new ArrayList<>(w.getPackets())) {
                boolean inside = isInsideAnyPoint(w, p);
                if (inside) ensureWrapped(p);
                else        restoreIfWrapped(p);
            }
        }

        // 3) اگر دیگه هیچ نقطه‌ای فعال نیست، همهٔ رَپ‌ها را آزاد کن
        if (activePoints.isEmpty() && !affected.isEmpty()) {
            for (PacketModel p : new ArrayList<>(affected)) {
                restoreIfWrapped(p);
            }
        }
    }

    private boolean isInsideAnyPoint(WireModel wire, PacketModel p) {
        if (p == null || wire == null) return false;
        if (activePoints.isEmpty())    return false;

        // ✅ به‌جای سنجشِ pointAt(progress)، مرکز واقعی پکت را بسنج
        double cx = p.getX() + p.getWidth()  / 2.0;
        double cy = p.getY() + p.getHeight() / 2.0;

        double r2 = EFFECT_RADIUS_PX * EFFECT_RADIUS_PX;
        for (Point ap : activePoints.keySet()) {
            double dx = cx - ap.x;
            double dy = cy - ap.y;
            if (dx*dx + dy*dy <= r2) {
                return true;
            }
        }
        return false;
    }


    private void ensureWrapped(PacketModel p) {
        if (affected.contains(p)) return;

        MotionStrategy cur = p.getMotionStrategy();
        if (cur instanceof CenteringWrapper) {
            affected.add(p);
            return;
        }
        if (!original.containsKey(p)) {
            original.put(p, cur);
        }
        p.setMotionStrategy(new CenteringWrapper(original.get(p), PULL_RATE_PER_SEC));
        affected.add(p);
    }

    private void restoreIfWrapped(PacketModel p) {
        if (!affected.contains(p)) return;

        MotionStrategy cur = p.getMotionStrategy();
        if (cur instanceof CenteringWrapper) {
            MotionStrategy back = original.remove(p);
            p.setMotionStrategy(back); // ممکن است null باشد
        }
        affected.remove(p);
    }

    /** Wrapper: بعد از آپدیت delegate، مرکز را با کشش نمایی به سنترلاین می‌برد. */
    private static final class CenteringWrapper implements MotionStrategy {
        private final MotionStrategy delegate;
        private final double pullRate;

        CenteringWrapper(MotionStrategy delegate, double pullRatePerSec) {
            this.delegate = delegate;
            this.pullRate = pullRatePerSec;
        }

        @Override
        public void update(PacketModel p, double dt) {
            if (delegate != null) {
                delegate.update(p, dt);
            }

            WireModel w = p.getCurrentWire();
            if (w == null) return;

            Point pc = w.pointAt(p.getProgress());
            double targetCx = pc.x;
            double targetCy = pc.y;

            double curCx = p.getX() + p.getWidth()  / 2.0;
            double curCy = p.getY() + p.getHeight() / 2.0;

            double alpha = 1.0 - Math.exp(-pullRate * Math.max(0, dt));
            double newCx = curCx + alpha * (targetCx - curCx);
            double newCy = curCy + alpha * (targetCy - curCy);

            int newX = (int) Math.round(newCx - p.getWidth()  / 2.0);
            int newY = (int) Math.round(newCy - p.getHeight() / 2.0);

            p.setX(newX);
            p.setY(newY);
        }
    }
}

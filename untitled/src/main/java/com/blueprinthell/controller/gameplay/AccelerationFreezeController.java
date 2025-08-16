// فایل: src/main/java/com/blueprinthell/controller/AccelerationFreezeController.java
package com.blueprinthell.controller.gameplay;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.motion.MotionStrategy;
import com.blueprinthell.motion.ConstantSpeedStrategy;

import java.awt.Point;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * کنترلر «انجماد شتاب» در یک نقطه روی سیم‌ها:
 * - با پرداخت هزینه و انتخاب یک نقطه، برای مدت مشخصی روی آن نقطه acceleration=0 اعمال نمی‌کنیم،
 *   بلکه استراتژی حرکت پکت‌ها را به «سرعت ثابت» سوییچ می‌کنیم تا با سرعت ثابت ادامه بدهند.
 * - بعد از خروج از ناحیه یا اتمام مدت اثر، استراتژی اصلی پکت به‌طور دقیق برگردانده می‌شود.
 * - دارای cooldown برای فعال‌سازی مجدد.
 */
public class AccelerationFreezeController implements Updatable {

    // --- تنظیمات ---
    private static final double EFFECT_RADIUS_PX    = 30.0;  // شعاع اثر روی سیم (px) - با رندر هم‌راستا نگه دار
    private static final double EFFECT_DURATION_SEC = 20.0;  // مدت اثر هر نقطه (ثانیه)
    private static final double COOLDOWN_SEC        = 30.0;  // مدت کول‌داون بین دو اکتیو (ثانیه)
    private static final double MIN_SPEED_FLOOR     = 0.8;   // کف سرعت محافظتی (px/s) اگر همه منابع سرعت صفر بودند

    private final List<WireModel> wires;

    /** نقاط فریز فعال به‌همراه زمان باقی‌ماندهٔ هرکدام (ثانیه) */
    private final Map<Point, Double> freezePoints = new ConcurrentHashMap<>();

    /** پکت‌هایی که الان داخل ناحیهٔ فریز هستند */
    private final Set<PacketModel> frozen = Collections.newSetFromMap(new WeakHashMap<>());

    /** استراتژی اصلی هر پکت (برای بازگردانی پس از خروج از ناحیه) */
    private final Map<PacketModel, MotionStrategy> originalStrategy = new WeakHashMap<>();

    /** رهگیری progress قبلی جهت تخمین سرعت واقعی از Δprogress/Δt */
    private final Map<PacketModel, Double> lastProgress   = new WeakHashMap<>();
    private final Map<PacketModel, Double> lastKnownSpeed = new WeakHashMap<>();

    /** کول‌داون فعال‌سازی مجدد (ثانیه) */
    private double cooldownRemaining = 0.0;

    /** اگر UI منتظر انتخاب نقطه توسط بازیکن است (برای هماهنگی با Selector/Shop) */
    private boolean waitingForSelection = false;

    public AccelerationFreezeController(List<WireModel> wires) {
        this.wires = Objects.requireNonNull(wires, "wires");
    }

    // ---------------- Updatable ----------------
    @Override
    public void update(double dt) {
        // 1) کاهش کول‌داون
        if (cooldownRemaining > 0) {
            cooldownRemaining = Math.max(0.0, cooldownRemaining - dt);
        }

        // 2) کم‌کردن تایمر نقاط فریز و حذف منقضی‌ها
        boolean anyExpired = false;
        Iterator<Map.Entry<Point, Double>> it = freezePoints.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Point, Double> e = it.next();
            double rem = e.getValue() - dt;
            if (rem <= 0) {
                it.remove();
                anyExpired = true;
            } else {
                e.setValue(rem);
            }
        }
        // اگر همه نقاط تمام شدند، همه پکت‌های فریز را آزاد کن
        if (anyExpired && freezePoints.isEmpty()) {
            unfreezeAll();
        }

        if (freezePoints.isEmpty()) {
            // در این فریم چیزی برای اعمال نیست
            return;
        }

        // 3) پردازش پکت‌ها روی هر سیم
        for (WireModel w : wires) {
            // snapshot برای جلوگیری از ConcurrentModification
            final List<PacketModel> packets = new ArrayList<>(w.getPackets());
            final double wireLen = Math.max(1.0, w.getLength());

            for (PacketModel p : packets) {
                // 3-الف) تخمین سرعت از Δprogress (برای fallback سرعت ثابت)
                final double prev = lastProgress.getOrDefault(p, p.getProgress());
                lastProgress.put(p, p.getProgress());
                if (dt > 0) {
                    final double dp = p.getProgress() - prev;
                    final double v  = Math.abs(dp) * wireLen / dt; // px/s
                    if (v > 0) lastKnownSpeed.put(p, v);
                }

                boolean inside = isInsideAnyFreezePoint(w, p);
                if (inside) {
                    if (!frozen.contains(p)) {
                        freezePacket(w, p);
                    }
                } else {
                    if (frozen.contains(p)) {
                        unfreezePacket(p);
                    }
                }
            }
        }
    }

    // ---------------- منطق فریز/آزادسازی ----------------

    /** آیا این پکت نسبت به یکی از نقاط فعال داخل شعاع اثر است؟ */
    private boolean isInsideAnyFreezePoint(WireModel wire, PacketModel p) {
        if (wire == null || p == null) return false;
        if (freezePoints.isEmpty())     return false;

        final Point pos = wire.pointAt(p.getProgress());
        for (Point fp : freezePoints.keySet()) {
            int dx = pos.x - fp.x, dy = pos.y - fp.y;
            if ((dx * dx + dy * dy) <= EFFECT_RADIUS_PX * EFFECT_RADIUS_PX) {
                return true;
            }
        }
        return false;
    }

    /** سوییچ استراتژی پکت به سرعت ثابت امن و ذخیرهٔ استراتژی اصلی */
    private void freezePacket(WireModel w, PacketModel p) {
        // 1) سرعت امن: ترجیح با سرعت فعلی، سپس آخرین سرعت معتبر، سپس baseSpeed، و در نهایت کف
        double v = p.getSpeed();
        if (v <= 0) v = lastKnownSpeed.getOrDefault(p, 0.0);
        if (v <= 0) v = p.getBaseSpeed();
        if (v <= 0) v = MIN_SPEED_FLOOR;

        // 2) ذخیرهٔ استراتژی اصلی (اگر وجود دارد)
        final MotionStrategy cur = p.getMotionStrategy();
        if (cur != null) originalStrategy.put(p, cur);

        // 3) اعمال استراتژی سرعت ثابت
        p.setMotionStrategy(new ConstantSpeedStrategy(v));
        p.setSpeed(v); // برای سازگاری با کنترلرها/هوک‌هایی که از speed می‌خوانند

        frozen.add(p);
        // System.out.println("[Freeze] packet=" + p + " v=" + v + " wire=" + w);
    }

    /** بازگردانی استراتژی اصلی پکت پس از خروج از ناحیهٔ اثر */
    private void unfreezePacket(PacketModel p) {
        final MotionStrategy orig = originalStrategy.remove(p);
        if (orig != null) {
            p.setMotionStrategy(orig);
        } else {
            // اگر قبلاً استراتژی نداشت، به حالت پیش‌فرض موتور برگردد
            p.setMotionStrategy(null);
        }
        frozen.remove(p);
        // System.out.println("[Unfreeze] packet=" + p);
    }

    /** آزادسازی همهٔ پکت‌های فریز (مثلاً وقتی همه نقاط منقضی شدند) */
    private void unfreezeAll() {
        // کپی برای جلوگیری از ConcurrentModification
        for (PacketModel p : new ArrayList<>(frozen)) {
            unfreezePacket(p);
        }
    }

    // ---------------- API عمومی (برای UI/Shop/Renderer) ----------------

    /**
     * تلاش برای فعال‌سازی اثر در نقطهٔ داده‌شده.
     * اگر در کول‌داون باشیم false برمی‌گرداند.
     */
    public boolean activateFreezeAt(Point point) {
        if (point == null) return false;
        if (cooldownRemaining > 0) return false;

        freezePoints.put(new Point(point), EFFECT_DURATION_SEC);
        cooldownRemaining = COOLDOWN_SEC;
        return true;
    }

    /** آیا هم‌اکنون می‌توان قابلیت را فعال کرد؟ (کول‌داون تمام شده) */
    public boolean canActivate() {
        return cooldownRemaining <= 0.0;
    }

    /** زمان باقیماندهٔ کول‌داون (ثانیه) */
    public double getCooldownRemaining() {
        return Math.max(0.0, cooldownRemaining);
    }

    /** وضعیت انتظار برای انتخاب نقطه (هماهنگی با FreezePointSelector) */
    public void setWaitingForSelection(boolean waiting) {
        this.waitingForSelection = waiting;
    }

    public boolean isWaitingForSelection() {
        return waitingForSelection;
    }

    /** برای رندرر: کپی از نقاط فعال و زمان باقی‌ماندهٔ هرکدام */
    public Map<Point, Double> getActiveFreezePoints() {
        return new HashMap<>(freezePoints);
    }

    /** برای کنترلرهای دیگر (در صورت نیاز به هماهنگی): آیا این پکت در حالت فریز است؟ */
    public boolean isFrozen(PacketModel p) {
        return frozen.contains(p);
    }
}

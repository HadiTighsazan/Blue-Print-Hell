// فایل: untitled/src/main/java/com/blueprinthell/controller/AccelerationFreezeController.java
package com.blueprinthell.controller;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.motion.ConstantSpeedStrategy;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * کنترلر مدیریت نقاط انجماد شتاب روی سیم‌ها
 * وقتی پکتی از این نقاط عبور می‌کند، شتابش صفر می‌شود
 */
public class AccelerationFreezeController implements Updatable {

    private static final double FREEZE_RADIUS = 30.0; // شعاع تأثیر نقطه انجماد (پیکسل)
    private static final double FREEZE_DURATION = 20.0; // مدت زمان فعال بودن (ثانیه)
    private static final double COOLDOWN_DURATION = 30.0; // مدت زمان cooldown (ثانیه)

    private final List<WireModel> wires;
    private final Map<Point, Double> freezePoints; // نقاط انجماد و زمان باقیمانده
    private final Set<PacketModel> affectedPackets; // پکت‌هایی که تحت تأثیر قرار گرفته‌اند
    private double cooldownRemaining = 0.0;
    private boolean waitingForSelection = false;

    public AccelerationFreezeController(List<WireModel> wires) {
        this.wires = Objects.requireNonNull(wires);
        this.freezePoints = new ConcurrentHashMap<>();
        this.affectedPackets = Collections.newSetFromMap(new WeakHashMap<>());
    }

    @Override
    public void update(double dt) {
        // کاهش cooldown
        if (cooldownRemaining > 0) {
            cooldownRemaining = Math.max(0, cooldownRemaining - dt);
        }

        // کاهش زمان نقاط فعال
        Iterator<Map.Entry<Point, Double>> it = freezePoints.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Point, Double> entry = it.next();
            double newTime = entry.getValue() - dt;
            if (newTime <= 0) {
                it.remove(); // حذف نقاط منقضی شده
            } else {
                entry.setValue(newTime);
            }
        }

        // بررسی پکت‌های روی سیم‌ها
        for (WireModel wire : wires) {
            for (PacketModel packet : wire.getPackets()) {
                checkAndFreezePacket(packet, wire);
            }
        }
    }

    /**
     * بررسی و اعمال انجماد به پکت در صورت عبور از نقطه انجماد
     */
    private void checkAndFreezePacket(PacketModel packet, WireModel wire) {
        if (packet == null || wire == null) return;

        Point packetPos = wire.pointAt(packet.getProgress());

        for (Point freezePoint : freezePoints.keySet()) {
            double distance = packetPos.distance(freezePoint);

            if (distance <= FREEZE_RADIUS) {
                if (!affectedPackets.contains(packet)) {
                    // انجماد شتاب پکت
                    packet.setAcceleration(0.0);

                    // تغییر استراتژی حرکت به سرعت ثابت
                    double currentSpeed = packet.getSpeed();
                    packet.setMotionStrategy(new ConstantSpeedStrategy(currentSpeed));

                    affectedPackets.add(packet);

                    System.out.println("[AccelerationFreeze] Packet frozen at speed: " + currentSpeed);
                }
                return; // پکت تحت تأثیر قرار گرفت
            }
        }

        // اگر پکت از محدوده خارج شد، از لیست تحت تأثیر حذف شود
        affectedPackets.remove(packet);
    }

    /**
     * فعال کردن انجماد در یک نقطه
     */
    public boolean activateFreezeAt(Point point) {
        if (cooldownRemaining > 0) {
            System.out.println("[AccelerationFreeze] Still on cooldown: " +
                    String.format("%.1f", cooldownRemaining) + "s remaining");
            return false;
        }

        freezePoints.put(point, FREEZE_DURATION);
        cooldownRemaining = COOLDOWN_DURATION;
        affectedPackets.clear();

        System.out.println("[AccelerationFreeze] Activated at point: " + point);
        return true;
    }

    /**
     * آیا می‌توان از قابلیت استفاده کرد؟
     */
    public boolean canActivate() {
        return cooldownRemaining <= 0;
    }

    /**
     * دریافت زمان باقیمانده cooldown
     */
    public double getCooldownRemaining() {
        return cooldownRemaining;
    }

    /**
     * تنظیم حالت انتظار برای انتخاب نقطه
     */
    public void setWaitingForSelection(boolean waiting) {
        this.waitingForSelection = waiting;
    }

    public boolean isWaitingForSelection() {
        return waitingForSelection;
    }

    /**
     * دریافت نقاط فعال (برای رندرینگ)
     */
    public Map<Point, Double> getActiveFreezePoints() {
        return new HashMap<>(freezePoints);
    }
}
package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;

import java.util.*;

public class ConfidentialThrottleController implements Updatable {

    private final List<WireModel> wires;
    private Map<WireModel, SystemBoxModel> destMap;

    private boolean enabled = true;

    // Phase-4 additions: remember original/base speeds to restore after congestion clears
    private final Map<PacketModel, Double> baseSpeed = new WeakHashMap<>();

    public ConfidentialThrottleController(List<WireModel> wires,
                                          Map<WireModel, SystemBoxModel> destMap) {
        this.wires = Objects.requireNonNull(wires, "wires");
        this.destMap = Objects.requireNonNull(destMap, "destMap");
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }

    public void setDestMap(Map<WireModel, SystemBoxModel> destMap) {
        this.destMap = Objects.requireNonNull(destMap, "destMap");
    }

    // در فایل ConfidentialThrottleController.java - خط 42 به بعد را جایگزین کنید:

    @Override
    public void update(double dt) {
        if (!enabled) return;

        for (WireModel w : wires) {
            SystemBoxModel dest = destMap.get(w);
            if (dest == null) continue;

            // بررسی وضعیت بافر مقصد
            boolean congested = !dest.getBuffer().isEmpty();

            for (PacketModel p : w.getPackets()) {
                // فقط پکت‌های محرمانه را بررسی کن
                if (!(p instanceof ConfidentialPacket)) continue;

                // پکت‌های VPN variant را نادیده بگیر (آن‌ها منطق خودشان را دارند)
                if (PacketOps.isConfidentialVpn(p)) continue;

                // اگر در حال نزدیک شدن به مقصد هستیم (پیشرفت > 70%)
                if (p.getProgress() > 0.7 && !p.isReturning()) {
                    if (congested) {
                        // ذخیره سرعت اصلی و کاهش سرعت
                        baseSpeed.putIfAbsent(p, p.getSpeed());
                        double slow = Config.CONF_SLOW_SPEED;
                        if (p.getSpeed() > slow) {
                            p.setSpeed(slow);
                        }
                    } else {
                        // بازگردانی سرعت اصلی
                        Double orig = baseSpeed.remove(p);
                        if (orig != null && orig > 0) {
                            p.setSpeed(orig);
                        }
                    }
                }
            }
        }
    }
}

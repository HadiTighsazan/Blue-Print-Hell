package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;
import com.blueprinthell.motion.MotionStrategy;
import com.blueprinthell.motion.ConstantSpeedStrategy;

import java.util.*;


public class ConfidentialThrottleController implements Updatable {

    private final List<WireModel> wires;
    private Map<WireModel, SystemBoxModel> destMap;

    private boolean enabled = true;

    private final Map<PacketModel, MotionStrategy> originalStrategy =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final Set<PacketModel> throttled =
            Collections.newSetFromMap(new WeakHashMap<>());

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

    @Override
    public void update(double dt) {
        if (!enabled) return;

        // مقدار کندی: اگر در Config اشتباهاً صفر/منفی بود، کمی مثبتش می‌کنیم
        double slowSpeed = Config.CONF_SLOW_SPEED;
        if (slowSpeed <= 0.0) slowSpeed = 0.1;

        for (WireModel w : wires) {
            final SystemBoxModel dest = destMap.get(w);
            if (dest == null) continue;

            // طبق خواسته‌ی شما: فقط همین بافر مقصد ملاک شلوغی باشد
            final boolean congested = !dest.getBuffer().isEmpty();

            // از اسنپ‌شات استفاده می‌کنیم که اگر لیست داخلی تغییر کرد، ConcurrentModification نگیریم
            final List<PacketModel> packets = new ArrayList<>(w.getPackets());
            for (PacketModel p : packets) {

                // فقط محرمانه‌ها؛ VPNها منطق فاصله‌گذاری خودشان را دارند
                if (!(p instanceof ConfidentialPacket)) continue;
                if (PacketOps.isConfidentialVpn(p)) continue;

                // شرط فاز شما:
                final boolean shouldThrottle =
                        (p.getProgress() > 0.0) && !p.isReturning() && congested;

                if (shouldThrottle) {
                    if (!throttled.contains(p)) {
                        // ذخیره‌ی استراتژی فعلی و سوییچ به کند
                        final MotionStrategy current = p.getMotionStrategy();
                        if (current != null) {
                            originalStrategy.put(p, current);
                        }
                        p.setMotionStrategy(new ConstantSpeedStrategy(slowSpeed));
                        throttled.add(p);
                    }
                } else {
                    // اگر دیگر لازم نیست کند باشد و قبلاً کندش کرده‌ایم: استراتژی اصلی را برگردان
                    if (throttled.contains(p)) {
                        final MotionStrategy original = originalStrategy.remove(p);
                        if (original != null) {
                            p.setMotionStrategy(original);
                        }
                        throttled.remove(p);
                    }
                }
            }
        }
    }

}

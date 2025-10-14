package com.blueprinthell.motion;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.WireModel;

import java.awt.Point;


public class DriftMotionStrategy implements MotionStrategy {

    private final double baseSpeed;
    private final double driftStepPx;
    private final double driftOffsetPx;

    private double distanceSinceFlipPx = 0.0;
    private boolean driftDirectionPos = false;

    // برای مشتق‌گیری عددیِ مماس مسیر
    private static final double EPS_T = 0.01;

    public DriftMotionStrategy(double baseSpeed, double driftStepPx, double driftOffsetPx) {
        this.baseSpeed     = baseSpeed;
        this.driftStepPx   = Math.max(1.0, driftStepPx);
        this.driftOffsetPx = Math.max(0.0, driftOffsetPx);
    }

    @Override
    public void update(PacketModel packet, double dt) {
        WireModel wire = packet.getCurrentWire();
        if (wire == null) return;

        double length = wire.getLength();
        if (length <= 0.0) return;

        double oldP = packet.getProgress();
        double deltaP = (baseSpeed * dt) / length;
        double nextP  = oldP + deltaP;
        if (nextP > 1.0) nextP = 1.0;

        packet.setProgress(nextP);

        double actualDeltaPx = Math.max(0.0, (nextP - oldP) * length);
        distanceSinceFlipPx += actualDeltaPx;

        if (distanceSinceFlipPx >= driftStepPx) {
            // ممکن است در یک فریم بیش از یک "گام" طی شده باشد
            int flips = (int) Math.floor(distanceSinceFlipPx / driftStepPx);
            distanceSinceFlipPx -= flips * driftStepPx;
            if ((flips & 1) == 1) {
                driftDirectionPos = !driftDirectionPos;
            }
        }

        // 3) محاسبهٔ نرمال مسیر در progress فعلی
        double t = nextP;
        double t0 = Math.max(0.0, t - EPS_T);
        double t2 = Math.min(1.0, t + EPS_T);

        Point p0 = wire.pointAt(t0);
        Point p2 = wire.pointAt(t2);
        // مماس تقریبی
        double tx = p2.x - p0.x;
        double ty = p2.y - p0.y;
        double mag = Math.hypot(tx, ty);

        double nx, ny; // نرمالِ واحد
        if (mag > 1e-6) {
            // نرمالِ عمود بر مماس: (-dy, +dx)
            nx = -ty / mag;
            ny =  tx / mag;
        } else {
            // اگر مسیر در این نقطه خیلی کوتاه بود، یک نرمال پیش‌فرض
            nx = 0.0;
            ny = -1.0;
        }

        int sign = driftDirectionPos ? 1 : -1;
        double off = sign * driftOffsetPx;

        Point pc = wire.pointAt(t);
        int drawX = (int) Math.round(pc.x + nx * off - packet.getWidth()  / 2.0);
        int drawY = (int) Math.round(pc.y + ny * off - packet.getHeight() / 2.0);

        packet.setX(drawX);
        packet.setY(drawY);
    }
}

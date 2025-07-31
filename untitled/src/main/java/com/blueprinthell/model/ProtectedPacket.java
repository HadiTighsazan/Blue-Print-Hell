package com.blueprinthell.model;

import com.blueprinthell.config.Config;


public class ProtectedPacket extends PacketModel {

    private double shield;


    public static ProtectedPacket wrap(PacketModel original, double shieldCapacity) {
        ProtectedPacket out = new ProtectedPacket(original.getType(), original.getBaseSpeed(), shieldCapacity);

        // کپی کامل state ران‌تایم
        PacketOps.copyRuntimeState(original, out);

        // اندازه = ۲× اندازهٔ پکت «واقعیِ» ورودی
        int w = original.getWidth();
        int h = original.getHeight();
        if (w > 0 && h > 0) {
            out.setWidth(w * 2);
            out.setHeight(h * 2);
        } else {
            // ایمنی: اگر ابعاد هنوز ست نشده، از sizeUnits کمک بگیریم
            int su2 = original.getType().sizeUnits * 2;
            out.setWidth(su2);
            out.setHeight(su2);
        }

        // نکتهٔ A1: رفتار حرکتی Protected را «شبیه پیام‌رسان» نگه می‌داریم
        // (پروفایل را در کارخانهٔ استراتژی و به صورت تصادفی روی هر سیم تعیین می‌کنیم)
        return out;
    }

    public ProtectedPacket(PacketType type, double baseSpeed, double shieldCapacity) {
        super(type, baseSpeed);
        this.shield = Math.max(0.0, shieldCapacity);
    }


    @Override
    public void increaseNoise(double value) {
        if (value <= 0) return;
        if (shield > 0) {
            double absorbed = Math.min(value, shield);
            shield -= absorbed;
            double remaining = value - absorbed;
            if (remaining > 0) {
                super.increaseNoise(remaining);
            }
        } else {
            super.increaseNoise(value);
        }
    }

    @Override
    public void resetNoise() {
        super.resetNoise();
        shield = Config.DEFAULT_SHIELD_CAPACITY;
    }


    public double getShield() { return shield; }

    public boolean isShieldDepleted() { return shield <= 0.0; }


}

package com.blueprinthell.model;

import java.io.Serializable;
import java.util.Objects;


public class ConfidentialPacket extends PacketModel implements Serializable {
    private static final long serialVersionUID = 3L;


    public ConfidentialPacket(PacketType type, double baseSpeed) {
        super(type, baseSpeed);
    }


    public static ConfidentialPacket wrap(PacketModel original) {
        Objects.requireNonNull(original, "packet");
        ConfidentialPacket out = new ConfidentialPacket(original.getType(), original.getBaseSpeed());

        // کپی state ران‌تایم (سیم/پیشرفت/سرعت/شتاب/نویز)
        copyRuntimeState(original, out);

        // --- اندازه: 4 واحد مطلق نسبت به واحدِ نوع اولیه ---
        int suOrig = Math.max(1, original.getType().sizeUnits);
        int w = original.getWidth();
        int h = original.getHeight();
        if (w > 0 && h > 0) {
            double pxPerUnitW = (double) w / suOrig;
            double pxPerUnitH = (double) h / suOrig;
            out.setWidth((int) Math.round(pxPerUnitW * 4));
            out.setHeight((int) Math.round(pxPerUnitH * 4));
        }
        return out;
    }


    public boolean isConfidential() {
        return true;
    }


    private static void copyRuntimeState(PacketModel src, PacketModel dst) {
        if (src.getCurrentWire() != null) {
            dst.attachToWire(src.getCurrentWire(), src.getProgress());
        } else {
            dst.setProgress(src.getProgress());
        }
        dst.setSpeed(src.getSpeed());
        dst.setAcceleration(src.getAcceleration());
        if (src.getNoise() > 0) {
            dst.increaseNoise(src.getNoise());
        }
    }
}

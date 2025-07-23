package com.blueprinthell.model.large;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.WireModel;

import java.io.Serializable;
import java.util.Objects;

/**
 * <h2>BitPacket – بیت‌پکت حاصل از توزیع پکت‌های حجیم</h2>
 * <p>
 *  هر BitPacket از نظر حرکتی مثل یک پیام‌رسان سایز 1 عمل می‌کند، اما برای اینکه بتوانیم آن را به گروه مربوطه وصل کنیم
 *  اطلاعات کمکی مثل {@code groupId}، {@code parentSizeUnits} و یک {@code index} محلی نگه می‌داریم.
 * </p>
 */
public class BitPacket extends PacketModel implements Serializable {
    private static final long serialVersionUID = 12L;

    /** شناسهٔ گروهی که این بیت‌پکت به آن تعلق دارد. */
    private final int groupId;
    /** اندازهٔ پکت حجیم اصلی (برای محاسبات Loss). */
    private final int parentSizeUnits;
    /** اندیس این بیت در گروه (0..expectedBits-1) – اختیاری اما برای دیباگ مفید است. */
    private final int indexInGroup;
    /** شناسهٔ رنگ جهت UI (می‌تواند 0 باشد). */
    private final int colorId;

    /** آیا این بیت قبلاً در Merger ثبت شده است؟ (برای جلوگیری از دوباره‌شماری) */
    private boolean registeredAtMerger = false;

    /* --------------------------------------------------------------- */
    /*                              CTORS                               */
    /* --------------------------------------------------------------- */

    public BitPacket(PacketType type,
                     double baseSpeed,
                     int groupId,
                     int parentSizeUnits,
                     int indexInGroup,
                     int colorId) {
        super(type, baseSpeed);
        this.groupId = groupId;
        this.parentSizeUnits = parentSizeUnits;
        this.indexInGroup = indexInGroup;
        this.colorId = colorId;
    }

    /**
     * کپی state از یک نمونهٔ موجود (پیش از جدا شدن یا از روی الگو).
     */
    public static BitPacket fromSample(PacketModel sample,
                                       int groupId,
                                       int parentSizeUnits,
                                       int indexInGroup,
                                       int colorId) {
        Objects.requireNonNull(sample, "sample");
        BitPacket bp = new BitPacket(sample.getType(), sample.getBaseSpeed(),
                groupId, parentSizeUnits, indexInGroup, colorId);
        copyRuntimeState(sample, bp);
        return bp;
    }

    /* --------------------------------------------------------------- */
    /*                               API                                 */
    /* --------------------------------------------------------------- */

    public int getGroupId()            { return groupId; }
    public int getParentSizeUnits()    { return parentSizeUnits; }
    public int getIndexInGroup()       { return indexInGroup; }
    public int getColorId()            { return colorId; }

    public boolean isRegisteredAtMerger() { return registeredAtMerger; }
    public void markRegisteredAtMerger()  { this.registeredAtMerger = true; }

    /* --------------------------------------------------------------- */
    /*                        Internal helpers                          */
    /* --------------------------------------------------------------- */

    private static void copyRuntimeState(PacketModel src, PacketModel dst) {
        WireModel w = src.getCurrentWire();
        if (w != null) {
            dst.attachToWire(w, src.getProgress());
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

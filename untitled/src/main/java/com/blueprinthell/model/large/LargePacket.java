package com.blueprinthell.model.large;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.WireModel;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * <h2>LargePacket – پکت حجیم</h2>
 * <p>
 *  یک پکت ویژه که دادهٔ حجیم حمل می‌کند. ویژگی‌ها:
 * </p>
 * <ul>
 *   <li>"سازگاری" با پورت‌ها معنایی ندارد؛ Router باید اجازهٔ عبور از هر پورت را بدهد.</li>
 *   <li>در ورود به سیستم Distributor به N بیت‌پکت (Messenger size=1) تقسیم می‌شود.</li>
 *   <li>در Merger با جمع شدن تمام بیت‌ها دوباره به LargePacket تبدیل می‌شود.</li>
 *   <li>شناسهٔ گروه (<code>groupId</code>) برای رهگیری بیت‌ها استفاده می‌شود.</li>
 * </ul>
 *
 * <p>
 *  این کلاس از {@link PacketModel} ارث‌بری می‌کند تا در همهٔ جای کد مانند دیگر پکت‌ها رفتار کند. اندازهٔ گرافیکی آن از
 *  طریق سازنده تنظیم می‌شود (بر اساس sizeUnits).
 * </p>
 */
public class LargePacket extends PacketModel implements Serializable {
    private static final long serialVersionUID = 11L;

    /** اندازهٔ پکت حجیم (واحدی که در پروژه آمده؛ مثلاً 8 یا 10). */
    private final int originalSizeUnits;

    /** شناسهٔ گروهی که هنگام توزیع ساخته شده است. -1 یعنی هنوز گروهی ندارد (پکت اولیه قبل از توزیع). */
    private int groupId = -1;

    /** تعداد بیت‌های مورد انتظار برای بازسازی. معمولاً == originalSizeUnits. */
    private int expectedBits;

    /** رنگ/شناسهٔ نمایشی برای UI؛ اختیاری. */
    private int colorId;

    /** آیا این پکت نتیجهٔ «Merge» است؟ فقط جهت اطلاع/دیباگ. */
    private boolean rebuiltFromBits;

    /* --------------------------------------------------------------- */
    /*                              CTORS                               */
    /* --------------------------------------------------------------- */

    /**
     * سازندهٔ پایه برای ساخت پکت حجیم. نوع PacketType را هنوز حفظ می‌کنیم تا با سیستم coin موجود تداخلی نداشته باشد.
     * @param type               نوع منطقی (فعلاً استفاده از enum فعلی – می‌توان بعدها enum جدا ساخت)
     * @param baseSpeed          سرعت پایه (برای MotionStrategy)
     * @param originalSizeUnits  اندازهٔ حجیم (8، 10، ...)
     */
    public LargePacket(PacketType type, double baseSpeed, int originalSizeUnits) {
        super(type, baseSpeed);
        this.originalSizeUnits = originalSizeUnits;
        this.expectedBits = originalSizeUnits;
    }

    /** سازندهٔ کامل با اطلاعات گروه. */
    public LargePacket(PacketType type, double baseSpeed,
                       int originalSizeUnits,
                       int groupId,
                       int expectedBits,
                       int colorId,
                       boolean rebuiltFromBits) {
        super(type, baseSpeed);
        this.originalSizeUnits = originalSizeUnits;
        this.groupId = groupId;
        this.expectedBits = expectedBits;
        this.colorId = colorId;
        this.rebuiltFromBits = rebuiltFromBits;
    }

    /* --------------------------------------------------------------- */
    /*                         Factory helpers                          */
    /* --------------------------------------------------------------- */

    /**
     * ساخت یک LargePacket جدید با کپی‌کردن state از یک پکت نمونه (مثلاً اولین بیت یا نسخهٔ اولیه).
     */
    public static LargePacket fromSample(PacketModel sample,
                                         int originalSizeUnits,
                                         int groupId,
                                         int expectedBits,
                                         int colorId,
                                         boolean rebuiltFromBits) {
        Objects.requireNonNull(sample, "sample");
        LargePacket lp = new LargePacket(sample.getType(), sample.getBaseSpeed(), originalSizeUnits,
                groupId, expectedBits, colorId, rebuiltFromBits);
        copyRuntimeState(sample, lp);
        return lp;
    }

    /**
     * نسخهٔ سریع برای وقتی هنوز گروه تعریف نشده (پکت اولیه قبل از Distributor).
     */
    public static LargePacket createRaw(PacketType type, double baseSpeed, int sizeUnits) {
        return new LargePacket(type, baseSpeed, sizeUnits);
    }

    /* --------------------------------------------------------------- */
    /*                              API                                 */
    /* --------------------------------------------------------------- */

    public int getOriginalSizeUnits() { return originalSizeUnits; }
    public int getGroupId()           { return groupId; }
    public int getExpectedBits()      { return expectedBits; }
    public int getColorId()           { return colorId; }
    public boolean isRebuiltFromBits(){ return rebuiltFromBits; }

    public boolean hasGroup()         { return groupId >= 0; }

    public void setGroupInfo(int groupId, int expectedBits, int colorId) {
        if (this.groupId >= 0) return; // already set
        this.groupId = groupId;
        this.expectedBits = expectedBits;
        this.colorId = colorId;
    }

    public void markRebuilt() { this.rebuiltFromBits = true; }

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

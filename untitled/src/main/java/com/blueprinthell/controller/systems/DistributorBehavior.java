package com.blueprinthell.controller.systems;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.motion.KinematicsProfile;
import com.blueprinthell.motion.KinematicsRegistry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Random;

/**
 * <h2>DistributorBehavior</h2>
 * رفتار سیستم Distributor:
 * <ul>
 *   <li>اگر پکت ورودی از نوع {@link LargePacket} باشد، آن را به تعداد اندازه‌اش بیت‌پکت تقسیم می‌کند.</li>
 *   <li>برای هر گروه یک شناسه یکتا از {@link LargeGroupRegistry} می‌گیرد و رنگ/اطلاعات را ست می‌کند.</li>
 *   <li>LargePacket اصلی از بافر حذف می‌شود و BitPacket ها جایگزین می‌شوند. اگر بافر جا نداشت → PacketLoss ثبت می‌شود و در رجیستری نیز markLost می‌کنیم.</li>
 *   <li>برای BitPacket ها پروفایل حرکتی مناسب (MSG1) ست می‌شود.</li>
 * </ul>
 */
public final class DistributorBehavior implements SystemBehavior {

    private final SystemBoxModel box;
    private final LargeGroupRegistry groupRegistry;
    private final PacketLossModel lossModel;
    private final Random rnd = new Random();

    public DistributorBehavior(SystemBoxModel box,
                               LargeGroupRegistry registry,
                               PacketLossModel lossModel) {
        this.box = Objects.requireNonNull(box, "box");
        this.groupRegistry = Objects.requireNonNull(registry, "registry");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
    }

    @Override
    public void update(double dt) {
        // No time-based logic for now.
    }

    @Override
    public void onPacketEnqueued(PacketModel packet) {
        if (!(packet instanceof LargePacket lp)) return; // فقط LargePacket را تقسیم می‌کنیم

        // 1) اطلاعات گروه را بساز اگر هنوز ندارد
        int originalSize = lp.getOriginalSizeUnits();
        int expectedBits = originalSize; // طبق فاز: تعداد بیت‌پکت برابر اندازه است
        int colorId = rnd.nextInt(256);  // سلیقه‌ای؛ UI می‌تواند تفسیر کند
        int groupId;
        if (!lp.hasGroup()) {
            groupId = groupRegistry.createGroup(originalSize, expectedBits, colorId);
            lp.setGroupInfo(groupId, expectedBits, colorId); // اگر لازم است بعداً از آن استفاده کنیم
        } else {
            groupId = lp.getGroupId();
            colorId = lp.getColorId();
        }

        // 2) پکت اصلی را از بافر حذف و بیت‌پکت‌ها را جایگزین کن
        replaceLargeWithBits(lp, groupId, originalSize, expectedBits, colorId);
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        // Distributor رفتار خاصی در enable/disable ندارد
    }

    /* --------------------------------------------------------------- */
    /*                            Helpers                               */
    /* --------------------------------------------------------------- */

    private void replaceLargeWithBits(LargePacket large,
                                      int groupId,
                                      int parentSize,
                                      int expectedBits,
                                      int colorId) {
        Deque<PacketModel> temp = new ArrayDeque<>();
        PacketModel p;
        boolean removed = false;
        while ((p = box.pollPacket()) != null) {
            if (!removed && p == large) {
                removed = true; // skip adding large
            } else {
                temp.addLast(p);
            }
        }
        // حالا temp شامل باقی پکت هاست. Large حذف شد.

        // BitPacket ها را بساز و اضافه کن
        int lost = 0;
        for (int i = 0; i < expectedBits; i++) {
            BitPacket bit = new BitPacket(large.getType(), large.getBaseSpeed(),
                    groupId, parentSize, i, colorId);
            // ست پروفایل حرکتی به MSG1 (یا هر پروفایل مناسب پکت اندازه 1)
            KinematicsRegistry.setProfile(bit, KinematicsProfile.MSG1);
            // تلاش برای enqueue
            if (!box.enqueue(bit)) {
                lost++;
                lossModel.increment();
                groupRegistry.markBitLost(groupId, 1);
            }
        }
        // بازگرداندن بقیه پکت‌ها به بافر
        for (PacketModel q : temp) box.enqueue(q);
    }
}

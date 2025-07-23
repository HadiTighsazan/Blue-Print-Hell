package com.blueprinthell.controller.systems;

import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.model.large.LargeGroupRegistry.GroupState;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.motion.KinematicsRegistry;

import java.util.*;

/**
 * <h2>MergerBehavior</h2>
 * رفتار سیستم Merger:
 * <ul>
 *   <li>بیت‌پکت‌های یک گروه را جمع‌آوری می‌کند (registerArrival در {@link LargeGroupRegistry}).</li>
 *   <li>وقتی همهٔ بیت‌ها رسیدند، آن‌ها را از بافر حذف کرده و یک {@link LargePacket} جدید می‌سازد و enqueue می‌کند.</li>
 *   <li>در صورت بسته بودن گروه یا دریافت دوبارهٔ یک بیت ثبت‌شده، بیت نادیده گرفته یا Drop می‌شود (PacketLoss++)</li>
 *   <li>اگر بافر جا نداشته باشد، LargePacket یا بیت‌های اضافه Drop شده و به Loss اضافه می‌شود.</li>
 * </ul>
 */
public final class MergerBehavior implements SystemBehavior {

    private final SystemBoxModel box;
    private final LargeGroupRegistry registry;
    private final PacketLossModel lossModel;

    public MergerBehavior(SystemBoxModel box,
                          LargeGroupRegistry registry,
                          PacketLossModel lossModel) {
        this.box = Objects.requireNonNull(box, "box");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
    }

    @Override
    public void update(double dt) {
        // No periodic logic needed currently
    }

    @Override
    public void onPacketEnqueued(PacketModel packet) {
        if (!(packet instanceof BitPacket bp)) return; // فقط بیت‌پکت‌ها برای مرج اهمیت دارند

        GroupState st = registry.get(bp.getGroupId());
        if (st == null || st.isClosed()) {
            // گروه ناشناخته یا بسته شده → بیت بی‌اثر/گمشده
            lossModel.increment();
            return;
        }

        // جلوگیری از ثبت دوباره
        if (!bp.isRegisteredAtMerger()) {
            registry.registerArrival(bp.getGroupId(), bp);
            bp.markRegisteredAtMerger();
        }

        // اگر کامل شد، ترکیب کن
        if (st.isComplete()) {
            mergeGroup(st);
        }
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        // Merger رفتار مخصوص enable/disable ندارد فعلاً
    }

    /* --------------------------------------------------------------- */
    /*                             Helpers                              */
    /* --------------------------------------------------------------- */

    /**
     * تمام بیت‌پکت‌های این گروه را از بافر خارج می‌کند، LargePacket می‌سازد و دوباره enqueue می‌کند.
     */
    private void mergeGroup(GroupState st) {
        int groupId = st.groupId;
        // 1) بیرون کشیدن همه بیت‌های این گروه از بافر
        List<BitPacket> bits = extractBitsFromBuffer(groupId);

        // ممکن است بعضی بیت‌ها در بافر نبودند (مثلاً روی سیم هستند)؛ collectedPackets در GroupState آن‌ها را دارد
        // برای ساخت LargePacket کافی است یک نمونه داشته باشیم تا state را کپی کنیم
        PacketModel sample = bits.isEmpty() ? (st.getCollectedPackets().isEmpty() ? null : st.getCollectedPackets().get(0)) : bits.get(0);
        if (sample == null) {
            // هیچ نمونه‌ای در دسترس نیست → نمی‌توان LargePacket ساخت، گروه را ببندیم و Loss حساب کنیم
            registry.closeGroup(groupId);
            return;
        }

        // 2) ساخت پکت حجیم
        LargePacket large = LargePacket.fromSample(sample,
                st.originalSizeUnits,
                groupId,
                st.expectedBits,
                st.colorId,
                true);
        // پروفایل حرکتی را از بیت نمونه کپی می‌کنیم تا قوانین سرعت حفظ شود
        KinematicsRegistry.copyProfile(sample, large);

        // 3) تلاش برای enqueue LargePacket
        if (!box.enqueue(large)) {
            // جا نشد → Loss
            lossModel.increment();
            // بیت‌ها همین الان حذف شده‌اند؛ به عنوان از دست رفتن داده حساب می‌شود
        }

        // 4) بستن و پاکسازی گروه
        registry.closeGroup(groupId);
        registry.removeGroup(groupId);
    }

    /**
     * همهٔ بیت‌های گروه مشخص را از بافر SystemBox حذف و برمی‌گرداند.
     */
    private List<BitPacket> extractBitsFromBuffer(int groupId) {
        Deque<PacketModel> temp = new ArrayDeque<>();
        List<BitPacket> result = new ArrayList<>();
        PacketModel p;
        while ((p = box.pollPacket()) != null) {
            if (p instanceof BitPacket bp && bp.getGroupId() == groupId) {
                result.add(bp);
            } else {
                temp.addLast(p);
            }
        }
        for (PacketModel q : temp) box.enqueue(q);
        return result;
    }
}

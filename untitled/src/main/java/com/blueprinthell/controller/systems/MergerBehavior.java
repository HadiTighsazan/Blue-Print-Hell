package com.blueprinthell.controller.systems;

import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.model.large.LargeGroupRegistry.GroupState;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.motion.KinematicsRegistry;

import java.util.*;

/**
 * <h2>MergerBehavior</h2>
 * <p>به محض ورود هر <i>BitPacket</i> به بافر این Merger، آن را در {@link LargeGroupRegistry}
 * ثبت می‌کنیم. وقتی تمام بیت‌های یک گروه رسیدند، به {@link #mergeGroup(GroupState)} می‌رویم تا یک
 * {@link LargePacket} بسازیم. اگر گروه هرگز تکمیل نشود ولی بسته شود (مثلاً زمان تمام یا
 * بسته‌شدن دستی)، Loss بر اساس فرمول فاز حساب می‌شود.</p>
 */
public final class MergerBehavior implements SystemBehavior {

    private final SystemBoxModel      box;
    private final LargeGroupRegistry  registry;
    private final PacketLossModel     lossModel;

    public MergerBehavior(SystemBoxModel box,
                          LargeGroupRegistry registry,
                          PacketLossModel lossModel) {
        this.box       = Objects.requireNonNull(box, "box");
        this.registry  = Objects.requireNonNull(registry, "registry");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
    }

    /* --------------------------------------------------------------- */
    /*                        Frame update (noop)                       */
    /* --------------------------------------------------------------- */
    @Override public void update(double dt) { /* no periodic logic */ }

    /* --------------------------------------------------------------- */
    /*                   Packet arrival into buffer                     */
    /* --------------------------------------------------------------- */

    @Override
    public void onPacketEnqueued(PacketModel packet) {
        onPacketEnqueued(packet, null);
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (!(packet instanceof BitPacket bp)) return;

        GroupState st = registry.get(bp.getGroupId());
        // (کیس‌های drop روی گروه نامعتبر یا بسته‌شده مثل قبل)

        // 1) ثبت بیت و دریافت نتیجهٔ تکمیل شدن گروه
        boolean completed = registry.registerArrival(bp.getGroupId(), bp);

        // 2) علامت‌گذاری جهت جلوگیری از ثبت مجدد
        bp.markRegisteredAtMerger();

        // 3) اگر گروه کامل شده، ادغام را انجام بده
        if (completed && st.isComplete()) {
            mergeGroup(st);
        }
        // توجه: اینجا **هیچ** removePacketFromBuffer انجام نمی‌دهیم
    }



    @Override public void onEnabledChanged(boolean enabled) { /* nothing */ }

    /* --------------------------------------------------------------- */
    /*                          Merge logic                             */
    /* --------------------------------------------------------------- */

    private void mergeGroup(GroupState st) {
        int gid = st.groupId;

        // 1) بیرون کشیدن بیت‌ها از بافر
        List<BitPacket> bits = extractBitsFromBuffer(gid);
        // اطمینان از تعداد درست (در برخی edge ها ممکن است کمتر باشد) – واقعاً باید expectedBits باشد
        int collected = bits.size();
        if (collected < st.expectedBits) {
            // هنوز کامل نیست (ممکن است برخی بیت‌ها روی سیم باشند) – منتظر شو
            return;
        }

        PacketModel sample = bits.get(0);
        LargePacket large = LargePacket.fromSample(sample,
                st.originalSizeUnits,
                gid,
                st.expectedBits,
                st.colorId,
                true);
        KinematicsRegistry.copyProfile(sample, large);

        // 2) تلاش برای enqueue در باکس
        if (!box.enqueue(large)) {
            // جا نشد → از دست رفتن Large و بیت‌ها
            lossModel.incrementBy(st.originalSizeUnits);
        }

        // 3) گروه را ببند و حذف کن
        registry.closeGroup(gid);
        registry.removeGroup(gid);
    }

    /* --------------------------------------------------------------- */
    /*                       Helper functions                           */
    /* --------------------------------------------------------------- */

    /** حذف یک پکت از بافر بدون به‌هم‌زدن ترتیب سایرین */
    private void removePacketFromBuffer(PacketModel pkt) {
        Deque<PacketModel> tmp = new ArrayDeque<>();
        PacketModel p;
        while ((p = box.pollPacket()) != null) {
            if (p == pkt) continue; // skip
            tmp.addLast(p);
        }
        for (PacketModel q : tmp) box.enqueue(q);
    }

    /** همهٔ بیت‌های یک گروه را از بافر خارج می‌کند و برمی‌گرداند. */
    private List<BitPacket> extractBitsFromBuffer(int groupId) {
        Deque<PacketModel> tmp = new ArrayDeque<>();
        List<BitPacket> out = new ArrayList<>();
        PacketModel p;
        while ((p = box.pollPacket()) != null) {
            if (p instanceof BitPacket bp && bp.getGroupId() == groupId) {
                out.add(bp);
            } else {
                tmp.addLast(p);
            }
        }
        for (PacketModel q : tmp) box.enqueue(q);
        return out;
    }
}

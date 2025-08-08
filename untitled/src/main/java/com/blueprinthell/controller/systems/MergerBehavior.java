package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.model.large.LargeGroupRegistry.GroupState;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.motion.KinematicsRegistry;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * MergerBehavior – هماهنگ با SystemBoxModel جدید:
 *   • Bit-Packetها را از bitBuffer برمی‌دارد و پس از هر ۴ تا، LargePacket
 *     تازه را در largeBuffer جعبه قرار می‌دهد (box.enqueue(..) خودش تشخیص می‌دهد).
 *   • از متد عمومی box.removeFromBuffer(..) برای حذف امن استفاده می‌کند؛
 *     بنابراین کد قبلیِ دست‌ساز برای پیمایش بافر حذف شد.
 */
public final class MergerBehavior implements SystemBehavior {

    /* === Constants =================================================== */
    private static final int BITS_PER_MERGE = 4;

    /* === Dependencies ================================================ */
    private final SystemBoxModel     box;
    private final LargeGroupRegistry registry;
    private final PacketLossModel    lossModel;

    /* === State ======================================================= */
    private final Map<Integer, GroupContext> groups  = new HashMap<>();
    private final Deque<Integer>             rrQueue = new ArrayDeque<>();

    public MergerBehavior(SystemBoxModel box,
                          LargeGroupRegistry registry,
                          PacketLossModel lossModel) {
        this.box       = Objects.requireNonNull(box, "box");
        this.registry  = Objects.requireNonNull(registry, "registry");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
    }

    /* ===================== Packet Arrived ============================ */
    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (!(packet instanceof BitPacket bp)) return;

        /* حذف امن بیت از بافر بیت */
        if (!box.removeFromBuffer(bp)) return;

        final int gid = bp.getGroupId();
        GroupContext ctx = groups.computeIfAbsent(gid, GroupContext::new);
        ctx.bits.addLast(bp);

        /* اطمینان از وجود گروه در رجیستری */
        GroupState st = registry.get(gid);
        if (st == null) {
            registry.createGroupWithId(
                    gid,
                    bp.getParentSizeUnits(),     // original N
                    bp.getParentSizeUnits(),     // expectedBits = N
                    bp.getColorId()
            );
            st = registry.get(gid);
        }

        registry.registerArrival(gid, bp);

        /* آمادهٔ مرج شد؟ */
        if (ctx.bits.size() >= BITS_PER_MERGE && !rrQueue.contains(gid)) {
            rrQueue.addLast(gid);
        }
    }

    /* ===================== Game-loop ================================= */
    @Override
    public void update(double dt) {
        processRoundRobinMerges();     // مرج عادلانه

        /* بستن گروه‌های تمام‌شده */
        List<Integer> done = new ArrayList<>();
        for (Map.Entry<Integer, GroupContext> e : groups.entrySet()) {
            GroupContext ctx = e.getValue();
            if (ctx.isDone()) {
                closeGroup(ctx);
                done.add(e.getKey());
            }
        }
        for (Integer gid : done) {
            groups.remove(gid);
            rrQueue.remove(gid);
        }
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        if (enabled) clear();
    }

    /* ===================== Merge Logic =============================== */
    private void processRoundRobinMerges() {
        int guard = 1024;     // جلوگیری از حلقهٔ بی‌نهایت
        while (!rrQueue.isEmpty() && guard-- > 0) {
            int gid = rrQueue.removeFirst();
            GroupContext ctx = groups.get(gid);
            if (ctx == null || ctx.bits.size() < BITS_PER_MERGE) continue;

            /* برداشتن ۴ بیت */
            List<BitPacket> four = new ArrayList<>(BITS_PER_MERGE);
            for (int i = 0; i < BITS_PER_MERGE; i++) four.add(ctx.bits.removeFirst());

            LargePacket merged = createMergedPacket(four);

            /* تلاش برای قرار دادن در largeBuffer */
            if (!box.enqueue(merged)) {
                /* جا نیست → بیت‌ها را برگردان و از حلقه خارج شو */
                for (int i = BITS_PER_MERGE - 1; i >= 0; i--) ctx.bits.addFirst(four.get(i));
                rrQueue.addFirst(gid);
                break;
            }

            ctx.mergeCount++;

            /* اگر دوباره ≥۴ بیت ماند، به انتهای صف برگردد */
            if (ctx.bits.size() >= BITS_PER_MERGE) rrQueue.addLast(gid);
        }
    }

    private LargePacket createMergedPacket(List<BitPacket> bits) {
        BitPacket first = bits.get(0);

        Color c = first.getColor();
        int chunkUnits = BITS_PER_MERGE;

        LargePacket lp = new LargePacket(
                PacketType.SQUARE,
                Config.DEFAULT_PACKET_SPEED,
                chunkUnits
        );
        lp.setCustomColor(c);
        lp.setWidth (chunkUnits * Config.PACKET_SIZE_MULTIPLIER);
        lp.setHeight(chunkUnits * Config.PACKET_SIZE_MULTIPLIER);

        lp.setGroupInfo(first.getGroupId(), first.getParentSizeUnits(), first.getColorId());

        KinematicsRegistry.copyProfile(first, lp);
        return lp;
    }

    /* ===================== Registry / Loss =========================== */
    private void closeGroup(GroupContext ctx) {
        int totalBitsMerged = ctx.mergeCount * BITS_PER_MERGE;
        GroupState st = registry.get(ctx.groupId);
        if (st == null) return;

        registry.registerPartialMerge(ctx.groupId, totalBitsMerged, BITS_PER_MERGE);

        int actualLoss = registry.calculateActualLoss(ctx.groupId);
        if (actualLoss > 0) lossModel.incrementBy(actualLoss);

        if (totalBitsMerged >= st.expectedBits) {
            registry.closeGroup(ctx.groupId);
            registry.removeGroup(ctx.groupId);
        }
    }

    /* ===================== Helpers =================================== */
    private void clear() {
        groups.clear();
        rrQueue.clear();
    }

    private static final class GroupContext {
        final int groupId;
        final Deque<BitPacket> bits = new ArrayDeque<>();
        int mergeCount = 0;
        GroupContext(int id){ this.groupId = id; }
        boolean isDone(){ return bits.isEmpty(); }
    }
}

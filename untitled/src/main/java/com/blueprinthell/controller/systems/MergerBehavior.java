package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.model.large.LargeGroupRegistry.GroupState;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.motion.KinematicsRegistry;

import java.awt.*;
import java.util.*;
import java.util.List;


public final class MergerBehavior implements SystemBehavior {

    /* === Constants ======================================================= */
    private static final int BITS_PER_MERGE  = 4;
    private static final int MERGED_PKT_SIZE = 4;

    /* === Dependencies ==================================================== */
    private final SystemBoxModel     box;
    private final LargeGroupRegistry registry;
    private final PacketLossModel    lossModel;

    /* === State =========================================================== */
    private final Map<Integer, GroupContext> groups = new HashMap<>();

    public MergerBehavior(SystemBoxModel box,
                          LargeGroupRegistry registry,
                          PacketLossModel lossModel) {
        this.box       = Objects.requireNonNull(box, "box");
        this.registry  = Objects.requireNonNull(registry, "registry");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
    }

    /* ---------------------------------------------------------------------
     *  SystemBehavior implementation
     * ------------------------------------------------------------------ */

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (!(packet instanceof BitPacket bp)) return;

        /* Remove immediately from buffer to avoid duplicate handling. */
        if (!box.getBuffer().remove(bp)) return;

        GroupContext ctx = groups.computeIfAbsent(bp.getGroupId(), GroupContext::new);
        ctx.bits.add(bp);

        /* Create registry records on first arrival. */
        GroupState st = registry.get(ctx.groupId);
        if (st == null) {
            registry.createGroupWithId(ctx.groupId,
                    bp.getParentSizeUnits(),
                    bp.getParentSizeUnits(),
                    bp.getColorId());
            st = registry.get(ctx.groupId);
        }
        registry.registerArrival(ctx.groupId, bp);

        tryMerge(ctx);
    }

    @Override
    public void update(double dt) {
        /* Second chance for groups that previously waited due to full buffer. */
        groups.values().forEach(this::tryMerge);
    }

    @Override
    public void onEnabledChanged(boolean enabled) {/* no‑op */}

    /* ---------------------------------------------------------------------
     *  Core merge workflow
     * ------------------------------------------------------------------ */

    private void tryMerge(GroupContext ctx) {
        /* Attempt as many merges as possible until the buffer fills. */
        while (ctx.ready()) {
            /* Snapshot first 4 bits – used both for packet creation and cleanup. */
            List<BitPacket> toMerge = new ArrayList<>(ctx.bits.subList(0, BITS_PER_MERGE));

            LargePacket merged = createMergedPacket(toMerge);
            if (!box.enqueue(merged)) {
                /* Buffer full — MOVE bits to pending, then abort loop. */
                ctx.pending.addAll(toMerge);
                ctx.bits.subList(0, BITS_PER_MERGE).clear(); // avoid duplicates
                break;
            }

            /* Successful merge. Remove merged bits and clean pending duplicates. */
            ctx.bits.subList(0, BITS_PER_MERGE).clear();
            ctx.pending.removeAll(toMerge);
            ctx.mergeCount++;
        }

        /* Close & report if the group is entirely processed. */
        if (ctx.isDone()) closeGroup(ctx);
    }

    private LargePacket createMergedPacket(List<BitPacket> bits) {
        if (bits == null || bits.size() != BITS_PER_MERGE) return null;

        BitPacket first = bits.get(0);
        Color mergedColor = Color.getHSBColor(first.getColorId() / 360f, 0.8f, 0.9f);

        LargePacket lp = new LargePacket(PacketType.SQUARE,
                Config.DEFAULT_PACKET_SPEED,
                MERGED_PKT_SIZE);
        lp.setCustomColor(mergedColor);

        int visual = MERGED_PKT_SIZE * Config.PACKET_SIZE_MULTIPLIER;
        lp.setWidth(visual);
        lp.setHeight(visual);
        lp.setGroupInfo(first.getGroupId(), BITS_PER_MERGE, first.getColorId());
        KinematicsRegistry.copyProfile(first, lp);
        return lp;
    }



    private static final class GroupContext {
        final int groupId;
        final List<BitPacket> bits    = new ArrayList<>();
        final Set<BitPacket>  pending = new HashSet<>();
        int mergeCount = 0;

        GroupContext(int id) { this.groupId = id; }

        /** Ready when at least 4 fresh bits exist. */
        boolean ready()  { return bits.size() >= BITS_PER_MERGE; }
        /** Done when nothing left to process or waiting. */
        boolean isDone() { return bits.isEmpty() && pending.isEmpty(); }
    }

    private void closeGroup(GroupContext ctx) {
        int totalBits = ctx.mergeCount * BITS_PER_MERGE;
        GroupState st = registry.get(ctx.groupId);
        if (st != null) {
            registry.registerPartialMerge(ctx.groupId, totalBits, MERGED_PKT_SIZE);

            // محاسبه و اعمال Loss
            int actualLoss = registry.calculateActualLoss(ctx.groupId);
            if (actualLoss > 0) {
                lossModel.incrementBy(actualLoss);
            }

            if (totalBits >= st.expectedBits) {
                registry.closeGroup(ctx.groupId);
                registry.removeGroup(ctx.groupId);
            }
        }
        groups.remove(ctx.groupId);
    }
}

package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.persistence.SnapshotService;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.model.large.LargeGroupRegistry.GroupState;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.model.large.MergedPacket;
import com.blueprinthell.snapshot.NetworkSnapshot;

import java.util.*;
import java.util.List;

public final class MergerBehavior implements SystemBehavior, SnapshottableBehavior {

    private static final int BITS_PER_MERGE = 4;
    private final SystemBoxModel     box;
    private final LargeGroupRegistry registry;
    private final PacketLossModel    lossModel;

    private final Map<Integer, GroupContext> groups  = new HashMap<>();
    private final Deque<Integer>             rrQueue = new ArrayDeque<>();
    public MergerBehavior(SystemBoxModel box,
                          LargeGroupRegistry registry,
                          PacketLossModel lossModel) {
        this.box       = Objects.requireNonNull(box, "box");
        this.registry  = Objects.requireNonNull(registry, "registry");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (!(packet instanceof BitPacket bp)) return;

        if (bp.isProcessedByMerger()) return;

        if (!box.removeFromBuffer(bp)) return;

        bp.markProcessedByMerger();
        final int gid = bp.getGroupId();
        GroupContext ctx = groups.computeIfAbsent(gid, GroupContext::new);
        ctx.bits.addLast(bp);

        GroupState st = registry.get(gid);
        if (st == null) {
            registry.createGroupWithId(
                    gid,
                    bp.getParentSizeUnits(),
                    bp.getParentSizeUnits(),
                    bp.getColorId()
            );
            st = registry.get(gid);
        }

        registry.registerArrival(gid, bp);

        if (ctx.bits.size() >= BITS_PER_MERGE && !rrQueue.contains(gid)) {
            rrQueue.addLast(gid);
        }
    }

    @Override
    public void update(double dt) {
        processRoundRobinMerges();

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

    private void processRoundRobinMerges() {
        int guard = 1024;
        while (!rrQueue.isEmpty() && guard-- > 0) {
            int gid = rrQueue.removeFirst();
            GroupContext ctx = groups.get(gid);
            if (ctx == null || ctx.bits.size() < BITS_PER_MERGE) continue;

            List<BitPacket> four = new ArrayList<>(BITS_PER_MERGE);
            for (int i = 0; i < BITS_PER_MERGE; i++) four.add(ctx.bits.removeFirst());

            MergedPacket merged = (MergedPacket) createMergedPacket(four);
            if (!box.enqueue(merged)) {
                /* جا نیست → بیت‌ها را برگردان و از حلقه خارج شو */
                for (int i = BITS_PER_MERGE - 1; i >= 0; i--) ctx.bits.addFirst(four.get(i));
                rrQueue.addFirst(gid);
                break;
            }

            registry.registerPartialMerge(gid, /*bitCount=*/BITS_PER_MERGE, /*mergedPacketSize=*/BITS_PER_MERGE);

            ctx.mergeCount++;

            /* اگر دوباره ≥۴ بیت ماند ... */
            if (ctx.bits.size() >= BITS_PER_MERGE) rrQueue.addLast(gid);
        }
    }

    private MergedPacket createMergedPacket(List<BitPacket> bits) {
        BitPacket first = bits.get(0);

        java.awt.Color c = first.getColor();
        int chunkUnits = BITS_PER_MERGE;

        MergedPacket lp = new MergedPacket(
                PacketType.SQUARE,
                Config.DEFAULT_PACKET_SPEED,
                chunkUnits,
                first.getGroupId(),
                first.getParentSizeUnits(),
                first.getColorId()
        );
        lp.setCustomColor(c);
        lp.setWidth (chunkUnits * Config.PACKET_SIZE_MULTIPLIER);
        lp.setHeight(chunkUnits * Config.PACKET_SIZE_MULTIPLIER);

        lp.setGroupInfo(first.getGroupId(), first.getParentSizeUnits(), first.getColorId());
        lp.markRebuilt();
        return lp;
    }
    private void closeGroup(GroupContext ctx) {
        GroupState st = registry.get(ctx.groupId);
        if (st == null) return;


        int totalBitsMerged = ctx.mergeCount * BITS_PER_MERGE;
        if (totalBitsMerged >= st.expectedBits) {
            registry.closeGroup(ctx.groupId);
        }
    }

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
    @Override
    public Map<String, Object> captureState() {
        Map<String, Object> state = new HashMap<>();
        Map<Integer, Map<String, Object>> groupsState = new HashMap<>();
        for (Map.Entry<Integer, GroupContext> entry : groups.entrySet()) {
            Map<String, Object> contextState = new HashMap<>();
            contextState.put("groupId", entry.getValue().groupId);
            contextState.put("mergeCount", entry.getValue().mergeCount);
            // بیت‌پکت‌ها را به حالت قابل ذخیره‌سازی تبدیل می‌کنیم
            List<NetworkSnapshot.PacketState> bitStates = new ArrayList<>();
            for (BitPacket bp : entry.getValue().bits) {
                // از متد استاتیک SnapshotService که قبلا اصلاح کردیم استفاده می‌کنیم
                bitStates.add(SnapshotService.toPacketState(bp));
            }

            contextState.put("bits", bitStates);
            groupsState.put(entry.getKey(), contextState);
        }
        state.put("groups", groupsState);
        state.put("rrQueue", new ArrayList<>(rrQueue));
        return state;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void restoreState(Map<String, Object> state) {
        clear(); // پاک کردن وضعیت فعلی
        if (state == null) return;

        Map<Integer, Map<String, Object>> groupsState = (Map<Integer, Map<String, Object>>) state.get("groups");
        if (groupsState != null) {
            for (Map.Entry<Integer, Map<String, Object>> entry : groupsState.entrySet()) {
                Map<String, Object> contextState = entry.getValue();
                int groupId = (int) contextState.get("groupId");
                GroupContext ctx = new GroupContext(groupId);
                ctx.mergeCount = (int) contextState.get("mergeCount");
                List<NetworkSnapshot.PacketState> bitStates = (List<NetworkSnapshot.PacketState>) contextState.get("bits");
                for (NetworkSnapshot.PacketState ps : bitStates) {
                    // بیت‌پکت‌ها را از حالت ذخیره شده بازسازی می‌کنیم
                    ctx.bits.addLast((BitPacket) SnapshotService.fromPacketState(ps));
                }
                groups.put(entry.getKey(), ctx);
            }
        }

        List<Integer> rrQueueState = (List<Integer>) state.get("rrQueue");
        if (rrQueueState != null) {
            rrQueue.addAll(rrQueueState);
        }
    }
}

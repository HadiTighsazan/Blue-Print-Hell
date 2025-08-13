package com.blueprinthell.model.large;

import com.blueprinthell.model.PacketModel;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class LargeGroupRegistry {

    /** Lightweight DTO for snapshotting group state. */
    public static final class GroupSnapshot {
        public final int id;
        public final int originalSizeUnits;
        public final int expectedBits;
        public final int colorId;
        public final int mergedBits;
        public final int lostBits;
        public final boolean closed;
        public final List<Integer> partialMerges; // store mergedPacketSize values

        public GroupSnapshot(int id,
                             int originalSizeUnits,
                             int expectedBits,
                             int colorId,
                             int mergedBits,
                             int lostBits,
                             boolean closed,
                             List<Integer> partialMerges) {
            this.id = id;
            this.originalSizeUnits = originalSizeUnits;
            this.expectedBits = expectedBits;
            this.colorId = colorId;
            this.mergedBits = mergedBits;
            this.lostBits = lostBits;
            this.closed = closed;
            this.partialMerges = (partialMerges == null) ? List.of() : List.copyOf(partialMerges);
        }
    }

    public static final class GroupState {
        public final int  groupId;
        public final int  originalSizeUnits;
        public final int  expectedBits;
        public final int  colorId;
        private int       receivedBits = 0;
        private int       mergedBits = 0;  // تعداد بیت‌هایی که ادغام شده‌اند
        private int       lostBits = 0;
        private boolean   closed   = false;
        private final List<PacketModel> collectedPackets = new ArrayList<>();
        private final List<Integer> partialMerges = new ArrayList<>(); // سایز پکت‌های ادغام شده

        GroupState(int id, int originalSize, int expectedBits, int colorId) {
            this.groupId = id;
            this.originalSizeUnits = originalSize;
            this.expectedBits = expectedBits;
            this.colorId = colorId;
        }

        public int  getReceivedBits() { return receivedBits; }
        public int  getMergedBits()   { return mergedBits; }
        public int  getMissingBits()  { return Math.max(0, expectedBits - receivedBits); }
        public int  getLostBits()     { return lostBits; }
        public boolean isComplete()   { return receivedBits >= expectedBits; }
        public boolean isClosed()     { return closed; }
        public List<PacketModel> getCollectedPackets() {return Collections.unmodifiableList(collectedPackets);}
        public List<Integer> getPartialMerges() { return Collections.unmodifiableList(partialMerges); }
        // Added getters for snapshotting
        public int getOriginalSize() { return originalSizeUnits; }
        public int getExpectedBits() { return expectedBits; }
        public int getColorId() { return colorId; }

        void addPacket(PacketModel p) {
            collectedPackets.add(p);
            receivedBits++;
        }

        void markMerged(int bitCount) {
            mergedBits += bitCount;
        }

        void addPartialMerge(int packetSize) {
            partialMerges.add(packetSize);
        }

        void markLost(int cnt)  { lostBits += Math.max(0, cnt); }
        void close()            { closed = true; }
    }

    private final AtomicInteger idSeq  = new AtomicInteger(1);
    private final Map<Integer, GroupState> groups = new HashMap<>();

    private int totalBitsProduced = 0;
    private int totalBitsLost     = 0;
    private int totalBitsMerged   = 0;

    public int createGroup(int originalSizeUnits, int expectedBits, int colorId) {
        int id = idSeq.getAndIncrement();
        createGroupWithId(id, originalSizeUnits, expectedBits, colorId);
        return id;
    }

    public void createGroupWithId(int groupId, int originalSizeUnits, int expectedBits, int colorId) {
        groups.computeIfAbsent(groupId, gid -> {
            // ensure idSeq ahead of manual ids
            idSeq.updateAndGet(v -> Math.max(v, gid + 1));
            return new GroupState(gid, originalSizeUnits, expectedBits, colorId);
        });
    }

    public boolean registerArrival(int groupId, PacketModel bit) {
        GroupState st = groups.get(groupId);
        if (st == null || st.closed) return false;
        st.addPacket(bit);
        return st.isComplete();
    }

    /**
     * ثبت ادغام partial (برای پکت‌های سایز 4)
     */
    public void registerPartialMerge(int groupId, int bitCount, int mergedPacketSize) {
        GroupState st = groups.get(groupId);
        if (st == null || st.closed) return;

        st.markMerged(bitCount);
        st.addPartialMerge(mergedPacketSize);
        totalBitsMerged += bitCount;
    }

    /**
     * Directly mark merged bits (used by restore when we only know counts).
     */
    public void markMerged(int groupId, int bitCount) {
        GroupState st = groups.get(groupId);
        if (st == null || st.closed) return;
        st.markMerged(bitCount);
        totalBitsMerged += Math.max(0, bitCount);
    }

    public void registerSplit(int groupId, PacketModel bit) {
        if (groups.containsKey(groupId)) {
            totalBitsProduced++;
        }
    }

    public void markBitLost(int groupId, int count) {
        GroupState st = groups.get(groupId);
        if (st == null || st.closed) return;
        st.markLost(count);
        totalBitsLost += count;
    }

    public GroupState get(int groupId) { return groups.get(groupId); }

    public void closeGroup(int groupId) {
        GroupState st = groups.get(groupId);
        if (st != null) st.close();
    }

    public void removeGroup(int groupId) { groups.remove(groupId); }

    public void clear() {
        groups.clear();
        totalBitsLost = 0;
        totalBitsProduced = 0;
        totalBitsMerged = 0;
    }

    public Map<Integer, GroupState> view() { return Collections.unmodifiableMap(groups); }

    /**
     * Create a serializable view of all groups.
     */
    public List<GroupSnapshot> snapshot() {
        List<GroupSnapshot> out = new ArrayList<>();
        for (Map.Entry<Integer, GroupState> e : view().entrySet()) {
            GroupState gs = e.getValue();
            out.add(new GroupSnapshot(
                    e.getKey(),
                    gs.getOriginalSize(),
                    gs.getExpectedBits(),
                    gs.getColorId(),
                    gs.getMergedBits(),
                    gs.getLostBits(),
                    gs.isClosed(),
                    gs.getPartialMerges()
            ));
        }
        return out;
    }

    /**
     * Restore groups minimally so that subsequent merges/loss accounting continues correctly.
     * Note: receivedBits is reconstructed later by scanning actual BitPackets during restore.
     */
    public void restore(List<GroupSnapshot> data) {
        clear();
        if (data == null) return;
        for (GroupSnapshot s : data) {
            createGroupWithId(s.id, s.originalSizeUnits, s.expectedBits, s.colorId);
            if (s.mergedBits > 0) {
                markMerged(s.id, s.mergedBits);
            }
            if (s.lostBits > 0) {
                markBitLost(s.id, s.lostBits);
            }
            if (s.partialMerges != null) {
                for (int mergedPacketSize : s.partialMerges) {
                    // Assumption: each partial merged packet of size N consumed N bits.
                    registerPartialMerge(s.id, mergedPacketSize, mergedPacketSize);
                }
            }
            if (s.closed) {
                closeGroup(s.id);
            }
        }
    }

    /**
     * محاسبه loss براساس تعداد بیت‌های ادغام شده
     */
    public int computeLossForGroup(int groupId) {
        GroupState st = groups.get(groupId);
        if (st == null) return 0;

        // محاسبه loss = تعداد بیت‌های اصلی - تعداد بیت‌های ادغام شده
        return Math.max(0, st.originalSizeUnits - st.getMergedBits());
    }

    /**
     * محاسبه loss با در نظر گرفتن partial merges
     */
    public int computePartialLoss(int groupId) {
        GroupState st = groups.get(groupId);
        if (st == null) return 0;

        List<Integer> merges = st.getPartialMerges();
        if (merges.isEmpty()) {
            return st.originalSizeUnits;
        }

        // محاسبه بر اساس فرمول پیچیده‌تر (اگر نیاز است)
        int totalRecovered = merges.stream().mapToInt(Integer::intValue).sum();
        return Math.max(0, st.originalSizeUnits - totalRecovered);
    }

    public int calculateActualLoss(int groupId) {
        GroupState st = groups.get(groupId);
        if (st == null) return 0;
        if (!st.isClosed()) return 0;

        var merges = st.getPartialMerges();
        if (merges.isEmpty()) {
            return st.originalSizeUnits; // هیچ مرجی به مقصد نرسیده
        }

        int i = merges.size();
        double product = 1.0;
        for (int a : merges) product *= a;

        // فرمول جدید: recovered = floor( i * sqrt(product) )
        int recovered = (int) Math.floor(i * Math.sqrt(product));
        return Math.max(0, st.originalSizeUnits - recovered);
    }

    public void closeAllOpenGroups() {
        for (var e : groups.entrySet()) {
            GroupState st = e.getValue();
            if (st != null && !st.isClosed()) {
                st.close();
            }
        }
    }
}

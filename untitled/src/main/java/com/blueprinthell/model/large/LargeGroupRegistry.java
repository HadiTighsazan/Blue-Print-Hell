package com.blueprinthell.model.large;

import com.blueprinthell.model.PacketModel;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class LargeGroupRegistry {


    public static final class GroupSnapshot {
        public final int id;
        public final int originalSizeUnits;
        public final int expectedBits;
        public final int colorId;
        public final int receivedBits;
        public final int mergedBits;
        public final int lostBits;
        public final boolean closed;
        public final List<Integer> partialMerges;

        public GroupSnapshot(int id,
                             int originalSizeUnits,
                             int expectedBits,
                             int colorId,
                             int receivedBits,
                             int mergedBits,
                             int lostBits,
                             boolean closed,
                             List<Integer> partialMerges) {
            this.id = id;
            this.originalSizeUnits = originalSizeUnits;
            this.expectedBits = expectedBits;
            this.colorId = colorId;
            this.receivedBits = receivedBits;
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
        private int       mergedBits = 0;
        private int       lostBits = 0; // <-- فیلد جدید
        private boolean   closed   = false;
        private final List<PacketModel> collectedPackets = new ArrayList<>();
        private final List<Integer> partialMerges = new ArrayList<>();

        GroupState(int id, int originalSize, int expectedBits, int colorId) {
            this.groupId = id;
            this.originalSizeUnits = originalSize;
            this.expectedBits = expectedBits;
            this.colorId = colorId;
            this.receivedBits = 0;
            this.mergedBits = 0;
            this.lostBits = 0;
            this.closed = false;
        }

        public int  getReceivedBits() { return receivedBits; }
        public int  getMergedBits()   { return mergedBits; }
        public int  getLostBits()     { return lostBits; } // <-- متد جدید
        public boolean isComplete()   { return receivedBits >= expectedBits; }
        public boolean isClosed()     { return closed; }
        public List<PacketModel> getCollectedPackets() {return Collections.unmodifiableList(collectedPackets);}
        public List<Integer> getPartialMerges() { return Collections.unmodifiableList(partialMerges); }

        public int getOriginalSize() { return originalSizeUnits; }
        public int getExpectedBits() { return expectedBits; }
        public int getColorId() { return colorId; }

        /**
         * بررسی می‌کند که آیا تمام بیت‌ها تعیین تکلیف شده‌اند (یا رسیده‌اند یا گم شده‌اند)
         */
        public boolean allBitsAccountedFor() {
            return (receivedBits + lostBits) >= expectedBits;
        }

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
        groups.putIfAbsent(id, new GroupState(id, originalSizeUnits, expectedBits, colorId));
        return id;
    }

    public void createGroupWithId(int groupId, int originalSizeUnits, int expectedBits, int colorId) {
        groups.computeIfAbsent(groupId, gid -> {
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
     * متد جدید برای ثبت از دست رفتن یک بیت‌پکت
     */
    public void registerLostBit(int groupId) {
        GroupState st = groups.get(groupId);
        if (st != null && !st.isClosed()) {
            st.markLost(1);
            totalBitsLost++;
        }
    }


    public void registerPartialMerge(int groupId, int bitCount, int mergedPacketSize) {
        GroupState st = groups.get(groupId);
        if (st == null || st.closed) return;

        st.markMerged(bitCount);
        st.addPartialMerge(mergedPacketSize);
        totalBitsMerged += bitCount;
    }



    public void registerSplit(int groupId, PacketModel bit) {
        if (groups.containsKey(groupId)) {
            totalBitsProduced++;
        }
    }



    public GroupState get(int groupId) { return groups.get(groupId); }

    public void closeGroup(int groupId) {
        GroupState st = groups.get(groupId);
        if (st != null) {
            st.close();
        }
    }


    public void clear() {
        groups.clear();
        totalBitsLost = 0;
        totalBitsProduced = 0;
        totalBitsMerged = 0;
    }

    public Map<Integer, GroupState> view() { return Collections.unmodifiableMap(groups); }


    public List<GroupSnapshot> snapshot() {
        List<GroupSnapshot> out = new ArrayList<>();
        for (Map.Entry<Integer, GroupState> e : view().entrySet()) {
            GroupState gs = e.getValue();
            out.add(new GroupSnapshot(
                    e.getKey(),
                    gs.getOriginalSize(),
                    gs.getExpectedBits(),
                    gs.getColorId(),
                    gs.getReceivedBits(),
                    gs.getMergedBits(),
                    gs.getLostBits(),
                    gs.isClosed(),
                    gs.getPartialMerges()
            ));
        }
        return out;
    }



    public void restore(List<GroupSnapshot> data) {
        clear();
        if (data == null) return;

        for (GroupSnapshot s : data) {
            GroupState newState = new GroupState(s.id, s.originalSizeUnits, s.expectedBits, s.colorId);

            newState.receivedBits = s.receivedBits;
            newState.mergedBits = s.mergedBits;
            newState.lostBits = s.lostBits;
            newState.closed = s.closed;

            if (s.partialMerges != null) {
                newState.partialMerges.addAll(s.partialMerges);
            }

            groups.put(s.id, newState);
            idSeq.updateAndGet(v -> Math.max(v, s.id + 1));
        }

    }



    public int calculateActualLoss(int groupId) {
        GroupState st = groups.get(groupId);
        if (st == null) return 0;
        if (!st.isClosed()) return 0; // فقط برای گروه‌های بسته شده محاسبه کن

        // بیت‌هایی که در مسیر گم شده‌اند
        int inTransitLoss = st.getLostBits();

        // بیت‌هایی که به مرجر رسیده‌اند ولی هرگز ترکیب نشده‌اند
        int strandedInMerger = st.getReceivedBits() - st.getMergedBits();

        return inTransitLoss + strandedInMerger;
    }

    public void closeAllOpenGroups() {
        for (var e : groups.entrySet()) {
            GroupState st = e.getValue();
            if (st != null && !st.isClosed()) {
                st.close();
            }
        }
    }
    public Integer findOpenGroupByColorAndSize(int colorId, int originalSizeUnits) {
        for (GroupState st : groups.values()) {
            if (!st.isClosed() && st.colorId == colorId && st.originalSizeUnits == originalSizeUnits) {
                return st.groupId;
            }
        }
        return null;
    }
}
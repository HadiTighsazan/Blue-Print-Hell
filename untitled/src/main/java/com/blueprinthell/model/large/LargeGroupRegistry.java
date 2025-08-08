package com.blueprinthell.model.large;

import com.blueprinthell.model.PacketModel;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class LargeGroupRegistry {

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


}
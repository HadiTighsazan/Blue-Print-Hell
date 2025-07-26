package com.blueprinthell.model.large;

import com.blueprinthell.model.PacketModel;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <h2>LargeGroupRegistry</h2>
 * رجیستری سطح مرحله برای رهگیری چرخهٔ حیات گروه‌های پکت‌های حجیم.
 */
public final class LargeGroupRegistry {

    /* =============================================================== */
    /*                          Inner type                              */
    /* =============================================================== */
    public static final class GroupState {
        public final int  groupId;
        public final int  originalSizeUnits;   // اندازهٔ LargePacket اصلی
        public final int  expectedBits;        // تعداد بیت‌های الزامی
        public final int  colorId;             // برای UI
        private int       receivedBits = 0;
        private int       lostBits = 0;
        private boolean   closed   = false;
        private final List<PacketModel> collectedPackets = new ArrayList<>();

        GroupState(int id, int originalSize, int expectedBits, int colorId) {
            this.groupId = id;
            this.originalSizeUnits = originalSize;
            this.expectedBits = expectedBits;
            this.colorId = colorId;
        }

        /* --------------- queries --------------- */
        public int  getReceivedBits() { return receivedBits; }
        public int  getMissingBits()  { return Math.max(0, expectedBits - receivedBits); }
        public int  getLostBits()     { return lostBits; }
        public boolean isComplete()   { return receivedBits >= expectedBits; }
        public boolean isClosed()     { return closed; }
        public List<PacketModel> getCollectedPackets() {return Collections.unmodifiableList(collectedPackets);}

        /* --------------- mutators -------------- */
        void addPacket(PacketModel p) {
            collectedPackets.add(p);
            receivedBits++;
        }
        void markLost(int cnt)  { lostBits += Math.max(0, cnt); }
        void close()            { closed = true; }
    }

    /* =============================================================== */
    /*                           Registry                               */
    /* =============================================================== */
    private final AtomicInteger idSeq  = new AtomicInteger(1);
    private final Map<Integer, GroupState> groups = new HashMap<>();

    // Diagnostics
    private int totalBitsProduced = 0;
    private int totalBitsLost     = 0;

    /* --------------------------------------------------------------- */
    /*                             CRUD                                */
    /* --------------------------------------------------------------- */
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

    /**
     * ثبت رسیدن بیت به Merger.
     * @return true اگر گروه پس از این ثبت کامل شد.
     */
    public boolean registerArrival(int groupId, PacketModel bit) {
        GroupState st = groups.get(groupId);
        if (st == null || st.closed) return false;
        st.addPacket(bit);
        return st.isComplete();
    }

    /** آمار تولید بیت در Distributor. */
    public void registerSplit(int groupId, PacketModel bit) {
        if (groups.containsKey(groupId)) {
            totalBitsProduced++;
        }
    }

    /** ثبت بیت گم‑شده (Drop). */
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

    public void clear() { groups.clear(); totalBitsLost = 0; totalBitsProduced = 0; }

    public Map<Integer, GroupState> view() { return Collections.unmodifiableMap(groups); }

    /* =============================================================== */
    /*                     Loss utility methods                         */
    /* =============================================================== */

    /**
     * Loss سادهٔ گروه: اصلی − دریافت‌شده.
     */
    public int computeSimpleLoss(int groupId) {
        GroupState st = groups.get(groupId);
        return (st == null) ? 0 : Math.max(0, st.originalSizeUnits - st.getReceivedBits());
    }

    /** فرمول عمومی پروژه: originalParts - ⌊k·√[k](Πni)⌋ */
    public static int computePartialLoss(int N, List<Integer> parts) {
        int k = parts.size();
        // ۱) محاسبه‌ی حاصل‌ضرب
        double product = 1.0;
        for (int p : parts) {
            product *= p;
        }
        // ۲) محاسبه‌ی جذر k
        double kthRoot = Math.pow(product, 1.0 / k);
        int restored = (int) Math.floor(kthRoot);
        // ۳) محاسبه‌ی Loss
        return N - restored;
    }
}

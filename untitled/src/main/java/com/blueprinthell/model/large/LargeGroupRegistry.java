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
        private int       lostBits = 0;
        private boolean   closed   = false;
        private final List<PacketModel> collectedPackets = new ArrayList<>();

        GroupState(int id, int originalSize, int expectedBits, int colorId) {
            this.groupId = id;
            this.originalSizeUnits = originalSize;
            this.expectedBits = expectedBits;
            this.colorId = colorId;
        }

        public int  getReceivedBits() { return receivedBits; }
        public int  getMissingBits()  { return Math.max(0, expectedBits - receivedBits); }
        public int  getLostBits()     { return lostBits; }
        public boolean isComplete()   { return receivedBits >= expectedBits; }
        public boolean isClosed()     { return closed; }
        public List<PacketModel> getCollectedPackets() {return Collections.unmodifiableList(collectedPackets);}

        void addPacket(PacketModel p) {
            collectedPackets.add(p);
            receivedBits++;
        }
        void markLost(int cnt)  { lostBits += Math.max(0, cnt); }
        void close()            { closed = true; }
    }


    private final AtomicInteger idSeq  = new AtomicInteger(1);
    private final Map<Integer, GroupState> groups = new HashMap<>();

    private int totalBitsProduced = 0;
    private int totalBitsLost     = 0;


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

    public void clear() { groups.clear(); totalBitsLost = 0; totalBitsProduced = 0; }

    public Map<Integer, GroupState> view() { return Collections.unmodifiableMap(groups); }


    public int computeSimpleLoss(int groupId) {
        GroupState st = groups.get(groupId);
        return (st == null) ? 0 : Math.max(0, st.originalSizeUnits - st.getReceivedBits());
    }

    public static int computePartialLoss(int N, List<Integer> parts) {
        int k = parts.size();
        double product = 1.0;
        for (int p : parts) {
            product *= p;
        }
        double kthRoot = Math.pow(product, 1.0 / k);
        int restored = (int) Math.floor(kthRoot);
        return N - restored;
    }
}

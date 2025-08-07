package com.blueprinthell.model;


import com.blueprinthell.model.large.LargePacket;

public class PacketLossModel {
    private int lostCount;

    public void increment() {
        lostCount++;
    }

    // PacketLossModel.java
    public void incrementBy(int n) {
        if (n <= 0) return;
        lostCount += n;
    }


    public int getLostCount() {
        return lostCount;
    }


    public void reset() {
        lostCount = 0;
    }
    public void incrementPacket(PacketModel p) {
        if (p instanceof LargePacket lp) {
            incrementBy(lp.getOriginalSizeUnits());
        } else {
            increment();
        }
    }

}

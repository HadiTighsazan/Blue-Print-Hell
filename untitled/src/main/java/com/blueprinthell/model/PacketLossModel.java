package com.blueprinthell.model;

import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.model.large.LargeGroupRegistry;

public class PacketLossModel {
    private int immediateLoss = 0;
    private LargeGroupRegistry registry; // تزریق از SimulationRegistrar

    public void setLargeGroupRegistry(LargeGroupRegistry reg) {
        this.registry = reg;
    }

    // فقط برای پکت‌های غیر بیت/غیر حجیم
    public void increment() { immediateLoss++; }

    public void incrementBy(int n) {
        if (n > 0) immediateLoss += n;
    }

    public void reset() { immediateLoss = 0; }

    public int getImmediateLoss() { return immediateLoss; }

    public void incrementPacket(PacketModel p) {
        if (p instanceof BitPacket) return;          // مؤخره
        if (p instanceof LargePacket) return;        // مؤخره (اولیه یا مرج‌شده)
        immediateLoss++;                              // فقط پکت معمولی
    }

    public int getLostCount() {
        return immediateLoss + computeDeferredLoss();
    }

    private int computeDeferredLoss() {
        if (registry == null) return 0;
        int total = 0;
        for (var e : registry.view().entrySet()) {
            total += registry.calculateActualLoss(e.getKey());
        }
        return total;
    }
}

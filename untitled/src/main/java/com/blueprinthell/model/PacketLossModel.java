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
    public void increment() {
        immediateLoss++;
    }

    public void incrementBy(int n) {
        if (n > 0) immediateLoss += n;
    }

    public void reset() {
        immediateLoss = 0;
    }

    public int getImmediateLoss() {
        return immediateLoss;
    }

    public void incrementPacket(PacketModel p) {
        if (p instanceof BitPacket) return;          // مؤخره
        if (p instanceof LargePacket) return;        // مؤخره (اولیه یا مرج‌شده)
        immediateLoss++;                              // فقط پکت معمولی
    }

    /**
     * محاسبه کل loss (immediate + deferred)
     */
    public int getLostCount() {
        return immediateLoss + computeDeferredLoss();
    }

    /**
     * محاسبه deferred loss از registry
     */
    private int computeDeferredLoss() {
        if (registry == null) return 0;
        int total = 0;
        for (var e : registry.view().entrySet()) {
            var st = e.getValue();
            if (!st.isClosed()) continue;            // فقط گروه‌های بسته‌شده
            total += registry.calculateActualLoss(e.getKey());
        }
        return total;
    }

    /**
     * متد جدید برای دیباگ - برگرداندن deferred loss جداگانه
     */
    public int getDeferredLoss() {
        return computeDeferredLoss();
    }

    /**
     * متد جدید برای دیباگ - اطلاعات کامل loss
     */
    public String getLossDetails() {
        int immediate = getImmediateLoss();
        int deferred = getDeferredLoss();
        int total = immediate + deferred;

        StringBuilder sb = new StringBuilder();
        sb.append("Loss Details:\n");
        sb.append("  Immediate Loss: ").append(immediate).append("\n");
        sb.append("  Deferred Loss: ").append(deferred).append("\n");
        sb.append("  Total Loss: ").append(total).append("\n");

        if (registry != null && deferred > 0) {
            sb.append("  Group Details:\n");
            for (var e : registry.view().entrySet()) {
                var st = e.getValue();
                if (st.isClosed()) {
                    int groupLoss = registry.calculateActualLoss(e.getKey());
                    sb.append("    Group ").append(e.getKey())
                            .append(": ").append(groupLoss)
                            .append(" (size=").append(st.getOriginalSize())
                            .append(", merges=").append(st.getPartialMerges()).append(")\n");
                }
            }
        }

        return sb.toString();
    }

    public void finalizeDeferredLossNow() {
        if (registry != null) {
            registry.closeAllOpenGroups();
        }
    }

    /**
     * متد برای restore مستقیم state
     * این متد فقط در restore استفاده می‌شود
     */
    public void restoreImmediateLoss(int value) {
        this.immediateLoss = Math.max(0, value);
    }
}
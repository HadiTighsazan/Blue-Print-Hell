package com.blueprinthell.model;

import com.blueprinthell.media.ResourceManager;
import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.model.large.LargeGroupRegistry;
import javax.sound.sampled.Clip;

public class PacketLossModel {
    private int immediateLoss = 0;
    private LargeGroupRegistry registry;

    public void setLargeGroupRegistry(LargeGroupRegistry reg) {
        this.registry = reg;
    }
    // این متد برای دسترسی LevelCompletionDetector لازم است
    public LargeGroupRegistry getRegistryForInternalUse() {
        return registry;
    }


    private void playLossSound() {
        try {
            Clip clip = ResourceManager.INSTANCE.getClip("impact_thud.wav");
            if (clip != null) {
                clip.stop();
                clip.setFramePosition(0);
                clip.start();
            }
        } catch (Exception ignored) {
        }
    }

    public void increment() {
        immediateLoss++;
        playLossSound();
    }

    public void incrementBy(int n) {
        if (n > 0) {
            immediateLoss += n;
            playLossSound();
        }
    }

    public void reset() {
        immediateLoss = 0;
        if (registry != null) {
            registry.clear();
        }
    }

    public int getImmediateLoss() {
        return immediateLoss;
    }

    public void incrementPacket(PacketModel p) {
        if (p == null) {
            return;
        }

        PacketModel originalPacket = PacketOps.unwrapTrojan(p);

        // *** تغییر کلیدی ***
        // اگر بیت‌پکت بود، به رجیستری اطلاع بده
        if (originalPacket instanceof BitPacket bp) {
            if (registry != null) {
                registry.registerLostBit(bp.getGroupId());
            }
            // بیت‌پکت‌ها به immediateLoss اضافه نمی‌شوند
            playLossSound(); // ولی صدایش را پخش می‌کنیم
            return;
        }

        if (originalPacket instanceof LargePacket lp) {
            // اگر یک بسته بزرگ کامل از بین رفت، معادل تمام بیت‌هایش loss ثبت می‌شود
            incrementBy(lp.getOriginalSizeUnits());
            return;
        }

        increment();
    }

    public void restoreImmediateLoss(int value) {
        this.immediateLoss = Math.max(0, value);
    }

    public int getLostCount() {
        return immediateLoss + computeDeferredLoss();
    }

    private int computeDeferredLoss() {
        if (registry == null) return 0;
        int total = 0;
        for (var e : registry.view().entrySet()) {
            var st = e.getValue();
            // فقط برای گروه‌هایی که بسته‌ شده‌اند، loss را محاسبه کن
            if (st.isClosed()) {
                total += registry.calculateActualLoss(e.getKey());
            }
        }
        return total;
    }

    public int getDeferredLoss() {
        return computeDeferredLoss();
    }

    public void finalizeDeferredLossNow() {
        if (registry != null) {
            registry.closeAllOpenGroups();
        }
    }
}
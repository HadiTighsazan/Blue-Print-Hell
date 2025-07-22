package com.blueprinthell.controller.systems;

import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.TrojanPacket; // only for instanceof checks if needed elsewhere
import com.blueprinthell.model.ProtectedPacket;
import com.blueprinthell.model.ConfidentialPacket;
import com.blueprinthell.model.PacketOps;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * SpyBehavior – رفتار سیستم جاسوسی
 *
 * <ul>
 *   <li>پکت محافظت‌شده (Protected) → بی‌اثر، اجازه می‌دهیم در بافر بماند.</li>
 *   <li>پکت محرمانه (Confidential) → حذف از بافر و افزایش PacketLoss.</li>
 *   <li>سایر پکت‌ها → می‌توانند از هر سیستم جاسوسی دیگری خارج شوند (تلپورت).
 *       به صورت تصادفی یکی از Spyهای دیگر انتخاب و پکت به بافر آن سیستم enqueue می‌شود.
 *       اگر بافر مقصد پر بود، پکت Drop و Loss افزایش می‌یابد.</li>
 * </ul>
 *
 * متد {@link #onPacketEnqueued(PacketModel)} را فراخوانی کرده است (گام 1، مرحله 1).
 */
public final class SpyBehavior implements SystemBehavior {

    private final SystemBoxModel        box;          // این سیستم
    private final BehaviorRegistry      registry;     // برای پیدا کردن Spyهای دیگر
    private final PacketLossModel       lossModel;

    public SpyBehavior(SystemBoxModel box,
                       BehaviorRegistry registry,
                       PacketLossModel lossModel) {
        this.box = Objects.requireNonNull(box);
        this.registry = Objects.requireNonNull(registry);
        this.lossModel = Objects.requireNonNull(lossModel);
    }

    @Override
    public void update(double dt) {
        // Spy سیستم نیاز به منطق زمان‌محور ندارد در فاز فعلی
    }

    @Override
    public void onPacketEnqueued(PacketModel packet) {
        // 1) Protected → untouched
        if (packet instanceof ProtectedPacket) {
            return;
        }
        // 2) Confidential → drop
        if (packet instanceof ConfidentialPacket) {
            removeFromBuffer(box, packet); // ensure it leaves this buffer
            lossModel.increment();
            return;
        }

        // 3) Teleport
        SystemBoxModel target = chooseAnotherSpy(box);
        if (target == null) {
            // هیچ Spy دیگری موجود نیست، پس همان‌جا بماند
            return;
        }

        // Remove from current buffer
        boolean removed = removeFromBuffer(box, packet);
        if (!removed) {
            // اگر برای هر دلیلی نتوانستیم حذف کنیم، ریسک دوگانگی نکنیم
            return;
        }

        // Enqueue into target spy
        boolean accepted = target.enqueue(packet);
        if (!accepted) {
            // مقصد پر است → drop
            lossModel.increment();
        }
    }

    /* --------------------------------------------------------------- */
    /*                         Helper methods                           */
    /* --------------------------------------------------------------- */

    /**
     * انتخاب یک Spy دیگر به جز {@code self}. اگر موجود نیست، null.
     */
    private SystemBoxModel chooseAnotherSpy(SystemBoxModel self) {
        List<SystemBoxModel> spies = new ArrayList<>();
        for (Map.Entry<SystemBoxModel, SystemBehavior> e : registry.view().entrySet()) {
            if (e.getValue() instanceof SpyBehavior && e.getKey() != self) {
                spies.add(e.getKey());
            }
        }
        if (spies.isEmpty()) return null;
        return spies.get(ThreadLocalRandom.current().nextInt(spies.size()));
    }

    /**
     * پکت مشخص را از بافر یک SystemBoxModel حذف می‌کند، بدون تغییر ترتیب باقی پکت‌ها.
     * از متدهای موجود (pollPacket/enqueue) استفاده می‌کند تا نیازی به دسترسی مستقیم به Queue نباشد.
     */
    private boolean removeFromBuffer(SystemBoxModel box, PacketModel target) {
        Deque<PacketModel> temp = new ArrayDeque<>();
        PacketModel p;
        boolean found = false;
        while ((p = box.pollPacket()) != null) {
            if (!found && p == target) {
                found = true; // skip it (remove)
            } else {
                temp.addLast(p);
            }
        }
        // re-enqueue others in original order
        for (PacketModel q : temp) {
            box.enqueue(q);
        }
        return found;
    }
}

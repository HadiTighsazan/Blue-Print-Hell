package com.blueprinthell.controller.systems;

import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.ProtectedPacket;
import com.blueprinthell.model.ConfidentialPacket;
import com.blueprinthell.model.large.LargePacket; // just in case we want special-case later
import com.blueprinthell.model.PacketOps;
import com.blueprinthell.model.PortModel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <h2>SpyBehavior – سیستم جاسوسی</h2>
 * قواعد فاز:
 * <ul>
 *   <li>ProtectedPacket: تحت تأثیر قرار نمی‌گیرد → در بافر می‌ماند.</li>
 *   <li>ConfidentialPacket: بلافاصله نابود (از بافر حذف) و PacketLoss++.</li>
 *   <li>سایر پکت‌ها: می‌توانند از هر سیستم جاسوسی دیگر خارج شوند. یکی از SpyBox های دیگر به صورت تصادفی انتخاب
 *       و پکت در بافر آن enqueue می‌شود. اگر بافر مقصد پر بود → Loss++.</li>
 * </ul>
 * این رفتار در رویداد {@link #onPacketEnqueued(PacketModel, PortModel)} عمل می‌کند که Dispatcher آن را صدا می‌زند.
 */
public final class SpyBehavior implements SystemBehavior {

    private final SystemBoxModel   box;        // این سیستم
    private final BehaviorRegistry registry;   // برای یافتن Spy های دیگر
    private final PacketLossModel  lossModel;

    public SpyBehavior(SystemBoxModel box,
                       BehaviorRegistry registry,
                       PacketLossModel lossModel) {
        this.box = Objects.requireNonNull(box, "box");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
    }

    @Override
    public void update(double dt) {
        // رفتار زمان‌محور ندارد
    }

    /** نسخهٔ جدید با پورت ورودی واقعی */
    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        // 1) Protected → untouched
        if (packet instanceof ProtectedPacket) return;
        // یا اگر از PacketOps استفاده کنیم:
        // if (PacketOps.isProtected(packet)) return;

        // 2) Confidential → drop & loss
        if (packet instanceof ConfidentialPacket) {
            removeFromBuffer(box, packet);
            lossModel.increment();
            return;
        }

        // 3) Teleport to another spy (if exists)
        SystemBoxModel target = chooseAnotherSpy(box);
        if (target == null) {
            // هیچ Spy دیگری نیست → همان‌جا بماند
            return;
        }

        // حذف از بافر فعلی
        if (!removeFromBuffer(box, packet)) {
            // اگر نبود، کار خاصی نکنیم
            return;
        }
        // تلاش برای enqueue در مقصد
        if (!target.enqueue(packet)) {
            lossModel.increment();
        }
    }

    /** سازگاری با امضای قدیمی */
    @Override
    public void onPacketEnqueued(PacketModel packet) {
        onPacketEnqueued(packet, null);
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        // نیازی به رفتار خاص نیست
    }

    /* --------------------------------------------------------------- */
    /*                         Helper methods                           */
    /* --------------------------------------------------------------- */

    /** انتخاب یک Spy دیگر بجز خودمان؛ اگر موجود نبود null. */
    private SystemBoxModel chooseAnotherSpy(SystemBoxModel self) {
        List<SystemBoxModel> spies = new ArrayList<>();
        for (Map.Entry<SystemBoxModel, List<SystemBehavior>> e : registry.view().entrySet()) {
            boolean isSpy = false;
            for (SystemBehavior b : e.getValue()) {
                if (b instanceof SpyBehavior) { isSpy = true; break; }
            }
            if (isSpy && e.getKey() != self) {
                spies.add(e.getKey());
            }
        }
        if (spies.isEmpty()) return null;
        return spies.get(ThreadLocalRandom.current().nextInt(spies.size()));
    }

    /** حذف یک پکت از بافر با حفظ ترتیب سایر پکت‌ها. */
    private boolean removeFromBuffer(SystemBoxModel box, PacketModel target) {
        Deque<PacketModel> temp = new ArrayDeque<>();
        PacketModel p;
        boolean found = false;
        while ((p = box.pollPacket()) != null) {
            if (!found && p == target) {
                found = true; // حذفش کنیم
            } else {
                temp.addLast(p);
            }
        }
        // بازگرداندن بقیه به همان ترتیب
        for (PacketModel q : temp) {
            box.enqueue(q);
        }
        return found;
    }
}

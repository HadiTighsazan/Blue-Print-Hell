package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.ProtectedPacket;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.PacketOps;
import com.blueprinthell.model.PortModel;

import java.util.*;

/**
 * <h2>VpnBehavior – سیستم VPN</h2>
 * <ul>
 *   <li>هر پکت ورودی (که هنوز Protected نیست) را به {@link ProtectedPacket} تبدیل می‌کند.</li>
 *   <li>نگاشت «Protected → Original» را نگه می‌دارد تا در زمان غیرفعال شدن VPN، پکت‌ها به حالت اولیه برگردند.</li>
 *   <li>اگر هنگام غیرفعال‌سازی پکت داخل بافر همین باکس نباشد، با استفاده از {@link VpnRevertHints} علامت‌گذاری می‌شود تا بعداً ریورت شود.</li>
 * </ul>
 */
public final class VpnBehavior implements SystemBehavior {

    /** باکس تحت کنترل این رفتار */
    private final SystemBoxModel box;
    /** ظرفیت شیلد برای پکت‌های محافظت‌شده جدید */
    private final double shieldCapacity;

    /** Map: ProtectedPacket -> OriginalPacket */
    private final Map<PacketModel, PacketModel> protectedMap = new WeakHashMap<>();

    public VpnBehavior(SystemBoxModel box) {
        this(box, Config.DEFAULT_SHIELD_CAPACITY);
    }

    public VpnBehavior(SystemBoxModel box, double shieldCapacity) {
        this.box = Objects.requireNonNull(box, "box");
        this.shieldCapacity = shieldCapacity;
    }

    @Override
    public void update(double dt) {
        // رفتار زمان‌محور ندارد
    }

    /* ---------------- onPacketEnqueued ---------------- */
    @Override
    public void onPacketEnqueued(PacketModel packet) {
        onPacketEnqueued(packet, null);
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        // اگر قبلاً محافظت شده است کاری نکن
        if (packet instanceof ProtectedPacket || PacketOps.isProtected(packet)) return;

        PacketModel prot = PacketOps.toProtected(packet, shieldCapacity);
        if (prot == packet) return; // تبدیل نشد

        replaceInBuffer(packet, prot);
        protectedMap.put(prot, packet);
    }

    /* ---------------- enable/disable ---------------- */
    @Override
    public void onEnabledChanged(boolean enabled) {
        if (enabled) return; // فقط هنگام خاموش شدن اهمیت دارد

        // کپی برای جلوگیری از ConcurrentModification
        List<Map.Entry<PacketModel, PacketModel>> snapshot = new ArrayList<>(protectedMap.entrySet());
        for (Map.Entry<PacketModel, PacketModel> e : snapshot) {
            PacketModel prot = e.getKey();
            PacketModel orig = e.getValue();

            // تلاش برای جایگزینی در بافر فعلی
            if (!replaceInBufferIfPresent(box, prot, orig)) {
                // اگر در بافر نبود، علامت‌گذاری برای ریورت تنبل
                VpnRevertHints.mark(prot, orig);
            }
            protectedMap.remove(prot);
        }
    }

    /* --------------------------------------------------------------- */
    /*                          Helpers                                 */
    /* --------------------------------------------------------------- */

    private void replaceInBuffer(PacketModel oldPkt, PacketModel newPkt) {
        replaceInBufferGeneric(box, oldPkt, newPkt);
    }

    /** جایگزینی در بافر targetBox اگر موجود بود. */
    private boolean replaceInBufferIfPresent(SystemBoxModel targetBox, PacketModel oldPkt, PacketModel newPkt) {
        return replaceInBufferGeneric(targetBox, oldPkt, newPkt);
    }

    private boolean replaceInBufferGeneric(SystemBoxModel targetBox, PacketModel oldPkt, PacketModel newPkt) {
        Deque<PacketModel> temp = new ArrayDeque<>();
        boolean replaced = false;
        PacketModel p;
        while ((p = targetBox.pollPacket()) != null) {
            if (!replaced && p == oldPkt) {
                temp.addLast(newPkt);
                replaced = true;
            } else {
                temp.addLast(p);
            }
        }
        for (PacketModel q : temp) {
            targetBox.enqueue(q);
        }
        return replaced;
    }

    /** پاکسازی داخلی هنگام ریست مرحله */
    public void clear() {
        protectedMap.clear();
    }
}

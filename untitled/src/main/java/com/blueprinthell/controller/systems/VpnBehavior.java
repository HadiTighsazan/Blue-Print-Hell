package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.ProtectedPacket;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.PacketOps;

import java.util.*;

/**
 * VPNBehavior – رفتار سیستم VPN
 *
 * <ul>
 *   <li>هر پکت ورودی را به ProtectedPacket تبدیل می‌کند (اگر قبلاً Protected نباشد).</li>
 *   <li>تمام مپ protected→original را نگه می‌دارد تا هنگام از کار افتادن VPN، آنها را به حالت اولیه بازگرداند.</li>
 *   <li>بازگشت: اگر پکت در بافر همین سیستم باشد فوراً جایگزین می‌شود؛ در غیر این صورت با {@link VpnRevertHints}
 *       علامت‌گذاری شده تا در اولین ورود بعدی به سیستمی بازگردانده شود.</li>
 * </ul>
 */
public final class VpnBehavior implements SystemBehavior {

    private final SystemBoxModel box;
    private final double shieldCapacity;

    // Map of protected packet -> original packet
    private final Map<PacketModel, PacketModel> protectedMap = new WeakHashMap<>();

    public VpnBehavior(SystemBoxModel box) {
        this(box, Config.DEFAULT_SHIELD_CAPACITY);
    }

    public VpnBehavior(SystemBoxModel box, double shieldCapacity) {
        this.box = Objects.requireNonNull(box);
        this.shieldCapacity = shieldCapacity;
    }

    @Override
    public void update(double dt) {
        // No time-based logic yet
    }

    @Override
    public void onPacketEnqueued(PacketModel packet) {
        // Already protected? do nothing
        if (packet instanceof ProtectedPacket) return;

        // Convert to protected
        PacketModel prot = PacketOps.toProtected(packet, shieldCapacity);
        if (prot == packet) {
            return; // unexpected but safe guard
        }
        replaceInBuffer(packet, prot);
        protectedMap.put(prot, packet);
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        if (enabled) return; // only care when disabling

        // Iterate over all protected packets we created
        for (Map.Entry<PacketModel, PacketModel> e : new ArrayList<>(protectedMap.entrySet())) {
            PacketModel prot = e.getKey();
            PacketModel orig = e.getValue();

            // Try to replace in this box buffer first
            if (!replaceInBufferIfPresent(box, prot, orig)) {
                // If not present, mark for lazy revert at next dispatch
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

    /**
     * Replace target packet in the given box if present; return true if replaced.
     */
    private boolean replaceInBufferIfPresent(SystemBoxModel targetBox, PacketModel oldPkt, PacketModel newPkt) {
        return replaceInBufferGeneric(targetBox, oldPkt, newPkt);
    }

    private boolean replaceInBufferGeneric(SystemBoxModel targetBox, PacketModel oldPkt, PacketModel newPkt) {
        Deque<PacketModel> temp = new ArrayDeque<>();
        PacketModel p;
        boolean replaced = false;
        while ((p = targetBox.pollPacket()) != null) {
            if (!replaced && p == oldPkt) {
                temp.addLast(newPkt);
                replaced = true;
            } else {
                temp.addLast(p);
            }
        }
        for (PacketModel q : temp) targetBox.enqueue(q);
        return replaced;
    }
}

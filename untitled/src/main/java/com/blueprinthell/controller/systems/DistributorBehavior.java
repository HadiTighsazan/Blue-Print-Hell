package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.motion.KinematicsProfile;
import com.blueprinthell.motion.KinematicsRegistry;

import java.util.*;

/**
 * <h2>DistributorBehavior</h2>
 * <ul>
 *   <li>هنگام ورود یک {@link LargePacket}، آن را به <i>BitPacket</i>های جداگانه تقسیم می‌کند.</li>
 *   <li>هر گروه یک شناسه یکتا، رنگ و متادیتا در {@link LargeGroupRegistry} دارد.</li>
 *   <li>BitPacketها پروفایل حرکتی «MSG1» می‌گیرند.</li>
 *   <li>اگر بافر جا نداشته باشد، بیت از بین می‌رود و <i>Packet Loss</i> و <i>GroupRegistry.markBitLost()</i> بروزرسانی می‌شود.</li>
 * </ul>
 */
public final class DistributorBehavior implements SystemBehavior {

    private final SystemBoxModel     box;
    private final LargeGroupRegistry registry;
    private final PacketLossModel    lossModel;
    private final Random             rnd = new Random();

    public DistributorBehavior(SystemBoxModel box,
                               LargeGroupRegistry registry,
                               PacketLossModel lossModel) {
        this.box       = Objects.requireNonNull(box, "box");
        this.registry  = Objects.requireNonNull(registry, "registry");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
    }

    /* --------------------- tick --------------------- */
    @Override public void update(double dt) { /* no-op */ }

    /* ------------------ packet arrived -------------- */
    @Override public void onPacketEnqueued(PacketModel packet) {
        onPacketEnqueued(packet, null);
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (!(packet instanceof LargePacket lp)) return;
        splitLarge(lp);
    }

    @Override public void onEnabledChanged(boolean enabled) { /* none */ }

    /* ------------------------------------------------ */
    /*                     Helpers                      */
    /* ------------------------------------------------ */

    private void splitLarge(LargePacket large) {
        int parentSize = large.getOriginalSizeUnits();
        int expectedBits = parentSize; // طبق توضیح فاز

        int groupId;
        int colorId;
        if (!large.hasGroup()) {
            colorId = rnd.nextInt(360); // hue (برای UI)
            groupId  = registry.createGroup(parentSize, expectedBits, colorId);
            large.setGroupInfo(groupId, expectedBits, colorId);
        } else {
            groupId = large.getGroupId();
            colorId = large.getColorId();
            // اگر قبلاً create شده ولی در Registry نیست، آن را بساز
            if (registry.get(groupId) == null) {
                registry.createGroupWithId(groupId, parentSize, expectedBits, colorId);
            }
        }

        // Large را از بافر حذف کنیم و بیت‌ها جایگزین شوند
        removeFromBuffer(large);

        int lostBits = 0;
        for (int i = 0; i < expectedBits; i++) {
            BitPacket bit = new BitPacket(
                    large.getType(),               // شکل و سکه همان نوع پدر
                    Config.DEFAULT_PACKET_SPEED,    // سرعت پایه (می‌تواند متفاوت باشد)
                    groupId,
                    parentSize,
                    i,
                    colorId);
            KinematicsRegistry.setProfile(bit, KinematicsProfile.MSG1);
            boolean accepted = box.enqueue(bit);
            if (!accepted) {
                lostBits++;
            } else {
                registry.registerSplit(groupId, bit);
            }
        }

        if (lostBits > 0) {
            lossModel.incrementBy(lostBits);
            registry.markBitLost(groupId, lostBits);
        }
    }

    private void removeFromBuffer(PacketModel target) {
        Deque<PacketModel> tmp = new ArrayDeque<>();
        PacketModel p;
        boolean removed = false;
        while ((p = box.pollPacket()) != null) {
            if (!removed && p == target) {
                removed = true; // skip
            } else {
                tmp.addLast(p);
            }
        }
        for (PacketModel q : tmp) box.enqueue(q);
    }
}

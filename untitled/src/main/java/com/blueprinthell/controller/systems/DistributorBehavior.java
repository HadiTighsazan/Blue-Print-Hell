package com.blueprinthell.controller.systems;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.motion.KinematicsProfile;
import com.blueprinthell.motion.KinematicsRegistry;

import java.awt.*;
import java.util.*;


public final class DistributorBehavior implements SystemBehavior {

    private final SystemBoxModel     box;
    private final LargeGroupRegistry registry;
    private final PacketLossModel    lossModel;
    private final Random             rnd = new Random();

    /** پکت‌هایی که قبلاً Split یا در صف Split قرار گرفته‌اند */
    private final Set<PacketModel> processedPackets =
            Collections.newSetFromMap(new WeakHashMap<>());

    /** صف پکت‌های حجیمی که منتظر Split هستند */
    private final Queue<LargePacket> pendingLargePackets = new ArrayDeque<>();

    /* ---- وضعیت هر گروه بیت برای Split تدریجی ---- */
    private final Map<Integer, Integer> remainingBits   = new HashMap<>();
    private final Map<Integer, Integer> parentSizeByGrp = new HashMap<>();
    private final Map<Integer, Integer> colorIdByGrp    = new HashMap<>();
    private final Map<Integer, Integer> nextIndexByGrp  = new HashMap<>();
    private final Deque<Integer>        rrGroups        = new ArrayDeque<>();

    public DistributorBehavior(SystemBoxModel box,
                               LargeGroupRegistry registry,
                               PacketLossModel lossModel) {
        this.box       = Objects.requireNonNull(box, "box");
        this.registry  = Objects.requireNonNull(registry, "registry");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
    }

    /* ============================ Game-loop ============================ */

    @Override
    public void update(double dt) {
        processPendingLargePackets();     // ➊ در هر فریم چند LP را Split می‌کنیم
        produceBitsRoundRobin();          // ➋ ساخت بیت‌ها تا حد ظرفیت بافر
    }

    /**
     * حداکثر Config.MAX_LP_SPLIT_PER_FRAME عدد LargePacket را پردازش می‌کنیم.
     * هر LP دقیقاً یک‌بار به این متد می‌آید (از طریق onPacketEnqueued).
     */
    private void processPendingLargePackets() {
        int splitsThisFrame = 0;

        while (!pendingLargePackets.isEmpty()
                && splitsThisFrame < Config.MAX_LP_SPLIT_PER_FRAME) {

            LargePacket lp = pendingLargePackets.peek();
            if (lp == null || processedPackets.contains(lp)) {
                pendingLargePackets.poll();           // پاک‌سازی موارد نامعتبر
                continue;
            }

            /* اگر بیت بافر پر است، فعلاً صبر کن تا فریم بعد */
            if (box.getBitBufferFree() == 0) break;


            scheduleSplit(lp);                        // زمان‌بندی Split
            processedPackets.add(lp);
            pendingLargePackets.poll();               // از صف خارج شد
            splitsThisFrame++;
        }
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (packet instanceof LargePacket lp && !processedPackets.contains(lp)) {
            pendingLargePackets.add(lp);
        }

    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        if (enabled) clear();
    }

    /* ===================== تبدیل Large → Bit ===================== */

    private void scheduleSplit(LargePacket large) {
        final int parentSize   = large.getOriginalSizeUnits();
        final int expectedBits = parentSize;          // 1 بیت به ازای هر واحد

        /* --- اطمینان از وجود/ثبت گروه در رجیستری --- */
        int groupId;
        int colorId;
        Color groupColor;

        if (!large.hasGroup()) {
                        int cid = large.getColorId();
                        if (cid <= 0) {
                                // اگر colorId نداریم، از hue رنگ فعلی استخراج کن تا پس از rewind ثابت بماند
                                        Color cc = large.getCustomColor();
                                if (cc != null) {
                                        float[] hsb = Color.RGBtoHSB(cc.getRed(), cc.getGreen(), cc.getBlue(), null);
                                        cid = Math.round(hsb[0] * 360f) % 360;
                                    } else {
                                        cid = rnd.nextInt(360);
                                    }
                           }
                        colorId    = cid;
                        groupColor = (large.getCustomColor() != null)
                                        ? large.getCustomColor()
                                        : Color.getHSBColor(colorId / 360.0f, 0.8f, 0.9f);

                                groupId = registry.createGroup(parentSize, expectedBits, colorId);
                        // حالا colorId سازگار روی خود پکت هم ثبت شود
                               large.setGroupInfo(groupId, expectedBits, colorId);
                      large.setCustomColor(groupColor);
                   }
        else {
            groupId    = large.getGroupId();
            colorId    = large.getColorId();
            groupColor = large.getCustomColor();
            if (registry.get(groupId) == null) {
                registry.createGroupWithId(groupId, parentSize, expectedBits, colorId);
            }
        }
        boolean removed = box.removeFromBuffer(large);
        if (!removed) {
            // اگر نتوانست حذف کند، مشکلی وجود دارد
            return;
        }



        /* --- ثبت وضعیت تولید تدریجی بیت‌ها --- */
        remainingBits.merge(groupId, expectedBits, Integer::sum);
        parentSizeByGrp.putIfAbsent(groupId, parentSize);
        colorIdByGrp   .putIfAbsent(groupId, colorId);
        nextIndexByGrp .putIfAbsent(groupId, 0);

        if (!rrGroups.contains(groupId)) rrGroups.addLast(groupId);
    }

    /* ===================== تولید بیت به روش Round-Robin ===================== */

    private void produceBitsRoundRobin() {
        int guard = 512;  // جلوگیری از حلقه بی‌نهایت زمانی که enqueue موفق است

        while (!rrGroups.isEmpty() && guard-- > 0) {
            int gid  = rrGroups.removeFirst();
            int left = remainingBits.getOrDefault(gid, 0);

            if (left <= 0) {
                cleanupGroup(gid);
                continue;
            }

            int parentSize = parentSizeByGrp.get(gid);
            int colorId    = colorIdByGrp   .get(gid);
            int index      = nextIndexByGrp .get(gid);

            BitPacket bit = new BitPacket(
                    PacketType.CIRCLE,
                    Config.DEFAULT_PACKET_SPEED,
                    gid, parentSize, index, colorId
            );

            int bitSize = Config.PACKET_SIZE_UNITS_CIRCLE * Config.PACKET_SIZE_MULTIPLIER;
            bit.setWidth(bitSize);
            bit.setHeight(bitSize);
            KinematicsRegistry.setProfile(bit, randomMessengerProfile());

            boolean accepted = box.enqueue(bit);
            if (accepted) {
                registry.registerSplit(gid, bit);
                left--; index++;

                if (left > 0) {
                    remainingBits.put(gid, left);
                    nextIndexByGrp .put(gid, index);
                    rrGroups.addLast(gid);          // هنوز بیت باقی‌ست
                } else {
                    cleanupGroup(gid);              // گروه تمام شد
                }
            } else {
                rrGroups.addFirst(gid);              // بافر پر – دفعهٔ بعد
                break;
            }
        }
    }


    private void cleanupGroup(int gid) {
        remainingBits.remove(gid);
        parentSizeByGrp.remove(gid);
        colorIdByGrp   .remove(gid);
        nextIndexByGrp .remove(gid);
    }

    /* ===================== ابزار کمکی ===================== */

    private KinematicsProfile randomMessengerProfile() {
        switch (rnd.nextInt(3)) {
            case 0:  return KinematicsProfile.MSG1;
            case 1:  return KinematicsProfile.MSG2;
            default: return KinematicsProfile.MSG3;
        }
    }

    public void clear() {
        processedPackets.clear();
        pendingLargePackets.clear();

        remainingBits.clear();
        parentSizeByGrp.clear();
        colorIdByGrp.clear();
        nextIndexByGrp.clear();
        rrGroups.clear();
    }
}

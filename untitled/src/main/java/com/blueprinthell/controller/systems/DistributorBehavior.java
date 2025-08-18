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
import java.util.List;


public final class DistributorBehavior implements SystemBehavior, SnapshottableBehavior {

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
        if (!large.hasGroup()) {
            // پکت گروه ندارد - ابتدا تلاش برای الحاق به گروه باز موجود (بر اساس رنگ/سایز)
            colorId = large.getColorId();
            if (colorId <= 0) {
                Color cc = large.getCustomColor();
                if (cc != null) {
                    float[] hsb = Color.RGBtoHSB(cc.getRed(), cc.getGreen(), cc.getBlue(), null);
                    colorId = Math.round(hsb[0] * 360f) % 360;
                } else {
                    colorId = rnd.nextInt(360);
                }
            }
            Integer adopt = registry.findOpenGroupByColorAndSize(colorId, parentSize);
            if (adopt != null) {
                groupId = adopt;
            } else {
                groupId = registry.createGroup(parentSize, expectedBits, colorId);
            }
            large.setGroupInfo(groupId, expectedBits, colorId);
        } else {
            // پکت از قبل گروه دارد
            groupId = large.getGroupId();
            colorId = large.getColorId();

            // ⭐ فقط اگر گروه واقعاً وجود ندارد، بازسازی کن
            if (registry.get(groupId) == null) {
                registry.createGroupWithId(groupId, parentSize, expectedBits, colorId);
            } else if (registry.get(groupId).isClosed()) {
                // گروه در snapshot بسته شده — دوباره split نکن
                return;
            }
        }

        boolean removed = box.removeFromBuffer(large);
        if (!removed) {
            // اگر نتوانست حذف کند، مشکلی وجود دارد
            return;
        }



        // از رجیستری بخوان که تا الان چند بیت برای این گروه «دریافت» شده
        int alreadyProduced = 0;
        var gs = registry.get(groupId);
        if (gs != null) {
            if (gs.isClosed()) {
                // گروه در snapshot بسته شده — دوباره split نکن
                return;
            }
            alreadyProduced = Math.max(0, gs.getReceivedBits());
        }

// فقط «باقیمانده» را حساب کن
        int toProduce = Math.max(0, expectedBits - alreadyProduced);

// اگر چیزی باقی نمانده، فقط ایندکس را هم‌تراز کن و برگرد
        parentSizeByGrp.putIfAbsent(groupId, parentSize);
        colorIdByGrp   .putIfAbsent(groupId, colorId);
        nextIndexByGrp .putIfAbsent(groupId, alreadyProduced);

        if (toProduce <= 0) {
            return; // دیگر نیازی به ورود به صف round-robin نیست
        }

// حالا واقعاً همان باقیمانده را برای تولید در نظر بگیر
        remainingBits.merge(groupId, toProduce, Integer::sum);

// در نهایت در صف round-robin قرار بده اگر قبلاً نیست
        if (!rrGroups.contains(groupId)) rrGroups.addLast(groupId);

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

    @Override
    public Map<String, Object> captureState() {
        Map<String, Object> state = new HashMap<>();
        state.put("remainingBits", new HashMap<>(remainingBits));
        state.put("parentSizeByGrp", new HashMap<>(parentSizeByGrp));
        state.put("colorIdByGrp", new HashMap<>(colorIdByGrp));
        state.put("nextIndexByGrp", new HashMap<>(nextIndexByGrp));
        state.put("rrGroups", new ArrayList<>(rrGroups));
        return state;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void restoreState(Map<String, Object> state) {
        if (state == null) return;

        clear(); // پاکسازی state فعلی

        // بازیابی state
        Map<Integer, Integer> rb = (Map<Integer, Integer>) state.get("remainingBits");
        if (rb != null) remainingBits.putAll(rb);

        Map<Integer, Integer> ps = (Map<Integer, Integer>) state.get("parentSizeByGrp");
        if (ps != null) parentSizeByGrp.putAll(ps);

        Map<Integer, Integer> ci = (Map<Integer, Integer>) state.get("colorIdByGrp");
        if (ci != null) colorIdByGrp.putAll(ci);

        Map<Integer, Integer> ni = (Map<Integer, Integer>) state.get("nextIndexByGrp");
        if (ni != null) nextIndexByGrp.putAll(ni);

        List<Integer> rr = (List<Integer>) state.get("rrGroups");
        if (rr != null) rrGroups.addAll(rr);
    }
}

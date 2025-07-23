package com.blueprinthell.model.large;

import com.blueprinthell.model.PacketModel;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <h2>LargeGroupRegistry</h2>
 * رجیستری سراسری/سطحی برای مدیریت چرخهٔ حیات "گروه"‌های پکت‌های حجیم.
 * <p>
 * وقتی یک {@code LargePacket} در سیستم Distributor شکسته می‌شود، برای آن یک "groupId" یکتا ساخته می‌شود و
 * تعداد بیت‌پکت‌های مورد انتظار (expectedBits) ثبت می‌گردد. هر بیت‌پکت هنگام رسیدن به Merger باید با همین groupId
 * ثبت شود تا زمانی که همهٔ آن‌ها جمع شدند، بتوان دوباره پکت حجیم را ساخت. اگر برخی بیت‌ها گم شوند، رجیستری به
 * ما اجازه می‌دهد مقدار Loss را محاسبه و گزارش کنیم.
 * </p>
 *
 * <p>
 * این کلاس فقط داده و منطق ثبتی را نگه می‌دارد؛ ساخت/ترکیب واقعی پکت‌ها توسط Behaviorهای Distributor / Merger انجام می‌شود.
 * </p>
 */
public final class LargeGroupRegistry {

    /* --------------------------------------------------------------- */
    /*                           Inner types                            */
    /* --------------------------------------------------------------- */

    /** وضعیت داخلی هر گروه. */
    public static final class GroupState {
        public final int groupId;
        public final int originalSizeUnits;   // اندازهٔ پکت حجیم اصلی (برای Loss)
        public final int expectedBits;        // تعداد بیت‌پکت‌هایی که باید برسند
        public final int colorId;             // برای UI (اختیاری – می‌توان صفر گذاشت)

        private int receivedBits = 0;         // تعداد بیت‌های دریافت شده تا کنون
        private int lostBits     = 0;         // بیت‌هایی که رسماً از دست رفته علامت زده شدند
        private final List<PacketModel> collectedPackets = new ArrayList<>();

        private boolean closed   = false;     // پس از نهایی‌سازی (Merge یا ابطال) true می‌شود

        GroupState(int groupId, int originalSizeUnits, int expectedBits, int colorId) {
            this.groupId = groupId;
            this.originalSizeUnits = originalSizeUnits;
            this.expectedBits = expectedBits;
            this.colorId = colorId;
        }

        /** آیا همهٔ بیت‌ها دریافت شده‌اند؟ */
        public boolean isComplete() {
            return receivedBits >= expectedBits;
        }

        public int getReceivedBits() { return receivedBits; }
        public int getExpectedBits() { return expectedBits; }
        public int getMissingBits()  { return Math.max(0, expectedBits - receivedBits); }
        public int getLostBits()     { return lostBits; }
        public int getOriginalSizeUnits() { return originalSizeUnits; }
        public int getColorId()      { return colorId; }
        public boolean isClosed()    { return closed; }
        public List<PacketModel> getCollectedPackets() { return Collections.unmodifiableList(collectedPackets); }

        private void addPacket(PacketModel p) {
            collectedPackets.add(p);
            receivedBits++;
        }

        private void markLost(int count) {
            lostBits += count;
        }

        private void close() { closed = true; }
    }

    /* --------------------------------------------------------------- */
    /*                             Fields                               */
    /* --------------------------------------------------------------- */

    private final AtomicInteger idSeq = new AtomicInteger(1);
    private final Map<Integer, GroupState> groups = new HashMap<>();

    /* --------------------------------------------------------------- */
    /*                               API                                 */
    /* --------------------------------------------------------------- */

    /**
     * یک گروه جدید ثبت می‌کند و شناسهٔ یکتا برمی‌گرداند.
     * @param originalSizeUnits اندازهٔ پکت حجیم اصلی
     * @param expectedBits       تعداد بیت‌پکت‌هایی که انتظار می‌رود برسند
     * @param colorId            رنگ نمایشی (در UI). اگر نیاز ندارید 0 بدهید.
     */
    public int createGroup(int originalSizeUnits, int expectedBits, int colorId) {
        int id = idSeq.getAndIncrement();
        GroupState st = new GroupState(id, originalSizeUnits, expectedBits, colorId);
        groups.put(id, st);
        return id;
    }

    /**
     * ثبت رسیدن یک بیت‌پکت به Merger.
     * @param groupId شناسهٔ گروه
     * @param bit     پکت بیت (معمولاً پیام‌رسان سایز 1)
     * @return true اگر پس از این ثبت، گروه کامل شده باشد.
     */
    public boolean registerArrival(int groupId, PacketModel bit) {
        GroupState st = groups.get(groupId);
        if (st == null || st.closed) return false;
        st.addPacket(bit);
        return st.isComplete();
    }

    /**
     * اگر بیت‌پکتی گم شد/Drop شد، می‌توانیم آن را ثبت کنیم تا Loss محاسبه شود.
     * @param groupId شناسهٔ گروه
     * @param count   تعداد بیت‌های از دست رفته (معمولاً 1)
     */
    public void markBitLost(int groupId, int count) {
        GroupState st = groups.get(groupId);
        if (st == null || st.closed) return;
        st.markLost(count);
    }

    /**
     * @return وضعیت گروه (Immutable view). اگر یافت نشود null.
     */
    public GroupState get(int groupId) {
        return groups.get(groupId);
    }

    /**
     * وقتی Merger تصمیم گرفت گروه را نهایی کند (چه کامل چه ناقص) این متد را صدا بزنید.
     * پس از آن، گروه بسته می‌شود و باید یا حذف شود یا از روی آن LargePacket ساخته شود.
     */
    public void closeGroup(int groupId) {
        GroupState st = groups.get(groupId);
        if (st != null) st.close();
    }

    /** گروه را از رجیستری پاک می‌کند (پس از ساخت پکت حجیم یا پایان مرحله). */
    public void removeGroup(int groupId) {
        groups.remove(groupId);
    }

    /** همه‌چیز را پاک می‌کند (ریست مرحله). */
    public void clear() {
        groups.clear();
    }

    /**
     * محاسبهٔ سادهٔ Loss: اصل - دریافت‌شده (یا اصل - بازسازی‌شده). می‌توانید بعداً فرمول پیچیده‌تر را جایگزین کنید.
     */
    public int computeSimpleLoss(int groupId) {
        GroupState st = groups.get(groupId);
        if (st == null) return 0;
        int rebuilt = st.getReceivedBits();
        return Math.max(0, st.originalSizeUnits - rebuilt);
    }

    /**
     * نسخهٔ عمومی‌تر: اگر گروه به چند پکت حجیم تقسیم دوباره شد، اندازه‌ها را پاس بدهید تا Loss حساب شود.
     * فعلاً پیاده‌سازی ساده است: original - sum(parts). می‌توان بعداً طبق فرمول پروژه تغییر داد.
     */
    public static int computeLossWithParts(int originalSize, List<Integer> rebuiltParts) {
        int sum = 0;
        for (int n : rebuiltParts) sum += n;
        return Math.max(0, originalSize - sum);
    }

    /** دید فقط خواندنی از گروه‌ها (برای تست/دیباگ). */
    public Map<Integer, GroupState> view() {
        return Collections.unmodifiableMap(groups);
    }
}

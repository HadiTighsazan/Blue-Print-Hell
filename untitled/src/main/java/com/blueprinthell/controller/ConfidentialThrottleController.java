package com.blueprinthell.controller;

import com.blueprinthell.model.*;
import com.blueprinthell.motion.KinematicsProfile;
import com.blueprinthell.motion.KinematicsRegistry;
import com.blueprinthell.motion.ProfileParams;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * <h2>ConfidentialThrottleController</h2>
 * کنترلری که سرعت پکت‌های «محرمانه» را وقتی مقصدشان (SystemBox) در حال حاضر پکتی در بافر دارد،
 * به مقدار مشخصی کاهش می‌دهد تا هم‌زمان داخل باکس نباشند.
 *
 * <ul>
 *   <li>به هر تیک روی تمام سیم‌ها نگاه می‌کند.</li>
 *   <li>اگر پکت {@link ConfidentialPacket} باشد یا پروفایلش {@link KinematicsProfile#CONFIDENTIAL} باشد
 *       و پارامتر {@link ProfileParams#slowDownBeforeBusyBox} فعال باشد، بررسی می‌کند.</li>
 *   <li>در صورت پر بودن بافر مقصد، سرعت جاری پکت تا {@link ProfileParams#slowDownSpeed} کلمپ می‌شود.</li>
 *   <li>در غیر این صورت کنترلی انجام نمی‌شود و استراتژی حرکتی آزاد است سرعت را تعیین کند.</li>
 * </ul>
 */
public class ConfidentialThrottleController implements Updatable {

    private final List<WireModel> wires;
    private Map<WireModel, SystemBoxModel> destMap; // تزریق از بیرون

    /** اگر destMap در حین بازی عوض شد می‌توان با این فلگ غیرفعال کرد. */
    private boolean enabled = true;

    public ConfidentialThrottleController(List<WireModel> wires,
                                          Map<WireModel, SystemBoxModel> destMap) {
        this.wires = Objects.requireNonNull(wires, "wires");
        this.destMap = Objects.requireNonNull(destMap, "destMap");
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }

    public void setDestMap(Map<WireModel, SystemBoxModel> destMap) {
        this.destMap = Objects.requireNonNull(destMap, "destMap");
    }

    @Override
    public void update(double dt) {
        if (!enabled) return;
        for (WireModel w : wires) {
            SystemBoxModel dest = destMap.get(w);
            if (dest == null) continue;
            boolean busy = !dest.getBuffer().isEmpty();
            if (!busy) continue; // نیازی به کند کردن نیست

            for (PacketModel p : w.getPackets()) {
                if (!isConfidentialAndNeedsThrottle(p)) continue;
                ProfileParams params = KinematicsRegistry.getProfile(p).getParams();
                double slow = params.slowDownSpeed;
                if (p.getSpeed() > slow) {
                    p.setSpeed(slow);
                }
            }
        }
    }

    /* --------------------------------------------------------------- */
    /*                          helpers                                 */
    /* --------------------------------------------------------------- */
    private boolean isConfidentialAndNeedsThrottle(PacketModel p) {
        // 1) instance check
        if (p instanceof ConfidentialPacket) {
            KinematicsProfile prof = KinematicsRegistry.getProfile(p);
            return prof != null && prof.getParams().slowDownBeforeBusyBox;
        }
        // 2) profile check (maybe packet decorated differently but profile set) \
        KinematicsProfile prof = KinematicsRegistry.getProfile(p);
        return prof == KinematicsProfile.CONFIDENTIAL && prof.getParams().slowDownBeforeBusyBox;
    }
}

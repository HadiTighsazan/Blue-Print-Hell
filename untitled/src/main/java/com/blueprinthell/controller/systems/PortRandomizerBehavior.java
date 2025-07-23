package com.blueprinthell.controller.systems;

import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.PortShape;
import com.blueprinthell.model.large.LargePacket;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * <h2>PortRandomizerBehavior</h2>
 * وقتی یک {@link LargePacket} وارد باکس شود، شکل پورتی که از آن وارد شده است به‌صورت تصادفی به شکل دیگری تغییر می‌کند.
 * اگر پورت ورود در دسترس نباشد، به‌صورت fallback یکی از پورت‌های باکس تغییر می‌یابد.
 */
public final class PortRandomizerBehavior implements SystemBehavior {

    private final SystemBoxModel box;
    private final Random rnd = new Random();

    public PortRandomizerBehavior(SystemBoxModel box) {
        this.box = Objects.requireNonNull(box, "box");
    }

    @Override
    public void update(double dt) {
        // رفتار زمان‌محور ندارد
    }

    /** نسخهٔ جدید با پورت ورودی واقعی. */
    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (!(packet instanceof LargePacket)) return;
        if (enteredPort != null && belongsToBox(enteredPort)) {
            mutateShape(enteredPort);
        } else {
            // fallback: انتخاب تصادفی یک پورت
            randomizeOnePort();
        }
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        // نیازی به واکنش خاصی نیست
    }

    /* --------------------------------------------------------------- */
    /*                            Helpers                               */
    /* --------------------------------------------------------------- */

    private boolean belongsToBox(PortModel p) {
        return box.getInPorts().contains(p) || box.getOutPorts().contains(p);
    }

    private void randomizeOnePort() {
        List<PortModel> ins = box.getInPorts();
        if (!ins.isEmpty()) {
            mutateShape(ins.get(rnd.nextInt(ins.size())));
            return;
        }
        List<PortModel> outs = box.getOutPorts();
        if (!outs.isEmpty()) {
            mutateShape(outs.get(rnd.nextInt(outs.size())));
        }
    }

    private void mutateShape(PortModel port) {
        PortShape current = port.getShape();
        PortShape[] vals = PortShape.values();
        if (vals.length <= 1) return;
        PortShape next;
        do {
            next = vals[rnd.nextInt(vals.length)];
        } while (next == current);
        // PortModel اکنون setter دارد (طبق استراتژی A)
        port.setShape(next);
    }
}

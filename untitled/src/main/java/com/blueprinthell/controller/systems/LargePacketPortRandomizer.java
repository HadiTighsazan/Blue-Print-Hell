package com.blueprinthell.controller.systems;

import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.PortShape;
import com.blueprinthell.model.large.LargePacket;

import java.util.List;
import java.util.Objects;
import java.util.Random;

public final class LargePacketPortRandomizer implements SystemBehavior {

    private final SystemBoxModel box;
    private final Random rnd = new Random();

    public LargePacketPortRandomizer(SystemBoxModel box) {
        this.box = Objects.requireNonNull(box, "box");
    }

    @Override
    public void update(double dt) {
        // No periodic updates needed
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        // فقط برای LargePacket فعال شود
        if (!(packet instanceof LargePacket)) return;

        // تغییر تصادفی یکی از پورت‌های سیستم
        randomizeOnePort();
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        // No action needed
    }

    private void randomizeOnePort() {
        // انتخاب تصادفی بین پورت‌های ورودی و خروجی
        List<PortModel> allPorts = new java.util.ArrayList<>();
        allPorts.addAll(box.getInPorts());
        allPorts.addAll(box.getOutPorts());

        if (allPorts.isEmpty()) return;

        // انتخاب یک پورت تصادفی
        PortModel target = allPorts.get(rnd.nextInt(allPorts.size()));
        mutateShape(target);
    }

    private void mutateShape(PortModel port) {
        PortShape current = port.getShape();
        PortShape[] vals = PortShape.values();
        if (vals.length <= 1) return;

        // تغییر به یک شکل تصادفی متفاوت
        PortShape next;
        do {
            next = vals[rnd.nextInt(vals.length)];
        } while (next == current);

        port.setShape(next);
    }
}
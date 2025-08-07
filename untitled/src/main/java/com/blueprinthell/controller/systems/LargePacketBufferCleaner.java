package com.blueprinthell.controller.systems;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.large.LargePacket;

import java.util.*;

public final class LargePacketBufferCleaner implements SystemBehavior {

    private final SystemBoxModel box;
    private final PacketLossModel lossModel;

    public LargePacketBufferCleaner(SystemBoxModel box, PacketLossModel lossModel) {
        this.box = Objects.requireNonNull(box, "box");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
    }

    @Override
    public void update(double dt) {
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (!(packet instanceof LargePacket)) {
            return;
        }

        clearBufferExceptLarge((LargePacket) packet);
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
    }

    private void clearBufferExceptLarge(LargePacket newLarge) {
        Queue<PacketModel> buffer = box.getBuffer();
        if (buffer == null || buffer.isEmpty()) {
            return;
        }

        // جمع‌آوری تمام پکت‌های موجود در بافر
        List<PacketModel> allPackets = new ArrayList<>();
        PacketModel p;
        while ((p = box.pollPacket()) != null) {
            allPackets.add(p);
        }

        int destroyedUnits = 0;

        // فقط پکت حجیم جدید را نگه می‌داریم
        for (PacketModel packet : allPackets) {
            if (packet == newLarge) {
                box.enqueue(packet);
            } else {
                         if (packet instanceof LargePacket lp) {
                                 destroyedUnits += lp.getOriginalSizeUnits();
                             } else {
                                 destroyedUnits++;
                             }
            }
        }

        if (destroyedUnits  > 0) {
            lossModel.incrementBy(destroyedUnits);
        }
    }
}
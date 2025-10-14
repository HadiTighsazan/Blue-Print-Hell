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

        if (!box.getOutPorts().isEmpty()) {
            return;  // اگر پورت خروجی دارد، هیچ کاری نکن*****
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

        List<PacketModel> allPackets = new ArrayList<>();
        PacketModel p;
        while ((p = box.pollPacket()) != null) {
            allPackets.add(p);
        }

        // Re-enqueue only the new large packet; drop others.
        for (PacketModel packet : allPackets) {
            if (packet == newLarge) {
                box.enqueue(packet);
            } else {

                lossModel.incrementPacket(packet);
            }
        }
    }
}
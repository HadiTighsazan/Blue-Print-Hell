package com.blueprinthell.controller.systems;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.Updatable;

public interface SystemBehavior extends Updatable {


    default void onPacketEnqueued(PacketModel packet) {
        // legacy no-op
    }

    default void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        onPacketEnqueued(packet);
    }

    default void onEnabledChanged(boolean enabled) {
    }
}
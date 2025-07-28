package com.blueprinthell.controller.systems;

import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PortModel;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * آداپتور برای اتصال SystemBehavior به SystemBoxModel
 * و مدیریت فراخوانی متدهای behavior
 */
public class SystemBehaviorAdapter implements Updatable {

    private final SystemBoxModel box;
    private final SystemBehavior behavior;
    private final Deque<PacketModel> lastBuffer = new ArrayDeque<>();

    public SystemBehaviorAdapter(SystemBoxModel box, SystemBehavior behavior) {
        this.box = box;
        this.behavior = behavior;
    }

    @Override
    public void update(double dt) {
        behavior.update(dt);

        checkNewPackets();

        checkEnabledState();
    }

    private void checkNewPackets() {
        Deque<PacketModel> currentBuffer = new ArrayDeque<>(box.getBuffer());

        for (PacketModel packet : currentBuffer) {
            if (!lastBuffer.contains(packet)) {
                PortModel enteredPort = findEnteredPort(packet);
                behavior.onPacketEnqueued(packet, enteredPort);
            }
        }

        lastBuffer.clear();
        lastBuffer.addAll(currentBuffer);
    }

    private PortModel findEnteredPort(PacketModel packet) {

        return null;
    }

    private boolean lastEnabledState = true;

    private void checkEnabledState() {
        boolean currentEnabled = box.isEnabled();
        if (currentEnabled != lastEnabledState) {
            behavior.onEnabledChanged(currentEnabled);
            lastEnabledState = currentEnabled;
        }
    }
}
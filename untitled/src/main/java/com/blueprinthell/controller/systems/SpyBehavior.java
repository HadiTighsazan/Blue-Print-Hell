package com.blueprinthell.controller.systems;

import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.ProtectedPacket;
import com.blueprinthell.model.ConfidentialPacket;
import com.blueprinthell.model.large.LargePacket; // just in case we want special-case later
import com.blueprinthell.model.PacketOps;
import com.blueprinthell.model.PortModel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;


public final class SpyBehavior implements SystemBehavior {

    private final SystemBoxModel   box;
    private final BehaviorRegistry registry;
    private final PacketLossModel  lossModel;

    public SpyBehavior(SystemBoxModel box,
                       BehaviorRegistry registry,
                       PacketLossModel lossModel) {
        this.box = Objects.requireNonNull(box, "box");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
    }

    @Override
    public void update(double dt) {
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (packet instanceof ProtectedPacket) return;

        if (packet instanceof ConfidentialPacket) {
            removeFromBuffer(box, packet);
            lossModel.increment();
            return;
        }

        SystemBoxModel target = chooseAnotherSpy(box);
        if (target == null) {
            return;
        }

        if (!removeFromBuffer(box, packet)) {
            return;
        }
        if (!target.enqueue(packet)) {
            lossModel.increment();
        }
    }

    @Override
    public void onPacketEnqueued(PacketModel packet) {
        onPacketEnqueued(packet, null);
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
    }



    private SystemBoxModel chooseAnotherSpy(SystemBoxModel self) {
        List<SystemBoxModel> spies = new ArrayList<>();
        for (Map.Entry<SystemBoxModel, List<SystemBehavior>> e : registry.view().entrySet()) {
            boolean isSpy = false;
            for (SystemBehavior b : e.getValue()) {
                if (b instanceof SpyBehavior) { isSpy = true; break; }
            }
            if (isSpy && e.getKey() != self) {
                spies.add(e.getKey());
            }
        }
        if (spies.isEmpty()) return null;
        return spies.get(ThreadLocalRandom.current().nextInt(spies.size()));
    }

    private boolean removeFromBuffer(SystemBoxModel box, PacketModel target) {
        Deque<PacketModel> temp = new ArrayDeque<>();
        PacketModel p;
        boolean found = false;
        while ((p = box.pollPacket()) != null) {
            if (!found && p == target) {
                found = true;
            } else {
                temp.addLast(p);
            }
        }
        for (PacketModel q : temp) {
            box.enqueue(q);
        }
        return found;
    }
}

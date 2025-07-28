package com.blueprinthell.controller.systems;

import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.ProtectedPacket;
import com.blueprinthell.model.ConfidentialPacket;
import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.WireModel;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class SpyBehavior implements SystemBehavior {

    private final SystemBoxModel   box;
    private final BehaviorRegistry registry;
    private final PacketLossModel  lossModel;
    private final List<WireModel>  wires;
    private final Map<WireModel, SystemBoxModel> destMap;

    private final Queue<TransferRequest> pendingTransfers = new ArrayDeque<>();

    public SpyBehavior(SystemBoxModel box,
                       BehaviorRegistry registry,
                       PacketLossModel lossModel,
                       List<WireModel> wires,
                       Map<WireModel, SystemBoxModel> destMap) {
        this.box = Objects.requireNonNull(box, "box");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
        this.wires = Objects.requireNonNull(wires, "wires");
        this.destMap = Objects.requireNonNull(destMap, "destMap");
    }

    @Override
    public void update(double dt) {
        processPendingTransfers();
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (packet instanceof ProtectedPacket) {
            return;
        }

        if (packet instanceof ConfidentialPacket) {
            removeFromBuffer(box, packet);
            lossModel.increment();
            return;
        }

        SystemBoxModel targetSpy = chooseAnotherSpy(box);
        if (targetSpy == null) {
            return;
        }

        if (!removeFromBuffer(box, packet)) {
            return;
        }

        pendingTransfers.add(new TransferRequest(packet, targetSpy));
    }

    @Override
    public void onPacketEnqueued(PacketModel packet) {
        onPacketEnqueued(packet, null);
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        if (!enabled) {
            pendingTransfers.clear();
        }
    }

    private void processPendingTransfers() {
        Iterator<TransferRequest> it = pendingTransfers.iterator();
        while (it.hasNext()) {
            TransferRequest req = it.next();

            if (transferToSpyOutput(req.packet, req.targetSpy)) {
                it.remove();
            }
        }
    }

    private boolean transferToSpyOutput(PacketModel packet, SystemBoxModel targetSpy) {
        List<WireModel> outgoingWires = findOutgoingWires(targetSpy);
        if (outgoingWires.isEmpty()) {
            lossModel.increment();
            return true;
        }

        WireModel selectedWire = outgoingWires.get(
                ThreadLocalRandom.current().nextInt(outgoingWires.size())
        );

        selectedWire.attachPacket(packet, 0.0);
        return true;
    }

    private List<WireModel> findOutgoingWires(SystemBoxModel system) {
        List<WireModel> outgoing = new ArrayList<>();
        for (WireModel wire : wires) {
            if (system.getOutPorts().contains(wire.getSrcPort())) {
                outgoing.add(wire);
            }
        }
        return outgoing;
    }

    private SystemBoxModel chooseAnotherSpy(SystemBoxModel self) {
        List<SystemBoxModel> spies = new ArrayList<>();
        for (Map.Entry<SystemBoxModel, List<SystemBehavior>> e : registry.view().entrySet()) {
            SystemBoxModel candidate = e.getKey();
            if (candidate == self) continue;

            boolean isSpy = false;
            for (SystemBehavior b : e.getValue()) {
                if (b instanceof SpyBehavior) {
                    isSpy = true;
                    break;
                }
            }
            if (isSpy) {
                spies.add(candidate);
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

    private static class TransferRequest {
        final PacketModel packet;
        final SystemBoxModel targetSpy;

        TransferRequest(PacketModel packet, SystemBoxModel targetSpy) {
            this.packet = packet;
            this.targetSpy = targetSpy;
        }
    }
}
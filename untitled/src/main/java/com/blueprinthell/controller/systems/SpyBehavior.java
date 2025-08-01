package com.blueprinthell.controller.systems;

import com.blueprinthell.model.*;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public final class SpyBehavior implements SystemBehavior, Updatable {

    private final SystemBoxModel box;
    private final BehaviorRegistry registry;
    private final PacketLossModel lossModel;
    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destMap;

    private final Random rnd = new Random();

    // Debug/telemetry
    private long teleportCount = 0;
    private long destroyedConfidentialCount = 0;
    private volatile boolean lastEnabledState = true;

    // Thread-safe transfer lock to prevent race conditions
    private static final ReentrantLock TRANSFER_LOCK = new ReentrantLock();

    // Track packets currently being transferred to avoid double-processing
    private static final Set<PacketModel> PACKETS_IN_TRANSFER = Collections.newSetFromMap(new WeakHashMap<>());

    public SpyBehavior(SystemBoxModel box,
                       BehaviorRegistry registry,
                       PacketLossModel lossModel,
                       List<WireModel> wires,
                       Map<WireModel, SystemBoxModel> destMap) {
        this.box = Objects.requireNonNull(box, "box");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.lossModel = Objects.requireNonNull(lossModel, "lossModel");
        this.wires = Objects.requireNonNullElseGet(wires, ArrayList::new);
        this.destMap = Objects.requireNonNullElseGet(destMap, HashMap::new);
    }


    @Override
    public void onPacketEnqueued(PacketModel packet) {
        onPacketEnqueued(packet, null);
    }

    @Override
    public void onPacketEnqueued(PacketModel packet, PortModel enteredPort) {
        if (packet == null) return;

        /* ADD START — revert زودهنگام با نگاشت سراسری (برای سازگاری با consumeGlobal) */
        if (PacketOps.isProtected(packet)) {
            PacketModel origGlobal = VpnRevertHints.consumeGlobal(packet);
            if (origGlobal != null) {
                replaceInBuffer(packet, origGlobal);
                return;
            }
        }
        // (اختیاری) اگر می‌خواهید Conf-VPN هم در جاسوس به حالت قبل برگردد:
        if (PacketOps.isConfidentialVpn(packet)) {
            PacketModel origGlobal2 = VpnRevertHints.consumeGlobal(packet);
            if (origGlobal2 != null) {
                replaceInBuffer(packet, origGlobal2);
                return;
            }
        }
        /* ADD END */

        if (PacketOps.isProtected(packet)) {
            PacketModel orig = VpnRevertHints.consume(packet);
            if (orig != null) {
                replaceInBuffer(packet, orig);
                return;
            }
            return;
        }

        // Confidential packets are destroyed (count as loss)
        if (PacketOps.isConfidential(packet)) {
            if (box.removeFromBuffer(packet)) {
                destroyedConfidentialCount++;
                lossModel.increment();
            }
            return;
        }

        if (enteredPort == null || !enteredPort.isInput()) {
            return;
        }

        // Check if this packet is already being transferred
        TRANSFER_LOCK.lock();
        try {
            if (PACKETS_IN_TRANSFER.contains(packet)) {
                return; // Already being processed by another spy system
            }
            PACKETS_IN_TRANSFER.add(packet);
        } finally {
            TRANSFER_LOCK.unlock();
        }

        final SystemBoxModel target = chooseAnotherSpy();
        if (target == null) {
            TRANSFER_LOCK.lock();
            try {
                PACKETS_IN_TRANSFER.remove(packet);
            } finally {
                TRANSFER_LOCK.unlock();
            }
            return;
        }

        if (transferToAnotherSpy(packet, target)) {
            teleportCount++;
        }

        // Clean up tracking after transfer attempt
        TRANSFER_LOCK.lock();
        try {
            PACKETS_IN_TRANSFER.remove(packet);
        } finally {
            TRANSFER_LOCK.unlock();
        }
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        lastEnabledState = enabled;
    }


    @Override
    public void update(double dt) {
    }

    private SystemBoxModel chooseAnotherSpy() {
        final List<SystemBoxModel> candidates = new ArrayList<>();
        try {
            for (Map.Entry<SystemBoxModel, List<SystemBehavior>> e : registry.view().entrySet()) {
                final SystemBoxModel candidate = e.getKey();
                if (candidate == box) continue;
                final List<SystemBehavior> bs = e.getValue();
                if (bs == null) continue;
                for (SystemBehavior b : bs) {
                    if (b instanceof SpyBehavior) {
                        if (hasUsableOutbound(candidate)) {
                            candidates.add(candidate);
                        } else {
                        }
                        break;
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(rnd.nextInt(candidates.size()));
    }

    private boolean hasUsableOutbound(SystemBoxModel system) {
        if (system.getOutPorts() == null || system.getOutPorts().isEmpty()) return false;
        if (wires != null && !wires.isEmpty()) {
            for (WireModel w : wires) {
                if (w == null) continue;
                PortModel src = w.getSrcPort();
                if (src != null && system.getOutPorts().contains(src)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private boolean transferToAnotherSpy(PacketModel packet, SystemBoxModel target) {
        if (packet == null || target == null) return false;

        // Use double-checked locking pattern for thread safety
        boolean removed = false;
        boolean enqueued = false;

        // First try to remove from source buffer
        TRANSFER_LOCK.lock();
        try {
            removed = box.removeFromBuffer(packet);
            if (removed) {
                // Try to enqueue in target
                enqueued = target.enqueue(packet, null);
                if (!enqueued) {
                    // If enqueue failed, put it back
                    box.enqueueFront(packet);
                    removed = false;
                }
            }
        } finally {
            TRANSFER_LOCK.unlock();
        }

        return removed && enqueued;
    }

    private void replaceInBuffer(PacketModel oldPkt, PacketModel newPkt) {
        Deque<PacketModel> temp = new ArrayDeque<>();
        boolean replaced = false;
        PacketModel p;

        // Thread-safe buffer manipulation
        TRANSFER_LOCK.lock();
        try {
            while ((p = box.pollPacket()) != null) {
                if (!replaced && p == oldPkt) {
                    temp.addLast(newPkt);
                    replaced = true;
                } else {
                    temp.addLast(p);
                }
            }
            for (PacketModel q : temp) {
                box.enqueue(q);
            }
        } finally {
            TRANSFER_LOCK.unlock();
        }
    }

    public void clear() {
        TRANSFER_LOCK.lock();
        try {
            PACKETS_IN_TRANSFER.clear();
        } finally {
            TRANSFER_LOCK.unlock();
        }
    }
}
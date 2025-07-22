package com.blueprinthell.controller;

import com.blueprinthell.controller.systems.BehaviorRegistry;
import com.blueprinthell.model.*;

import java.util.List;
import java.util.Map;

/**
 * Dispatches packets that reach the end of a wire into the destination SystemBox.
 * Adds coin rewards on successful enqueue. If the destination buffer is full,
 * the packet is dropped and PacketLossModel is incremented.
 *
 * G1 – مرحله 1: اضافه شدن Hook برای رفتار سیستم‌ها (onPacketEnqueued).
 * عملکرد قبلی حفظ شده؛ فقط در صورت وجود BehaviorRegistry بعد از enqueue موفق، رفتار سیستم صدا زده می‌شود.
 */
public class PacketDispatcherController implements Updatable {

    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destinationMap;
    private final CoinModel coinModel;
    private final PacketLossModel lossModel;
    private final BehaviorRegistry behaviorRegistry; // ممکن است null باشد

    /** سازندهٔ قدیمی برای سازگاری */
    public PacketDispatcherController(List<WireModel> wires,
                                      Map<WireModel, SystemBoxModel> destinationMap,
                                      CoinModel coinModel,
                                      PacketLossModel lossModel) {
        this(wires, destinationMap, coinModel, lossModel, null);
    }

    /** سازندهٔ جدید با BehaviorRegistry. */
    public PacketDispatcherController(List<WireModel> wires,
                                      Map<WireModel, SystemBoxModel> destinationMap,
                                      CoinModel coinModel,
                                      PacketLossModel lossModel,
                                      BehaviorRegistry behaviorRegistry) {
        this.wires = wires;
        this.destinationMap = destinationMap;
        this.coinModel = coinModel;
        this.lossModel = lossModel;
        this.behaviorRegistry = behaviorRegistry;
    }

    @Override
    public void update(double dt) {
        for (WireModel wire : wires) {
            List<PacketModel> arrived = wire.update(dt);
            SystemBoxModel dest = destinationMap.get(wire);
            for (PacketModel packet : arrived) {
                // VPN revert one-shot hint
                PacketModel reverted = com.blueprinthell.controller.systems.VpnRevertHints.consume(packet);
                if (reverted != null) {
                    packet = reverted;
                }

                boolean accepted = dest.enqueue(packet);
                if (accepted) {
                    // Reward coins only if packet successfully enters the system
                    coinModel.add(packet.getType().coins);

                    // Hook behaviour (if any)
                    if (behaviorRegistry != null) {
                        var behavior = behaviorRegistry.get(dest);
                        if (behavior != null) {
                            behavior.onPacketEnqueued(packet);
                        }
                    }
                } else {
                    // Buffer full: drop packet and count as loss
                    lossModel.increment();
                }
            }
        }
    }
}

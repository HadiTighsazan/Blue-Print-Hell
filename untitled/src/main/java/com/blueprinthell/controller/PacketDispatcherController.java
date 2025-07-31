package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.LargePacket;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class PacketDispatcherController implements Updatable {

    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destinationMap;
    private final CoinModel coinModel;
    private final PacketLossModel lossModel;
    private WireDurabilityController durability;
    private WireRemovalController wireRemover;
    private final List<WireModel> wiresForRemoval = new ArrayList<>();

    public PacketDispatcherController(List<WireModel> wires,
                                      Map<WireModel, SystemBoxModel> destinationMap,
                                      CoinModel coinModel,
                                      PacketLossModel lossModel) {
        this.wires = wires;
        this.destinationMap = destinationMap;
        this.coinModel = coinModel;
        this.lossModel = lossModel;
    }

    public void setDurabilityController(WireDurabilityController durability) {
        this.durability = durability;
    }

    public void setWireRemover(WireRemovalController remover) {
        this.wireRemover = remover;
    }

    @Override
    public void update(double dt) {
        for (WireModel wire : wires) {
            List<PacketModel> arrived = wire.update(dt);
            SystemBoxModel dest = destinationMap.get(wire);
            PortModel dstPort = wire.getDstPort();

            for (PacketModel packet : arrived) {

                // بررسی و شمارش عبور LargePacket
                if (packet instanceof LargePacket) {
                    wire.incrementLargePacketPass();

                    // بررسی آیا سیم باید حذف شود
                    if (wire.shouldBeDestroyed()) {
                        if (wireRemover != null) {
                            wireRemover.scheduleRemoval(wire);
                        } else {
                            wiresForRemoval.add(wire);
                        }
                    }
                }

                if (durability != null) {
                    durability.onPacketArrived(packet, wire);
                }

                if (dstPort != null && !dstPort.isCompatible(packet) && PacketOps.isMessenger(packet)) {
                    packet.setExitBoostMultiplier(2.0);
                }

                if (dest == null) {
                    // مقصد تعریف نشده؛ از ادامه دادن خودداری کنید
                    continue;
                }

                if (packet.getSpeed() > Config.MAX_ALLOWED_SPEED && dest.isEnabled()) {
                    dest.disable();
                    packet.setReturning(true);
                    wire.attachPacket(packet, 1.0);
                    continue;
                }

                boolean accepted = dest.enqueue(packet, dstPort);

                if (accepted) {
                    int coins = PacketOps.coinValueOnEntry(packet);

                    if (dest.getPrimaryKind() == SystemKind.VPN) {
                        if (PacketOps.isMessenger(packet)) {
                            coins = 5;
                        } else if (PacketOps.isConfidential(packet) && !PacketOps.isConfidentialVpn(packet)) {
                            coins = 4;
                        }
                    }

                    if (coins > 0) {
                        coinModel.add(coins);
                    }
                } else {
                    lossModel.increment();
                }
            }
        }

        // حذف سیم‌های نشانه‌گذاری شده
        for (WireModel wire : wiresForRemoval) {
            wires.remove(wire);
            destinationMap.remove(wire);
            // اطلاع رسانی به UI برای حذف نمای سیم
            // این کار باید در WireRemovalController انجام شود
        }
        wiresForRemoval.clear();
    }
}

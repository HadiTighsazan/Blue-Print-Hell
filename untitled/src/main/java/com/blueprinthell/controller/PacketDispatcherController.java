package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;

import java.util.List;
import java.util.Map;

public class PacketDispatcherController implements Updatable {

    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destinationMap;
    private final CoinModel coinModel;
    private final PacketLossModel lossModel;

    public PacketDispatcherController(List<WireModel> wires,
                                      Map<WireModel, SystemBoxModel> destinationMap,
                                      CoinModel coinModel,
                                      PacketLossModel lossModel) {
        this.wires = wires;
        this.destinationMap = destinationMap;
        this.coinModel = coinModel;
        this.lossModel = lossModel;
    }

    @Override
    public void update(double dt) {
        for (WireModel wire : wires) {
            List<PacketModel> arrived = wire.update(dt);
            SystemBoxModel dest = destinationMap.get(wire);
            PortModel dstPort = wire.getDstPort(); // پورت مقصد

            for (PacketModel packet : arrived) {
                // قانون مرحله ۳: ورود از پورت ناسازگار => خروج بعدی با 2×
                if (dstPort != null && !dstPort.isCompatible(packet) && PacketOps.isMessenger(packet)) {
                    packet.setExitBoostMultiplier(2.0);
                }

                if (packet.getSpeed() > Config.MAX_ALLOWED_SPEED && dest.isEnabled()) {
                    dest.disable();
                }

                // استفاده از متد جدید enqueue با پورت
                boolean accepted = dest.enqueue(packet, dstPort);

                if (accepted) {
                    coinModel.add(packet.getType().coins);
                } else {
                    lossModel.increment();
                }
            }
        }
    }
}
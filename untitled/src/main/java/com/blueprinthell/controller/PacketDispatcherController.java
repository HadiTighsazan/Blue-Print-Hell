package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.CoinModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.PacketLossModel;
import java.util.List;
import java.util.Map;

/**
 * Dispatches packets that reach the end of a wire into the destination SystemBox.
 * Adds coin rewards on successful enqueue. If the destination buffer is full,
 * the packet is dropped and PacketLossModel is incremented.
 * Also disables the destination if a packet enters at excessive speed.
 */
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
            for (PacketModel packet : arrived) {
                // Disable system if packet speed exceeds threshold and system is still enabled
                if (packet.getSpeed() > Config.MAX_ALLOWED_SPEED && dest.isEnabled()) {
                    dest.disable();
                }
                boolean accepted = dest.enqueue(packet);
                if (accepted) {
                    // Reward coins only if packet successfully enters the system
                    coinModel.add(packet.getType().coins);
                } else {
                    // Buffer full: drop packet and count as loss
                    lossModel.increment();
                }
            }
        }
    }
}

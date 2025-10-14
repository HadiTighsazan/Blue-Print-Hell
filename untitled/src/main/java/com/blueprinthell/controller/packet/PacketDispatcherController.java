package com.blueprinthell.controller.packet;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.wire.WireDurabilityController;
import com.blueprinthell.controller.wire.WireRemovalController;
import com.blueprinthell.controller.simulation.SimulationController;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.model.large.MergedPacket;

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
    private Map<WireModel, SystemBoxModel> sourceMap;
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
    public void setSourceMap(Map<WireModel, SystemBoxModel> sourceMap) {
                this.sourceMap = sourceMap;
            }
    @Override
    public void update(double dt) {
        for (WireModel wire : wires) {
            List<PacketModel> arrived = wire.update(dt);
            SystemBoxModel dest = destinationMap.get(wire);
            PortModel dstPort = wire.getDstPort();

            for (PacketModel packet : arrived) {

                if (packet.isReturning()) {
                    SystemBoxModel srcBox = (sourceMap != null) ? sourceMap.get(wire) : null;
                    PortModel srcPort = wire.getSrcPort();
                    if (srcBox != null && srcPort != null) {
                        boolean ok = srcBox.enqueue(packet, srcPort);
                        if (ok) {
                            packet.setReturning(false);
                            continue;
                        } else {
                            lossModel.incrementPacket(packet);
                            SimulationController sim = WireModel.getSimulationController();
                            if (sim != null && sim.getPacketProducerController() != null) {
                                sim.getPacketProducerController().onPacketLost();
                            }
                            continue;
                        }
                    }
                }

                if (packet instanceof LargePacket lp && !lp.isRebuiltFromBits() && !(packet instanceof MergedPacket)) {
                    wire.incrementLargePacketPass();

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

                if (dest == null) {
                    continue;
                }


                double entrySpeed = packet.getSpeed();
                double maxAllowed = getMaxAllowedSpeed(packet);
                if (entrySpeed > maxAllowed + 1e-6 && dest.isEnabled()) {
                    dest.disable();
                    packet.setReturning(true);
                    wire.attachPacket(packet, 1.0); // progress=1.0 یعنی از انتهای سیم برگردد
                    continue;
                }

                if (dstPort != null && PacketOps.isMessenger(packet)) {
                    boolean enteredIncompat =
                            dstPort.isInput() &&
                                    (dstPort.getShape() != PortModel.shapeForPacket(packet));
                    if (enteredIncompat) {
                        packet.setExitBoostMultiplier(2.0);
                    }
                }

                boolean accepted = dest.enqueue(packet, dstPort);

                if (accepted) {
                    int coins = 0;

                    if (packet instanceof LargePacket lp2) {
                        coins = lp2.getOriginalSizeUnits();
                    }
                    else if (PacketOps.isConfidential(packet)) {
                        if (PacketOps.isConfidentialVpn(packet)) {
                            coins = 4;
                        } else {
                            coins = 3; // پکت محرمانه عادی
                        }
                    }
                    else if (PacketOps.isMessenger(packet)) {
                        coins = PacketOps.coinValueOnEntry(packet);
                    }
                    else if (dest.getPrimaryKind() == SystemKind.VPN) {
                        if (PacketOps.isMessenger(packet)) {
                            coins = 5;
                        }
                    }

                    if (coins > 0) {
                        coinModel.add(coins);
                    }
                } else {
                    if (!dest.isEnabled()) {
                        packet.setReturning(true);
                        wire.attachPacket(packet, 1.0);
                        continue;
                    } else {
                        lossModel.incrementPacket(packet);

                        // اطلاع به producer که پکت از بین رفت
                        SimulationController sim = WireModel.getSimulationController();
                        if (sim != null && sim.getPacketProducerController() != null) {
                            sim.getPacketProducerController().onPacketLost();
                        }
                    }
                }
            }
        }

        // حذف سیم‌های نشانه‌گذاری شده
        for (WireModel wire : wiresForRemoval) {
            wires.remove(wire);
            destinationMap.remove(wire);

        }
        wiresForRemoval.clear();
    }


    private double getMaxAllowedSpeed(PacketModel p) {
        return Config.MAX_ALLOWED_SPEED;
    }
}

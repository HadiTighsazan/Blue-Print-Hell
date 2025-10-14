package com.blueprinthell.controller.packet;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.systems.SystemBehaviorAdapter;
import com.blueprinthell.controller.systems.TeleportTracking;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.motion.MotionStrategy;
import com.blueprinthell.motion.MotionStrategyFactory;
import com.blueprinthell.controller.systems.RouteHints;
import com.blueprinthell.controller.systems.SystemKind;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class PacketRouterController implements Updatable {
    private final SystemBoxModel box;
    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destMap;
    private final PacketLossModel lossModel;
    private final Random rnd = new Random();

    // Statistics
    private long packetsRouted = 0;
    private long incompatibleRoutes = 0;
    private long droppedPackets = 0;

    private static final ReentrantLock TRANSFER_LOCK = new ReentrantLock();
    private static final Map<SystemBoxModel, Queue<PacketModel>> TELEPORTED_PACKETS = new HashMap<>();

    public PacketRouterController(SystemBoxModel box,
                                  List<WireModel> wires,
                                  Map<WireModel, SystemBoxModel> destMap,
                                  PacketLossModel lossModel) {
        this.box = box;
        this.wires = wires;
        this.destMap = destMap;
        this.lossModel = lossModel;
    }



    private boolean hasAvailableRoute() {
        return box.getOutPorts().stream()
                .anyMatch(port -> {
                    WireModel w = findWire(port);
                    if (w == null) return false;
                    SystemBoxModel d = destMap.get(w);
                    return d != null && d.isEnabled() && isWireEmpty(port);
                });
    }

    private boolean routePacket(PacketModel packet) {
        // Get available output ports with enabled destinations
        List<PortModel> availableOuts = box.getOutPorts().stream()
                .filter(port -> {
                    WireModel w = findWire(port);
                    if (w == null) return false;
                    SystemBoxModel d = destMap.get(w);
                    return d != null && d.isEnabled();
                })
                .collect(Collectors.toList());

        if (availableOuts.isEmpty()) {
            // No available routes - keep in buffer
            return false; // پکت در buffer می‌ماند (با منطق بازگردانی در update)
        }

        // Check if packet should be routed incompatibly (from Malicious system or hint)
        boolean forceIncompat = RouteHints.peekForceIncompatible(packet);
        boolean isMaliciousSource = (box.getPrimaryKind() == SystemKind.MALICIOUS);

        if (forceIncompat || isMaliciousSource) {
            return routeIncompatibly(packet, availableOuts);
        } else {
            return routeNormally(packet, availableOuts);
        }
    }

    private boolean routeNormally(PacketModel packet, List<PortModel> availableOuts) {
        // 1. ابتدا پورت‌های سازگار خالی را پیدا کن
        List<PortModel> emptyCompatPorts = availableOuts.stream()
                .filter(port -> port.isCompatible(packet))
                .filter(this::isWireEmpty)
                .collect(Collectors.toList());

        if (!emptyCompatPorts.isEmpty()) {
            // انتخاب تصادفی از بین پورت‌های سازگار خالی
            PortModel chosen = emptyCompatPorts.get(rnd.nextInt(emptyCompatPorts.size()));
            sendPacketToPort(packet, chosen);
            return true;
        }

        // 2. اگر پورت سازگار خالی نبود، هر پورت خالی را انتخاب کن
        List<PortModel> emptyPorts = availableOuts.stream()
                .filter(this::isWireEmpty)
                .collect(Collectors.toList());

        if (!emptyPorts.isEmpty()) {
            // انتخاب تصادفی از بین پورت‌های خالی
            PortModel chosen = emptyPorts.get(rnd.nextInt(emptyPorts.size()));
            sendPacketToPort(packet, chosen);
            return true;
        }

        // 3. هیچ پورت خالی نیست - تلاش ناموفق
        return false;
    }

    private boolean routeIncompatibly(PacketModel packet, List<PortModel> availableOuts) {
        // 1. ابتدا پورت‌های ناسازگار خالی را پیدا کن
        List<PortModel> emptyIncompatPorts = availableOuts.stream()
                .filter(port -> !port.isCompatible(packet))
                .filter(this::isWireEmpty)
                .collect(Collectors.toList());

        if (!emptyIncompatPorts.isEmpty()) {
            // انتخاب تصادفی از بین پورت‌های ناسازگار خالی
            PortModel chosen = emptyIncompatPorts.get(rnd.nextInt(emptyIncompatPorts.size()));
            sendPacketToPort(packet, chosen);
            return true;
        }

        // 2. اگر پورت ناسازگار خالی نبود، هر پورت خالی را انتخاب کن
        List<PortModel> emptyPorts = availableOuts.stream()
                .filter(this::isWireEmpty)
                .collect(Collectors.toList());

        if (!emptyPorts.isEmpty()) {
            // انتخاب تصادفی از بین پورت‌های خالی
            PortModel chosen = emptyPorts.get(rnd.nextInt(emptyPorts.size()));
            sendPacketToPort(packet, chosen);
            return true;
        }

        // 3. هیچ پورت خالی نیست - تلاش ناموفق
        return false;
    }



    private void sendPacketToPort(PacketModel packet, PortModel port) {
        WireModel wire = findWire(port);
        if (wire == null) {
            if (!box.enqueue(packet)) {
                drop(packet);
            }
            return;
        }

        // Apply exit boost and motion strategy
        boolean compatible = port.isCompatible(packet);
        double exitBoost = packet.consumeExitBoostMultiplier();
        packet.setStartSpeedMul(exitBoost);

        MotionStrategy ms = MotionStrategyFactory.create(packet, compatible);
        packet.setMotionStrategy(ms);

        wire.attachPacket(packet, 0.0);
        TeleportTracking.clearTeleported(packet);

        packetsRouted++;
        if (!compatible) incompatibleRoutes++;
        RouteHints.clearForceIncompatible(packet);
    }

    private boolean isWireEmpty(PortModel port) {
        WireModel w = findWire(port);
        return w != null && w.getPackets().size()<3;
    }

    private WireModel findWire(PortModel port) {
        for (WireModel w : wires) {
            if (w.getSrcPort() == port) return w;
        }
        return null;
    }

    private void drop(PacketModel packet) {
        lossModel.incrementPacket(packet); // خودش نوع را تشخیص می‌دهد
        droppedPackets++;
    }





    @Override
    public void update(double dt) {
        while (true) {
                        PacketModel ret = box.pollReturned();
                        if (ret == null) break;
                        boolean routed = routePacket(ret);
                        if (!routed) {
                            if (!box.enqueueReturnedFront(ret)) {
                                        drop(ret);}
                            break;
                            }
                   }

        processTeleportedPacketsForThisBox();

        if (!hasAvailableRoute()) {
            return;
        }
         if (box.getPrimaryKind() == SystemKind.MERGER) {
              while (hasAvailableRoute()) {
                     LargePacket lp = box.pollLarge();
                         if (lp == null) break;
                       boolean routed = routePacket(lp);
                      if (!routed) {
                          box.enqueueFront(lp);
                          break;
                                   }
                       }
                           }

        if (box.getPrimaryKind() == SystemKind.DISTRIBUTOR) {
            // فقط پکت‌های معمولی (BitPacket ها) را پردازش کن
            while (hasAvailableRoute()) {
                PacketModel packet = box.pollPacket();
                if (packet == null) break;

                if (packet instanceof LargePacket) {
                    box.enqueue(packet);
                    break;
                }

                boolean routed = routePacket(packet);
                if (!routed) {
                    if (!box.enqueue(packet)) {
                        drop(packet);
                    }
                    break;
                }
            }
            return;
        }

        while (hasAvailableRoute()) {
            PacketModel packet = box.pollPacket();
            if (packet == null) break;

            boolean noWire = (packet.getCurrentWire() == null);
            boolean noTrackedPort = (SystemBehaviorAdapter.EnteredPortTracker.peek(packet) == null);

            if (box.getPrimaryKind() == SystemKind.SPY && noWire && noTrackedPort) {
                // این پکت احتمالاً تله‌پورت شده، مستقیم route کن
                boolean routed = routeTeleportedPacket(packet);
                if (!routed) {
                    if (!box.enqueue(packet)) {
                        drop(packet);
                    }
                }
                continue;
            }

            boolean routed = routePacket(packet);
            if (!routed) {
                if (!box.enqueue(packet)) {
                    drop(packet);
                }
                break;
            }
        }
    }

    private void processTeleportedPacketsForThisBox() {
        if (box.getPrimaryKind() != SystemKind.SPY) {
            return;
        }

        TRANSFER_LOCK.lock();
        try {
            Queue<PacketModel> myTeleportedPackets = TELEPORTED_PACKETS.get(box);
            if (myTeleportedPackets == null || myTeleportedPackets.isEmpty()) {
                return;
            }

            List<PacketModel> toProcess = new ArrayList<>(myTeleportedPackets);
            myTeleportedPackets.clear();

            for (PacketModel packet : toProcess) {
                if (!routeTeleportedPacket(packet)) {
                    if (!box.enqueue(packet)) {
                        lossModel.incrementPacket(packet);
                    } else {
                    }
                }
            }
        } finally {
            TRANSFER_LOCK.unlock();
        }
    }
    private boolean routeTeleportedPacket(PacketModel packet) {
        List<PortModel> availableOuts = new ArrayList<>();

        for (PortModel port : box.getOutPorts()) {
            WireModel wire = findWireForPort(port);
            if (wire != null) {
                SystemBoxModel dest = destMap.get(wire);
                if (dest != null && dest.isEnabled()) {
                    // بررسی که سیم خیلی شلوغ نباشد
                    if (wire.getPackets().size() < 3) {
                        availableOuts.add(port);
                    }
                }
            }
        }

        if (availableOuts.isEmpty()) {
            return false;
        }

        // انتخاب تصادفی یک پورت
        PortModel chosenPort = availableOuts.get(rnd.nextInt(availableOuts.size()));
        WireModel chosenWire = findWireForPort(chosenPort);

        if (chosenWire == null) {
            return false;
        }

        boolean compatible = chosenPort.isCompatible(packet);
        packet.setStartSpeedMul(1.0);

        MotionStrategy ms = MotionStrategyFactory.create(packet, compatible);
        packet.setMotionStrategy(ms);

        chosenWire.attachPacket(packet, 0.0);

        TeleportTracking.clearTeleported(packet);
        packetsRouted++;
        if (!compatible) {
            incompatibleRoutes++;
        }

        return true;
    }


    private WireModel findWireForPort(PortModel port) {
        for (WireModel wire : wires) {
            if (wire.getSrcPort() == port) {
                return wire;
            }
        }
        return null;
    }




}

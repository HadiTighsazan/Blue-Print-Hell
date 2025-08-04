package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;
import com.blueprinthell.motion.MotionStrategy;
import com.blueprinthell.motion.MotionStrategyFactory;
import com.blueprinthell.controller.systems.RouteHints;
import com.blueprinthell.controller.systems.SystemKind;

import java.util.*;
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

    public PacketRouterController(SystemBoxModel box,
                                  List<WireModel> wires,
                                  Map<WireModel, SystemBoxModel> destMap,
                                  PacketLossModel lossModel) {
        this.box = box;
        this.wires = wires;
        this.destMap = destMap;
        this.lossModel = lossModel;
    }

    @Override
    public void update(double dt) {
        // اگر هیچ مسیر خالی نیست، اصلاً پکت از buffer برندار
        if (!hasAvailableRoute()) {
            return;
        }

        List<PacketModel> toRoute = new ArrayList<>();
        PacketModel p;
        while ((p = box.pollPacket()) != null) toRoute.add(p);

        for (PacketModel packet : toRoute) {
            routePacket(packet);
        }
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


    private void routePacket(PacketModel packet) {
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
            return; // پکت در buffer می‌ماند
        }

        // Check if packet should be routed incompatibly (from Malicious system)
        boolean forceIncompat = RouteHints.peekForceIncompatible(packet);

        if (forceIncompat) {
            routeIncompatibly(packet, availableOuts);
        } else {
            routeNormally(packet, availableOuts);
        }
    }

    private void routeNormally(PacketModel packet, List<PortModel> availableOuts) {
        // 1. ابتدا پورت‌های سازگار خالی را پیدا کن
        List<PortModel> emptyCompatPorts = availableOuts.stream()
                .filter(port -> port.isCompatible(packet))
                .filter(this::isWireEmpty)
                .collect(Collectors.toList());

        if (!emptyCompatPorts.isEmpty()) {
            // انتخاب تصادفی از بین پورت‌های سازگار خالی
            PortModel chosen = emptyCompatPorts.get(rnd.nextInt(emptyCompatPorts.size()));
            sendPacketToPort(packet, chosen);
            return;
        }

        // 2. اگر پورت سازگار خالی نبود، هر پورت خالی را انتخاب کن
        List<PortModel> emptyPorts = availableOuts.stream()
                .filter(this::isWireEmpty)
                .collect(Collectors.toList());

        if (!emptyPorts.isEmpty()) {
            // انتخاب تصادفی از بین پورت‌های خالی
            PortModel chosen = emptyPorts.get(rnd.nextInt(emptyPorts.size()));
            sendPacketToPort(packet, chosen);
            return;
        }

        // 3. هیچ پورت خالی نیست - پکت در buffer بماند
        // (پکت از قبل در buffer است، نیازی به re-enqueue نیست)
    }

    private void routeIncompatibly(PacketModel packet, List<PortModel> availableOuts) {
        // 1. ابتدا پورت‌های ناسازگار خالی را پیدا کن
        List<PortModel> emptyIncompatPorts = availableOuts.stream()
                .filter(port -> !port.isCompatible(packet))
                .filter(this::isWireEmpty)
                .collect(Collectors.toList());

        if (!emptyIncompatPorts.isEmpty()) {
            // انتخاب تصادفی از بین پورت‌های ناسازگار خالی
            PortModel chosen = emptyIncompatPorts.get(rnd.nextInt(emptyIncompatPorts.size()));
            sendPacketToPort(packet, chosen);
            return;
        }

        // 2. اگر پورت ناسازگار خالی نبود، هر پورت خالی را انتخاب کن
        List<PortModel> emptyPorts = availableOuts.stream()
                .filter(this::isWireEmpty)
                .collect(Collectors.toList());

        if (!emptyPorts.isEmpty()) {
            // انتخاب تصادفی از بین پورت‌های خالی
            PortModel chosen = emptyPorts.get(rnd.nextInt(emptyPorts.size()));
            sendPacketToPort(packet, chosen);
            return;
        }

        // 3. هیچ پورت خالی نیست - پکت در buffer بماند
        // (پکت از قبل در buffer است، نیازی به re-enqueue نیست)
    }

    private PortModel selectBestPort(List<PortModel> ports, PacketModel packet) {
        if (ports.isEmpty()) return null;

        // Score each port
        Map<PortModel, Integer> scores = new HashMap<>();

        for (PortModel port : ports) {
            int score = 0;

            // Check wire load
            WireModel wire = findWire(port);
            if (wire != null) {
                score += (10 - wire.getPackets().size()) * 2; // Less packets = higher score

                // Check destination buffer
                SystemBoxModel dest = destMap.get(wire);
                if (dest != null) {
                    int bufferSpace = Config.MAX_BUFFER_CAPACITY - dest.getBuffer().size();
                    score += bufferSpace * 3; // More space = higher score

                    // Bonus for compatible routing
                    if (port.isCompatible(packet)) {
                        score += 5;
                    }

                    // Consider destination type
                    score += getDestinationTypeScore(dest, packet);
                }
            }

            scores.put(port, score);
        }

        // Weighted random selection from top ports
        List<Map.Entry<PortModel, Integer>> sorted = scores.entrySet().stream()
                .sorted(Map.Entry.<PortModel, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());

        int topCount = Math.min(3, sorted.size());
        List<Map.Entry<PortModel, Integer>> topPorts = sorted.subList(0, topCount);

        // Calculate total weight
        int totalWeight = topPorts.stream().mapToInt(Map.Entry::getValue).sum();
        if (totalWeight <= 0) return topPorts.get(0).getKey();

        // Weighted random selection
        int random = rnd.nextInt(totalWeight);
        int cumulative = 0;
        for (Map.Entry<PortModel, Integer> entry : topPorts) {
            cumulative += entry.getValue();
            if (random < cumulative) {
                return entry.getKey();
            }
        }

        return topPorts.get(0).getKey();
    }


    private int getDestinationTypeScore(SystemBoxModel dest, PacketModel packet) {
        SystemKind kind = dest.getPrimaryKind();
        if (kind == null) return 0;

        switch (kind) {
            case VPN:
                // VPN is good for messengers
                if (PacketOps.isMessenger(packet)) return 10;
                return 5;

            case ANTI_TROJAN:
                // Anti-Trojan is good for trojans
                if (PacketOps.isTrojan(packet)) return 15;
                return 0;

            case SPY:
                // Avoid spy systems for confidential packets
                if (PacketOps.isConfidential(packet)) return -20;
                return 0;

            case MALICIOUS:
                // Avoid malicious systems for clean packets
                if (packet.getNoise() == 0 && !PacketOps.isProtected(packet)) return -10;
                return 0;

            case MERGER:
                // Merger is good for BitPackets
                if (PacketOps.isBit(packet)) return 10;
                return -5;

            case DISTRIBUTOR:
                // Distributor is good for LargePackets
                if (PacketOps.isLarge(packet)) return 10;
                return -5;

            default:
                return 0;
        }
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
        packetsRouted++;
        RouteHints.clearForceIncompatible(packet);
    }


    private boolean isWireEmpty(PortModel port) {
        WireModel w = findWire(port);
        return w != null && w.getPackets().isEmpty();
    }


    private WireModel findWire(PortModel port) {
        for (WireModel w : wires) {
            if (w.getSrcPort() == port) return w;
        }
        return null;
    }


    private void drop(PacketModel packet) {
        lossModel.increment();
        droppedPackets++;
    }

    public long getPacketsRouted() { return packetsRouted; }
    public long getIncompatibleRoutes() { return incompatibleRoutes; }
    public long getDroppedPackets() { return droppedPackets; }
}
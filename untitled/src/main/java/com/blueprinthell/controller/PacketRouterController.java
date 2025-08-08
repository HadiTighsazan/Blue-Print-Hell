package com.blueprinthell.controller;

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
        TeleportTracking.clearTeleported(packet);

        packetsRouted++;
        if (!compatible) incompatibleRoutes++;
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
    // اضافه کردن این کد به PacketRouterController.java

    // در PacketRouterController.java - اضافه/تغییر این متدها:

    /**
     * Enhanced processTeleportedPackets - better detection
     */
    private void processTeleportedPackets() {
        // Check if this is a spy system
        if (box.getPrimaryKind() != SystemKind.SPY) {
            return;
        }

        // Create a temporary list to avoid concurrent modification
        List<PacketModel> packetsToRoute = new ArrayList<>();
        Queue<PacketModel> buffer = box.getBuffer();

        // Check each packet in buffer
        for (PacketModel packet : buffer) {
            // CRITICAL: A teleported packet won't have a current wire
            // AND won't have an entered port tracked
            boolean noWire = (packet.getCurrentWire() == null);
            boolean noTrackedPort = (SystemBehaviorAdapter.EnteredPortTracker.peek(packet) == null);

            if (noWire && noTrackedPort) {
                packetsToRoute.add(packet);
            }
        }

        // Route teleported packets immediately
        for (PacketModel packet : packetsToRoute) {
            // Remove from buffer
            if (box.removeFromBuffer(packet)) {

                // Try to route it
                boolean routed = routePacketDirect(packet);

                if (!routed) {
                    // Put back in buffer if routing failed
                    if (!box.enqueue(packet)) {
                        drop(packet);
                    } else {
                    }
                } else {
                }
            }
        }
    }

    /**
     * New method: Route packet directly to wire without normal routing logic
     */
    private boolean routePacketDirect(PacketModel packet) {
        // Get available output ports with enabled destinations
        List<PortModel> availableOuts = box.getOutPorts().stream()
                .filter(port -> {
                    WireModel w = findWire(port);
                    if (w == null) return false;
                    SystemBoxModel d = destMap.get(w);
                    // Check destination is enabled and wire is not full
                    return d != null && d.isEnabled() && w.getPackets().size() < 3;
                })
                .collect(Collectors.toList());

        if (availableOuts.isEmpty()) {
            return false;
        }

        // Choose random port for teleported packet
        PortModel chosen = availableOuts.get(rnd.nextInt(availableOuts.size()));
        sendPacketToPort(packet, chosen);
        return true;
    }

    @Override
    public void update(double dt) {
        // CRITICAL: ابتدا پکت‌های تله‌پورت شده را پردازش کن
        processTeleportedPacketsForThisBox();

        if (!hasAvailableRoute()) {
            return;
        }

        if (box.getPrimaryKind() != SystemKind.DISTRIBUTOR) {
            // ابتدا LargePacket ها را از largeBuffer پردازش کن
            while (hasAvailableRoute()) {
                LargePacket largePacket = box.pollLarge();
                if (largePacket == null) break;

                boolean routed = routePacket(largePacket);
                if (!routed) {
                    // اگر نتوانست route کند، برگردان به largeBuffer
                    box.enqueue(largePacket);
                    break;
                }
            }
        }

        // سپس پکت‌های معمولی را از bitBuffer پردازش کن
        while (hasAvailableRoute()) {
            PacketModel packet = box.pollPacket();
            if (packet == null) break;

            // بررسی که آیا این پکت تله‌پورت شده است
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
        // فقط برای سیستم‌های SPY عمل کن
        if (box.getPrimaryKind() != SystemKind.SPY) {
            return;
        }

        TRANSFER_LOCK.lock();
        try {
            // بررسی صف پکت‌های تله‌پورت شده برای این box
            Queue<PacketModel> myTeleportedPackets = TELEPORTED_PACKETS.get(box);
            if (myTeleportedPackets == null || myTeleportedPackets.isEmpty()) {
                return;
            }

            // کپی از پکت‌ها برای پردازش (برای جلوگیری از concurrent modification)
            List<PacketModel> toProcess = new ArrayList<>(myTeleportedPackets);
            myTeleportedPackets.clear(); // صف را پاک کن

            // پردازش هر پکت تله‌پورت شده
            for (PacketModel packet : toProcess) {
                // تلاش برای route کردن مستقیم
                if (!routeTeleportedPacket(packet)) {
                    // اگر نتوانست route کند، در بافر قرار بده
                    if (!box.enqueue(packet)) {
                        lossModel.increment();
                    } else {
                        // پکت در بافر قرار گرفت، در چرخه بعدی route خواهد شد
                    }
                }
            }
        } finally {
            TRANSFER_LOCK.unlock();
        }
    }
    private boolean routeTeleportedPacket(PacketModel packet) {
        // پیدا کردن پورت‌های خروجی available
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
            return false; // نمی‌تواند route کند
        }

        // انتخاب تصادفی یک پورت
        PortModel chosenPort = availableOuts.get(rnd.nextInt(availableOuts.size()));
        WireModel chosenWire = findWireForPort(chosenPort);

        if (chosenWire == null) {
            return false;
        }

        // تنظیم motion strategy
        boolean compatible = chosenPort.isCompatible(packet);
        packet.setStartSpeedMul(1.0);

        MotionStrategy ms = MotionStrategyFactory.create(packet, compatible);
        packet.setMotionStrategy(ms);

        // چسباندن پکت به سیم
        chosenWire.attachPacket(packet, 0.0);

        // پاک کردن علامت teleported
        TeleportTracking.clearTeleported(packet);

        // به‌روزرسانی آمار
        packetsRouted++;
        if (!compatible) {
            incompatibleRoutes++;
        }

        return true;
    }

    /**
     * Helper method to find wire connected to a port
     */
    private WireModel findWireForPort(PortModel port) {
        for (WireModel wire : wires) {
            if (wire.getSrcPort() == port) {
                return wire;
            }
        }
        return null;
    }

    /**
     * Static method to add teleported packet to a target spy system
     * (این متد توسط SpyBehavior فراخوانی می‌شود)
     */
    public static void addTeleportedPacket(SystemBoxModel targetBox, PacketModel packet) {
        TRANSFER_LOCK.lock();
        try {
            Queue<PacketModel> queue = TELEPORTED_PACKETS.computeIfAbsent(
                    targetBox,
                    k -> new LinkedList<>()
            );
            queue.offer(packet);
        } finally {
            TRANSFER_LOCK.unlock();
        }
    }

}

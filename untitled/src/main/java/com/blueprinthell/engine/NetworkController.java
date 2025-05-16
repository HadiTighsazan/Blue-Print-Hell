package com.blueprinthell.engine;

import com.blueprinthell.model.Packet;
import com.blueprinthell.engine.PacketSnapshot;
import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.Port;
import com.blueprinthell.model.SystemBox;
import com.blueprinthell.model.Wire;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class NetworkController {
    private final List<Wire> wires;
    private final List<SystemBox> systems;
    private final Map<Port, Wire> portToWire = new HashMap<>();
    private final Random rng = new Random();

    private int packetLoss = 0;
    private int coins      = 0;

    private double impactDisableTimer    = 0;
    private double collisionDisableTimer = 0;

    private final double maxWireLength;
    private int    cellSize;  // grid cell size for collisions

    public NetworkController(List<Wire> wires, List<SystemBox> systems, double maxWireLength) {
        this.wires         = new ArrayList<>(wires);
        this.systems       = Objects.requireNonNull(systems);
        this.maxWireLength = maxWireLength;

        // map ports to wires
        for (Wire w : wires) {
            portToWire.put(w.getSrcPort(), w);
            portToWire.put(w.getDstPort(), w);
        }

        // compute cellSize from PacketType.sizeUnits
        int maxUnits = 0;
        System.out.println("[NetworkController] PacketType values:");
        for (PacketType pt : PacketType.values()) {
            System.out.printf("  - %s.sizeUnits = %d%n", pt, pt.sizeUnits);
            maxUnits = Math.max(maxUnits, pt.sizeUnits);
        }
        cellSize = maxUnits * 6;
        System.out.printf("[NetworkController] Computed cellSize = %d%n", cellSize);
    }

    public double getRemainingWireLength() {
        return maxWireLength - wires.stream().mapToDouble(Wire::getLength).sum();
    }

    public int getPacketLoss() { return packetLoss; }
    public int getCoins()      { return coins;   }

    public void spendCoins(int c)       { coins = Math.max(0, coins - c); }
    public void disableImpact(int sec)  { impactDisableTimer    = sec; }
    public void disableCollisions(int s) { collisionDisableTimer = s;   }
    public void resetNoise() {
        wires.forEach(w -> w.getPackets().forEach(Packet::resetNoise));
        systems.forEach(s -> s.getBuffer().forEach(Packet::resetNoise));
    }

    public void addWire(Wire w) {
        wires.add(w);
        portToWire.put(w.getSrcPort(), w);
        portToWire.put(w.getDstPort(), w);
    }

    /** Return all packets currently flowing on wires */
    public List<Packet> getPackets() {
        return wires.stream()
                .flatMap(w -> w.getPackets().stream())
                .collect(Collectors.toList());
    }

    /** Increment packet-loss counter */
    public void incrementPacketLoss() {
        this.packetLoss++;
    }

    public void removeWire(Wire w) {
        wires.remove(w);
        portToWire.remove(w.getSrcPort());
        portToWire.remove(w.getDstPort());
    }

    public void tick(double dt) {
        if (impactDisableTimer > 0)    impactDisableTimer    -= dt;
        if (collisionDisableTimer > 0) collisionDisableTimer -= dt;

        var arrivals = updateAllWires(dt);
        handleCollisions();
        enqueueArrived(arrivals);
        dispatchFromSystems();
    }

    private Map<SystemBox, List<Packet>> updateAllWires(double dt) {
        var map = new HashMap<SystemBox, List<Packet>>();
        for (Wire w : wires) {
            List<Packet> arr = w.update(dt);
            if (!arr.isEmpty()) {
                SystemBox dest = (SystemBox) w.getDstPort().getParent();
                map.computeIfAbsent(dest, k -> new ArrayList<>()).addAll(arr);
            }
        }
        return map;
    }

    private void handleCollisions() {
        if (collisionDisableTimer > 0) return;

        List<Packet> all = getPackets();
        System.out.printf("[NetworkController] handleCollisions: cellSize=%d, totalPackets=%d%n",
                cellSize, all.size());

        if (cellSize <= 0 || all.isEmpty()) return;

        var grid = new SpatialHashGrid<Packet>(cellSize);
        all.forEach(p -> grid.insert(p.getCenterX(), p.getCenterY(), p));

        Set<Packet> toRemove = new HashSet<>();
        for (int i = 0; i < all.size(); i++) {
            Packet p = all.get(i);
            Point pc = new Point(p.getCenterX(), p.getCenterY());
            double rP = p.getWidth() / 2.0;

            for (Packet q : grid.retrieve(pc.x, pc.y)) {
                if (q == p) continue;
                Point qc = new Point(q.getCenterX(), q.getCenterY());
                double dist = pc.distance(qc);

                double th = p.getWidth()/10.0;
                if (dist < th) toRemove.add(p);
                th = q.getWidth()/10.0;
                if (dist < th) toRemove.add(q);

                if (impactDisableTimer <= 0) {
                    double minD = rP + q.getWidth()/2.0;
                    if (dist <= minD) {
                        double factor = 1.0 - (dist/minD);
                        p.increaseNoise(factor);
                        q.increaseNoise(factor);
                        if (p.getNoise() > rP)    toRemove.add(p);
                        if (q.getNoise() > q.getWidth()/2.0) toRemove.add(q);
                    }
                }
            }
        }

        for (Packet dead : toRemove) {
            Wire w = dead.getCurrentWire();
            if (w != null && w.getPackets().remove(dead)) {
                packetLoss++;
                SwingUtilities.invokeLater(() -> {
                    Container parent = dead.getParent();
                    if (parent instanceof JComponent jc) {
                        jc.remove(dead);
                        jc.revalidate();
                        jc.repaint();
                    }
                });
            }
        }
    }

    private void enqueueArrived(Map<SystemBox, List<Packet>> arrivals) {
        for (var e : arrivals.entrySet()) {
            SystemBox sys = e.getKey();
            for (Packet p : e.getValue()) {
                if (sys.enqueue(p)) coins += p.getType().coins;
                else packetLoss++;
            }
        }
    }

    private void dispatchFromSystems() {
        for (SystemBox sys : systems) {
            if (sys.getOutPorts().isEmpty()) continue;

            Packet p;
            while ((p = sys.pollPacket()) != null) {
                List<Port> freeAll        = new ArrayList<>();
                List<Port> freeCompatible = new ArrayList<>();
                boolean hasConnectedPort  = false;

                for (Port out : sys.getOutPorts()) {
                    Wire w = portToWire.get(out);
                    if (w == null) continue;
                    hasConnectedPort = true;
                    if (w.getPackets().isEmpty()) freeAll.add(out);
                    if (out.isCompatible(p))   freeCompatible.add(out);
                }

                if (!hasConnectedPort) {
                    packetLoss++;
                    continue;
                }
                if (freeAll.isEmpty()) {
                    if (!sys.enqueue(p)) packetLoss++;
                    break;
                }

                Port chosen = !freeCompatible.isEmpty()
                        ? freeCompatible.get(rng.nextInt(freeCompatible.size()))
                        : freeAll.get(rng.nextInt(freeAll.size()));

                boolean compatible = chosen.isCompatible(p);
                p.setAcceleration(compatible ? 0 : p.getBaseSpeed());
                p.setSpeed(compatible ? p.getBaseSpeed() : p.getBaseSpeed());

                Wire next = portToWire.get(chosen);
                // <-- اینجا از attachPacket استفاده می‌کنیم:
                next.attachPacket(p, 0.0);
            }
        }
    }

    /** Capture a snapshot of current network state */
    public NetworkSnapshot captureSnapshot() {
        List<PacketSnapshot> packetSnaps = new ArrayList<>();
        for (int i = 0; i < wires.size(); i++) {
            Wire w = wires.get(i);
            for (Packet p : w.getPackets()) {
                packetSnaps.add(new PacketSnapshot(
                        p.getType(), p.getBaseSpeed(), p.getSpeed(),
                        p.getNoise(), p.getProgress(), i
                ));
            }
        }
        var bufferSnaps = new HashMap<Integer,List<PacketSnapshot>>();
        for (int i = 0; i < systems.size(); i++) {
            SystemBox sys = systems.get(i);
            var buf = new ArrayList<PacketSnapshot>();
            for (Packet p : sys.getBuffer()) {
                buf.add(new PacketSnapshot(
                        p.getType(), p.getBaseSpeed(), p.getSpeed(),
                        p.getNoise(), p.getProgress(), wires.indexOf(p.getCurrentWire())
                ));
            }
            bufferSnaps.put(i, buf);
        }
        return new NetworkSnapshot(packetSnaps, bufferSnaps, coins, packetLoss);
    }

    /** Restore network state from given snapshot */
    public void restoreState(NetworkSnapshot snap) {
        // ۱) پاک‌کردن مدل فعلی
        wires.forEach(w -> w.getPackets().clear());
        systems.forEach(SystemBox::clearBuffer);
        coins      = snap.coins();
        packetLoss = snap.packetLoss();

        // ۲) بازسازی Packetها روی سیم‌ها
        for (PacketSnapshot ps : snap.packets()) {
            Packet p = new Packet(ps.type(), ps.baseSpeed());
            p.setSpeed(ps.speed());
            p.increaseNoise(ps.noise());
            // ← مهم: از خود Wire.attachPacket استفاده کنیم،
            //     تا هم مدل و هم UI با progress صحیح ثبت شوند.
            Wire w = wires.get(ps.wireIndex());
            w.attachPacket(p, ps.progress());
        }

        // ۳) بازسازی Buffer سیستم‌ها (بدون نمایش گرافیکی)
        snap.buffers().forEach((idx, list) -> {
            SystemBox sys = systems.get(idx);
            for (PacketSnapshot ps : list) {
                Packet p = new Packet(ps.type(), ps.baseSpeed());
                p.setSpeed(ps.speed());
                p.increaseNoise(ps.noise());
                sys.enqueue(p);
            }
        });
    }
}

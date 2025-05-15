// NetworkController.java
package com.blueprinthell.engine;

import com.blueprinthell.model.Packet;
import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.Port;
import com.blueprinthell.model.SystemBox;
import com.blueprinthell.model.Wire;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * منطق اصلی شبکه: حرکت پکت‌ها، برخورد، صف‌بندی، پاورآپ‌ها و اسنپ‌شات.
 */
public class NetworkController {
    private final List<Wire> wires;
    private final List<SystemBox> systems;
    private final Map<Port, Wire> portToWire = new HashMap<>();
    private final Random rng = new Random();

    private int packetLoss = 0;
    private int coins = 0;

    private double impactDisableTimer = 0;
    private double collisionDisableTimer = 0;

    private final int cellSize;
    private final double maxWireLength;

    public NetworkController(List<Wire> wires, List<SystemBox> systems, double maxWireLength) {
        this.wires = new ArrayList<>(wires);
        this.systems = Objects.requireNonNull(systems);
        this.maxWireLength = maxWireLength;
        for (Wire w : wires) {
            portToWire.put(w.getSrcPort(), w);
            portToWire.put(w.getDstPort(), w);
        }
        int maxUnits = 0;
        for (PacketType pt : PacketType.values()) {
            maxUnits = Math.max(maxUnits, pt.sizeUnits);
        }
        this.cellSize = maxUnits * 6;
    }

    public double getRemainingWireLength() {
        return maxWireLength - wires.stream().mapToDouble(Wire::getLength).sum();
    }

    public int getPacketLoss() { return packetLoss; }
    public int getCoins()      { return coins; }

    public void spendCoins(int c) { coins = Math.max(0, coins - c); }
    public void disableImpact(int seconds) { impactDisableTimer = seconds; }
    public void disableCollisions(int seconds) { collisionDisableTimer = seconds; }
    public void resetNoise() {
        wires.forEach(w -> w.getPackets().forEach(Packet::resetNoise));
        systems.forEach(s -> s.getBuffer().forEach(Packet::resetNoise));
    }
    /** افزودن سیم جدید به شبکه */
    public void addWire(Wire w) {
        wires.add(w);
        portToWire.put(w.getSrcPort(), w);
        portToWire.put(w.getDstPort(), w);
    }


    public void tick(double dt) {
        if (impactDisableTimer > 0)    impactDisableTimer -= dt;
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
        List<Packet> all = wires.stream()
                .flatMap(w -> w.getPackets().stream())
                .collect(Collectors.toList());
        var grid = new SpatialHashGrid<Packet>(cellSize);
        all.forEach(p -> grid.insert(p.getCenterX(), p.getCenterY(), p));

        Set<Packet> toRemove = new HashSet<>();
        for (int i = 0; i < all.size(); i++) {
            Packet p = all.get(i);
            if (impactDisableTimer <= 0) {
                Point pc = new Point(p.getCenterX(), p.getCenterY());
                double rP = p.getWidth() / 2.0;
                for (Packet q : grid.retrieve(pc.x, pc.y)) {
                    if (q == p) continue;
                    int j = all.indexOf(q);
                    if (j <= i) continue;
                    double dx = pc.x - q.getCenterX(), dy = pc.y - q.getCenterY();
                    double minD = rP + q.getWidth() / 2.0;
                    if (dx*dx + dy*dy <= minD*minD) {
                        double dist   = Math.hypot(dx, dy);
                        double factor = 1.0 - (dist / minD);
                        p.increaseNoise(factor);
                        q.increaseNoise(factor);
                        if (p.getNoise() > rP) toRemove.add(p);
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
                    if (parent instanceof JComponent) {
                        JComponent pc = (JComponent) parent;
                        pc.remove(dead);
                        pc.revalidate();
                        pc.repaint();
                    }
                });
            }
        }
    }

    private void enqueueArrived(Map<SystemBox, List<Packet>> arrivals) {
        for (var entry : arrivals.entrySet()) {
            SystemBox sys = entry.getKey();
            for (Packet p : entry.getValue()) {
                if (sys.enqueue(p)) coins += p.getType().coins;
                else packetLoss++;
            }
        }
    }

    private void dispatchFromSystems() {
        for (SystemBox sys : systems) {
            if (sys.getOutPorts().isEmpty()) {
                while (sys.pollPacket() != null);
                continue;
            }
            Packet p;
            while ((p = sys.pollPacket()) != null) {
                List<Port> freeAll = new ArrayList<>(), freeComp = new ArrayList<>();
                for (Port out : sys.getOutPorts()) {
                    Wire w = portToWire.get(out);
                    if (w.getPackets().isEmpty()) {
                        freeAll.add(out);
                        if (out.isCompatible(p)) freeComp.add(out);
                    }
                }
                if (freeAll.isEmpty()) {
                    if (!sys.enqueue(p)) packetLoss++;
                    break;
                }
                Port chosen;
                if (!freeComp.isEmpty()) {
                    chosen = freeComp.get(rng.nextInt(freeComp.size()));
                } else {
                    chosen = freeAll.get(rng.nextInt(freeAll.size()));
                }
                adjustSpeed(p, chosen.isCompatible(p));
                portToWire.get(chosen).attachPacket(p, 0.0);
            }
        }
    }

    private void adjustSpeed(Packet p, boolean compatible) {
        double base = p.getBaseSpeed();
        if (p.getType() == PacketType.SQUARE) p.setSpeed(compatible ? base/2 : base);
        else p.setSpeed(compatible ? base : base*2);
    }

    /** تولید اسنپ‌شات فعلی شبکه */
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
        Map<Integer, List<PacketSnapshot>> bufferSnaps = new HashMap<>();
        for (int i = 0; i < systems.size(); i++) {
            SystemBox sys = systems.get(i);
            List<PacketSnapshot> buf = new ArrayList<>();
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

    /** بازگرداندن وضعیت شبکه به اسنپ‌شات داده‌شده */
    public void restoreState(NetworkSnapshot snap) {
        wires.forEach(w -> w.getPackets().clear());
        systems.forEach(s -> s.clearBuffer());
        coins = snap.coins();
        packetLoss = snap.packetLoss();
        for (PacketSnapshot ps : snap.packets()) {
            Packet p = new Packet(ps.type(), ps.baseSpeed());
            p.setSpeed(ps.speed()); p.increaseNoise(ps.noise());
            wires.get(ps.wireIndex()).attachPacket(p, ps.progress());
        }
        snap.buffers().forEach((idx, list) -> {
            SystemBox sys = systems.get(idx);
            for (PacketSnapshot ps : list) {
                Packet p = new Packet(ps.type(), ps.baseSpeed());
                p.setSpeed(ps.speed()); p.increaseNoise(ps.noise());
                sys.enqueue(p);
            }
        });
    }
}

package com.blueprinthell.engine;

import com.blueprinthell.model.*;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;


public class NetworkController {
    private final List<Wire> wires;
    private final List<SystemBox> systems;
    private final Map<Port, Wire> portToWire;
    private final Random rng = new Random();

    private int packetLoss = 0;
    private int coins      = 0;
    private final int cellSize;

    public NetworkController(List<Wire> wires, List<SystemBox> systems) {
        this.wires   = objectsNotNull(wires);
        this.systems = objectsNotNull(systems);
        this.portToWire = new HashMap<>();
        for (Wire w : wires) {
            portToWire.put(w.getSrcPort(), w);
        }
        int maxUnits = 0;
        for (PacketType pt : PacketType.values()) {
            maxUnits = Math.max(maxUnits, pt.sizeUnits);
        }
        this.cellSize = maxUnits * 6;
    }

    public void tick(double dt) {
        Map<SystemBox, List<Packet>> arrivals = updateAllWires(dt);
        handleCollisions();
        enqueueArrived(arrivals);
        dispatchFromSystems();
    }

    private Map<SystemBox, List<Packet>> updateAllWires(double dt) {
        Map<SystemBox, List<Packet>> arrivals = new HashMap<>();
        for (Wire w : wires) {
            List<Packet> arrived = w.update(dt);
            if (!arrived.isEmpty()) {
                SystemBox dest = (SystemBox) w.getDstPort().getParent();
                arrivals.computeIfAbsent(dest, k -> new ArrayList<>()).addAll(arrived);
            }
        }
        return arrivals;
    }





    private void handleCollisions() {
        List<Packet> all = new ArrayList<>();
        for (Wire w : wires) {
            all.addAll(w.getPackets());
        }

        SpatialHashGrid<Packet> grid = new SpatialHashGrid<>(cellSize);
        for (Packet p : all) {
            grid.insert(p.getCenterX(), p.getCenterY(), p);
        }

        Set<Packet> toRemove = new HashSet<>();
        int n = all.size();
        for (int i = 0; i < n; i++) {
            Packet p = all.get(i);
            int px = p.getCenterX(), py = p.getCenterY();
            double radiusP = p.getWidth() / 2.0;

            for (Packet q : grid.retrieve(px, py)) {
                if (q == p) continue;
                int j = all.indexOf(q);
                if (j <= i) continue;

                int qx = q.getCenterX(), qy = q.getCenterY();
                double radiusQ = q.getWidth() / 2.0;

                double dx = px - qx;
                double dy = py - qy;
                double dist = Math.hypot(dx, dy);
                double minD = radiusP + radiusQ;

                if (dist <= minD) {

                    double factor = 1.0 - (dist / minD);
                    p.increaseNoise(factor);
                    q.increaseNoise(factor);

                    if (p.getNoise() > radiusP) toRemove.add(p);
                    if (q.getNoise() > radiusQ) toRemove.add(q);
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
        for (var e : arrivals.entrySet()) {
            SystemBox sys = e.getKey();
            for (Packet p : e.getValue()) {
                if (sys.enqueue(p)) {
                    coins += p.getType().coins;
                } else {
                    packetLoss++;
                }
            }
        }
    }



    private void dispatchFromSystems() {
        for (SystemBox sys : systems) {
            if (sys.getOutPorts().isEmpty()) {
                Packet p;
                while ((p = sys.pollPacket()) != null) {
                    // just for dequeue
                }
                continue;
            }

            Packet p;
            while ((p = sys.pollPacket()) != null) {
                List<Port> freeAll = new ArrayList<>();
                List<Port> freeCompatible = new ArrayList<>();
                for (Port out : sys.getOutPorts()) {
                    Wire w = portToWire.get(out);
                    if (w.getPackets().isEmpty()) {
                        freeAll.add(out);
                        if (out.isCompatible(p)) {
                            freeCompatible.add(out);
                        }
                    }
                }


                if (freeAll.isEmpty()) {
                    boolean ok = sys.enqueue(p);
                    if (!ok) {
                        packetLoss++;
                    }
                    break;
                }


                Port chosen;
                if (!freeCompatible.isEmpty()) {
                    Collections.shuffle(freeCompatible, rng);
                    chosen = freeCompatible.get(0);
                } else {
                    Collections.shuffle(freeAll, rng);
                    chosen = freeAll.get(0);
                }

                boolean compatible = chosen.isCompatible(p);
                adjustSpeedForPort(p, compatible);

                Wire next = portToWire.get(chosen);
                next.attachPacket(p, 0.0);
            }
        }
    }


    private void adjustSpeedForPort(Packet p, boolean compatible) {
        double base = p.getBaseSpeed();
        if (p.getType() == PacketType.SQUARE) {
            p.setSpeed( compatible ? base/2 : base );
        } else {
            p.setSpeed( compatible ? base : base*2 );
        }
    }


    public int getPacketLoss() { return packetLoss; }
    public int getCoins()      { return coins; }

    private static <T> List<T> objectsNotNull(List<T> list) {
        Objects.requireNonNull(list);
        for (T obj : list) Objects.requireNonNull(obj);
        return list;
    }

    public NetworkSnapshot captureSnapshot() {
        return null;
    }

    public void restoreState(NetworkSnapshot snap) {
        return;
    }



}

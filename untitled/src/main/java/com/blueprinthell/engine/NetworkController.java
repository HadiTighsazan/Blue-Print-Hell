package com.blueprinthell.engine;

import com.blueprinthell.model.Wire;
import com.blueprinthell.model.SystemBox;
import com.blueprinthell.model.Packet;
import com.blueprinthell.model.Port;

import java.util.*;


public class NetworkController {
    private final List<Wire> wires;
    private final List<SystemBox> systems;
    private final Map<Port, Wire> portToWire;

    private int packetLoss = 0;
    private int coins = 0;
    private final Random rng = new Random();


    public NetworkController(List<Wire> wires, List<SystemBox> systems) {
        this.wires = new ArrayList<>(wires);
        this.systems = new ArrayList<>(systems);
        this.portToWire = new HashMap<>();
        for (Wire w : wires) {
            portToWire.put(w.getSrc(), w);
        }
    }


    public void tick(double dt) {
        Map<SystemBox, List<Packet>> arrived = updateAllWires(dt);
        enqueueArrived(arrived);
        dispatchFromSystems();
    }


    private Map<SystemBox, List<Packet>> updateAllWires(double dt) {
        Map<SystemBox, List<Packet>> arrivedMap = new HashMap<>();
        for (Wire w : wires) {
            List<Packet> arrived = w.update(dt);
            if (!arrived.isEmpty()) {
                Port dstPort = w.getDst();
                SystemBox destSystem = (SystemBox) dstPort.getParent();
                arrivedMap.putIfAbsent(destSystem, new ArrayList<>());
                arrivedMap.get(destSystem).addAll(arrived);
            }
        }
        return arrivedMap;
    }


    private void enqueueArrived(Map<SystemBox, List<Packet>> arrivedMap) {
        for (Map.Entry<SystemBox, List<Packet>> entry : arrivedMap.entrySet()) {
            SystemBox sys = entry.getKey();
            for (Packet p : entry.getValue()) {
                boolean ok = sys.enqueue(p);
                if (ok) {
                    coins += p.getType().coins;
                } else {
                    packetLoss++;
                }
            }
        }
    }


    private void dispatchFromSystems() {
        for (SystemBox sys : systems) {
            while (true) {
                Packet p = sys.pollPacket();
                if (p == null) break;
                List<Port> candidates = new ArrayList<>();
                for (Port port : sys.getOutPorts()) {
                    Wire next = portToWire.get(port);
                    if (next != null && port.isCompatible(p) && next.getPackets().isEmpty()) {
                        candidates.add(port);
                    }
                }
                if (candidates.isEmpty()) {
                    sys.enqueue(p);
                    break;
                }

                Collections.shuffle(candidates, rng);
                Port chosen = candidates.get(0);
                Wire nextWire = portToWire.get(chosen);
                nextWire.attachPacket(p, 0.0);
            }
        }
    }

    public int getPacketLoss() { return packetLoss; }
    public int getCoins()      { return coins; }
}

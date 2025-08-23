package com.blueprinthell.server.pvp;

import com.blueprinthell.shared.protocol.NetworkProtocol.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * شبیه‌سازی سبک بازی Messenger برای سرور
 * فقط پکت‌های پیام‌رسان را شبیه‌سازی می‌کند
 */
public class MessengerGameSimulation {

    // Constants
    private static final double MESSENGER_BASE_SPEED = 100.0; // pixels per second
    private static final double PACKET_SPAWN_INTERVAL = 1.0; // seconds between auto spawns
    private static final int WIRE_LENGTH_PIXELS = 300; // average wire length

    // Player networks
    private final NetworkLayout layoutP1;
    private final NetworkLayout layoutP2;

    // Active packets
    private final Map<String, SimPacket> activePackets = new ConcurrentHashMap<>();
    private final AtomicInteger packetIdCounter = new AtomicInteger(0);

    // Automatic spawning
    private double spawnAccumulatorP1 = 0;
    private double spawnAccumulatorP2 = 0;
    private int autoSpawnTurn = 1; // Alternates between 1 and 2

    // Score tracking
    private int deliveredP1 = 0;
    private int lostP1 = 0;
    private int deliveredP2 = 0;
    private int lostP2 = 0;

    // System states for controlled systems
    private final Map<String, ControlledSystem> controlledSystems = new ConcurrentHashMap<>();

    /**
     * Create simulation from player layouts
     */
    public MessengerGameSimulation(SubmitLayout layoutP1, SubmitLayout layoutP2) {
        this.layoutP1 = new NetworkLayout(layoutP1, 1);
        this.layoutP2 = new NetworkLayout(layoutP2, 2);

        initializeControlledSystems();
    }

    /**
     * Initialize controlled systems (non-source, non-sink)
     */
    private void initializeControlledSystems() {
        // P1 systems
        if (layoutP1 != null && layoutP1.layout.boxes != null) {
            for (SystemLayout box : layoutP1.layout.boxes) {
                if (!box.isSource && !box.isSink) {
                    controlledSystems.put(box.id, new ControlledSystem(box.id));
                }
            }
        }

        // P2 systems
        if (layoutP2 != null && layoutP2.layout.boxes != null) {
            for (SystemLayout box : layoutP2.layout.boxes) {
                if (!box.isSource && !box.isSink) {
                    controlledSystems.put(box.id, new ControlledSystem(box.id));
                }
            }
        }
    }

    /**
     * Tick simulation forward
     * @param dt Delta time in seconds
     * @param globalSpeedMultiplier Speed multiplier from penalties
     * @return Simulation results for this tick
     */
    public PvPGameSession.SimulationResult tick(double dt, double globalSpeedMultiplier) {
        PvPGameSession.SimulationResult result = new PvPGameSession.SimulationResult();

        // Handle automatic spawning from uncontrolled sources
        handleAutoSpawning(dt);

        // Update all active packets
        List<String> toRemove = new ArrayList<>();

        for (SimPacket packet : activePackets.values()) {
            // Update packet position
            double speed = MESSENGER_BASE_SPEED * globalSpeedMultiplier;
            packet.progress += (speed * dt) / packet.wireLength;

            // Check if packet reached destination
            if (packet.progress >= 1.0) {
                // Find destination
                WireLayout wire = findWire(packet.wireId, packet.playerSide);
                if (wire != null) {
                    SystemLayout destBox = findBox(wire.toBoxId, packet.playerSide);

                    if (destBox != null && destBox.isSink) {
                        // Delivered successfully
                        if (packet.playerSide == 1) {
                            deliveredP1++;
                            result.deliveredP1++;
                        } else {
                            deliveredP2++;
                            result.deliveredP2++;
                        }
                    }
                }

                toRemove.add(packet.id);
            }

            // Simplified collision/loss logic
            // In real implementation, would check for trojans, incompatible ports, etc.
            if (packet.noise > 100) {
                // Lost due to noise
                if (packet.playerSide == 1) {
                    lostP1++;
                    result.lostP1++;
                } else {
                    lostP2++;
                    result.lostP2++;
                }
                toRemove.add(packet.id);
            }
        }

        // Remove completed/lost packets
        for (String id : toRemove) {
            activePackets.remove(id);
        }

        return result;
    }

    /**
     * Handle automatic spawning from uncontrolled sources
     */
    private void handleAutoSpawning(double dt) {
        // Accumulate spawn time
        spawnAccumulatorP1 += dt;
        spawnAccumulatorP2 += dt;

        // Spawn from P1 sources (on odd turns)
        if (autoSpawnTurn == 1 && spawnAccumulatorP1 >= PACKET_SPAWN_INTERVAL) {
            spawnFromSources(1);
            spawnAccumulatorP1 = 0;
            autoSpawnTurn = 2;
        }

        // Spawn from P2 sources (on even turns)
        if (autoSpawnTurn == 2 && spawnAccumulatorP2 >= PACKET_SPAWN_INTERVAL) {
            spawnFromSources(2);
            spawnAccumulatorP2 = 0;
            autoSpawnTurn = 1;
        }
    }

    /**
     * Spawn packets from all sources of a player
     */
    private void spawnFromSources(int playerSide) {
        NetworkLayout layout = (playerSide == 1) ? layoutP1 : layoutP2;
        if (layout == null || layout.layout == null) return;

        for (SystemLayout box : layout.layout.boxes) {
            if (box.isSource) {
                // Spawn from each output port
                for (int i = 0; i < box.outShapes.size(); i++) {
                    String wireId = findWireFromSource(box.id, i, playerSide);
                    if (wireId != null) {
                        spawnPacket(wireId, playerSide);
                    }
                }
            }
        }
    }

    /**
     * Inject packet from controlled system
     */
    public void injectPacket(String systemId, int playerSide) {
        // Find system box
        SystemLayout box = findBox(systemId, playerSide);
        if (box == null || box.isSource || box.isSink) return;

        // Find an output wire (simplified - just use first available)
        for (int i = 0; i < box.outShapes.size(); i++) {
            String wireId = findWireFromSource(box.id, i, playerSide);
            if (wireId != null) {
                spawnPacket(wireId, playerSide);
                break; // Only spawn one packet per inject
            }
        }
    }

    /**
     * Spawn a packet on a wire
     */
    private void spawnPacket(String wireId, int playerSide) {
        String packetId = "pkt-" + packetIdCounter.incrementAndGet();

        SimPacket packet = new SimPacket();
        packet.id = packetId;
        packet.wireId = wireId;
        packet.playerSide = playerSide;
        packet.progress = 0.0;
        packet.wireLength = WIRE_LENGTH_PIXELS; // Simplified
        packet.noise = 0;
        packet.type = "MESSENGER";

        activePackets.put(packetId, packet);
    }

    /**
     * Find wire from source
     */
    private String findWireFromSource(String boxId, int outIndex, int playerSide) {
        NetworkLayout layout = (playerSide == 1) ? layoutP1 : layoutP2;
        if (layout == null || layout.layout == null) return null;

        for (WireLayout wire : layout.layout.wires) {
            if (wire.fromBoxId.equals(boxId) && wire.fromOutIndex == outIndex) {
                return wire.id;
            }
        }
        return null;
    }

    /**
     * Find wire by ID
     */
    private WireLayout findWire(String wireId, int playerSide) {
        NetworkLayout layout = (playerSide == 1) ? layoutP1 : layoutP2;
        if (layout == null || layout.layout == null) return null;

        for (WireLayout wire : layout.layout.wires) {
            if (wire.id.equals(wireId)) {
                return wire;
            }
        }
        return null;
    }

    /**
     * Find box by ID
     */
    private SystemLayout findBox(String boxId, int playerSide) {
        NetworkLayout layout = (playerSide == 1) ? layoutP1 : layoutP2;
        if (layout == null || layout.layout == null) return null;

        for (SystemLayout box : layout.layout.boxes) {
            if (box.id.equals(boxId)) {
                return box;
            }
        }
        return null;
    }

    /**
     * Get current state for synchronization
     */
    public Map<String, Object> getState() {
        Map<String, Object> state = new HashMap<>();

        // Packet positions
        Map<String, Map<String, Object>> packets = new HashMap<>();
        for (SimPacket packet : activePackets.values()) {
            Map<String, Object> pktState = new HashMap<>();
            pktState.put("wireId", packet.wireId);
            pktState.put("progress", packet.progress);
            pktState.put("playerSide", packet.playerSide);
            pktState.put("type", packet.type);
            packets.put(packet.id, pktState);
        }
        state.put("packets", packets);

        // Scores
        state.put("deliveredP1", deliveredP1);
        state.put("lostP1", lostP1);
        state.put("deliveredP2", deliveredP2);
        state.put("lostP2", lostP2);

        return state;
    }

    // Internal classes

    /**
     * Wrapper for network layout with player side
     */
    static class NetworkLayout {
        public final SubmitLayout layout;
        public final int playerSide;

        NetworkLayout(SubmitLayout layout, int side) {
            this.layout = layout;
            this.playerSide = side;
        }
    }

    /**
     * Simplified packet representation
     */
    static class SimPacket {
        String id;
        String wireId;
        int playerSide;
        double progress; // 0.0 to 1.0
        double wireLength;
        double noise;
        String type;
    }

    /**
     * Controlled system state
     */
    static class ControlledSystem {
        final String id;
        int ammoP1 = 0;
        int ammoP2 = 0;
        long lastInjectTime = 0;

        ControlledSystem(String id) {
            this.id = id;
        }
    }
}
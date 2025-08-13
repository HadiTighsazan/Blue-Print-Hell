package com.blueprinthell.snapshot;

import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.*; // for enums used in DTO fields (SystemKind, PortShape, PacketType)
import java.io.Serializable;
import java.util.*;

/**
 * Versioned, model-only snapshot DTOs for autosave/rewind.
 * Pure data: no logic, no references to live models or controllers.
 *
 * Notes for Gson:
 *  - Public no-arg constructors + public fields for easy (de)serialization.
 *  - Avoid java.awt.Point to keep JSON lean and toolkit-agnostic.
 */
public final class NetworkSnapshot implements Serializable {
    public static final String SCHEMA_VERSION = "model-v1";

    /** Top-level meta information. */
    public Meta meta;
    /** World state (boxes, wires, counters, producers). */
    public WorldState world;
    /** Large-packet group registry state. */
    public List<LargeGroupState> largeGroups;

    /** Default ctor for Gson. Initializes empty containers. */
    public NetworkSnapshot() {
        this.meta = new Meta();
        this.world = new WorldState();
        this.largeGroups = new ArrayList<>();
        this.meta.schemaVersion = SCHEMA_VERSION;
    }

    /** Compatibility ctor (existing call sites may pass score). */
    public NetworkSnapshot(int score) {
        this();
        this.world.score = score;
    }

    // ---------------------------------------------------------------------
    // META
    // ---------------------------------------------------------------------
    public static final class Meta implements Serializable {
        public String schemaVersion;     // e.g., "model-v1"
        public int levelNumber;          // LevelManager.getCurrentLevelNumber()
        public long tick;                // optional; engine tick if available
        public double timeSeconds;       // wall/engine time when captured
        public boolean producerFinished; // PacketProducerController.isFinished()
        public int producedUnits;        // PacketProducerController.getProducedUnits()

        public Meta() {}
    }

    // ---------------------------------------------------------------------
    // WORLD
    // ---------------------------------------------------------------------
    public static final class WorldState implements Serializable {
        public int score;
        public int coins;
        public int packetLoss;

        public double wireUsageTotal; // WireUsageModel.getTotalWireLength()
        public double wireUsageUsed;  // WireUsageModel.getUsedWireLength()
        public List<WireState> wires  = new ArrayList<>();
        public List<BoxState> boxes = new ArrayList<>();

        /** Snapshot of all packet producers (order-corresponds to controllers list). */
        public List<ProducerState> producers = new ArrayList<>();

        public WorldState() {}
    }
    // ---------------------------------------------------------------------
    // PRODUCER
    // ---------------------------------------------------------------------
    /** Minimal, stable snapshot of a PacketProducerController. */
    public static final class ProducerState implements Serializable {
        /** How many packets each out-port is allowed to emit in this stage. */
        public int packetsPerPort;
        /** Total emission budget of this producer (outs * packetsPerPort). */
        public int totalToProduce;
        /** How many packets already emitted. */
        public int producedCount;
        /** How many packets currently in-flight on wires. */
        public int inFlight;
        /** Whether the producer was running at snapshot time. */
        public boolean running;
        /** Emission time accumulator (seconds) to preserve spacing between bursts. */
        public double accumulatorSec;
        /** Per-port emission counters keyed by (boxId, outIndex). */
        public List<PortQuota> portQuotas = new ArrayList<>();
    }

    /** Per-port quota/counter for a producer, keyed stably for snapshotting. */
    public static final class PortQuota implements Serializable {
        public String boxId;
        public int outIndex;
        public int producedForThisPort;
    }

    public static final class BoxState implements Serializable {
        public String id;                 // SystemBoxModel.getId()
        public SystemKind primaryKind;    // kind at capture time
        public boolean enabled;           // SystemBoxModel.isEnabled()
        public double disableTimer;       // remaining cooldown/disable secs

        public List<PortShape> inShapes = new ArrayList<>();
        public List<PortShape> outShapes = new ArrayList<>();

        // Buffers at the box (runtime order preserved)
        public List<PacketState> bitBuffer = new ArrayList<>();
        public List<PacketState> largeBuffer = new ArrayList<>(); // LargePacket only

        public BoxState() {}
    }

    // ---------------------------------------------------------------------
    // WIRE
    // ---------------------------------------------------------------------
    public static final class WireState implements Serializable {
        public String id; // WireModel.getCanonicalId()

        public String fromBoxId;
        public int fromOutIndex; // index in fromBox.getOutPorts()
        public String toBoxId;
        public int toInIndex;    // index in toBox.getInPorts()

        public List<IntPoint> path = new ArrayList<>(); // WirePath.getPoints()
        // Optional: future-proofing if you add fractures/obstacles on wire
        public List<IntPoint> fractures = new ArrayList<>();

        public int largePassCount; // WireModel.getLargePacketPassCount()

        public List<PacketOnWire> packetsOnWire = new ArrayList<>();

        public WireState() {}
    }

    public static final class IntPoint implements Serializable {
        public int x;
        public int y;
        public IntPoint() {}
        public IntPoint(int x, int y) { this.x = x; this.y = y; }
    }

    public static final class PacketOnWire implements Serializable {
        public PacketState base; // common packet runtime info
        public double progress;  // 0..1 along the wire
        public PacketOnWire() {}
    }

    // ---------------------------------------------------------------------
    // PACKET (common state for any packet family)
    // ---------------------------------------------------------------------
    public static final class PacketState implements Serializable {
        // Identity / family
        // One of: BIT | LARGE | MERGED | TROJAN | CONFIDENTIAL | PROTECTED | MESSENGER
        public String family;
        // PacketType enum name (SQUARE, TRIANGLE, CIRCLE) where applicable
        public String type;

        // Motion & runtime scalars
        public double speed;
        public double acceleration;
        public double progress; // reused when in buffer for deterministic resume
        public double noise;
        public boolean returning;
        public double collisionCooldown;
        public boolean holdWhileCooldown;
        public String kinematicsProfileId; // KinematicsProfile enum name

        // Visual/size (when mutated, e.g., Confidential VPN 4->6 units)
        public Integer width;  // nullable
        public Integer height; // nullable

        // Protection / confidentiality
        public Double protectedShield; // nullable; for ProtectedPacket
        public Boolean confidential;   // nullable (true = confidential)
        public Boolean confidentialVpn;// nullable (true = VPN-tagged confidential)

        // Trojan specifics
        public String trojanOriginalFamily; // e.g., MESSENGER
        public String trojanOriginalType;   // PacketType of original

        // Large/Bit/Merged specifics
        public Integer groupId;           // nullable
        public Integer parentSizeUnits;   // for Bit/Large
        public Integer indexInGroup;      // for Bit
        public Integer expectedBits;      // for Large/Merged context
        public Integer colorId;           // for Large/Bit/Merged coloration
        public Boolean rebuiltFromBits;   // for Large reborn from bits

        public PacketState() {}
    }

    // ---------------------------------------------------------------------
    // LARGE GROUPS REGISTRY
    // ---------------------------------------------------------------------
    public static final class LargeGroupState implements Serializable {
        public int id;
        public int originalSizeUnits;
        public int expectedBits;
        public int colorId;
        public int mergedBits;
        public int lostBits;
        public boolean closed;
        public List<Integer> partialMerges = new ArrayList<>(); // merged packet sizes

        public LargeGroupState() {}
    }

}

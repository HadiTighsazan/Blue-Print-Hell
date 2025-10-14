package com.blueprinthell.snapshot;

import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.*; // for enums used in DTO fields (SystemKind, PortShape, PacketType)
import java.io.Serializable;
import java.util.*;


public final class NetworkSnapshot implements Serializable {
    public static final String SCHEMA_VERSION = "model-v3";

    public Meta meta;
    public WorldState world;
    public List<LargeGroupState> largeGroups;

    public NetworkSnapshot() {
        this.meta = new Meta();
        this.world = new WorldState();
        this.largeGroups = new ArrayList<>();
        this.meta.schemaVersion = SCHEMA_VERSION;
    }

    public NetworkSnapshot(int score) {
        this();
        this.world.score = score;
    }


    public static final class Meta implements Serializable {
        public String schemaVersion;
        public int levelNumber;
        public long tick;
        public double timeSeconds;
        public boolean producerFinished;
        public int producedUnits;

        public Meta() {}
    }


    public static final class WorldState implements Serializable {
        public int score;
        public int coins;
        public int packetLoss;

        public double wireUsageTotal; // WireUsageModel.getTotalWireLength()
        public double wireUsageUsed;  // WireUsageModel.getUsedWireLength()
        public List<WireState> wires  = new ArrayList<>();
        public List<BoxState> boxes = new ArrayList<>();

        public List<ProducerState> producers = new ArrayList<>();

        public WorldState() {}
    }

    public static final class ProducerState implements Serializable {
        public int packetsPerPort;
        public int totalToProduce;
        public int producedCount;
        public int inFlight;
        public boolean running;
        public double accumulatorSec;
        public List<PortQuota> portQuotas = new ArrayList<>();
    }

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
        public int x;
        public int y;
        public List<PortShape> inShapes = new ArrayList<>();
        public List<PortShape> outShapes = new ArrayList<>();
        public Map<String, Map<String, Object>> behaviorStates;

        // Buffers at the box (runtime order preserved)
        public List<PacketState> bitBuffer = new ArrayList<>();
        public List<PacketState> largeBuffer = new ArrayList<>();  // LargePacket only
        public List<PacketState> returnBuffer = new ArrayList<>(); // Packets returning to source

        // --- [PATCH] DistributorBehavior state ---
        public Map<Integer, Integer> distributorRemainingBits;
        public Map<Integer, Integer> distributorParentSizeByGrp;
        public Map<Integer, Integer> distributorColorIdByGrp;
        public Map<Integer, Integer> distributorNextIndexByGrp;
        public List<Integer> distributorRrGroups;

        public BoxState() {
            // init for distributor maps/lists
            distributorRemainingBits   = new HashMap<>();
            distributorParentSizeByGrp = new HashMap<>();
            distributorColorIdByGrp    = new HashMap<>();
            distributorNextIndexByGrp  = new HashMap<>();
            distributorRrGroups        = new ArrayList<>();
            behaviorStates = new HashMap<>();

        }
    }



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
        public PacketState base;
        public double progress;
        public PacketOnWire() {}
    }


    public static final class PacketState implements Serializable {
        // One of: BIT | LARGE | MERGED | TROJAN | CONFIDENTIAL | PROTECTED | MESSENGER
        public String family;
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

        public Double protectedShield;
        public Boolean confidential;
        public Boolean confidentialVpn;

        // Trojan specifics
        public String trojanOriginalFamily;
        public String trojanOriginalType;

        public Integer groupId;
        public Integer parentSizeUnits;
        public Integer indexInGroup;
        public Integer expectedBits;
        public Integer colorId;
        public Boolean rebuiltFromBits;
        public Integer customRgb;
        public PacketState() {}
    }


    public static final class LargeGroupState implements Serializable {
        public int id;
        public int originalSizeUnits;
        public int expectedBits;
        public int colorId;
        public int receivedBits;
        public int mergedBits;
        public int lostBits;
        public boolean closed;
        public List<Integer> partialMerges = new ArrayList<>(); // merged packet sizes

        public LargeGroupState() {}
    }

}

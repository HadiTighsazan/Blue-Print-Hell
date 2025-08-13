package com.blueprinthell.motion;

import com.blueprinthell.model.PacketModel;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;


public final class KinematicsRegistry {

    private static final Map<PacketModel, KinematicsProfile> MAP = new WeakHashMap<>();

    private KinematicsRegistry() {  }



    public static void setProfile(PacketModel packet, KinematicsProfile profile) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(profile, "profile");
        MAP.put(packet, profile);
    }


    public static KinematicsProfile getProfile(PacketModel packet) {
        return MAP.get(packet);
    }


    public static KinematicsProfile getOrDefault(PacketModel packet, KinematicsProfile deflt) {
        KinematicsProfile p = MAP.get(packet);
        return (p != null) ? p : deflt;
    }


    public static KinematicsProfile ensure(PacketModel packet, KinematicsProfile deflt) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(deflt, "default profile");
        return MAP.computeIfAbsent(packet, k -> deflt);
    }

    public static boolean has(PacketModel packet) {
        return MAP.containsKey(packet);
    }

    public static void remove(PacketModel packet) {
        MAP.remove(packet);
    }

    public static void clear() {
        MAP.clear();
    }


    public static void copyProfile(PacketModel from, PacketModel to) {
        KinematicsProfile p = MAP.get(from);
        if (p != null) MAP.put(to, p);
    }

    public static Map<PacketModel, KinematicsProfile> view() {
        return Collections.unmodifiableMap(MAP);

    }

    /**
     * Returns enum name for snapshotting; may be null if no explicit profile set yet.
     */
    public static String getProfileId(PacketModel packet) {
        KinematicsProfile k = getProfile(packet);
        return (k != null) ? k.name() : null;
    }

    /**
     * Restores profile from its enum name; if id is null, leaves current/default profile.
     */
    public static void setProfileById(PacketModel packet, String id) {
        if (id == null) return;
        setProfile(packet, KinematicsProfile.valueOf(id));
    }
}

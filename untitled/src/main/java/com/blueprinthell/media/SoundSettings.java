package com.blueprinthell.media;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Global sound settings model holding master volume (0.0 - 1.0).
 * Observers can subscribe to be notified when the volume changes.
 */
public final class SoundSettings {
    private SoundSettings() {}

    private static float volume = 1.0f; // default full volume
    private static final List<Consumer<Float>> listeners = new ArrayList<>();

    /** Returns current master volume in [0,1]. */
    public static synchronized float getVolume() {
        return volume;
    }

    /** Sets master volume; notifies listeners if changed. */
    public static synchronized void setVolume(float v) {
        if (v < 0f) v = 0f;
        if (v > 1f) v = 1f;
        if (volume != v) {
            volume = v;
            for (Consumer<Float> l : listeners) l.accept(volume);
        }
    }

    /** Registers a listener to volume changes. */
    /** Registers a listener to volume changes and immediately notifies it with current volume. */
    public static synchronized void addVolumeListener(Consumer<Float> l) {
        listeners.add(l);
        l.accept(volume); // immediate sync with current value
    }

    /** Removes a previously added listener. */
    public static synchronized void removeVolumeListener(Consumer<Float> l) {
        listeners.remove(l);
    }
}

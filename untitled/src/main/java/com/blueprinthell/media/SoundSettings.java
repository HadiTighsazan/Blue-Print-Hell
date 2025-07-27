package com.blueprinthell.media;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;


public final class SoundSettings {
    private SoundSettings() {}

    private static float volume = 1.0f; // default full volume
    private static final List<Consumer<Float>> listeners = new ArrayList<>();

    public static synchronized float getVolume() {
        return volume;
    }

    public static synchronized void setVolume(float v) {
        if (v < 0f) v = 0f;
        if (v > 1f) v = 1f;
        if (volume != v) {
            volume = v;
            for (Consumer<Float> l : listeners) l.accept(volume);
        }
    }

    public static synchronized void addVolumeListener(Consumer<Float> l) {
        listeners.add(l);
        l.accept(volume);
    }

    public static synchronized void removeVolumeListener(Consumer<Float> l) {
        listeners.remove(l);
    }
}

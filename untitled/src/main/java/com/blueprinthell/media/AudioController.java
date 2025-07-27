package com.blueprinthell.media;

import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;


public class AudioController {
    private Clip bgLoop;

    public AudioController() {
        SoundSettings.addVolumeListener(this::applyVolumeToAll);
    }

    public void playBackgroundLoop() {
        if (bgLoop == null) {
            bgLoop = ResourceManager.INSTANCE.getClip("bg_loop.wav");
            bgLoop.loop(Clip.LOOP_CONTINUOUSLY);
            applyVolume(bgLoop, SoundSettings.getVolume());
        } else if (!bgLoop.isActive()) {
            bgLoop.setFramePosition(0);
            bgLoop.loop(Clip.LOOP_CONTINUOUSLY);
        }
    }

    public void stopBackgroundLoop() {
        if (bgLoop != null && bgLoop.isRunning()) {
            bgLoop.stop();
        }
    }

    private void applyVolumeToAll(float vol) {
        if (bgLoop != null) applyVolume(bgLoop, vol);
    }

    private void applyVolume(Clip clip, float vol) {
        try {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float min = gain.getMinimum();
            float max = gain.getMaximum();
            float dB = (vol == 0f) ? min : (float) (20.0 * Math.log10(vol));
            dB = Math.max(min, Math.min(max, dB));
            gain.setValue(dB);
        } catch (Exception ignored) {
        }
    }
}

package com.blueprinthell.ui;

import javax.sound.sampled.*;
import java.io.IOException;
import java.net.URL;
import java.util.List;


public class SoundManager {

    private final Clip bgClip;
    private final Clip impactClip;
    private final Clip connectClip;
    private final Clip gameoverClip;

    private static final SoundManager INSTANCE = new SoundManager();
    public static SoundManager get() { return INSTANCE; }

    private SoundManager() {
        bgClip      = loadClip("bg_loop.wav");
        impactClip  = loadClip("impact_thud.wav");
        connectClip = loadClip("connect_click.wav");
        gameoverClip= loadClip("gameover_jingle.wav");
    }

    /* ======================= متدهای عمومی ====================== */
    public void loopBg() {
        if (bgClip != null && !bgClip.isActive()) {
            bgClip.loop(Clip.LOOP_CONTINUOUSLY);
        }
    }

    public void stopBg() {
        if (bgClip != null && bgClip.isRunning()) {
            bgClip.stop();
        }
    }

    public void impact()  { restart(impactClip); }
    public void connect() { restart(connectClip); }
    public void gameover(){ restart(gameoverClip); }

    /* ======================= متدهای کمکی ======================= */
    private static Clip loadClip(String fileName) {
        try {
            URL url = SoundManager.class.getClassLoader()
                    .getResource("resource/" + fileName);
            if (url == null) throw new IOException("Audio resource not found: " + fileName);
            AudioInputStream ais = AudioSystem.getAudioInputStream(url);
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            return clip;
        } catch (UnsupportedAudioFileException | LineUnavailableException | IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private static void restart(Clip clip) {
        if (clip == null) return;
        if (clip.isRunning()) clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }

    public void setMasterVolume(int pct) {
        float dB;
        if (pct <= 0) {           // سکوت
            dB = -80f;            // مینیمم عملى بیشتر کارت‌ها
        } else {
            dB = 20f * (float) Math.log10(pct / 100f);
        }
        // روى هر Clip ثبت‌شده اعمال کن
        for (Clip c : List.of(bgClip, impactClip, connectClip, gameoverClip)) {
            if (c == null) continue;
            try {
                FloatControl gain = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
                gain.setValue(dB);
            } catch (Exception ignore) { /* بعضى کارت‌ها MASTER_GAIN ندارند */ }
        }
    }
}

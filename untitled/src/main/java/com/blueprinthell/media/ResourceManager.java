package com.blueprinthell.media;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;


public enum ResourceManager {
    INSTANCE;

    private static final String BASE_PATH = "/C:/Users/hadit/Desktop/AP-DA/Blue_Print_Hell/untitled/src/main/resources/";

    private final Map<String, Clip> clipCache = new HashMap<>();
    private final Map<String, BufferedImage> imageCache = new HashMap<>();

    public Clip getClip(String fileName) {
        return clipCache.computeIfAbsent(fileName, this::loadClip);
    }

    private Clip loadClip(String fileName) {
        try {
            URL url = new URL("file:" + BASE_PATH + fileName);
            AudioInputStream ais = AudioSystem.getAudioInputStream(url);
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            return clip;
        } catch (Exception e) {
            throw new RuntimeException("Could not load audio file: " + fileName, e);
        }
    }

    public BufferedImage getImage(String fileName) {
        return imageCache.computeIfAbsent(fileName, this::loadImage);
    }

    private BufferedImage loadImage(String fileName) {
        try {
            URL url = new URL("file:" + BASE_PATH + fileName);
            return ImageIO.read(url);
        } catch (IOException e) {
            throw new RuntimeException("Could not load image file: " + fileName, e);
        }
    }
}

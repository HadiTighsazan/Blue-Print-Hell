package com.blueprinthell.view;

import com.blueprinthell.media.ResourceManager;

import javax.swing.*;
import java.awt.*;

/**
 * Simple JPanel subclass that paints a scaled background image covering the entire panel.
 * All menu screens can extend this class to reuse the same background.
 */
public class BackgroundPanel extends JPanel {

    private final Image bg;

    public BackgroundPanel() {
        // Load once from ResourceManager to benefit from caching
        this.bg = ResourceManager.INSTANCE.getImage("BG.png");
        setOpaque(false); // let the background image show instead of default color
    }

    @Override
    protected void paintComponent(Graphics g) {
        // First paint the background image, then let Swing paint children.
        if (bg != null) {
            g.drawImage(bg, 0, 0, getWidth(), getHeight(), this);
        }
        super.paintComponent(g);
    }
}

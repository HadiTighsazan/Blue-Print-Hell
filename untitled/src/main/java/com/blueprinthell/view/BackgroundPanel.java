package com.blueprinthell.view;

import com.blueprinthell.media.ResourceManager;

import javax.swing.*;
import java.awt.*;


public class BackgroundPanel extends JPanel {

    private final Image bg;

    public BackgroundPanel() {
        this.bg = ResourceManager.INSTANCE.getImage("BG.png");
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (bg != null) {
            g.drawImage(bg, 0, 0, getWidth(), getHeight(), this);
        }
        super.paintComponent(g);
    }
}

package com.blueprinthell.view.screens;

import com.blueprinthell.view.BackgroundPanel;

import javax.swing.*;
import java.awt.*;


public class MainMenuView extends BackgroundPanel {

    public final JButton startButton    = makeButton("Start");
    public final JButton settingsButton = makeButton("Settings");
    public final JButton exitButton     = makeButton("Exit");

    public MainMenuView() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setPreferredSize(new Dimension(800, 600));
        setOpaque(false);

        add(Box.createVerticalGlue());
        add(startButton);
        add(Box.createRigidArea(new Dimension(0, 15)));
        add(settingsButton);
        add(Box.createRigidArea(new Dimension(0, 15)));
        add(exitButton);
        add(Box.createVerticalGlue());
    }

    private JButton makeButton(String text) {
        JButton btn = new JButton(text);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 18f));
        btn.setPreferredSize(new Dimension(200, 40));
        btn.setMaximumSize(new Dimension(200, 40));
        btn.setFocusPainted(false);
        return btn;
    }
}

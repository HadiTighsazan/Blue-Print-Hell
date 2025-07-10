package com.blueprinthell.view.screens;

import javax.swing.*;
import java.awt.*;

/**
 * Main menu screen with Start, Settings, and Exit buttons.
 */
public class MainMenuView extends JPanel {
    public final JButton startButton = new JButton("Start");
    public final JButton settingsButton = new JButton("Settings");
    public final JButton exitButton = new JButton("Exit");

    public MainMenuView() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Color.DARK_GRAY);
        setPreferredSize(new Dimension(800, 600));

        add(Box.createVerticalGlue());
        add(createButtonPanel(startButton));
        add(createButtonPanel(settingsButton));
        add(createButtonPanel(exitButton));
        add(Box.createVerticalGlue());
    }

    private JPanel createButtonPanel(JButton button) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.add(button);
        return panel;
    }
}

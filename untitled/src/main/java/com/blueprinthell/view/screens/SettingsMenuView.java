package com.blueprinthell.view.screens;

import javax.swing.*;
import java.awt.*;

/**
 * Settings menu screen with options and Back button.
 */
public class SettingsMenuView extends JPanel {
    public final JButton backButton = new JButton("Back");
    public final JSlider volumeSlider = new JSlider(0, 100, 50);

    public SettingsMenuView() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Color.GRAY);
        setPreferredSize(new Dimension(800, 600));

        add(Box.createVerticalGlue());
        add(createLabel("Volume:"));
        add(createSliderPanel(volumeSlider));
        add(Box.createRigidArea(new Dimension(0, 20)));
        add(createButtonPanel(backButton));
        add(Box.createVerticalGlue());
    }

    private Component createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setForeground(Color.WHITE);
        return label;
    }

    private Component createSliderPanel(JSlider slider) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.add(slider);
        return panel;
    }

    private JPanel createButtonPanel(JButton button) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.add(button);
        return panel;
    }
}

package com.blueprinthell.ui;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;


public class SettingsScreen extends JPanel {
    private final SettingsListener listener;

    public SettingsScreen(SettingsListener listener) {
        this.listener = listener;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));

        JLabel title = new JLabel("Settings");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setAlignmentX(CENTER_ALIGNMENT);

        JLabel volLabel = new JLabel("Sound Volume");
        volLabel.setAlignmentX(CENTER_ALIGNMENT);
        JSlider slider = new JSlider(0, 100, 50);
        slider.setAlignmentX(CENTER_ALIGNMENT);
        slider.addChangeListener((ChangeListener) e -> {
            if (!slider.getValueIsAdjusting()) {
                listener.onSoundVolumeChanged(slider.getValue());
            }
        });

        JButton btnKey = new JButton("Change Key Bindings");
        btnKey.setAlignmentX(CENTER_ALIGNMENT);
        btnKey.addActionListener(e -> listener.onKeyBindingsRequested());

        JButton btnBack = new JButton("Back");
        btnBack.setAlignmentX(CENTER_ALIGNMENT);
        btnBack.addActionListener(e -> listener.onBack());

        add(Box.createVerticalGlue());
        add(title);
        add(Box.createRigidArea(new Dimension(0, 20)));
        add(volLabel);
        add(slider);
        add(Box.createRigidArea(new Dimension(0, 20)));
        add(btnKey);
        add(Box.createVerticalGlue());
        add(btnBack);
    }
}
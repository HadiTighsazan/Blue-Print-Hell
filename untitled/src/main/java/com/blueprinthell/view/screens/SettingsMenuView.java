package com.blueprinthell.view.screens;

import com.blueprinthell.view.BackgroundPanel;

import javax.swing.*;
import java.awt.*;


public class SettingsMenuView extends BackgroundPanel {

    public final JButton backButton        = makeButton("Back");
    public final JSlider volumeSlider      = new JSlider(0, 100, 50);
    public final JButton keyBindingButton  = makeButton("");

    public SettingsMenuView() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setPreferredSize(new Dimension(800, 600));
        setOpaque(false);

        add(Box.createVerticalGlue());
        add(makeLabel("Volume:"));
        add(createSliderPanel(volumeSlider));
        add(Box.createRigidArea(new Dimension(0, 30)));

        add(makeLabel("Key Bindings:"));
        add(Box.createRigidArea(new Dimension(0, 10)));
        add(keyBindingButton);
        add(Box.createRigidArea(new Dimension(0, 30)));

        add(backButton);
        add(Box.createVerticalGlue());
    }

    private JLabel makeLabel(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 18f));
        return label;
    }

    private JPanel createSliderPanel(JSlider slider) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        slider.setPreferredSize(new Dimension(200, 40));
        panel.add(slider);
        panel.setAlignmentX(Component.CENTER_ALIGNMENT);
        return panel;
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

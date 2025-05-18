package com.blueprinthell.ui;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class SettingsScreen extends JPanel {
    private final SettingsListener listener;
    private final Image bgImage;

    public SettingsScreen(SettingsListener listener, int initialVolume) {
        this.listener = listener;
        bgImage = new ImageIcon(
                getClass().getClassLoader().getResource("BG.png")
        ).getImage();

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
        setOpaque(false);

        JLabel title = new JLabel("Settings");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setAlignmentX(CENTER_ALIGNMENT);
        title.setForeground(Color.WHITE);

        JLabel volLabel = new JLabel("Sound Volume");
        volLabel.setAlignmentX(CENTER_ALIGNMENT);
        volLabel.setForeground(Color.WHITE);
        JSlider slider = new JSlider(0, 100, initialVolume);
        slider.setAlignmentX(CENTER_ALIGNMENT);
        slider.setMaximumSize(new Dimension(300, 50));
        slider.addChangeListener((ChangeListener) e -> {
            if (!slider.getValueIsAdjusting()) {
                listener.onSoundVolumeChanged(slider.getValue());
            }
        });

        Dimension btnSize = new Dimension(240, 60);
        Font btnFont = getFont().deriveFont(18f);

        JButton btnKey = new JButton("Change Key Bindings");
        btnKey.setFont(btnFont);
        btnKey.setMaximumSize(btnSize);
        btnKey.setAlignmentX(CENTER_ALIGNMENT);
        btnKey.addActionListener(e -> listener.onKeyBindingsRequested());

        JButton btnBack = new JButton("Back");
        btnBack.setFont(btnFont);
        btnBack.setMaximumSize(btnSize);
        btnBack.setAlignmentX(CENTER_ALIGNMENT);
        btnBack.addActionListener(e -> listener.onBack());

        add(Box.createVerticalGlue());
        add(title);
        add(Box.createRigidArea(new Dimension(0, 20)));
        add(volLabel);
        add(slider);
        add(Box.createRigidArea(new Dimension(0, 20)));
        add(btnKey);
        add(Box.createRigidArea(new Dimension(0, 10)));
        add(btnBack);
        add(Box.createVerticalGlue());
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(bgImage, 0, 0, getWidth(), getHeight(), this);
    }
}

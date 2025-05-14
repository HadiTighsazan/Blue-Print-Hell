package com.blueprinthell.ui;

import javax.swing.*;
import java.awt.*;


public class MainMenuScreen extends JPanel {
    private final MainMenuListener listener;
    private final JButton btnStart, btnSettings, btnExit;

    public MainMenuScreen(MainMenuListener listener) {
        this.listener = listener;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(100, 100, 100, 100));

        JLabel title = new JLabel("Network Puzzle Game");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 32f));
        title.setAlignmentX(CENTER_ALIGNMENT);

        btnStart = new JButton("Start");
        btnStart.setAlignmentX(CENTER_ALIGNMENT);
        btnStart.addActionListener(e -> listener.onAction(MainMenuListener.Action.START));

        btnSettings = new JButton("Settings");
        btnSettings.setAlignmentX(CENTER_ALIGNMENT);
        btnSettings.addActionListener(e -> listener.onAction(MainMenuListener.Action.SETTINGS));

        btnExit = new JButton("Exit");
        btnExit.setAlignmentX(CENTER_ALIGNMENT);
        btnExit.addActionListener(e -> listener.onAction(MainMenuListener.Action.EXIT));

        add(Box.createVerticalGlue());
        add(title);
        add(Box.createRigidArea(new Dimension(0, 50)));
        add(btnStart);
        add(Box.createRigidArea(new Dimension(0, 10)));
        add(btnSettings);
        add(Box.createRigidArea(new Dimension(0, 10)));
        add(btnExit);
        add(Box.createVerticalGlue());
    }
}
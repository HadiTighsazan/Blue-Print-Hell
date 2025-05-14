package com.blueprinthell.ui;

import javax.swing.*;
import java.awt.*;

public class MainMenuScreen extends JPanel {
    private final MainMenuListener listener;
    private final JButton btnStart;
    private final JButton btnSettings;
    private final JButton btnExit;

    public MainMenuScreen(MainMenuListener listener) {
        this.listener = listener;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Color.DARK_GRAY);

        btnStart    = createMenuButton("Start");
        btnSettings = createMenuButton("Settings");
        btnExit     = createMenuButton("Exit");

        add(Box.createVerticalGlue());
        add(btnStart);
        add(Box.createVerticalStrut(20));
        add(btnSettings);
        add(Box.createVerticalStrut(20));
        add(btnExit);
        add(Box.createVerticalGlue());

        btnStart.addActionListener(e -> listener.onAction(MainMenuListener.Action.START));
        btnSettings.addActionListener(e -> listener.onAction(MainMenuListener.Action.SETTINGS));
        btnExit.addActionListener(e -> listener.onAction(MainMenuListener.Action.EXIT));
    }

    private JButton createMenuButton(String text) {
        JButton btn = new JButton(text);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(200, 50));
        btn.setFont(new Font("Arial", Font.BOLD, 18));
        return btn;
    }
}
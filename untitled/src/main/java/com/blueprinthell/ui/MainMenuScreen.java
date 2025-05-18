package com.blueprinthell.ui;

import javax.swing.*;
import java.awt.*;

public class MainMenuScreen extends JPanel {
    private final MainMenuListener listener;
    private final JButton btnStart, btnSettings, btnExit;
    private final Image bgImage;

    public MainMenuScreen(MainMenuListener listener) {
        this.listener = listener;
        bgImage = new ImageIcon(
                getClass().getClassLoader().getResource("BG.png")
        ).getImage();

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(100, 100, 100, 100));
        setOpaque(false);

        JLabel title = new JLabel("BLUE PRINT HELL");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 32f));
        title.setAlignmentX(CENTER_ALIGNMENT);
        title.setForeground(Color.WHITE);

        Dimension btnSize = new Dimension(220, 60);

        btnStart = new JButton("Start");
        btnStart.setFont(btnStart.getFont().deriveFont(20f));
        btnStart.setMaximumSize(btnSize);
        btnStart.setAlignmentX(CENTER_ALIGNMENT);
        btnStart.addActionListener(e -> listener.onAction(MainMenuListener.Action.START));

        btnSettings = new JButton("Settings");
        btnSettings.setFont(btnSettings.getFont().deriveFont(20f));
        btnSettings.setMaximumSize(btnSize);
        btnSettings.setAlignmentX(CENTER_ALIGNMENT);
        btnSettings.addActionListener(e -> listener.onAction(MainMenuListener.Action.SETTINGS));

        btnExit = new JButton("Exit");
        btnExit.setFont(btnExit.getFont().deriveFont(20f));
        btnExit.setMaximumSize(btnSize);
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

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(bgImage, 0, 0, getWidth(), getHeight(), this);
    }
}

package com.blueprinthell.ui;

import javax.swing.*;
import java.awt.*;

public class LevelSelectScreen extends JPanel {
    private final LevelSelectListener listener;
    private final Image bgImage;

    public LevelSelectScreen(LevelSelectListener listener) {
        this.listener = listener;
        bgImage = new ImageIcon(
                getClass().getClassLoader().getResource("BG.png")).getImage();

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(80, 80, 80, 80));
        setOpaque(false);

        JLabel title = new JLabel("Select Level");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setAlignmentX(CENTER_ALIGNMENT);
        title.setForeground(Color.WHITE);

        Dimension btnSize = new Dimension(220, 60);
        Font btnFont = getFont().deriveFont(20f);

        JButton btn1 = new JButton("Level 1");
        btn1.setFont(btnFont);
        btn1.setMaximumSize(btnSize);
        btn1.setAlignmentX(CENTER_ALIGNMENT);
        btn1.addActionListener(e -> listener.onLevelSelected(1));

        JButton btn2 = new JButton("Level 2");
        btn2.setFont(btnFont);
        btn2.setMaximumSize(btnSize);
        btn2.setAlignmentX(CENTER_ALIGNMENT);
        btn2.addActionListener(e -> listener.onLevelSelected(2));

        JButton btnBack = new JButton("Back");
        btnBack.setFont(btnFont);
        btnBack.setMaximumSize(btnSize);
        btnBack.setAlignmentX(CENTER_ALIGNMENT);
        btnBack.addActionListener(e -> listener.onBack());

        add(Box.createVerticalGlue());
        add(title);
        add(Box.createRigidArea(new Dimension(0, 30)));
        add(btn1);
        add(Box.createRigidArea(new Dimension(0, 10)));
        add(btn2);
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

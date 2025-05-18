package com.blueprinthell.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


public class MissionPassedScreen extends JPanel {
    public MissionPassedScreen(SettingsListener listener) {
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(new Color(0, 0, 0, 160));

        Box box = Box.createVerticalBox();

        JLabel lbl = new JLabel("Mission Passed!");
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 32f));
        lbl.setForeground(Color.WHITE);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton btn = new JButton("Back to Main Menu");
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                listener.onBack();
            }
        });

        box.add(Box.createVerticalGlue());
        box.add(lbl);
        box.add(Box.createRigidArea(new Dimension(0, 20)));
        box.add(btn);
        box.add(Box.createVerticalGlue());

        add(box, BorderLayout.CENTER);
    }
}

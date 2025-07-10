package com.blueprinthell.view.screens;

import javax.swing.*;
import java.awt.*;

/**
 * Screen displayed when a mission is passed, with options to proceed or return to menu.
 */
public class MissionPassedView extends JPanel {
    public final JButton nextMissionButton = new JButton("Next Mission");
    public final JButton mainMenuButton = new JButton("Main Menu");

    public MissionPassedView() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Color.BLUE.darker());
        setPreferredSize(new Dimension(800, 600));

        JLabel label = new JLabel("Mission Passed!");
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(48f));

        add(Box.createVerticalGlue());
        add(label);
        add(Box.createRigidArea(new Dimension(0, 20)));
        add(createButtonPanel(nextMissionButton));
        add(createButtonPanel(mainMenuButton));
        add(Box.createVerticalGlue());
    }

    private JPanel createButtonPanel(JButton button) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.add(button);
        return panel;
    }
}

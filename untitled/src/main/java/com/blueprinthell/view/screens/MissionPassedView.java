package com.blueprinthell.view.screens;

import javax.swing.*;
import java.awt.*;

/**
 * Screen displayed when a mission is passed, with a summary and navigation options.
 */
public class MissionPassedView extends JPanel {

    /* ------------- components ------------- */
    private final JLabel titleLabel  = new JLabel("Mission Passed!");
    private final JLabel levelLabel  = new JLabel();
    private final JLabel scoreLabel  = new JLabel();
    private final JLabel lossLabel   = new JLabel();

    public final JButton nextMissionButton = new JButton("Next Mission");
    public final JButton mainMenuButton   = new JButton("Main Menu");

    public MissionPassedView() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Color.BLUE.darker());
        setPreferredSize(new Dimension(800, 600));

        /* -------- title -------- */
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(48f));

        /* -------- summary labels -------- */
        for (JLabel l : new JLabel[]{levelLabel, scoreLabel, lossLabel}) {
            l.setAlignmentX(Component.CENTER_ALIGNMENT);
            l.setForeground(Color.WHITE);
            l.setFont(l.getFont().deriveFont(Font.BOLD, 20f));
        }

        add(Box.createVerticalGlue());
        add(titleLabel);
        add(Box.createRigidArea(new Dimension(0, 20)));
        add(levelLabel);
        add(scoreLabel);
        add(lossLabel);
        add(Box.createRigidArea(new Dimension(0, 30)));
        add(createButtonPanel(nextMissionButton));
        add(Box.createRigidArea(new Dimension(0, 10)));
        add(createButtonPanel(mainMenuButton));
        add(Box.createVerticalGlue());
    }

    /* ---------------- public API ---------------- */

    /**
     * Updates the summary information shown on this screen.
     *
     * @param currentLevel index of the level just completed (1‑based)
     * @param score        final score for that level
     * @param packetLoss   total lost packets
     */
    public void setSummary(int currentLevel, int score, int packetLoss) {
        levelLabel.setText("Level " + currentLevel + " completed");
        scoreLabel.setText("Score: " + score);
        lossLabel.setText("Packet Loss: " + packetLoss);
        nextMissionButton.setText("Next Mission (Level " + (currentLevel + 1) + ")");
    }

    /* ---------------- helper ---------------- */
    private JPanel createButtonPanel(JButton button) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(button);
        return panel;
    }
}

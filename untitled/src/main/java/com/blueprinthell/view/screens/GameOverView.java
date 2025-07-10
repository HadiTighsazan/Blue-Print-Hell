package com.blueprinthell.view.screens;

import javax.swing.*;
import java.awt.*;

/**
 * Game Over screen with Retry and Main Menu buttons.
 */
public class GameOverView extends JPanel {
    public final JButton retryButton = new JButton("Retry");
    public final JButton mainMenuButton = new JButton("Main Menu");

    public GameOverView() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(800, 600));

        JLabel label = new JLabel("Game Over");
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setForeground(Color.RED);
        label.setFont(label.getFont().deriveFont(48f));

        add(Box.createVerticalGlue());
        add(label);
        add(Box.createRigidArea(new Dimension(0, 20)));
        add(createButtonPanel(retryButton));
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

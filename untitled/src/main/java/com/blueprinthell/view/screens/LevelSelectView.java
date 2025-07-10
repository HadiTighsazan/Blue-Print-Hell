package com.blueprinthell.view.screens;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Screen for selecting a level/mission.
 */
public class LevelSelectView extends JPanel {
    public final JButton backButton = new JButton("Back");
    private final java.util.List<JButton> levelButtons;

    public LevelSelectView() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Color.LIGHT_GRAY);
        setPreferredSize(new Dimension(800, 600));

        add(Box.createVerticalGlue());
        // Example: 5 levels
        levelButtons = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> new JButton("Level " + i))
                .toList();
        for (JButton btn : levelButtons) {
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            add(btn);
            add(Box.createRigidArea(new Dimension(0, 10)));
        }
        add(Box.createRigidArea(new Dimension(0, 20)));
        add(createButtonPanel(backButton));
        add(Box.createVerticalGlue());
    }

    private JPanel createButtonPanel(JButton button) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.add(button);
        return panel;
    }

    public List<JButton> getLevelButtons() {
        return levelButtons;
    }
}

package com.blueprinthell.view.screens;

import com.blueprinthell.view.BackgroundPanel;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Screen for selecting a level/mission with a shared background and styled buttons.
 */
public class LevelSelectView extends BackgroundPanel {

    public final JButton backButton = makeButton("Back");
    private final List<JButton> levelButtons;

    public LevelSelectView() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setPreferredSize(new Dimension(800, 600));
        setOpaque(false);

        add(Box.createVerticalGlue());

        levelButtons = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> makeButton("Level " + i))
                .toList();

        for (JButton btn : levelButtons) {
            add(btn);
            add(Box.createRigidArea(new Dimension(0, 10)));
        }

        add(Box.createRigidArea(new Dimension(0, 20)));
        add(backButton);
        add(Box.createVerticalGlue());
    }

    /* ---------------- helper ---------------- */
    private JButton makeButton(String text) {
        JButton btn = new JButton(text);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 18f));
        btn.setPreferredSize(new Dimension(200, 40));
        btn.setMaximumSize(new Dimension(200, 40));
        btn.setFocusPainted(false);
        return btn;
    }

    public List<JButton> getLevelButtons() { return levelButtons; }
}

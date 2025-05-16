package com.blueprinthell.ui;

import javax.swing.*;
import java.awt.*;

/**
 * نمایش صفحه‌ی Game Over با دکمه بازگشت به منوی اصلی
 */
public class GameOverScreen extends JPanel {
    private final SettingsListener listener;

    public GameOverScreen(SettingsListener listener) {
        this.listener = listener;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(100, 100, 100, 100));

        // عنوان Game Over
        JLabel title = new JLabel("Game Over");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 36f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        // دکمه بازگشت به منوی اصلی
        JButton btnMenu = new JButton("Return to Main Menu");
        btnMenu.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnMenu.addActionListener(e -> listener.onBack());

        // چینش عمودی با فضای انعطاف‌پذیر
        add(Box.createVerticalGlue());
        add(title);
        add(Box.createRigidArea(new Dimension(0, 50)));
        add(btnMenu);
        add(Box.createVerticalGlue());
    }
}

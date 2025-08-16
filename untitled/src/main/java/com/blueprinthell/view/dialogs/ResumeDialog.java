// untitled/src/main/java/com/blueprinthell/view/dialogs/ResumeDialog.java
package com.blueprinthell.view.dialogs;

import com.blueprinthell.controller.AutoSaveController.SaveMetadata;
import javax.swing.*;
import java.awt.*;

public class ResumeDialog extends JDialog {
    private boolean resumeSelected = false;

    public ResumeDialog(JFrame parent, SaveMetadata metadata) {
        super(parent, "Resume Previous Game?", true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(new Color(30, 30, 40));

        // عنوان
        JLabel titleLabel = new JLabel("Unsaved Progress Detected", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // اطلاعات بازی قبلی
        JPanel infoPanel = new JPanel(new GridLayout(5, 1, 5, 5));
        infoPanel.setOpaque(false);

        addInfoLabel(infoPanel, "Level: " + metadata.levelNumber + " - " + metadata.levelName);
        addInfoLabel(infoPanel, "Score: " + metadata.score);
        addInfoLabel(infoPanel, "Coins: " + metadata.coins);
        addInfoLabel(infoPanel, String.format("Progress: %.1f%%", metadata.progressPercent));
        addInfoLabel(infoPanel, "Last Saved: " + formatTimestamp(metadata.timestamp));

        mainPanel.add(infoPanel, BorderLayout.CENTER);

        // دکمه‌ها
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        buttonPanel.setOpaque(false);

        JButton resumeButton = createButton("Resume Game", new Color(60, 179, 113));
        resumeButton.addActionListener(e -> {
            resumeSelected = true;
            dispose();
        });

        JButton newGameButton = createButton("Start New Game", new Color(220, 53, 69));
        newGameButton.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(this,
                    "Are you sure? The saved progress will be lost.",
                    "Confirm New Game",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (result == JOptionPane.YES_OPTION) {
                resumeSelected = false;
                dispose();
            }
        });

        buttonPanel.add(resumeButton);
        buttonPanel.add(newGameButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(parent);
    }

    private void addInfoLabel(JPanel panel, String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(new Font("Arial", Font.PLAIN, 14));
        label.setForeground(new Color(200, 200, 200));
        panel.add(label);
    }

    private JButton createButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(bgColor);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setPreferredSize(new Dimension(150, 40));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // hover effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(bgColor.brighter());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(bgColor);
            }
        });

        return button;
    }

    private String formatTimestamp(String timestamp) {
        // ساده‌سازی timestamp
        if (timestamp != null && timestamp.contains("T")) {
            String[] parts = timestamp.split("T");
            if (parts.length == 2) {
                String time = parts[1].split("\\.")[0]; // حذف میلی‌ثانیه
                return parts[0] + " " + time;
            }
        }
        return timestamp;
    }

    public boolean isResumeSelected() {
        return resumeSelected;
    }
}
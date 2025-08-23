package com.blueprinthell.view.pvp;

import com.blueprinthell.shared.protocol.NetworkProtocol.PlayerScore;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * صفحه نمایش نتایج بازی PvP
 */
public class PvPResultView extends JPanel {

    // UI Components
    private final JLabel resultLabel;
    private final JLabel playerScoreLabel;
    private final JLabel opponentScoreLabel;
    private final JLabel xpEarnedLabel;
    private final JProgressBar xpBar;
    private final JButton rematchButton;
    private final JButton mainMenuButton;
    private final JButton viewStatsButton;

    // Animation
    private Timer animationTimer;
    private int currentXP = 0;
    private final int targetXP;

    /**
     * Constructor
     */
    public PvPResultView(boolean isWinner, PlayerScore playerScore, PlayerScore opponentScore,
                         int xpEarned, String opponentName) {
        this.targetXP = xpEarned;

        setLayout(new BorderLayout());
        setBackground(new Color(20, 20, 30));

        // === Main Panel ===
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setOpaque(false);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(50, 50, 50, 50));

        // Result header
        resultLabel = new JLabel(isWinner ? "VICTORY!" : "DEFEAT");
        resultLabel.setFont(new Font("Arial", Font.BOLD, 48));
        resultLabel.setForeground(isWinner ? Color.GREEN : Color.RED);
        resultLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(resultLabel);

        mainPanel.add(Box.createVerticalStrut(30));

        // Opponent name
        JLabel vsLabel = new JLabel("vs " + opponentName);
        vsLabel.setFont(new Font("Arial", Font.PLAIN, 18));
        vsLabel.setForeground(Color.GRAY);
        vsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(vsLabel);

        mainPanel.add(Box.createVerticalStrut(40));

        // === Score Panel ===
        JPanel scorePanel = new JPanel(new GridLayout(1, 3, 30, 0));
        scorePanel.setOpaque(false);
        scorePanel.setMaximumSize(new Dimension(600, 150));

        // Player score
        JPanel playerPanel = createScoreCard("YOU", playerScore, Color.BLUE);
        playerScoreLabel = (JLabel) playerPanel.getComponent(1);
        scorePanel.add(playerPanel);

        // VS separator
        JLabel vsCenter = new JLabel("VS", SwingConstants.CENTER);
        vsCenter.setFont(new Font("Arial", Font.BOLD, 24));
        vsCenter.setForeground(Color.WHITE);
        scorePanel.add(vsCenter);

        // Opponent score
        JPanel opponentPanel = createScoreCard("OPPONENT", opponentScore, Color.RED);
        opponentScoreLabel = (JLabel) opponentPanel.getComponent(1);
        scorePanel.add(opponentPanel);

        mainPanel.add(scorePanel);

        mainPanel.add(Box.createVerticalStrut(40));

        // === XP Panel ===
        JPanel xpPanel = new JPanel();
        xpPanel.setLayout(new BoxLayout(xpPanel, BoxLayout.Y_AXIS));
        xpPanel.setOpaque(false);
        xpPanel.setMaximumSize(new Dimension(400, 100));

        xpEarnedLabel = new JLabel("XP Earned: 0 / " + xpEarned);
        xpEarnedLabel.setFont(new Font("Arial", Font.BOLD, 20));
        xpEarnedLabel.setForeground(Color.YELLOW);
        xpEarnedLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        xpPanel.add(xpEarnedLabel);

        xpPanel.add(Box.createVerticalStrut(10));

        xpBar = new JProgressBar(0, xpEarned);
        xpBar.setValue(0);
        xpBar.setStringPainted(true);
        xpBar.setString("0 XP");
        xpBar.setForeground(new Color(255, 215, 0));
        xpBar.setBackground(new Color(50, 50, 50));
        xpBar.setPreferredSize(new Dimension(300, 25));
        xpPanel.add(xpBar);

        mainPanel.add(xpPanel);

        mainPanel.add(Box.createVerticalStrut(30));

        // === Statistics Panel ===
        JPanel statsPanel = createStatsPanel(playerScore, opponentScore, isWinner);
        statsPanel.setMaximumSize(new Dimension(500, 100));
        mainPanel.add(statsPanel);

        mainPanel.add(Box.createVerticalStrut(40));

        // === Button Panel ===
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        buttonPanel.setOpaque(false);

        rematchButton = createButton("Rematch", new Color(50, 150, 50));
        mainMenuButton = createButton("Main Menu", new Color(100, 100, 100));
        viewStatsButton = createButton("View Stats", new Color(50, 100, 150));

        buttonPanel.add(rematchButton);
        buttonPanel.add(mainMenuButton);
        buttonPanel.add(viewStatsButton);

        mainPanel.add(buttonPanel);

        add(mainPanel, BorderLayout.CENTER);

        // Start XP animation
        startXPAnimation();

        // Victory/Defeat animation
        startResultAnimation(isWinner);
    }

    /**
     * Create score card for player/opponent
     */
    private JPanel createScoreCard(String title, PlayerScore score, Color color) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createLineBorder(color, 2));
        panel.setPreferredSize(new Dimension(150, 120));

        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
        titleLabel.setForeground(color);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(titleLabel);

        panel.add(Box.createVerticalStrut(10));

        JLabel scoreLabel = new JLabel(String.valueOf(score.totalScore), SwingConstants.CENTER);
        scoreLabel.setFont(new Font("Arial", Font.BOLD, 36));
        scoreLabel.setForeground(Color.WHITE);
        scoreLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(scoreLabel);

        panel.add(Box.createVerticalStrut(10));

        JLabel detailLabel = new JLabel("D: " + score.delivered + " | L: " + score.lost);
        detailLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        detailLabel.setForeground(Color.GRAY);
        detailLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(detailLabel);

        return panel;
    }

    /**
     * Create statistics panel
     */
    private JPanel createStatsPanel(PlayerScore playerScore, PlayerScore opponentScore, boolean isWinner) {
        JPanel panel = new JPanel(new GridLayout(2, 3, 20, 10));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                "Match Statistics",
                0, 0,
                new Font("Arial", Font.BOLD, 12),
                Color.WHITE
        ));

        // Calculate stats
        int totalDelivered = playerScore.delivered + opponentScore.delivered;
        int totalLost = playerScore.lost + opponentScore.lost;
        double efficiency = totalDelivered > 0 ?
                (double)playerScore.delivered / totalDelivered * 100 : 0;

        // Add stat labels
        addStatLabel(panel, "Total Packets", String.valueOf(totalDelivered + totalLost));
        addStatLabel(panel, "Your Efficiency", String.format("%.1f%%", efficiency));
        addStatLabel(panel, "Final Ammo", String.valueOf(playerScore.ammo));

        addStatLabel(panel, "Total Delivered", String.valueOf(totalDelivered));
        addStatLabel(panel, "Total Lost", String.valueOf(totalLost));
        addStatLabel(panel, "Match Result", isWinner ? "WIN" : "LOSS");

        return panel;
    }

    /**
     * Add a stat label to panel
     */
    private void addStatLabel(JPanel panel, String label, String value) {
        JPanel statPanel = new JPanel(new BorderLayout());
        statPanel.setOpaque(false);

        JLabel labelComp = new JLabel(label + ":", SwingConstants.LEFT);
        labelComp.setFont(new Font("Arial", Font.PLAIN, 11));
        labelComp.setForeground(Color.GRAY);

        JLabel valueComp = new JLabel(value, SwingConstants.RIGHT);
        valueComp.setFont(new Font("Arial", Font.BOLD, 12));
        valueComp.setForeground(Color.WHITE);

        statPanel.add(labelComp, BorderLayout.WEST);
        statPanel.add(valueComp, BorderLayout.EAST);

        panel.add(statPanel);
    }

    /**
     * Create styled button
     */
    private JButton createButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(120, 40));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Hover effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            Color original = color;
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(color.brighter());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(original);
            }
        });

        return button;
    }

    /**
     * Start XP gain animation
     */
    private void startXPAnimation() {
        animationTimer = new Timer(50, e -> {
            if (currentXP < targetXP) {
                currentXP += Math.max(1, targetXP / 30);
                if (currentXP > targetXP) {
                    currentXP = targetXP;
                }

                xpBar.setValue(currentXP);
                xpBar.setString(currentXP + " XP");
                xpEarnedLabel.setText("XP Earned: " + currentXP + " / " + targetXP);

                if (currentXP >= targetXP) {
                    ((Timer)e.getSource()).stop();

                    // Flash effect when complete
                    flashComponent(xpEarnedLabel, Color.YELLOW, Color.WHITE);
                }
            }
        });
        animationTimer.start();
    }

    /**
     * Start result animation
     */
    private void startResultAnimation(boolean isWinner) {
        // Pulsing effect for result label
        Timer pulseTimer = new Timer(1000, null);
        final float[] scale = {1.0f};
        final boolean[] growing = {true};

        pulseTimer.addActionListener(e -> {
            if (growing[0]) {
                scale[0] += 0.02f;
                if (scale[0] >= 1.1f) {
                    growing[0] = false;
                }
            } else {
                scale[0] -= 0.02f;
                if (scale[0] <= 1.0f) {
                    growing[0] = true;
                }
            }

            Font currentFont = resultLabel.getFont();
            int newSize = (int)(48 * scale[0]);
            resultLabel.setFont(currentFont.deriveFont((float)newSize));
        });

        pulseTimer.start();

        // Stop after 5 seconds
        Timer stopTimer = new Timer(5000, e -> pulseTimer.stop());
        stopTimer.setRepeats(false);
        stopTimer.start();
    }

    /**
     * Flash component color
     */
    private void flashComponent(JComponent component, Color from, Color to) {
        Timer flashTimer = new Timer(100, null);
        final int[] steps = {0};
        final int maxSteps = 5;

        flashTimer.addActionListener(e -> {
            steps[0]++;
            component.setForeground(steps[0] % 2 == 0 ? from : to);

            if (steps[0] >= maxSteps * 2) {
                ((Timer)e.getSource()).stop();
                component.setForeground(from);
            }
        });

        flashTimer.start();
    }

    // === Public methods for button listeners ===

    public void addRematchListener(ActionListener listener) {
        rematchButton.addActionListener(listener);
    }

    public void addMainMenuListener(ActionListener listener) {
        mainMenuButton.addActionListener(listener);
    }

    public void addViewStatsListener(ActionListener listener) {
        viewStatsButton.addActionListener(listener);
    }

    /**
     * Clean up animations
     */
    public void cleanup() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
    }
}
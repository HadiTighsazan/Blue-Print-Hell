package com.blueprinthell.view.pvp;

import com.blueprinthell.view.screens.GameScreenView;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.shared.protocol.NetworkProtocol.SystemState;
import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * رابط کاربری فاز Match برای PvP
 * نمایش امتیازات، Ammo و کنترل‌های Inject
 */
public class PvPMatchView extends JPanel {

    // UI Components
    private final JLabel playerScoreLabel;
    private final JLabel opponentScoreLabel;
    private final JLabel ammoLabel;
    private final JLabel speedMultiplierLabel;
    private final JPanel systemControlPanel;
    private final Map<String, SystemControlButton> systemButtons;
    private final JPanel cooldownPanel;

    // Game view reference
    private final GameScreenView gameView;

    // Callback for inject
    private Consumer<String> injectCallback;

    // State
    private int playerScore = 0;
    private int opponentScore = 0;
    private int currentAmmo = 3;

    /**
     * Constructor
     */
    public PvPMatchView(GameScreenView gameView) {
        this.gameView = gameView;
        this.systemButtons = new HashMap<>();

        setLayout(new BorderLayout());
        setBackground(new Color(20, 20, 20));
        setPreferredSize(new Dimension(1200, 100));

        // === Top Score Panel ===
        JPanel scorePanel = new JPanel(new GridLayout(1, 3, 20, 0));
        scorePanel.setOpaque(false);
        scorePanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        // Player score (left)
        JPanel playerPanel = createScorePanel("YOU", Color.BLUE);
        playerScoreLabel = (JLabel) playerPanel.getComponent(1);
        scorePanel.add(playerPanel);

        // Center info
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();

        // Ammo display
        JPanel ammoPanel = new JPanel(new FlowLayout());
        ammoPanel.setOpaque(false);

        JLabel ammoIcon = new JLabel("⚡");
        ammoIcon.setFont(new Font("Arial", Font.BOLD, 24));
        ammoIcon.setForeground(Color.YELLOW);
        ammoPanel.add(ammoIcon);

        ammoLabel = new JLabel("3");
        ammoLabel.setFont(new Font("Arial", Font.BOLD, 28));
        ammoLabel.setForeground(Color.WHITE);
        ammoPanel.add(ammoLabel);

        gbc.gridx = 0; gbc.gridy = 0;
        centerPanel.add(ammoPanel, gbc);

        // Speed multiplier
        speedMultiplierLabel = new JLabel("Speed: 1.0x");
        speedMultiplierLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        speedMultiplierLabel.setForeground(Color.GRAY);
        gbc.gridy = 1; gbc.insets = new Insets(5, 0, 0, 0);
        centerPanel.add(speedMultiplierLabel, gbc);

        scorePanel.add(centerPanel);

        // Opponent score (right)
        JPanel opponentPanel = createScorePanel("OPPONENT", Color.RED);
        opponentScoreLabel = (JLabel) opponentPanel.getComponent(1);
        scorePanel.add(opponentPanel);

        add(scorePanel, BorderLayout.NORTH);

        // === Center System Control Panel ===
        systemControlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        systemControlPanel.setOpaque(false);
        systemControlPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                "System Controls",
                0, 0,
                new Font("Arial", Font.BOLD, 12),
                Color.WHITE
        ));

        add(systemControlPanel, BorderLayout.CENTER);

        // === Bottom Cooldown Panel ===
        cooldownPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        cooldownPanel.setOpaque(false);
        cooldownPanel.setPreferredSize(new Dimension(0, 30));

        add(cooldownPanel, BorderLayout.SOUTH);

        // Initialize system buttons (will be populated when systems are detected)
        SwingUtilities.invokeLater(this::detectAndCreateSystemButtons);
    }

    /**
     * Create score panel for a player
     */
    private JPanel createScorePanel(String label, Color color) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createLineBorder(color, 2));

        JLabel nameLabel = new JLabel(label, SwingConstants.CENTER);
        nameLabel.setFont(new Font("Arial", Font.BOLD, 14));
        nameLabel.setForeground(color);
        panel.add(nameLabel, BorderLayout.NORTH);

        JLabel scoreLabel = new JLabel("0", SwingConstants.CENTER);
        scoreLabel.setFont(new Font("Arial", Font.BOLD, 32));
        scoreLabel.setForeground(Color.WHITE);
        panel.add(scoreLabel, BorderLayout.CENTER);

        JLabel detailLabel = new JLabel("D: 0 | L: 0", SwingConstants.CENTER);
        detailLabel.setFont(new Font("Arial", Font.PLAIN, 10));
        detailLabel.setForeground(Color.GRAY);
        panel.add(detailLabel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Detect controllable systems and create buttons
     */
    private void detectAndCreateSystemButtons() {
        if (gameView == null) return;

        // Find all system boxes in the game
        for (Component comp : gameView.getGameArea().getComponents()) {
            if (comp instanceof com.blueprinthell.view.SystemBoxView boxView) {
                SystemBoxModel model = boxView.getModel();

                // Only add non-source, non-sink systems
                if (!model.getInPorts().isEmpty() && !model.getOutPorts().isEmpty()) {
                    createSystemButton(model);
                }
            }
        }
    }

    /**
     * Create button for a system
     */
    private void createSystemButton(SystemBoxModel system) {
        String systemId = system.getId();

        SystemControlButton button = new SystemControlButton(systemId, system.getPrimaryKind().toString());
        button.addActionListener(e -> onInjectClicked(systemId));

        systemButtons.put(systemId, button);
        systemControlPanel.add(button);

        systemControlPanel.revalidate();
        systemControlPanel.repaint();
    }

    /**
     * Handle inject button click
     */
    private void onInjectClicked(String systemId) {
        SystemControlButton button = systemButtons.get(systemId);

        // Check ammo
        if (currentAmmo <= 0) {
            showMessage("No ammo!", Color.RED);
            return;
        }

        // Check if button is on cooldown
        if (button != null && button.isOnCooldown()) {
            showMessage("System on cooldown!", Color.ORANGE);
            return;
        }

        // Send inject command
        if (injectCallback != null) {
            injectCallback.accept(systemId);
            // Visual feedback will now be handled by updateSystemCooldowns
        }
    }

    /**
     * Update scores display
     */
    public void updateScores(int playerDelivered, int playerLost,
                             int opponentDelivered, int opponentLost) {
        // Calculate scores
        playerScore = playerDelivered - (int)(playerLost * 1.5);
        opponentScore = opponentDelivered - (int)(opponentLost * 1.5);

        // Update player display
        playerScoreLabel.setText(String.valueOf(playerScore));
        JPanel playerPanel = (JPanel) playerScoreLabel.getParent();
        JLabel playerDetail = (JLabel) playerPanel.getComponent(2);
        playerDetail.setText("D: " + playerDelivered + " | L: " + playerLost);

        // Update opponent display
        opponentScoreLabel.setText(String.valueOf(opponentScore));
        JPanel opponentPanel = (JPanel) opponentScoreLabel.getParent();
        JLabel opponentDetail = (JLabel) opponentPanel.getComponent(2);
        opponentDetail.setText("D: " + opponentDelivered + " | L: " + opponentLost);

        // Highlight leader
        if (playerScore > opponentScore) {
            playerScoreLabel.setForeground(Color.GREEN);
            opponentScoreLabel.setForeground(Color.WHITE);
        } else if (opponentScore > playerScore) {
            playerScoreLabel.setForeground(Color.WHITE);
            opponentScoreLabel.setForeground(Color.GREEN);
        } else {
            playerScoreLabel.setForeground(Color.WHITE);
            opponentScoreLabel.setForeground(Color.WHITE);
        }
    }

    /**
     * Update ammo display
     */
    public void updateAmmo(int ammo) {
        currentAmmo = ammo;
        ammoLabel.setText(String.valueOf(ammo));

        // Change color based on ammo level
        if (ammo == 0) {
            ammoLabel.setForeground(Color.RED);
        } else if (ammo <= 2) {
            ammoLabel.setForeground(Color.ORANGE);
        } else {
            ammoLabel.setForeground(Color.WHITE);
        }
    }

    /**
     * Update speed multiplier display
     */
    public void updateSpeedMultiplier(double multiplier) {
        speedMultiplierLabel.setText(String.format("Speed: %.2fx", multiplier));

        if (multiplier > 1.5) {
            speedMultiplierLabel.setForeground(Color.RED);
        } else if (multiplier > 1.0) {
            speedMultiplierLabel.setForeground(Color.ORANGE);
        } else {
            speedMultiplierLabel.setForeground(Color.GRAY);
        }
    }

    /**
     * به‌روزرسانی وضعیت cooldown تمام دکمه‌های سیستم.
     * @param systemStates لیست وضعیت‌ها از سرور
     * @param playerSide شناسه بازیکن فعلی (1 یا 2)
     */
    public void updateSystemCooldowns(List<SystemState> systemStates, int playerSide) {
        for (SystemState state : systemStates) {
            SystemControlButton button = systemButtons.get(state.id);
            if (button != null) {
                int playerCooldown = (playerSide == 1) ? state.packetCooldownMsP1 : state.packetCooldownMsP2;
                int systemCooldown = state.systemCooldownMs;
                // بیشترین زمان cooldown را برای نمایش انتخاب می‌کنیم
                button.updateCooldown(Math.max(playerCooldown, systemCooldown));
            }
        }
    }

    /**
     * Show temporary message
     */
    private void showMessage(String message, Color color) {
        JLabel msgLabel = new JLabel(message);
        msgLabel.setFont(new Font("Arial", Font.BOLD, 14));
        msgLabel.setForeground(color);

        cooldownPanel.removeAll();
        cooldownPanel.add(msgLabel);
        cooldownPanel.revalidate();
        cooldownPanel.repaint();

        // Remove after 2 seconds
        Timer timer = new Timer(2000, e -> {
            cooldownPanel.removeAll();
            cooldownPanel.revalidate();
            cooldownPanel.repaint();
        });
        timer.setRepeats(false);
        timer.start();
    }

    /**
     * Set inject callback
     */
    public void setInjectCallback(Consumer<String> callback) {
        this.injectCallback = callback;
    }

    /**
     * Custom button for system control
     */
    private static class SystemControlButton extends JButton {
        private final String systemId;
        private final String systemType; // ذخیره نوع سیستم برای بازیابی متن
        private boolean onCooldown = false;
        private Timer cooldownTimer;
        private int remainingMs = 0;

        SystemControlButton(String systemId, String systemType) {
            // ### START OF FIX ###
            super(getButtonLabel(systemType)); // فراخوانی متد استاتیک
            // ### END OF FIX ###
            this.systemId = systemId;
            this.systemType = systemType; // ذخیره نوع

            setFont(new Font("Arial", Font.BOLD, 12));
            setBackground(new Color(60, 60, 60));
            setForeground(Color.WHITE);
            setFocusPainted(false);
            setBorderPainted(true);
            setPreferredSize(new Dimension(80, 40));

            // Hover effect
            addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseEntered(java.awt.event.MouseEvent evt) {
                    if (!onCooldown) {
                        setBackground(new Color(80, 80, 80));
                    }
                }
                public void mouseExited(java.awt.event.MouseEvent evt) {
                    if (!onCooldown) {
                        setBackground(new Color(60, 60, 60));
                    }
                }
            });
        }

        void updateCooldown(int newRemainingMs) {
            this.remainingMs = newRemainingMs;
            boolean shouldBeOnCooldown = newRemainingMs > 0;

            if (shouldBeOnCooldown && !onCooldown) {
                // شروع Cooldown
                onCooldown = true;
                setEnabled(false);
                startTimer();
            } else if (!shouldBeOnCooldown && onCooldown) {
                // پایان Cooldown
                endCooldown();
            }
        }

        private void startTimer() {
            if (cooldownTimer != null && cooldownTimer.isRunning()) {
                cooldownTimer.stop();
            }
            cooldownTimer = new Timer(100, e -> {
                remainingMs -= 100;
                if (remainingMs <= 0) {
                    endCooldown();
                } else {
                    // به‌روزرسانی ظاهر دکمه
                    setText(String.format("%.1fs", remainingMs / 1000.0));
                    setBackground(new Color(40, 40, 40));
                }
            });
            cooldownTimer.start();
        }

        void endCooldown() {
            onCooldown = false;
            remainingMs = 0;
            if (cooldownTimer != null) {
                cooldownTimer.stop();
            }
            setEnabled(true);
            setText(getButtonLabel(this.systemType)); // بازیابی متن اصلی
            setBackground(new Color(60, 60, 60));
        }

        boolean isOnCooldown() {
            return onCooldown;
        }

        // ### START OF FIX ###
        /**
         * متد استاتیک برای دریافت لیبل دکمه بر اساس نوع سیستم.
         */
        private static String getButtonLabel(String systemType) {
            return switch (systemType) {
                case "VPN" -> "VPN";
                case "DISTRIBUTOR" -> "DIST";
                case "MERGER" -> "MERG";
                case "SPY" -> "SPY";
                case "MALICIOUS" -> "MAL";
                case "ANTI_TROJAN" -> "A-TRJ";
                case "PORT_RANDOMIZER" -> "RND";
                default -> "SYS";
            };
        }
        // ### END OF FIX ###
    }
}
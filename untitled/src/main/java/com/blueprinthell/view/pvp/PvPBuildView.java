package com.blueprinthell.view.pvp;

import com.blueprinthell.controller.GameController;
import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * رابط کاربری فاز Build برای PvP
 */
public class PvPBuildView extends JPanel {

    // Controllers
    private final GameController gameController;

    // UI Components
    private final JLabel timerLabel;
    private final JLabel opponentLabel;
    private final JLabel statusLabel;
    private final JButton readyButton;
    private final JButton extendButton;
    private final JProgressBar timerBar;
    private final JPanel penaltyPanel;
    private final JLabel penaltyLabel;

    // Build area (reuse game view)
    private final JPanel buildArea;

    // State
    private boolean isReady = false;
    private int remainingTime = 30;
    private int extendCount = 0;

    // Callbacks
    private Consumer<Boolean> readyCallback;
    private Runnable extendCallback;

    /**
     * Constructor
     */
    public PvPBuildView(GameController gameController, String opponentName, int playerSide) {
        this.gameController = gameController;

        setLayout(new BorderLayout());
        setBackground(new Color(30, 30, 30));

        // === Top Panel - Match Info ===
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(40, 40, 40));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Opponent info
        opponentLabel = new JLabel("vs " + opponentName);
        opponentLabel.setFont(new Font("Arial", Font.BOLD, 18));
        opponentLabel.setForeground(Color.WHITE);
        topPanel.add(opponentLabel, BorderLayout.WEST);

        // Timer
        JPanel timerPanel = new JPanel(new GridBagLayout());
        timerPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();

        timerLabel = new JLabel("30");
        timerLabel.setFont(new Font("Arial", Font.BOLD, 36));
        timerLabel.setForeground(Color.CYAN);

        gbc.gridx = 0; gbc.gridy = 0;
        timerPanel.add(timerLabel, gbc);

        timerBar = new JProgressBar(0, 30);
        timerBar.setValue(30);
        timerBar.setPreferredSize(new Dimension(200, 20));
        timerBar.setForeground(Color.CYAN);

        gbc.gridy = 1; gbc.insets = new Insets(5, 0, 0, 0);
        timerPanel.add(timerBar, gbc);

        topPanel.add(timerPanel, BorderLayout.CENTER);

        // Player side indicator
        JLabel sideLabel = new JLabel("Player " + playerSide);
        sideLabel.setFont(new Font("Arial", Font.BOLD, 16));
        sideLabel.setForeground(playerSide == 1 ? Color.BLUE : Color.RED);
        topPanel.add(sideLabel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        // === Center - Build Area ===
        buildArea = new JPanel(new BorderLayout());
        buildArea.setBackground(new Color(50, 50, 50));
        buildArea.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));

        // Add game view here
        if (gameController != null && gameController.getGameView() != null) {
            buildArea.add(gameController.getGameView(), BorderLayout.CENTER);
        }

        add(buildArea, BorderLayout.CENTER);

        // === Bottom Panel - Controls ===
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(new Color(40, 40, 40));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Status
        statusLabel = new JLabel("Build your network...");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        statusLabel.setForeground(Color.LIGHT_GRAY);
        bottomPanel.add(statusLabel, BorderLayout.WEST);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setOpaque(false);

        extendButton = new JButton("Extend Time");
        extendButton.setFont(new Font("Arial", Font.BOLD, 14));
        extendButton.setBackground(new Color(100, 100, 50));
        extendButton.setForeground(Color.WHITE);
        extendButton.setFocusPainted(false);
        extendButton.addActionListener(e -> onExtendClicked());
        buttonPanel.add(extendButton);

        readyButton = new JButton("Ready");
        readyButton.setFont(new Font("Arial", Font.BOLD, 16));
        readyButton.setBackground(new Color(50, 150, 50));
        readyButton.setForeground(Color.WHITE);
        readyButton.setFocusPainted(false);
        readyButton.addActionListener(e -> onReadyClicked());
        buttonPanel.add(readyButton);

        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);

        // === Penalty Panel (hidden by default) ===
        penaltyPanel = new JPanel();
        penaltyPanel.setBackground(new Color(100, 50, 50));
        penaltyPanel.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
        penaltyPanel.setVisible(false);

        penaltyLabel = new JLabel();
        penaltyLabel.setFont(new Font("Arial", Font.BOLD, 14));
        penaltyLabel.setForeground(Color.YELLOW);
        penaltyPanel.add(penaltyLabel);

        add(penaltyPanel, BorderLayout.EAST);
    }

    /**
     * Update timer display
     */
    public void updateTimer(int seconds) {
        remainingTime = seconds;
        timerLabel.setText(String.valueOf(seconds));
        timerBar.setValue(seconds);

        // Change color based on time
        if (seconds <= 5) {
            timerLabel.setForeground(Color.RED);
            timerBar.setForeground(Color.RED);
        } else if (seconds <= 10) {
            timerLabel.setForeground(Color.ORANGE);
            timerBar.setForeground(Color.ORANGE);
        }
    }

    /**
     * Update ready status
     */
    public void updateReadyStatus(boolean selfReady, boolean opponentReady) {
        if (selfReady && opponentReady) {
            statusLabel.setText("Both players ready! Starting soon...");
            statusLabel.setForeground(Color.GREEN);
        } else if (selfReady) {
            statusLabel.setText("Waiting for opponent...");
            statusLabel.setForeground(Color.YELLOW);
        } else if (opponentReady) {
            statusLabel.setText("Opponent is ready!");
            statusLabel.setForeground(Color.ORANGE);
        }
    }

    /**
     * Show penalty notification
     */
    public void showPenalty(String penaltyType) {
        penaltyPanel.setVisible(true);

        String message = switch (penaltyType) {
            case "PENIA" -> "Wrath of Penia: Random ammo to opponent!";
            case "AERGIA" -> "Wrath of Aergia: Cooldowns increasing!";
            case "PENIA_SPEED" -> "Wrath of Penia: Speed increasing!";
            default -> "Unknown penalty active";
        };

        penaltyLabel.setText(message);

        // Flash effect
        Timer flashTimer = new Timer(500, null);
        flashTimer.addActionListener(e -> {
            penaltyPanel.setBackground(
                    penaltyPanel.getBackground().equals(Color.RED)
                            ? new Color(100, 50, 50)
                            : Color.RED
            );
        });
        flashTimer.start();

        // Stop after 3 seconds
        Timer stopTimer = new Timer(3000, e -> {
            flashTimer.stop();
            penaltyPanel.setBackground(new Color(100, 50, 50));
        });
        stopTimer.setRepeats(false);
        stopTimer.start();
    }

    /**
     * Show extend granted message
     */
    public void showExtendGranted(int stage, String penaltyType) {
        extendCount = stage;

        String message = String.format(
                "Extension %d/3 granted! +10 seconds\nPenalty: %s",
                stage, getPenaltyName(penaltyType)
        );

        JOptionPane.showMessageDialog(this, message, "Time Extended",
                JOptionPane.INFORMATION_MESSAGE);

        // Update extend button
        if (extendCount >= 3) {
            extendButton.setEnabled(false);
            extendButton.setText("Max Extends");
        } else {
            extendButton.setText("Extend (" + (3 - extendCount) + " left)");
        }
    }

    /**
     * Update queue status (for initial queue)
     */
    public void updateQueueStatus(int position, int estimatedWait) {
        statusLabel.setText("Queue position: " + position +
                " (est. " + estimatedWait + " seconds)");
    }

    // === Event Handlers ===

    private void onReadyClicked() {
        isReady = !isReady;

        if (isReady) {
            readyButton.setText("Not Ready");
            readyButton.setBackground(new Color(150, 50, 50));

            // Lock network editing
            gameController.getGameView().setEnabled(false);
        } else {
            readyButton.setText("Ready");
            readyButton.setBackground(new Color(50, 150, 50));

            // Unlock network editing
            gameController.getGameView().setEnabled(true);
        }

        if (readyCallback != null) {
            readyCallback.accept(isReady);
        }
    }

    private void onExtendClicked() {
        if (extendCount >= 3) return;

        int result = JOptionPane.showConfirmDialog(this,
                "Request time extension?\nThis will apply a penalty!",
                "Extend Time",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION && extendCallback != null) {
            extendCallback.run();
        }
    }

    private String getPenaltyName(String type) {
        return switch (type) {
            case "PENIA" -> "Wrath of Penia (Ammo)";
            case "AERGIA" -> "Wrath of Aergia (Cooldowns)";
            case "PENIA_SPEED" -> "Wrath of Penia (Speed)";
            default -> type;
        };
    }

    // === Setters for callbacks ===

    public void setReadyCallback(Consumer<Boolean> callback) {
        this.readyCallback = callback;
    }

    public void setExtendCallback(Runnable callback) {
        this.extendCallback = callback;
    }
}
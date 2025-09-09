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
    private int currentAmmo = 3;

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

        JPanel playerPanel = createScorePanel("YOU", Color.BLUE);
        playerScoreLabel = (JLabel) playerPanel.getComponent(1);
        scorePanel.add(playerPanel);

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();

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

        speedMultiplierLabel = new JLabel("Speed: 1.0x");
        speedMultiplierLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        speedMultiplierLabel.setForeground(Color.GRAY);
        gbc.gridy = 1; gbc.insets = new Insets(5, 0, 0, 0);
        centerPanel.add(speedMultiplierLabel, gbc);
        scorePanel.add(centerPanel);

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

        SwingUtilities.invokeLater(this::detectAndCreateSystemButtons);
    }

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

    private void detectAndCreateSystemButtons() {
        if (gameView == null) return;
        for (Component comp : gameView.getGameArea().getComponents()) {
            if (comp instanceof com.blueprinthell.view.SystemBoxView boxView) {
                SystemBoxModel model = boxView.getModel();
                if (!model.getInPorts().isEmpty() && !model.getOutPorts().isEmpty()) {
                    createSystemButton(model);
                }
            }
        }
    }

    private void createSystemButton(SystemBoxModel system) {
        String systemId = system.getId();
        SystemControlButton button = new SystemControlButton(systemId, system.getPrimaryKind().toString());
        button.addActionListener(e -> onInjectClicked(systemId));
        systemButtons.put(systemId, button);
        systemControlPanel.add(button);
        systemControlPanel.revalidate();
        systemControlPanel.repaint();
    }

    private void onInjectClicked(String systemId) {
        SystemControlButton button = systemButtons.get(systemId);
        if (currentAmmo <= 0) {
            showMessage("No ammo!", Color.RED);
            return;
        }
        if (button != null && button.isOnCooldown()) {
            showMessage("System on cooldown!", Color.ORANGE);
            return;
        }
        if (injectCallback != null) {
            injectCallback.accept(systemId);
        }
    }

    public void updateScores(int playerDelivered, int playerLost, int opponentDelivered, int opponentLost) {
        int playerScore = playerDelivered - (int)(playerLost * 1.5);
        int opponentScore = opponentDelivered - (int)(opponentLost * 1.5);

        playerScoreLabel.setText(String.valueOf(playerScore));
        ((JLabel)((JPanel)playerScoreLabel.getParent()).getComponent(2)).setText("D: " + playerDelivered + " | L: " + playerLost);

        opponentScoreLabel.setText(String.valueOf(opponentScore));
        ((JLabel)((JPanel)opponentScoreLabel.getParent()).getComponent(2)).setText("D: " + opponentDelivered + " | L: " + opponentLost);

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

    public void updateAmmo(int ammo) {
        currentAmmo = ammo;
        ammoLabel.setText(String.valueOf(ammo));
        if (ammo == 0) ammoLabel.setForeground(Color.RED);
        else if (ammo <= 2) ammoLabel.setForeground(Color.ORANGE);
        else ammoLabel.setForeground(Color.WHITE);
    }

    public void updateSpeedMultiplier(double multiplier) {
        speedMultiplierLabel.setText(String.format("Speed: %.2fx", multiplier));
        if (multiplier > 1.5) speedMultiplierLabel.setForeground(Color.RED);
        else if (multiplier > 1.0) speedMultiplierLabel.setForeground(Color.ORANGE);
        else speedMultiplierLabel.setForeground(Color.GRAY);
    }

    public void updateSystemCooldowns(List<SystemState> systemStates, int playerSide) {
        for (SystemState state : systemStates) {
            SystemControlButton button = systemButtons.get(state.id);
            if (button != null) {
                int playerCooldown = (playerSide == 1) ? state.packetCooldownMsP1 : state.packetCooldownMsP2;
                int systemCooldown = state.systemCooldownMs;
                button.updateCooldown(Math.max(playerCooldown, systemCooldown));
            }
        }
    }

    private void showMessage(String message, Color color) {
        JLabel msgLabel = new JLabel(message);
        msgLabel.setFont(new Font("Arial", Font.BOLD, 14));
        msgLabel.setForeground(color);
        cooldownPanel.removeAll();
        cooldownPanel.add(msgLabel);
        cooldownPanel.revalidate();
        cooldownPanel.repaint();
        Timer timer = new Timer(2000, e -> {
            cooldownPanel.removeAll();
            cooldownPanel.revalidate();
            cooldownPanel.repaint();
        });
        timer.setRepeats(false);
        timer.start();
    }

    public void setInjectCallback(Consumer<String> callback) {
        this.injectCallback = callback;
    }
}
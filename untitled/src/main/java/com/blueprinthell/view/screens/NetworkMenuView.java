package com.blueprinthell.view.screens;

import com.blueprinthell.client.network.ConnectionManager;
import com.blueprinthell.client.network.ConnectionManager.ConnectionState;
import com.blueprinthell.shared.protocol.NetworkProtocol.*;

import javax.swing.*;
import java.awt.*;

/**
 * پنل مدیریت اتصال شبکه در منوی اصلی
 */
public class NetworkMenuView extends JPanel {

    // Connection status
    private final JLabel statusLabel;
    private final JLabel statusIcon;
    private final JLabel userIdLabel;
    private final JLabel offlineQueueLabel;

    // Connection controls
    private final JTextField serverField;
    private final JTextField portField;
    private final JButton connectButton;
    private final JButton disconnectButton;
    private final JButton syncButton;

    // Mode selection
    private final JRadioButton onlineMode;
    private final JRadioButton offlineMode;
    private final ButtonGroup modeGroup;

    // Profile display
    private final JLabel xpLabel;
    private final JLabel levelLabel;
    private final JButton profileButton;

    private final ConnectionManager connectionManager;

    public NetworkMenuView(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;

        setLayout(new BorderLayout(10, 10));
        setPreferredSize(new Dimension(350, 400));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.DARK_GRAY, 2),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        setBackground(new Color(30, 30, 30));

        // === Status Panel ===
        JPanel statusPanel = new JPanel(new GridBagLayout());
        statusPanel.setOpaque(false);
        statusPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                "Connection Status",
                0, 0,
                new Font("Dialog", Font.BOLD, 14),
                Color.WHITE
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Status icon and label
        statusIcon = new JLabel("●");
        statusIcon.setFont(new Font("Dialog", Font.BOLD, 20));
        statusLabel = new JLabel("Disconnected");
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setFont(new Font("Dialog", Font.BOLD, 12));

        gbc.gridx = 0; gbc.gridy = 0;
        statusPanel.add(statusIcon, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        statusPanel.add(statusLabel, gbc);

        // User ID
        String userId = connectionManager.getUserId();
        String displayId = userId.length() > 16
                ? userId.substring(0, 16) + "..."
                : userId;
        userIdLabel = new JLabel("ID: " + displayId);
        userIdLabel.setForeground(Color.LIGHT_GRAY);
        userIdLabel.setFont(new Font("Monospaced", Font.PLAIN, 10));

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        statusPanel.add(userIdLabel, gbc);

        // Offline queue
        offlineQueueLabel = new JLabel("Offline Queue: 0 results");
        offlineQueueLabel.setForeground(Color.YELLOW);
        gbc.gridy = 2;
        statusPanel.add(offlineQueueLabel, gbc);

        add(statusPanel, BorderLayout.NORTH);

        // === Connection Panel ===
        JPanel connectionPanel = new JPanel(new GridBagLayout());
        connectionPanel.setOpaque(false);
        connectionPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                "Server Connection",
                0, 0,
                new Font("Dialog", Font.BOLD, 14),
                Color.WHITE
        ));

        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Server field
        JLabel serverLabel = new JLabel("Server:");
        serverLabel.setForeground(Color.WHITE);
        serverField = new JTextField("localhost", 15);
        serverField.setBackground(new Color(50, 50, 50));
        serverField.setForeground(Color.WHITE);
        serverField.setCaretColor(Color.WHITE);

        gbc.gridx = 0; gbc.gridy = 0;
        connectionPanel.add(serverLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        connectionPanel.add(serverField, gbc);

        // Port field
        JLabel portLabel = new JLabel("Port:");
        portLabel.setForeground(Color.WHITE);
        portField = new JTextField("7777", 15);
        portField.setBackground(new Color(50, 50, 50));
        portField.setForeground(Color.WHITE);
        portField.setCaretColor(Color.WHITE);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        connectionPanel.add(portLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        connectionPanel.add(portField, gbc);

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        buttonPanel.setOpaque(false);

        connectButton = createButton("Connect", new Color(50, 120, 50));
        disconnectButton = createButton("Disconnect", new Color(120, 50, 50));
        syncButton = createButton("Sync Queue", new Color(50, 50, 120));

        buttonPanel.add(connectButton);
        buttonPanel.add(disconnectButton);
        buttonPanel.add(syncButton);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        connectionPanel.add(buttonPanel, gbc);

        add(connectionPanel, BorderLayout.CENTER);

        // === Mode & Profile Panel ===
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setOpaque(false);

        // Mode selection
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        modePanel.setOpaque(false);
        modePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                "Game Mode",
                0, 0,
                new Font("Dialog", Font.BOLD, 12),
                Color.WHITE
        ));

        onlineMode = new JRadioButton("Online");
        onlineMode.setForeground(Color.WHITE);
        onlineMode.setOpaque(false);

        offlineMode = new JRadioButton("Offline");
        offlineMode.setForeground(Color.WHITE);
        offlineMode.setOpaque(false);
        offlineMode.setSelected(true);

        modeGroup = new ButtonGroup();
        modeGroup.add(onlineMode);
        modeGroup.add(offlineMode);

        modePanel.add(offlineMode);
        modePanel.add(onlineMode);

        bottomPanel.add(modePanel, BorderLayout.NORTH);

        // Profile info
        JPanel profilePanel = new JPanel(new GridBagLayout());
        profilePanel.setOpaque(false);
        profilePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                "Profile",
                0, 0,
                new Font("Dialog", Font.BOLD, 12),
                Color.WHITE
        ));

        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 5, 2, 5);

        xpLabel = new JLabel("Total XP: 0");
        xpLabel.setForeground(Color.CYAN);
        levelLabel = new JLabel("Best Level: -");
        levelLabel.setForeground(Color.GREEN);

        gbc.gridx = 0; gbc.gridy = 0;
        profilePanel.add(xpLabel, gbc);
        gbc.gridy = 1;
        profilePanel.add(levelLabel, gbc);

        profileButton = createButton("View Profile", new Color(80, 80, 40));
        gbc.gridy = 2; gbc.insets = new Insets(5, 5, 5, 5);
        profilePanel.add(profileButton, gbc);

        bottomPanel.add(profilePanel, BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);

        // Setup callbacks
        setupCallbacks();

        // Initial state
        updateConnectionState(ConnectionState.DISCONNECTED);
        updateOfflineQueue();
    }

    private JButton createButton(String text, Color bg) {
        JButton button = new JButton(text);
        button.setBackground(bg);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setFont(new Font("Dialog", Font.BOLD, 11));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Hover effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(bg.brighter());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(bg);
            }
        });

        return button;
    }

    private void setupCallbacks() {
        // Connection state changes
        connectionManager.setStateChangeCallback(this::updateConnectionState);

        // Error messages
        connectionManager.setErrorCallback(error -> {
            JOptionPane.showMessageDialog(this, error, "Connection Error",
                    JOptionPane.ERROR_MESSAGE);
        });

        // Profile updates
        connectionManager.registerHandler(MessageType.PROFILE, msg -> {
            if (msg instanceof Profile profile) {
                updateProfile(profile);
            }
        });

        // Connect button
        connectButton.addActionListener(e -> {
            String host = serverField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());

            connectionManager.connect(host, port).thenAccept(success -> {
                if (success) {
                    // Request profile after connection
                    connectionManager.requestProfile(connectionManager.getUserId());
                }
            });
        });

        // Disconnect button
        disconnectButton.addActionListener(e -> {
            connectionManager.disconnect();
        });

        // Sync button
        syncButton.addActionListener(e -> {
            syncButton.setEnabled(false);
            connectionManager.syncOfflineQueue().thenAccept(count -> {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Synced " + count + " results to server",
                            "Sync Complete",
                            JOptionPane.INFORMATION_MESSAGE);
                    updateOfflineQueue();
                    syncButton.setEnabled(true);
                });
            });
        });

        // Profile button
        profileButton.addActionListener(e -> {
            if (connectionManager.getState() == ConnectionState.CONNECTED) {
                connectionManager.requestProfile(connectionManager.getUserId());
            } else {
                JOptionPane.showMessageDialog(this,
                        "Connect to server to view profile",
                        "Offline",
                        JOptionPane.WARNING_MESSAGE);
            }
        });
    }

    private void updateConnectionState(ConnectionState state) {
        SwingUtilities.invokeLater(() -> {
            switch (state) {
                case DISCONNECTED -> {
                    statusIcon.setForeground(Color.RED);
                    statusLabel.setText("Disconnected");
                    connectButton.setEnabled(true);
                    disconnectButton.setEnabled(false);
                    syncButton.setEnabled(false);
                    onlineMode.setEnabled(false);
                    if (onlineMode.isSelected()) {
                        offlineMode.setSelected(true);
                    }
                }
                case CONNECTING -> {
                    statusIcon.setForeground(Color.YELLOW);
                    statusLabel.setText("Connecting...");
                    connectButton.setEnabled(false);
                    disconnectButton.setEnabled(false);
                    syncButton.setEnabled(false);
                }
                case CONNECTED -> {
                    statusIcon.setForeground(Color.GREEN);
                    statusLabel.setText("Connected");
                    connectButton.setEnabled(false);
                    disconnectButton.setEnabled(true);
                    syncButton.setEnabled(connectionManager.getOfflineQueueSize() > 0);
                    onlineMode.setEnabled(true);
                }
                case ERROR -> {
                    statusIcon.setForeground(Color.ORANGE);
                    statusLabel.setText("Connection Error");
                    connectButton.setEnabled(true);
                    disconnectButton.setEnabled(false);
                    syncButton.setEnabled(false);
                    onlineMode.setEnabled(false);
                }
            }
        });
    }

    private void updateOfflineQueue() {
        int queueSize = connectionManager.getOfflineQueueSize();
        offlineQueueLabel.setText("Offline Queue: " + queueSize + " results");
        offlineQueueLabel.setForeground(queueSize > 0 ? Color.YELLOW : Color.GREEN);

        if (connectionManager.getState() == ConnectionState.CONNECTED) {
            syncButton.setEnabled(queueSize > 0);
        }
    }

    private void updateProfile(Profile profile) {
        SwingUtilities.invokeLater(() -> {
            xpLabel.setText("Total XP: " + profile.xpTotal);

            // Find best level from history
            if (profile.history != null && profile.history.length > 0) {
                int maxLevel = 0;
                for (GameResult result : profile.history) {
                    maxLevel = Math.max(maxLevel, result.level);
                }
                levelLabel.setText("Best Level: " + maxLevel);
            }

            // Show profile dialog
            showProfileDialog(profile);
        });
    }

    private void showProfileDialog(Profile profile) {
        String info = String.format(
                "User: %s\n" +
                        "Total XP: %d\n" +
                        "Games Played: %d\n" +
                        "Active Perk: %s\n\n" +
                        "XP by Mode:\n%s",
                profile.username,
                profile.xpTotal,
                profile.history != null ? profile.history.length : 0,
                profile.activePerk,
                formatXpByMode(profile.xpByMode)
        );

        JOptionPane.showMessageDialog(this, info, "Player Profile",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private String formatXpByMode(java.util.Map<String, Integer> xpByMode) {
        if (xpByMode == null || xpByMode.isEmpty()) {
            return "  No data";
        }

        StringBuilder sb = new StringBuilder();
        xpByMode.forEach((mode, xp) -> {
            sb.append("  ").append(mode).append(": ").append(xp).append("\n");
        });
        return sb.toString();
    }

    public GameMode getSelectedMode() {
        return onlineMode.isSelected() ? GameMode.SOLO_ONLINE : GameMode.SOLO_OFFLINE;
    }

    public boolean isConnected() {
        return connectionManager.getState() == ConnectionState.CONNECTED;
    }
}
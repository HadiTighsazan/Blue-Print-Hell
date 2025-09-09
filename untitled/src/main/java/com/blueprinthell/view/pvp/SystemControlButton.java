package com.blueprinthell.view.pvp;

import javax.swing.*;
import java.awt.*;

public class SystemControlButton extends JButton {
    private final String systemId;
    private final String systemType;
    private boolean onCooldown = false;
    private Timer cooldownTimer;
    private int remainingMs = 0;

    public SystemControlButton(String systemId, String systemType) {
        super(getButtonLabel(systemType));
        this.systemId = systemId;
        this.systemType = systemType;

        setFont(new Font("Arial", Font.BOLD, 12));
        setBackground(new Color(60, 60, 60));
        setForeground(Color.WHITE);
        setFocusPainted(false);
        setBorderPainted(true);
        setPreferredSize(new Dimension(80, 40));

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

    public void updateCooldown(int newRemainingMs) {
        this.remainingMs = newRemainingMs;
        boolean shouldBeOnCooldown = newRemainingMs > 0;

        if (shouldBeOnCooldown && !onCooldown) {
            onCooldown = true;
            setEnabled(false);
            startTimer();
        } else if (!shouldBeOnCooldown && onCooldown) {
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
                setText(String.format("%.1fs", remainingMs / 1000.0));
                setBackground(new Color(40, 40, 40));
            }
        });
        cooldownTimer.start();
    }

    private void endCooldown() {
        onCooldown = false;
        remainingMs = 0;
        if (cooldownTimer != null) {
            cooldownTimer.stop();
        }
        setEnabled(true);
        setText(getButtonLabel(this.systemType));
        setBackground(new Color(60, 60, 60));
    }

    public boolean isOnCooldown() {
        return onCooldown;
    }

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
}
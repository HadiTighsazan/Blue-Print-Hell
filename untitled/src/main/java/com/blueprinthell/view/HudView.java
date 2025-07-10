package com.blueprinthell.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * HUD view for displaying game information such as score, wire length,
 * temporal progress, packet loss, and coins.
 */
public class HudView extends JPanel {
    private final JLabel scoreLabel;
    private final JLabel wireLengthLabel;
    private final JLabel timeLabel;
    private final JLabel packetLossLabel;
    private final JLabel coinsLabel;
    private final JButton startButton;

    public HudView(int x, int y, int width, int height) {
        setLayout(new FlowLayout(FlowLayout.LEFT));
        setOpaque(true);
        setBackground(Color.BLACK);
        setBounds(x, y, width, height);

        Font boldFont = new JLabel().getFont().deriveFont(Font.BOLD, 16f);

        scoreLabel = new JLabel("Score: 0");
        scoreLabel.setFont(boldFont);
        scoreLabel.setForeground(Color.WHITE);
        add(scoreLabel);

        wireLengthLabel = new JLabel("Wire Left: 0");
        wireLengthLabel.setFont(boldFont);
        wireLengthLabel.setForeground(Color.WHITE);
        add(wireLengthLabel);

        timeLabel = new JLabel("Time: 0");
        timeLabel.setFont(boldFont);
        timeLabel.setForeground(Color.WHITE);
        add(timeLabel);

        packetLossLabel = new JLabel("Loss: 0");
        packetLossLabel.setFont(boldFont);
        packetLossLabel.setForeground(Color.WHITE);
        add(packetLossLabel);

        coinsLabel = new JLabel("Coins: 0");
        coinsLabel.setFont(boldFont);
        coinsLabel.setForeground(Color.WHITE);
        add(coinsLabel);

        startButton = new JButton("Start");
        add(startButton);
    }

    /** Updates the score displayed in the HUD. */
    public void setScore(int score) {
        scoreLabel.setText("Score: " + score);
    }

    /** Updates the remaining wire length displayed. */
    public void setWireLength(double length) {
        wireLengthLabel.setText(String.format("Wire Left: %.2f", length));
    }

    /** Updates the temporal progress displayed. */
    public void setTime(double time) {
        timeLabel.setText(String.format("Time: %.2f", time));
    }

    /** Updates the packet loss displayed. */
    public void setPacketLoss(int loss) {
        packetLossLabel.setText("Loss: " + loss);
    }

    /** Updates the coins displayed. */
    public void setCoins(int coins) {
        coinsLabel.setText("Coins: " + coins);
    }

    /** Allows a controller to listen for the Start button click. */
    public void addStartListener(ActionListener listener) {
        startButton.addActionListener(listener);
    }
}

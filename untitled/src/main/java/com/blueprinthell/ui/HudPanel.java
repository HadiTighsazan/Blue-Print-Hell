package com.blueprinthell.ui;

import javax.swing.*;
import java.awt.*;

/**
 * HudPanel فقط عناصر HUD را نگه می‌دارد و داده‌ها را از بیرون دریافت می‌کند.
 */
public class HudPanel extends JPanel {

    private final JLabel lblWire  = new JLabel();
    private final JLabel lblCoins = new JLabel();
    private final JLabel lblLoss  = new JLabel();

    private final JButton btnStart      = new JButton("Start");
    private final JButton btnShop       = new JButton("Shop");
    private final JButton btnPausePlay  = new JButton("Pause");

    private final JSlider timeSlider    = new JSlider();

    public HudPanel(Runnable onStart,
                    Runnable onShop,
                    Runnable onPauseToggle) {
        super(new FlowLayout(FlowLayout.LEFT, 10, 5));
        setBackground(new Color(0, 0, 0, 160));

        btnStart.addActionListener(e -> onStart.run());
        btnShop.addActionListener(e  -> onShop.run());
        btnPausePlay.addActionListener(e -> onPauseToggle.run());

        timeSlider.setEnabled(false);

        add(lblWire); add(lblCoins); add(lblLoss);
        add(btnStart); add(btnShop); add(timeSlider); add(btnPausePlay);
    }

    /**
     * به‌روزرسانی مقادیر نمایشی.
     */
    public void update(double remainingWire, int coins, int loss, int total) {
        lblWire.setText("Wire Left: " + (int) remainingWire);
        lblCoins.setText("Coins: " + coins);
        lblLoss.setText("Loss: " + loss + " / " + total);
    }

    public void setButtonsState(boolean startEnabled, boolean pausePlaying) {
        btnStart.setEnabled(startEnabled);
        btnPausePlay.setText(pausePlaying ? "Pause" : "Play");
    }

    public JSlider getTimeSlider() {
        return timeSlider;
    }
}

package com.blueprinthell.ui;

import com.blueprinthell.engine.NetworkController;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;


public class ShopDialog extends JDialog {
    private final NetworkController ctrl;
    private final Runnable         onPurchase;


    public ShopDialog(Window owner,
                      NetworkController ctrl,
                      Runnable onPurchase) {
        super(owner, "Shop", ModalityType.APPLICATION_MODAL);
        this.ctrl       = Objects.requireNonNull(ctrl);
        this.onPurchase = Objects.requireNonNull(onPurchase);

        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(10, 10, 10, 10);
        gbc.gridx   = 0;
        gbc.gridy   = 0;

        JLabel title = new JLabel("Shop - Buy Power-Ups");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        add(title, gbc);

        gbc.gridy++;
        add(makeButton(
                "O'Atar (Disable Impact 10s)",
                3,
                () -> ctrl.disableImpact(10)
        ), gbc);

        gbc.gridy++;
        add(makeButton(
                "O’Airyaman (Disable Collisions 5s)",
                4,
                () -> ctrl.disableCollisions(5)
        ), gbc);

        gbc.gridy++;
        add(makeButton(
                "O'Anahita (Reset All Noise)",
                5,
                ctrl::resetNoise
        ), gbc);

        gbc.gridy++;
        JButton btnClose = new JButton("Close");
        btnClose.addActionListener(e -> dispose());
        add(btnClose, gbc);

        pack();
        setLocationRelativeTo(owner);
    }


    private JButton makeButton(String text, int cost, Runnable effect) {
        JButton btn = new JButton(text + " - " + cost + "¢");
        btn.setEnabled(ctrl.getCoins() >= cost);
        btn.addActionListener(e -> {
            if (ctrl.getCoins() < cost) return;
            ctrl.spendCoins(cost);
            effect.run();
            btn.setEnabled(false);
            onPurchase.run();
        });
        return btn;
    }
}

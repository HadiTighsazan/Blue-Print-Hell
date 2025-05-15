package com.blueprinthell.ui;

import com.blueprinthell.engine.NetworkController;

import javax.swing.*;
import java.awt.*;

/**
 * پنجرهٔ فروشگاه برای خرید پاورآپ‌ها
 */
public class ShopDialog extends JDialog {
    public ShopDialog(Window owner, NetworkController ctrl) {
        super(owner, "Shop", ModalityType.APPLICATION_MODAL);
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridx = 0; gbc.gridy = 0;

        // عنوان
        JLabel title = new JLabel("Shop - Buy Power-Ups");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        add(title, gbc);

        // O'Atar
        gbc.gridy++;
        add(makeButton(ctrl, "O'Atar (Disable Impact 10s)", 3, () ->
                ctrl.disableImpact(10)
        ), gbc);

        // O’Airyaman
        gbc.gridy++;
        add(makeButton(ctrl, "O’Airyaman (Disable Collisions 5s)", 4, () ->
                ctrl.disableCollisions(5)
        ), gbc);

        // O'Anahita
        gbc.gridy++;
        add(makeButton(ctrl, "O'Anahita (Reset All Noise)", 5, () ->
                ctrl.resetNoise()
        ), gbc);

        // دکمه خروج
        gbc.gridy++;
        JButton btnClose = new JButton("Close");
        btnClose.addActionListener(e -> dispose());
        add(btnClose, gbc);

        pack();
        setLocationRelativeTo(owner);
    }

    private JButton makeButton(NetworkController ctrl, String text, int cost, Runnable effect) {
        JButton btn = new JButton(text + " - " + cost + "¢");
        btn.setEnabled(ctrl.getCoins() >= cost);
        btn.addActionListener(e -> {
            if (ctrl.getCoins() < cost) return;
            ctrl.spendCoins(cost);
            effect.run();
            btn.setEnabled(false);
        });
        return btn;
    }
}
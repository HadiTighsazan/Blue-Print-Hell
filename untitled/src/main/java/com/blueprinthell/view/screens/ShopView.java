package com.blueprinthell.view.screens;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * Shop view for purchasing power-ups during the game.
 */
public class ShopView extends JPanel {
    public final JButton buyOAtarButton = new JButton("O’Atar - 3 coins");
    public final JButton buyOAiryamanButton = new JButton("O’Airyaman - 4 coins");
    public final JButton buyOAnahitaButton = new JButton("O’Anahita - 5 coins");
    public final JButton closeButton = new JButton("Close");
    private final JLabel messageLabel;

    public ShopView() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(new Color(30, 30, 30));
        setPreferredSize(new Dimension(800, 600));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        Font titleFont = new JLabel().getFont().deriveFont(Font.BOLD, 24f);
        JLabel title = new JLabel("Store");
        title.setForeground(Color.WHITE);
        title.setFont(titleFont);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(title);

        add(Box.createRigidArea(new Dimension(0, 20)));

        messageLabel = new JLabel(" ");
        messageLabel.setForeground(Color.YELLOW);
        messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(messageLabel);

        add(Box.createRigidArea(new Dimension(0, 10)));
        add(createButtonPanel(buyOAtarButton));
        add(Box.createRigidArea(new Dimension(0, 10)));
        add(createButtonPanel(buyOAiryamanButton));
        add(Box.createRigidArea(new Dimension(0, 10)));
        add(createButtonPanel(buyOAnahitaButton));
        add(Box.createRigidArea(new Dimension(0, 20)));
        add(createButtonPanel(closeButton));

        add(Box.createVerticalGlue());
    }

    private JPanel createButtonPanel(JButton button) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(button);
        return panel;
    }

    /**
     * Shows a message (e.g., error or confirmation) in the shop.
     */
    public void setMessage(String msg) {
        messageLabel.setText(msg);
    }

    /**
     * Listener registration methods
     */
    public void addBuyOAtarListener(ActionListener l) { buyOAtarButton.addActionListener(l); }
    public void addBuyOAiryamanListener(ActionListener l) { buyOAiryamanButton.addActionListener(l); }
    public void addBuyOAnahitaListener(ActionListener l) { buyOAnahitaButton.addActionListener(l); }
    public void addCloseListener(ActionListener l) { closeButton.addActionListener(l); }
}

package com.blueprinthell.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * HUD view for displaying live game metrics and basic controls.
 */
public class HudView extends JPanel {
    /* ------------- labels ------------- */
    private final JLabel levelLabel;
    private final JLabel wireLengthLabel;
    private final JLabel packetLossLabel;
    private final JLabel coinsLabel;

    /* ------------- active features panel ------------- */
    private final JPanel activeFeaturesPanel;

    /* ------------- buttons ------------- */
    private final JButton startButton;   // first start of simulation
    private final JButton toggleButton;  // pause / resume
    private final JButton storeButton;   // opens shop

    public HudView(int x, int y, int width, int height) {
        setLayout(new FlowLayout(FlowLayout.LEFT, 10, 5));
        setOpaque(true);
        setBackground(Color.BLACK);
        setBounds(x, y, width, height);

        Font bold = new JLabel().getFont().deriveFont(Font.BOLD, 16f);
        Color fg   = Color.WHITE;

        levelLabel       = makeLabel("Level: 1", bold, fg);
        wireLengthLabel  = makeLabel("Wire Left: 0.00", bold, fg);
        packetLossLabel  = makeLabel("Loss: 0", bold, fg);
        coinsLabel       = makeLabel("Coins: 0", bold, fg);

        // Panel for active network features (scroll effects)
        activeFeaturesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        activeFeaturesPanel.setOpaque(false);
        add(activeFeaturesPanel);

        startButton  = new JButton("Start");
        toggleButton = new JButton("Pause");
        storeButton  = new JButton("Store");

        // Add components in order
        add(levelLabel);
        add(wireLengthLabel);
        add(packetLossLabel);
        add(coinsLabel);
        add(startButton);
        add(toggleButton);
        add(storeButton);
    }

    /* ------------------ helper ------------------ */
    private JLabel makeLabel(String txt, Font f, Color c) {
        JLabel l = new JLabel(txt);
        l.setFont(f);
        l.setForeground(c);
        add(l);
        return l;
    }

    /* ------------------ setters ------------------ */
    public void setLevel(int lv)           { levelLabel.setText("Level: " + lv); }
    public void setWireLength(double len)  { wireLengthLabel.setText(String.format("Wire Left: %.2f", len)); }
    public void setPacketLoss(int loss)    { packetLossLabel.setText("Loss: " + loss); }
    public void setCoins(int coins)        { coinsLabel.setText("Coins: " + coins); }

    /**
     * Updates the panel showing active scroll effects.
     * @param names list of scroll effect names
     * @param remainingSeconds list of seconds remaining parallel to names
     */
    public void setActiveFeatures(List<String> names, List<Integer> remainingSeconds) {
        activeFeaturesPanel.removeAll();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            int secs = remainingSeconds.get(i);
            String time = String.format("%d:%02d", secs / 60, secs % 60);
            JLabel lbl = new JLabel(name + " (" + time + ")");
            lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN, 14f));
            lbl.setForeground(Color.YELLOW);
            activeFeaturesPanel.add(lbl);
        }
        activeFeaturesPanel.revalidate();
        activeFeaturesPanel.repaint();
    }

    /* ------------------ toggle button ------------------ */
    public void setToggleText(String txt)  { toggleButton.setText(txt); }

    /* ------------------ listeners ------------------ */
    public void addStartListener(ActionListener l)  { startButton.addActionListener(l); }
    public void addToggleListener(ActionListener l) { toggleButton.addActionListener(l); }
    public void addStoreListener(ActionListener l)  { storeButton.addActionListener(l); }

    public JButton getStartButton()          { return startButton; }
    public JButton getToggleButton()         { return toggleButton; }
    public JButton getStoreButton()          { return storeButton; }


}

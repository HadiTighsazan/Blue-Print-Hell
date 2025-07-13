package com.blueprinthell.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * HUD view for displaying live game metrics and basic controls.
 */
public class HudView extends JPanel {
    /* ------------- labels ------------- */
    private final JLabel levelLabel;
    private final JLabel scoreLabel;
    private final JLabel wireLengthLabel;
    private final JLabel packetLossLabel;
    private final JLabel coinsLabel;

    /* ------------- buttons ------------- */
    private final JButton startButton;   // first start of simulation
    private final JButton toggleButton;  // pause / resume
    private final JButton storeButton;   // opens shop

    public HudView(int x, int y, int width, int height) {
        setLayout(new FlowLayout(FlowLayout.LEFT));
        setOpaque(true);
        setBackground(Color.BLACK);
        setBounds(x, y, width, height);

        Font bold = new JLabel().getFont().deriveFont(Font.BOLD, 16f);
        Color fg   = Color.WHITE;

        levelLabel = makeLabel("Level: 1", bold, fg);
        scoreLabel = makeLabel("Score: 0", bold, fg);
        wireLengthLabel = makeLabel("Wire Left: 0", bold, fg);
        packetLossLabel = makeLabel("Loss: 0", bold, fg);
        coinsLabel = makeLabel("Coins: 0", bold, fg);

        startButton  = new JButton("Start");
        toggleButton = new JButton("Pause");
        storeButton  = new JButton("Store");

        add(levelLabel);      add(scoreLabel);
        add(wireLengthLabel); add(packetLossLabel); add(coinsLabel);
        add(startButton);     add(toggleButton);    add(storeButton);
    }

    /* ------------------ helper ------------------ */
    private JLabel makeLabel(String txt, Font f, Color c) {
        JLabel l = new JLabel(txt);
        l.setFont(f); l.setForeground(c); add(l); return l;
    }

    /* ------------------ setters ------------------ */
    public void setLevel(int lv)           { levelLabel.setText("Level: " + lv); }
    public void setScore(int score)        { scoreLabel.setText("Score: " + score); }
    public void setWireLength(double len)  { wireLengthLabel.setText(String.format("Wire Left: %.2f", len)); }
    public void setPacketLoss(int loss)    { packetLossLabel.setText("Loss: " + loss); }
    public void setCoins(int coins)        { coinsLabel.setText("Coins: " + coins); }

    /* ------------------ toggle button ------------------ */
    public void setToggleText(String txt)  { toggleButton.setText(txt); }

    /* ------------------ listeners ------------------ */
    public void addStartListener(ActionListener l)  { startButton.addActionListener(l); }
    public void addToggleListener(ActionListener l) { toggleButton.addActionListener(l); }
    public void addStoreListener(ActionListener l)  { storeButton.addActionListener(l); }


    public JButton getStartButton() {
        return startButton;
    }

    /** Returns the Pause/Resume toggle button so controllers can manage listeners. */
    public JButton getToggleButton() {
        return toggleButton;
    }
}

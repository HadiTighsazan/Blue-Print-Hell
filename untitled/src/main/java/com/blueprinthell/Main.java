package com.blueprinthell;

import com.blueprinthell.ui.GameApp;

import javax.swing.*;


public class Main  {


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new GameApp().setVisible(true);
        });
    }

}

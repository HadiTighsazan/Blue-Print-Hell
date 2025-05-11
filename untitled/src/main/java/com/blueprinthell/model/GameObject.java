package com.blueprinthell.model;

import javax.swing.*;
import java.awt.*;
import java.io.Serializable;

public abstract class GameObject extends JComponent implements Serializable {
    private static final long serialVersionUID = 1L;

    protected GameObject(int x, int y, int w, int h) {
        setBounds(x, y, w, h);
        setOpaque(false);
    }

    public int getCenterX() { return getX() + getWidth()/2; }
    public int getCenterY() { return getY() + getHeight()/2; }
}

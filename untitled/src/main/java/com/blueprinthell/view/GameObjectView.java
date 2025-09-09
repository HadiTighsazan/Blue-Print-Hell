package com.blueprinthell.view;

import com.blueprinthell.model.GameObjectModel;
import javax.swing.*;


public abstract class GameObjectView<T extends GameObjectModel> extends JComponent {
    protected  T model;

    public GameObjectView(T model) {
        this.model = model;
        setBounds(model.getX(), model.getY(), model.getWidth(), model.getHeight());
        setOpaque(false);
    }

    public void setModel(T model) {
        this.model = model;
    }


    public void refresh() {
        setBounds(model.getX(), model.getY(), model.getWidth(), model.getHeight());
        repaint();
    }
}

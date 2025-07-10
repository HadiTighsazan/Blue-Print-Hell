package com.blueprinthell.view;

import com.blueprinthell.model.GameObjectModel;
import javax.swing.*;

/**
 * Base Swing view for game objects, rendering position and size from the model.
 */
public abstract class GameObjectView<T extends GameObjectModel> extends JComponent {
    protected final T model;

    public GameObjectView(T model) {
        this.model = model;
        setBounds(model.getX(), model.getY(), model.getWidth(), model.getHeight());
        setOpaque(false);
    }

    /**
     * Refreshes view bounds and repaints to reflect model changes.
     */
    public void refresh() {
        setBounds(model.getX(), model.getY(), model.getWidth(), model.getHeight());
        repaint();
    }
}

package com.blueprinthell.view;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.PortModel;
import javax.swing.*;
import java.awt.*;

/**
 * Swing view for displaying a SystemBoxModel with its ports.
 */
public class SystemBoxView extends GameObjectView<SystemBoxModel> {

    public SystemBoxView(SystemBoxModel model) {
        super(model);
        setLayout(null);
        setBackground(Config.COLOR_BOX_BG);
        setOpaque(true);

        // Create and add PortViews as child components with relative bounds
        for (PortModel pm : model.getInPorts()) {
            PortView pv = new PortView(pm);
            add(pv);
        }
        for (PortModel pm : model.getOutPorts()) {
            PortView pv = new PortView(pm);
            add(pv);
        }
        refresh();
    }

    @Override
    public void refresh() {
        // First update this view's bounds
        setBounds(model.getX(), model.getY(), model.getWidth(), model.getHeight());
        // Then position each PortView relative to this view
        for (Component c : getComponents()) {
            if (c instanceof PortView pv) {
                PortModel pm = pv.getModel();
                int relX = pm.getX() - model.getX();
                int relY = pm.getY() - model.getY();
                c.setBounds(relX, relY, pm.getWidth(), pm.getHeight());
            }
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(Config.COLOR_BOX_FILL);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(Config.COLOR_BOX_BORDER);
        g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
        g2.dispose();
    }

    public SystemBoxModel getModel() {
        return model;
    }
}

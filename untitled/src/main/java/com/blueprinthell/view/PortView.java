package com.blueprinthell.view;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.PortShape;
import javax.swing.*;
import java.awt.*;

/**
 * Swing view for displaying a PortModel.
 */
public class PortView extends GameObjectView<PortModel> {

    public PortView(PortModel model) {
        super(model);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        // Color based on input/output from Config
        Color baseColor = model.isInput() ? Config.COLOR_PORT_INPUT : Config.COLOR_PORT_OUTPUT;
        g2.setColor(baseColor);
        int s = getWidth();
        if (model.getShape() == PortShape.SQUARE) {
            g2.fillRect(0, 0, s, s);
        } else {
            int[] xs = {0, s / 2, s};
            int[] ys = {s, 0, s};
            g2.fillPolygon(xs, ys, 3);
        }
        g2.dispose();
    }

    public PortModel getModel() {
        return model;
    }
}

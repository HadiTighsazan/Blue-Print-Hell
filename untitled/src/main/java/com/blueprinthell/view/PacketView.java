package com.blueprinthell.view;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PacketType;
import javax.swing.*;
import java.awt.*;

/**
 * Swing view for displaying a PacketModel.
 */
public class PacketView extends GameObjectView<PacketModel> {

    public PacketView(PacketModel model) {
        super(model);
        setToolTipText(model.getType().name());
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        // Choose color based on packet type
        Color color = (model.getType() == PacketType.SQUARE)
                ? Config.COLOR_PACKET_SQUARE
                : Config.COLOR_PACKET_TRIANGLE;
        g2.setColor(color);
        int s = Math.min(getWidth(), getHeight());
        if (model.getType() == PacketType.SQUARE) {
            g2.fillRect(0, 0, s, s);
        } else {
            int[] xs = {0, s / 2, s};
            int[] ys = {s, 0, s};
            g2.fillPolygon(xs, ys, 3);
        }
        g2.dispose();
    }

    /**
     * Updates the view's position and size when the model changes.
     */
    public void refreshView() {
        refresh();
    }
}

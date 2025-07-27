package com.blueprinthell.view;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PacketType;
import java.awt.*;


public class PacketView extends GameObjectView<PacketModel> {

    public PacketView(PacketModel model) {
        super(model);
        setToolTipText(model.getType().name());
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
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


    public void refreshView() {
        refresh();
    }
}

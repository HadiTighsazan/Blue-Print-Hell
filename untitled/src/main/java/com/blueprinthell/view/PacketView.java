package com.blueprinthell.view;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketModel;
import java.awt.*;


public class PacketView extends GameObjectView<PacketModel> {

    public PacketView(PacketModel model) {
        super(model);
        setToolTipText(model.getType().name());
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);


            int s = Math.min(getWidth(), getHeight());

            switch (model.getType()) {
                case SQUARE:
                    g2.setColor(Config.COLOR_PACKET_SQUARE);
                    break;
                case TRIANGLE:
                    g2.setColor(Config.COLOR_PACKET_TRIANGLE);
                    break;
                case CIRCLE:
                    g2.setColor(Config.COLOR_PACKET_CIRCLE);
                    break;
                default:
                    g2.setColor(Config.COLOR_PACKET_SQUARE);
            }

            switch (model.getType()) {
                case SQUARE:
                    g2.fillRect(0, 0, s, s);
                    break;
                case TRIANGLE:
                    int[] xs = {0, s/2, s};
                    int[] ys = {s, 0, s};
                    g2.fillPolygon(xs, ys, 3);
                    break;
                case CIRCLE:
                    g2.fillOval(0, 0, s, s);
                    break;
            }
        } finally {
            g2.dispose();
        }
    }



    public void refreshView() {
        refresh();
    }
}

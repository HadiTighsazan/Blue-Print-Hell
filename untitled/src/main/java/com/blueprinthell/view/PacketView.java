package com.blueprinthell.view;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.ConfidentialPacket;
import com.blueprinthell.model.ProtectedPacket;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.view.draw.ShapeUtils;

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
            // کیفیت رندر
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ShapeUtils.enableQuality(g2);

            final int w = getWidth();
            final int h = getHeight();

            // ===== رندر ویژه برای LargePacket (۸/۱۰ضلعی بنفش) =====
            if (model instanceof LargePacket lp) {
                final int sides = (lp.getExpectedBits() == 8) ? 8 : 10;

                g2.setColor(Config.COLOR_PACKET_LARGE);
                Polygon poly = ShapeUtils.regularPolygon(sides, w, h, Config.POLY_INSET);
                g2.fillPolygon(poly);

                // (اختیاری) خط دور برای تمایز
                g2.setColor(Config.COLOR_BOX_BORDER);
                g2.drawPolygon(poly);

                // Badgeها
                drawPacketBadges(g2, model, w, h);
                return; // مسیر Large تمام شد
            }

            // ===== رندر قدیمی برای انواع معمولی =====
            int s = Math.min(w, h);

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
                    int[] xs = {0, s / 2, s};
                    int[] ys = {s, 0, s};
                    g2.fillPolygon(xs, ys, 3);
                    break;
                case CIRCLE:
                    g2.fillOval(0, 0, s, s);
                    break;
            }

            // Badgeها برای پکت‌های غیر-Large
            drawPacketBadges(g2, model, w, h);

        } finally {
            g2.dispose();
        }
    }

    // رسم Badge "C" یا "P" در گوشه‌ی بالا-راست
    private void drawPacketBadges(Graphics2D g2, PacketModel m, int w, int h) {
        final int x = w - (18 + Config.BADGE_MARGIN_X);
        final int y = Config.BADGE_MARGIN_Y;

        if (m instanceof ConfidentialPacket) {
            ShapeUtils.drawBadge(g2, "C", x, y);
        } else if (m instanceof ProtectedPacket) {
            ShapeUtils.drawBadge(g2, "P", x, y);
        }
    }

    public void refreshView() {
        refresh();
    }
}

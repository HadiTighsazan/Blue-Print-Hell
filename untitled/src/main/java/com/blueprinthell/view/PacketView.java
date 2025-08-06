package com.blueprinthell.view;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.ConfidentialPacket;
import com.blueprinthell.model.ProtectedPacket;
import com.blueprinthell.model.large.BitPacket;
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

            // خط 36-47 که در مرحله قبل تغییر دادیم را دوباره اصلاح کنید:
            if (model instanceof LargePacket lp) {
                final int sizeUnits = lp.getOriginalSizeUnits();
                final int sides = (sizeUnits == 8) ? 8 : 10;

                // استفاده از رنگ دینامیک
                g2.setColor(lp.getCustomColor());
                Polygon poly = ShapeUtils.regularPolygon(sides, w, h, Config.POLY_INSET);
                g2.fillPolygon(poly);

                // حاشیه متفاوت برای تمایز
                g2.setStroke(new BasicStroke(sizeUnits == 8 ? 2f : 3f));
                g2.setColor(sizeUnits == 8 ? Color.WHITE : Color.YELLOW);
                g2.drawPolygon(poly);

                // نمایش عدد اندازه در مرکز
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Arial", Font.BOLD, 14));
                String sizeStr = String.valueOf(sizeUnits);
                FontMetrics fm = g2.getFontMetrics();
                int tx = (w - fm.stringWidth(sizeStr)) / 2;
                int ty = (h + fm.getAscent()) / 2 - 2;
                g2.drawString(sizeStr, tx, ty);

                drawPacketBadges(g2, model, w, h);
                return;
            }

            if (model instanceof BitPacket bp) {
                // رنگ بر اساس گروه
                g2.setColor(bp.getColor());

                // رسم دایره کوچک‌تر برای بیت‌پکت
                int margin = 3;
                g2.fillOval(margin, margin, w - 2*margin, h - 2*margin);

                // حاشیه سفید برای تمایز
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1f));
                g2.drawOval(margin, margin, w - 2*margin, h - 2*margin);

                // نمایش شماره index کوچک
                g2.setFont(new Font("Arial", Font.PLAIN, 8));
                String idx = String.valueOf(bp.getIndexInGroup());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (w - fm.stringWidth(idx)) / 2;
                int ty = (h + fm.getAscent()) / 2 - 2;
                g2.setColor(Color.BLACK);
                g2.drawString(idx, tx, ty);

                return;
            }

            // ===== رندر انواع معمولی + Protected با شفافیت پایین =====
            int s = Math.min(w, h);

            // اعمال شفافیت پایین فقط برای بدنه اگر Protected باشد
            final boolean isProtected = (model instanceof ProtectedPacket);
            Composite savedComposite = null;
            if (isProtected) {
                savedComposite = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
            }

            // رنگ بر اساس نوع
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

            // رسم شکل
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

            // بازگرداندن کامپوزیت تا Badgeها واضح بمانند
            if (savedComposite != null) {
                g2.setComposite(savedComposite);
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

package com.blueprinthell.view;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.ConfidentialPacket;
import com.blueprinthell.model.PacketOps;
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

            /* ===================== LargePacket ===================== */
            if (model instanceof LargePacket lp) {
                final int sizeUnits = lp.getOriginalSizeUnits(); // 8 یا 10
                final int sides = (sizeUnits == 8) ? 8 : 10;

                // اطمینان از سایز صحیح
                int expected = sizeUnits * Config.PACKET_SIZE_MULTIPLIER;
                if (model.getWidth() != expected) {
                    model.setWidth(expected);
                    model.setHeight(expected);
                    setBounds(model.getX(), model.getY(), expected, expected);
                }

                // بدنه
                g2.setColor(lp.getCustomColor());
                Polygon poly = ShapeUtils.regularPolygon(sides, getWidth(), getHeight(), Config.POLY_INSET);
                g2.fillPolygon(poly);

                // حاشیه (قطر متناسب با اندازه)
                g2.setStroke(new BasicStroke(sizeUnits == 8 ? 2f : 3f));
                g2.setColor(sizeUnits == 8 ? Color.WHITE : Color.YELLOW);
                g2.drawPolygon(poly);

                // شماره اندازه در مرکز
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Arial", Font.BOLD, 16));
                String sizeStr = String.valueOf(sizeUnits);
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(sizeStr)) / 2;
                int ty = (getHeight() + fm.getAscent()) / 2 - 2;
                g2.drawString(sizeStr, tx, ty);

                drawPacketBadges(g2, model, getWidth(), getHeight());
                return;
            }

            /* ===================== BitPacket ===================== */
            if (model instanceof BitPacket bp) {
                int expected = Config.BIT_PACKET_SIZE * Config.PACKET_SIZE_MULTIPLIER;
                if (model.getWidth() != expected) {
                    model.setWidth(expected);
                    model.setHeight(expected);
                    setBounds(model.getX(), model.getY(), expected, expected);
                }

                // بدنه (رنگ گروه)
                Color bitColor = bp.getColor();
                int m = 2;
                g2.setColor(bitColor);
                g2.fillRect(m, m, getWidth() - 2*m, getHeight() - 2*m);

                // حاشیه سفید نازک
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRect(m, m, getWidth() - 2*m, getHeight() - 2*m);

                // ایندکس کوچک
                g2.setFont(new Font("Arial", Font.PLAIN, 8));
                g2.setColor(Color.WHITE);
                g2.drawString(String.valueOf(bp.getIndexInGroup()), 3, 10);
                return;
            }

            /* ============= ConfidentialPacket (عادی/VPN) ============= */
            if (model instanceof ConfidentialPacket) {
                boolean isVpn = PacketOps.isConfidentialVpn(model);
                int expectedUnits = isVpn ? 6 : 4; // 1.5×
                int expected = expectedUnits * Config.PACKET_SIZE_MULTIPLIER;

                if (model.getWidth() != expected) {
                    model.setWidth(expected);
                    model.setHeight(expected);
                    setBounds(model.getX(), model.getY(), expected, expected);
                }

                // --- به‌جای پنج‌ضلعی، مربع رسم شود ---
                int m = 2;
                if (isVpn) {
                    // صورتیِ کانفیگ + حاشیه روشن‌تر
                    Color fill = Config.CONF_VPN_COLOR;
                    int r = Math.min(255, (int)(fill.getRed()   * 1.15));
                    int gr = Math.min(255, (int)(fill.getGreen() * 1.15));
                    int b = Math.min(255, (int)(fill.getBlue()  * 1.15));
                    Color border = new Color(r, gr, b);

                    g2.setColor(fill);
                    g2.fillRect(m, m, getWidth() - 2*m, getHeight() - 2*m);
                    g2.setStroke(new BasicStroke(2.5f));
                    g2.setColor(border);
                    g2.drawRect(m, m, getWidth() - 2*m, getHeight() - 2*m);

                } else {
                    // بنفش عادی
                    Color fill = new Color(0x7C3AED);
                    Color border = new Color(0xA78BFA);
                    g2.setColor(fill);
                    g2.fillRect(m, m, getWidth() - 2*m, getHeight() - 2*m);
                    g2.setStroke(new BasicStroke(2f));
                    g2.setColor(border);
                    g2.drawRect(m, m, getWidth() - 2*m, getHeight() - 2*m);
                }

                // برچسب «C» وسط مربع (برای هر دو حالت)
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Arial", Font.BOLD, isVpn ? 14 : 12));
                String label = "C";
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(label)) / 2;
                int ty = (getHeight() + fm.getAscent()) / 2 - 2;
                g2.drawString(label, tx, ty);

                drawPacketBadges(g2, model, getWidth(), getHeight());
                return;
            }

            int s = Math.min(w, h);

            // اگر Protected: فقط بدنه با شفافیت کمتر
            final boolean isProtected = (model instanceof ProtectedPacket);
            Composite savedComposite = null;
            if (isProtected) {
                savedComposite = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
            }

            // رنگ بدنه بر اساس Type
            switch (model.getType()) {
                case SQUARE -> g2.setColor(Config.COLOR_PACKET_SQUARE);
                case TRIANGLE -> g2.setColor(Config.COLOR_PACKET_TRIANGLE);
                case CIRCLE -> g2.setColor(Config.COLOR_PACKET_CIRCLE);
                default -> g2.setColor(Config.COLOR_PACKET_SQUARE);
            }

            // رسم شکل
            switch (model.getType()) {
                case SQUARE -> g2.fillRect(0, 0, s, s);
                case TRIANGLE -> {
                    int[] xs = {0, s / 2, s};
                    int[] ys = {s, 0, s};
                    g2.fillPolygon(xs, ys, 3);
                }
                case CIRCLE -> g2.fillOval(0, 0, s, s);
            }

            // بازگردانی کامپوزیت برای Badgeها
            if (savedComposite != null) g2.setComposite(savedComposite);

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

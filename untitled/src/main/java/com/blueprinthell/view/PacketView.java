package com.blueprinthell.view;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;
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
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ShapeUtils.enableQuality(g2);

            final int w = getWidth();
            final int h = getHeight();

            final PacketModel shapeModel = (model instanceof TrojanPacket) ? ((TrojanPacket) model).getOriginal() : model;
            final boolean isTrojan = model instanceof TrojanPacket;

            if (shapeModel instanceof LargePacket lp) {
                final int sizeUnits = lp.getOriginalSizeUnits();
                final int sides = (sizeUnits == 8) ? 8 : 10;

                int expected = sizeUnits * Config.PACKET_SIZE_MULTIPLIER;
                if (model.getWidth() != expected) {
                    model.setWidth(expected);
                    model.setHeight(expected);
                    setBounds(model.getX(), model.getY(), expected, expected);
                }

                g2.setColor(lp.getCustomColor());
                Polygon poly = ShapeUtils.regularPolygon(sides, getWidth(), getHeight(), Config.POLY_INSET);
                g2.fillPolygon(poly);

                g2.setStroke(new BasicStroke(sizeUnits == 8 ? 2f : 3f));
                g2.setColor(sizeUnits == 8 ? Color.WHITE : Color.YELLOW);
                g2.drawPolygon(poly);

                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Arial", Font.BOLD, 16));
                String sizeStr = String.valueOf(sizeUnits);
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(sizeStr)) / 2;
                int ty = (getHeight() + fm.getAscent()) / 2 - 2;
                g2.drawString(sizeStr, tx, ty);

            } else if (shapeModel instanceof BitPacket bp) {
                int expected = Config.BIT_PACKET_SIZE * Config.PACKET_SIZE_MULTIPLIER;
                if (model.getWidth() != expected) {
                    model.setWidth(expected);
                    model.setHeight(expected);
                    setBounds(model.getX(), model.getY(), expected, expected);
                }

                Color bitColor = bp.getColor();
                int m = 2;
                g2.setColor(bitColor);
                g2.fillRect(m, m, getWidth() - 2*m, getHeight() - 2*m);

                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRect(m, m, getWidth() - 2*m, getHeight() - 2*m);

                g2.setFont(new Font("Arial", Font.PLAIN, 8));
                g2.setColor(Color.WHITE);
                g2.drawString(String.valueOf(bp.getIndexInGroup()), 3, 10);

            } else if (shapeModel instanceof ConfidentialPacket) {
                boolean isVpn = PacketOps.isConfidentialVpn(shapeModel);
                int expectedUnits = isVpn ? 6 : 4;
                int expected = expectedUnits * Config.PACKET_SIZE_MULTIPLIER;

                if (model.getWidth() != expected) {
                    model.setWidth(expected);
                    model.setHeight(expected);
                    setBounds(model.getX(), model.getY(), expected, expected);
                }

                int m = 2;
                if (isVpn) {
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
                    Color fill = new Color(0x7C3AED);
                    Color border = new Color(0xA78BFA);
                    g2.setColor(fill);
                    g2.fillRect(m, m, getWidth() - 2*m, getHeight() - 2*m);
                    g2.setStroke(new BasicStroke(2f));
                    g2.setColor(border);
                    g2.drawRect(m, m, getWidth() - 2*m, getHeight() - 2*m);
                }

                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Arial", Font.BOLD, isVpn ? 14 : 12));
                String label = "C";
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(label)) / 2;
                int ty = (getHeight() + fm.getAscent()) / 2 - 2;
                g2.drawString(label, tx, ty);

            } else { // Fallback for normal messenger packets
                int s = Math.min(w, h);
                int units = shapeModel.getType().sizeUnits;
                int expected = units * Config.PACKET_SIZE_MULTIPLIER;

                if (shapeModel instanceof ProtectedPacket) {
                    expected *= 2;
                }

                if (model.getWidth() != expected || model.getHeight() != expected
                        || getWidth() != expected || getHeight() != expected) {
                    model.setWidth(expected);
                    model.setHeight(expected);
                    setBounds(model.getX(), model.getY(), expected, expected);
                }

                final boolean isProtected = (shapeModel instanceof ProtectedPacket);
                Composite savedComposite = null;
                if (isProtected) {
                    savedComposite = g2.getComposite();
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
                }

                switch (shapeModel.getType()) {
                    case SQUARE -> g2.setColor(Config.COLOR_PACKET_SQUARE);
                    case TRIANGLE -> g2.setColor(Config.COLOR_PACKET_TRIANGLE);
                    case CIRCLE -> g2.setColor(Config.COLOR_PACKET_CIRCLE);
                    default -> g2.setColor(Config.COLOR_PACKET_SQUARE);
                }

                switch (shapeModel.getType()) {
                    case SQUARE -> g2.fillRect(0, 0, s, s);
                    case TRIANGLE -> {
                        int[] xs = {0, s / 2, s};
                        int[] ys = {s, 0, s};
                        g2.fillPolygon(xs, ys, 3);
                    }
                    case CIRCLE -> g2.fillOval(0, 0, s, s);
                }

                if (savedComposite != null) g2.setComposite(savedComposite);
            }

            if (isTrojan) {
                g2.setColor(Color.RED.darker());
                g2.setFont(new Font("Arial", Font.BOLD, 16));
                String label = "T";
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(label)) / 2;
                int ty = (getHeight() + fm.getAscent()) / 2 - 2;
                g2.drawString(label, tx, ty);
            }

            drawPacketBadges(g2, model, w, h);

        } finally {
            g2.dispose();
        }
    }
    private void drawPacketBadges(Graphics2D g2, PacketModel m, int w, int h) {
        final int x = w - (18 + Config.BADGE_MARGIN_X);
        final int y = Config.BADGE_MARGIN_Y;

        if (m instanceof ConfidentialPacket) {
            ShapeUtils.drawBadge(g2, "C", x, y);
        } else if (m instanceof ProtectedPacket) {
            ShapeUtils.drawBadge(g2, "P", x, y);
        } else if (m instanceof com.blueprinthell.model.TrojanPacket) {
            ShapeUtils.drawBadge(g2, "T", x, y);
        }
    }

    public void refreshView() {
        refresh();
    }
}

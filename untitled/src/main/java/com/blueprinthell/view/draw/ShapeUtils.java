package com.blueprinthell.view.draw;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import com.blueprinthell.config.Config;

public final class ShapeUtils {
    private ShapeUtils() {}

    public static void enableQuality(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,    RenderingHints.VALUE_STROKE_PURE);
    }


    public static Polygon regularPolygon(int sides, int w, int h, double inset) {
        if (sides < 3) throw new IllegalArgumentException("sides >= 3 required");
        int cx = w / 2;
        int cy = h / 2;
        int r  = (int) Math.round(Math.min(w, h) * (0.5 - Math.max(0, Math.min(inset, 0.49))));

        int[] xs = new int[sides];
        int[] ys = new int[sides];
        double a0 = -Math.PI / 2.0;
        double step = 2 * Math.PI / sides;
        for (int i = 0; i < sides; i++) {
            double a = a0 + i * step;
            xs[i] = cx + (int) Math.round(r * Math.cos(a));
            ys[i] = cy + (int) Math.round(r * Math.sin(a));
        }
        return new Polygon(xs, ys, sides);
    }


    public static void drawBadge(Graphics2D g2, String text, int x, int y) {
        Font old = g2.getFont();
        g2.setFont(Config.FONT_BADGE);
        FontMetrics fm = g2.getFontMetrics();

        int pad = Config.BADGE_PADDING;
        int w   = fm.stringWidth(text) + pad * 2;
        int h   = fm.getAscent() + fm.getDescent() + pad * 2;

        g2.setColor(Config.COLOR_BADGE_BG);
        Shape bg = new RoundRectangle2D.Double(x, y, w, h, Config.BADGE_CORNER_RADIUS, Config.BADGE_CORNER_RADIUS);
        g2.fill(bg);

        g2.setColor(Config.COLOR_BADGE_FG);
        int tx = x + pad;
        int ty = y + pad + fm.getAscent();
        g2.drawString(text, tx, ty);

        g2.setFont(old);
    }
}

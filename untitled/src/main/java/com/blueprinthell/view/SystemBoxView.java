package com.blueprinthell.view;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.view.draw.ShapeUtils;

import javax.swing.*;
import java.awt.*;

import static com.blueprinthell.controller.systems.SystemKind.*;

public class SystemBoxView extends GameObjectView<SystemBoxModel> {

    public SystemBoxView(SystemBoxModel model) {
        super(model);
        setLayout(null);
        setBackground(Config.COLOR_BOX_BG);
        setOpaque(true);

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
        setBounds(model.getX(), model.getY(), model.getWidth(), model.getHeight());
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
        try {
            ShapeUtils.enableQuality(g2);

            // --- رسم قبلی باکس (حفظ‌شده) ---
            g2.setColor(Config.COLOR_BOX_FILL);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(Config.COLOR_BOX_BORDER);
            g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);

            // --- [NEW] لیبل وسط باکس ---
            final String label = toDisplayName(model.getPrimaryKind());

            g2.setFont(Config.FONT_SYSTEM_LABEL);
            FontMetrics fm = g2.getFontMetrics();

            int tx = (getWidth() - fm.stringWidth(label)) / 2;
            int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;

            // زمینه‌ی گرد برای خوانایی بهتر (اختیاری ولی مفید)
            int padX = 6, padY = 3;
            int bw = fm.stringWidth(label) + padX * 2;
            int bh = fm.getAscent() + fm.getDescent() + padY * 2;
            int bx = tx - padX;
            int by = ty - fm.getAscent() - padY;

            int bgAlpha = model.isEnabled() ? 140 : 90;
            g2.setColor(new Color(
                    Config.COLOR_BADGE_BG.getRed(),
                    Config.COLOR_BADGE_BG.getGreen(),
                    Config.COLOR_BADGE_BG.getBlue(),
                    bgAlpha
            ));
            g2.fillRoundRect(bx, by, bw, bh, Config.BADGE_CORNER_RADIUS, Config.BADGE_CORNER_RADIUS);

            g2.setColor(model.isEnabled() ? Config.COLOR_BADGE_FG : new Color(255, 255, 255, 180));
            g2.drawString(label, tx, ty);

        } finally {
            g2.dispose();
        }
    }

    public SystemBoxModel getModel() {
        return model;
    }

    // --- helper: نگاشت enum به متن نمایشی ---
    private static String toDisplayName(SystemKind kind) {
        if (kind == null) return "Normal";
        switch (kind) {
            case NORMAL:       return "Normal";
            case VPN:          return "Vpn";
            case SPY:          return "Spy";
            case MALICIOUS:    return "Malicious";
            case DISTRIBUTOR:  return "Distributor";
            case MERGER:       return "Merger";
            case ANTI_TROJAN:  return "Anti‑Trojan";
            default:           return capitalize(kind.name());
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        s = s.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}

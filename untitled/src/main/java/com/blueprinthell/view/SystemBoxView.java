package com.blueprinthell.view;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.view.draw.ShapeUtils;

import java.awt.*;


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

            // Get system kind for special rendering
            SystemKind kind = model.getPrimaryKind();

            // --- Draw box background with special colors ---
            Color fillColor = getSystemFillColor(kind);
            Color borderColor = getSystemBorderColor(kind);

            g2.setColor(fillColor);
            g2.fillRect(0, 0, getWidth(), getHeight());

            // Draw special effects for certain systems
            drawSpecialEffects(g2, kind);

            // Draw border
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRect(1, 1, getWidth() - 2, getHeight() - 2);

            // Draw disabled overlay if needed
            if (!model.isEnabled()) {
                g2.setColor(new Color(50, 50, 50, 128));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }

            // --- Draw system label ---
            final String label = toDisplayName(kind);

            g2.setFont(Config.FONT_SYSTEM_LABEL);
            FontMetrics fm = g2.getFontMetrics();

            int tx = (getWidth() - fm.stringWidth(label)) / 2;
            int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;

            // Label background
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

            // Label text
            g2.setColor(model.isEnabled() ? getLabelColor(kind) : new Color(255, 255, 255, 180));
            g2.drawString(label, tx, ty);

            // Draw special indicators
            drawSpecialIndicators(g2, kind);

        } finally {
            g2.dispose();
        }
    }

    /**
     * Get fill color based on system kind
     */
    private Color getSystemFillColor(SystemKind kind) {
        if (kind == null) kind = SystemKind.NORMAL;

        switch (kind) {
            case SPY:
                return new Color(60, 30, 60); // Dark purple
            case MALICIOUS:
                return new Color(80, 20, 20); // Dark red
            case VPN:
                return new Color(20, 60, 80); // Dark blue
            case ANTI_TROJAN:
                return new Color(20, 70, 40); // Dark green
            case DISTRIBUTOR:
                return new Color(80, 60, 20); // Dark yellow
            case MERGER:
                return new Color(80, 50, 20); // Dark orange
            case PORT_RANDOMIZER:
                return new Color(60, 60, 60); // Dark gray
            case NORMAL:
            default:
                return Config.COLOR_BOX_FILL;
        }
    }

    /**
     * Get border color based on system kind
     */
    private Color getSystemBorderColor(SystemKind kind) {
        if (kind == null) kind = SystemKind.NORMAL;

        switch (kind) {
            case SPY:
                return new Color(150, 50, 150); // Purple
            case MALICIOUS:
                return new Color(200, 50, 50); // Red
            case VPN:
                return new Color(50, 150, 200); // Blue
            case ANTI_TROJAN:
                return new Color(50, 200, 100); // Green
            case DISTRIBUTOR:
                return new Color(200, 150, 50); // Yellow
            case MERGER:
                return new Color(200, 120, 50); // Orange
            case PORT_RANDOMIZER:
                return new Color(150, 150, 150); // Gray
            case NORMAL:
            default:
                return Config.COLOR_BOX_BORDER;
        }
    }

    /**
     * Get label color based on system kind
     */
    private Color getLabelColor(SystemKind kind) {
        if (kind == null || kind == SystemKind.NORMAL) {
            return Config.COLOR_BADGE_FG;
        }

        // Special systems get brighter labels
        return new Color(255, 255, 200); // Light yellow for visibility
    }

    /**
     * Draw special visual effects for certain systems
     */
    private void drawSpecialEffects(Graphics2D g2, SystemKind kind) {
        if (kind == null) return;

        switch (kind) {
            case ANTI_TROJAN:
                // Draw range indicator
                if (model.isEnabled()) {
                    g2.setColor(new Color(50, 200, 100, 30));
                    int radius = (int) Config.ANTI_TROJAN_RADIUS_PX;
                    g2.fillOval(
                            getWidth()/2 - radius,
                            getHeight()/2 - radius,
                            radius * 2,
                            radius * 2
                    );
                }
                break;

            case VPN:
                // Draw shield pattern
                g2.setColor(new Color(100, 150, 200, 40));
                for (int i = 0; i < 3; i++) {
                    g2.drawRect(5 + i*2, 5 + i*2,
                            getWidth() - 10 - i*4,
                            getHeight() - 10 - i*4);
                }
                break;

            case SPY:
                // Draw teleport effect corners
                g2.setColor(new Color(200, 100, 200, 80));
                int corner = 15;
                // Top-left
                g2.fillArc(0, 0, corner*2, corner*2, 90, 90);
                // Top-right
                g2.fillArc(getWidth()-corner*2, 0, corner*2, corner*2, 0, 90);
                // Bottom-left
                g2.fillArc(0, getHeight()-corner*2, corner*2, corner*2, 180, 90);
                // Bottom-right
                g2.fillArc(getWidth()-corner*2, getHeight()-corner*2, corner*2, corner*2, 270, 90);
                break;
        }
    }

    /**
     * Draw special indicators (icons/symbols)
     */
    private void drawSpecialIndicators(Graphics2D g2, SystemKind kind) {
        if (kind == null || kind == SystemKind.NORMAL) return;

        // Draw small icon in top-right corner
        int iconSize = 16;
        int iconX = getWidth() - iconSize - 5;
        int iconY = 5;

        g2.setColor(new Color(255, 255, 255, 200));
        g2.setFont(new Font("Dialog", Font.BOLD, 12));

        String icon = getSystemIcon(kind);
        if (!icon.isEmpty()) {
            g2.drawString(icon, iconX, iconY + 12);
        }
    }

    /**
     * Get icon/symbol for system kind
     */
    private String getSystemIcon(SystemKind kind) {
        switch (kind) {
            case SPY: return "◉";
            case MALICIOUS: return "☠";
            case VPN: return "⛨";
            case ANTI_TROJAN: return "✚";
            case DISTRIBUTOR: return "⊕";
            case MERGER: return "⊗";
            case PORT_RANDOMIZER: return "?";
            default: return "";
        }
    }

    public SystemBoxModel getModel() {
        return model;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        s = s.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String toDisplayName(SystemKind kind) {
        if (kind == null) return "Normal";
        switch (kind) {
            case NORMAL:           return "Normal";
            case VPN:              return "Vpn";
            case SPY:              return "Spy";
            case MALICIOUS:        return "Malicious";
            case DISTRIBUTOR:      return "Distributor";
            case MERGER:           return "Merger";
            case ANTI_TROJAN:      return "Anti-Trojan";
            case PORT_RANDOMIZER:  return "Port-Randomizer";
            default:               return capitalize(kind.name());
        }
    }
}
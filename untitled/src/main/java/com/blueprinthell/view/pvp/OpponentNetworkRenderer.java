package com.blueprinthell.view.pvp;

import com.blueprinthell.shared.protocol.NetworkProtocol.*;
import com.blueprinthell.view.screens.GameScreenView;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.List;

/**
 * رندر کردن شبکه حریف به صورت کم‌رنگ و غیرقابل تعامل
 */
public class OpponentNetworkRenderer extends JComponent {

    private static final float OPACITY = 0.35f;
    private static final Color OPPONENT_COLOR = new Color(255, 100, 100, 90);
    private static final Stroke WIRE_STROKE = new BasicStroke(
            2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
            0, new float[]{5, 5}, 0
    );

    private final List<SystemLayout> opponentBoxes;
    private final List<WireLayout> opponentWires;
    private final GameScreenView gameView;

    /**
     * Constructor
     */
    public OpponentNetworkRenderer(List<SystemLayout> boxes,
                                   List<WireLayout> wires,
                                   GameScreenView gameView) {
        this.opponentBoxes = boxes;
        this.opponentWires = wires;
        this.gameView = gameView;

        setOpaque(false);
        setLayout(null);
    }

    /**
     * Render opponent network
     */
    public void render() {
        if (gameView == null) return;

        // Add this component as overlay
        JPanel gameArea = gameView.getGameArea();
        setBounds(0, 0, gameArea.getWidth(), gameArea.getHeight());
        gameArea.add(this);
        gameArea.setComponentZOrder(this, 0); // On top

        // Add resize listener
        gameArea.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                setBounds(0, 0, gameArea.getWidth(), gameArea.getHeight());
            }
        });

        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();

        // Enable anti-aliasing
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // Set composite for transparency
        g2.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, OPACITY));

        // Draw wires
        drawWires(g2);

        // Draw boxes
        drawBoxes(g2);

        g2.dispose();
    }

    /**
     * Draw opponent's wires
     */
    private void drawWires(Graphics2D g2) {
        if (opponentWires == null) return;

        g2.setColor(OPPONENT_COLOR);
        g2.setStroke(WIRE_STROKE);

        for (WireLayout wire : opponentWires) {
            if (wire.path == null || wire.path.size() < 2) continue;

            Path2D path = new Path2D.Double();

            WireLayout.Point2D first = wire.path.get(0);
            path.moveTo(first.x, first.y);

            for (int i = 1; i < wire.path.size(); i++) {
                WireLayout.Point2D point = wire.path.get(i);
                path.lineTo(point.x, point.y);
            }

            g2.draw(path);

            // Draw arrow at destination
            if (wire.path.size() >= 2) {
                WireLayout.Point2D last = wire.path.get(wire.path.size() - 1);
                WireLayout.Point2D prev = wire.path.get(wire.path.size() - 2);
                drawArrow(g2, prev, last);
            }
        }
    }

    /**
     * Draw opponent's boxes
     */
    private void drawBoxes(Graphics2D g2) {
        if (opponentBoxes == null) return;

        for (SystemLayout box : opponentBoxes) {
            // Box background
            g2.setColor(new Color(100, 50, 50, 60));
            g2.fillRoundRect(box.x, box.y, box.width, box.height, 10, 10);

            // Box border
            g2.setColor(OPPONENT_COLOR);
            g2.setStroke(new BasicStroke(2.0f));
            g2.drawRoundRect(box.x, box.y, box.width, box.height, 10, 10);

            // Label
            String label = getBoxLabel(box);
            if (label != null) {
                g2.setFont(new Font("Arial", Font.BOLD, 11));
                FontMetrics fm = g2.getFontMetrics();
                int textWidth = fm.stringWidth(label);
                int textX = box.x + (box.width - textWidth) / 2;
                int textY = box.y - 5;

                // Background for label
                g2.setColor(new Color(0, 0, 0, 100));
                g2.fillRect(textX - 2, textY - fm.getHeight() + 2,
                        textWidth + 4, fm.getHeight());

                // Draw label
                g2.setColor(Color.WHITE);
                g2.drawString(label, textX, textY);
            }

            // Draw ports
            drawPorts(g2, box);
        }
    }

    /**
     * Draw ports for a box
     */
    private void drawPorts(Graphics2D g2, SystemLayout box) {
        int portSize = 10;

        // Input ports
        if (box.inShapes != null) {
            for (int i = 0; i < box.inShapes.size(); i++) {
                int portY = box.y + (i + 1) * box.height / (box.inShapes.size() + 1);

                g2.setColor(new Color(100, 255, 100, 100));
                g2.fillOval(box.x - portSize/2, portY - portSize/2, portSize, portSize);

                g2.setColor(Color.GREEN.darker());
                g2.drawOval(box.x - portSize/2, portY - portSize/2, portSize, portSize);
            }
        }

        // Output ports
        if (box.outShapes != null) {
            for (int i = 0; i < box.outShapes.size(); i++) {
                int portY = box.y + (i + 1) * box.height / (box.outShapes.size() + 1);

                g2.setColor(new Color(100, 100, 255, 100));
                g2.fillOval(box.x + box.width - portSize/2, portY - portSize/2,
                        portSize, portSize);

                g2.setColor(Color.BLUE.darker());
                g2.drawOval(box.x + box.width - portSize/2, portY - portSize/2,
                        portSize, portSize);
            }
        }
    }

    /**
     * Draw arrow head
     */
    private void drawArrow(Graphics2D g2, WireLayout.Point2D from, WireLayout.Point2D to) {
        double angle = Math.atan2(to.y - from.y, to.x - from.x);
        int arrowSize = 8;

        Path2D arrow = new Path2D.Double();
        arrow.moveTo(to.x, to.y);
        arrow.lineTo(
                to.x - arrowSize * Math.cos(angle - Math.PI/6),
                to.y - arrowSize * Math.sin(angle - Math.PI/6)
        );
        arrow.moveTo(to.x, to.y);
        arrow.lineTo(
                to.x - arrowSize * Math.cos(angle + Math.PI/6),
                to.y - arrowSize * Math.sin(angle + Math.PI/6)
        );

        g2.draw(arrow);
    }

    /**
     * Get label for box type
     */
    private String getBoxLabel(SystemLayout box) {
        if (box.isSource) return "SOURCE";
        if (box.isSink) return "SINK";
        if (box.kind != null) {
            return switch (box.kind) {
                case "NORMAL" -> null;
                case "VPN" -> "VPN";
                case "DISTRIBUTOR" -> "DIST";
                case "MERGER" -> "MERG";
                case "SPY" -> "SPY";
                case "MALICIOUS" -> "MAL";
                case "ANTI_TROJAN" -> "A-TRJ";
                case "PORT_RANDOMIZER" -> "RND";
                default -> box.kind;
            };
        }
        return null;
    }

    /**
     * Clean up
     */
    public void cleanup() {
        if (gameView != null && gameView.getGameArea() != null) {
            gameView.getGameArea().remove(this);
            gameView.getGameArea().repaint();
        }
    }
}
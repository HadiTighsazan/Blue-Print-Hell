package com.blueprinthell.view.pvp;

import com.blueprinthell.shared.protocol.NetworkProtocol.*;
import com.blueprinthell.snapshot.NetworkSnapshot;
import com.blueprinthell.view.screens.GameScreenView;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * رندر کردن شبکه حریف به صورت کم‌رنگ و غیرقابل تعامل
 * با قابلیت به‌روزرسانی زنده packets
 */
public class OpponentNetworkRenderer extends JComponent {

    private static final float OPACITY = 0.35f;
    private static final Color OPPONENT_COLOR = new Color(255, 100, 100, 90);
    private static final Color PACKET_COLOR = new Color(255, 150, 150, 120);
    private static final Stroke WIRE_STROKE = new BasicStroke(
            2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
            0, new float[]{5, 5}, 0
    );

    private List<SystemLayout> opponentBoxes;
    private List<WireLayout> opponentWires;
    private final GameScreenView gameView;

    // برای نمایش packets به صورت زنده
    private final List<PacketDisplay> livePackets = new CopyOnWriteArrayList<>();
    private NetworkSnapshot currentOpponentState;
    private Timer updateTimer;

    // کلاس داخلی برای نمایش packet
    private static class PacketDisplay {
        String wireId;
        double progress;
        String type;
        int x, y;
        Color color;

        PacketDisplay(String wireId, double progress, String type) {
            this.wireId = wireId;
            this.progress = progress;
            this.type = type;
            this.color = PACKET_COLOR;
        }
    }

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

        // شروع تایمر برای به‌روزرسانی مداوم نمای packets
        startUpdateTimer();
    }

    /**
     * به‌روزرسانی state حریف برای نمایش packets
     */
    public void updateOpponentState(NetworkSnapshot opponentState) {
        if (opponentState == null) return;

        this.currentOpponentState = opponentState;

        // به‌روزرسانی لیست packets
        livePackets.clear();

        if (opponentState.world != null && opponentState.world.wires != null) {
            for (NetworkSnapshot.WireState wireState : opponentState.world.wires) {
                if (wireState.packetsOnWire != null) {
                    for (NetworkSnapshot.PacketOnWire pow : wireState.packetsOnWire) {
                        PacketDisplay pd = new PacketDisplay(
                                wireState.id,
                                pow.progress,
                                pow.base.type
                        );

                        // محاسبه موقعیت packet بر روی wire
                        Point packetPos = calculatePacketPosition(wireState, pow.progress);
                        if (packetPos != null) {
                            pd.x = packetPos.x;
                            pd.y = packetPos.y;
                            livePackets.add(pd);
                        }
                    }
                }
            }
        }

        // درخواست repaint برای نمایش تغییرات
        repaint();
    }

    /**
     * محاسبه موقعیت packet بر روی wire
     */
    private Point calculatePacketPosition(NetworkSnapshot.WireState wireState, double progress) {
        if (wireState.path == null || wireState.path.size() < 2) return null;

        // محاسبه طول کل مسیر
        double totalLength = 0;
        List<Double> segmentLengths = new ArrayList<>();

        for (int i = 0; i < wireState.path.size() - 1; i++) {
            NetworkSnapshot.IntPoint p1 = wireState.path.get(i);
            NetworkSnapshot.IntPoint p2 = wireState.path.get(i + 1);
            double len = Math.hypot(p2.x - p1.x, p2.y - p1.y);
            segmentLengths.add(len);
            totalLength += len;
        }

        // پیدا کردن segment مناسب بر اساس progress
        double targetDistance = totalLength * progress;
        double accumulated = 0;

        for (int i = 0; i < segmentLengths.size(); i++) {
            double segLen = segmentLengths.get(i);

            if (accumulated + segLen >= targetDistance) {
                // packet در این segment است
                NetworkSnapshot.IntPoint p1 = wireState.path.get(i);
                NetworkSnapshot.IntPoint p2 = wireState.path.get(i + 1);

                double segProgress = (targetDistance - accumulated) / segLen;
                int x = (int)(p1.x + (p2.x - p1.x) * segProgress);
                int y = (int)(p1.y + (p2.y - p1.y) * segProgress);

                return new Point(x, y);
            }

            accumulated += segLen;
        }

        // اگر به اینجا رسیدیم، packet در انتهای wire است
        NetworkSnapshot.IntPoint last = wireState.path.get(wireState.path.size() - 1);
        return new Point(last.x, last.y);
    }

    /**
     * شروع تایمر برای refresh مداوم
     */
    private void startUpdateTimer() {
        updateTimer = new Timer(50, e -> repaint());
        updateTimer.start();
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

        // Draw live packets
        drawPackets(g2);

        g2.dispose();
    }

    /**
     * رسم packets زنده
     */
    private void drawPackets(Graphics2D g2) {
        if (livePackets.isEmpty()) return;

        // افزایش opacity برای packets تا بهتر دیده شوند
        g2.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, 0.6f));

        for (PacketDisplay packet : livePackets) {
            // رسم packet به صورت دایره درخشان
            int size = 12;

            // سایه برای packet
            g2.setColor(new Color(0, 0, 0, 50));
            g2.fillOval(packet.x - size/2 + 2, packet.y - size/2 + 2, size, size);

            // خود packet
            g2.setColor(packet.color);
            g2.fillOval(packet.x - size/2, packet.y - size/2, size, size);

            // حاشیه درخشان
            g2.setColor(new Color(255, 200, 200, 180));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(packet.x - size/2, packet.y - size/2, size, size);

            // افکت glow
            RadialGradientPaint gradient = new RadialGradientPaint(
                    packet.x, packet.y, size,
                    new float[]{0f, 0.5f, 1f},
                    new Color[]{
                            new Color(255, 255, 255, 100),
                            new Color(255, 200, 200, 50),
                            new Color(255, 200, 200, 0)
                    }
            );
            g2.setPaint(gradient);
            g2.fillOval(packet.x - size, packet.y - size, size * 2, size * 2);
        }

        // بازگرداندن composite اصلی
        g2.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, OPACITY));
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
        if (updateTimer != null) {
            updateTimer.stop();
        }

        if (gameView != null && gameView.getGameArea() != null) {
            gameView.getGameArea().remove(this);
            gameView.getGameArea().repaint();
        }
    }
}
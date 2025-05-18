package com.blueprinthell.ui;

import com.blueprinthell.model.Port;

import javax.swing.*;
import java.awt.*;


public class WirePreviewLayer extends JComponent {
    private final InputManager im;

    public WirePreviewLayer(InputManager im) {
        this.im = im;
        setOpaque(false);
    }

    @Override
    public boolean contains(int x, int y) {
        return im.getDragSource() != null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Port src = im.getDragSource();
        Point mouse = im.getMousePos();

        if (src != null && mouse != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setStroke(new BasicStroke(2f));

            boolean enough = im.isEnoughLength();
            boolean valid = enough && im.isValidTarget();
            g2.setColor(valid ? Color.GREEN : Color.RED);

            Point p1 = SwingUtilities.convertPoint(
                    src, src.getWidth() / 2, src.getHeight() / 2, this
            );
            g2.drawLine(p1.x, p1.y, mouse.x, mouse.y);
            g2.dispose();
        }
    }
}

package com.blueprinthell.ui;

import com.blueprinthell.engine.NetworkController;
import com.blueprinthell.model.Port;
import com.blueprinthell.model.Wire;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.Serializable;
import java.util.List;




public class WirePreviewLayer extends JComponent {
    private final InputManager im;

    public WirePreviewLayer(InputManager inputManager) {
        setOpaque(false);
        this.im = inputManager;

    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Port src = im.getDragSource();
        Point mouse = im.getMousePos();
        if (src != null && mouse != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(im.isValidTarget() && im.isEnoughLength()
                    ? Color.GREEN : Color.RED);

            Point p1 = SwingUtilities.convertPoint(
                    src,
                    src.getWidth()/2, src.getHeight()/2,
                    this
            );
            Point p2 = mouse;

            g2.drawLine(p1.x, p1.y, p2.x, p2.y);
            g2.dispose();
        }
    }


}
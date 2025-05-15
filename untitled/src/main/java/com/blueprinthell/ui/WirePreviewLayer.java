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
    public boolean contains(int x, int y) {
        // فقط وقتی داریم درگ می‌کنیم، خودِ این لایه را مصرف کن:
        return im.getDragSource() != null;
    }


    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (im.getDragSource() != null && im.getMousePos() != null) {
            Point start = SwingUtilities.convertPoint(
                    im.getDragSource(),
                    im.getDragSource().getWidth()/2,
                    im.getDragSource().getHeight()/2,
                    this
            );
            Point end = im.getMousePos(); // این قبلاً به previewLayer ترجمه شده

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(im.isValidTarget() && im.isEnoughLength()
                    ? Color.GREEN : Color.RED);
            g2.drawLine(start.x, start.y, end.x, end.y);
            g2.dispose();
        }
    }



}
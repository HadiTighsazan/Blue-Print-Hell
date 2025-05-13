package com.blueprinthell;

import com.blueprinthell.engine.NetworkController;
import com.blueprinthell.model.Port;
import com.blueprinthell.model.SystemBox;
import com.blueprinthell.model.Wire;
import com.blueprinthell.ui.InputManager;
import com.blueprinthell.ui.WirePreviewLayer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Arrays;

public class WirePreviewDemo {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(WirePreviewDemo::createAndShow);
    }

    private static void createAndShow() {
        final int W = 800, H = 600;
        JFrame frame = new JFrame("Wire Preview & Drag-Drop Demo");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(W, H);
        frame.getContentPane().setLayout(null);

        SystemBox left  = new SystemBox(20,  H/2 - 40, 100, 80, 0, 1);
        SystemBox right = new SystemBox(W-120, H/2 - 40, 100, 80, 1, 0);
        frame.add(left);
        frame.add(right);

        NetworkController controller = new NetworkController(
                Arrays.asList(),
                Arrays.asList(left, right),
                1000
        );

        InputManager im = new InputManager(controller);
        im.registerHitContainer(frame.getContentPane());

        WirePreviewLayer preview = new WirePreviewLayer(im);
        preview.setBounds(0, 0, W, H);
        preview.setOpaque(false);
        frame.getLayeredPane().add(preview, JLayeredPane.DRAG_LAYER);
        im.registerEventContainer(preview);

        for (Port p : left.getOutPorts())  im.registerPort(p);
        for (Port p : right.getInPorts()) im.registerPort(p);

        im.setWireCreatedCallback(w -> {
            frame.getContentPane().add(w);
            frame.getContentPane().revalidate();
            frame.getContentPane().repaint();
        });

        frame.getContentPane().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                Dimension d = frame.getContentPane().getSize();
                preview.setBounds(0,0,d.width,d.height);
            }
        });

        JLabel lbl = new JLabel("Remaining length: 1000 px");
        lbl.setBounds(10,10,300,25);
        frame.add(lbl);
        new Timer(200, e -> {
            lbl.setText("Remaining length: " +
                    String.format("%.0f", controller.getRemainingWireLength()) + " px");
        }).start();

        frame.setVisible(true);
    }
}

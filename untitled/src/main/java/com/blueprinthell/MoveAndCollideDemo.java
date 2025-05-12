// MoveAndCollideDemo.java
package com.blueprinthell;

import com.blueprinthell.model.*;
import com.blueprinthell.engine.NetworkController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Arrays;

public class MoveAndCollideDemo {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(MoveAndCollideDemo::createAndShow);
    }

    private static void createAndShow() {
        final int W = 800, H = 600;
        JFrame frame = new JFrame("Move & Collide Demo");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(W, H);
        frame.getContentPane().setLayout(null);


        SystemBox left   = new SystemBox(  20, H/2 - 50, 100, 80, 0, 1);
        SystemBox right  = new SystemBox(W-120, H/2 - 50, 100, 80, 1, 0);
        SystemBox top    = new SystemBox(W/2 - 50,  20, 100, 80, 0, 1);
        SystemBox bottom = new SystemBox(W/2 - 50, H-100, 100, 80, 1, 0);
        frame.add(left);
        frame.add(right);
        frame.add(top);
        frame.add(bottom);

        Wire horizontal = new Wire(
                left.getOutPorts().get(0),
                right.getInPorts().get(0)
        );
        Wire vertical = new Wire(
                top.getOutPorts().get(0),
                bottom.getInPorts().get(0)
        );
        frame.add(horizontal);
        horizontal.setBounds(0, 0, W, H);

        frame.add(vertical);
        vertical.setBounds(  0, 0, W, H);


        NetworkController controller = new NetworkController(
                Arrays.asList(horizontal, vertical),
                Arrays.asList(left, right, top, bottom)
        );

        Packet pH = new Packet(PacketType.SQUARE, 100);
        horizontal.attachPacket(pH, 0.0);
        frame.add(pH);

        Packet pV = new Packet(PacketType.TRIANGLE, 80);
        vertical.attachPacket(pV, 0.0);
        frame.add(pV);

        JLabel lbl = new JLabel();
        lbl.setBounds(10, 10, 600, 20);
        lbl.setFont(new Font("Monospaced", Font.PLAIN, 14));
        frame.add(lbl);

        new Timer(16, (ActionEvent e) -> {
            controller.tick(0.016);
            lbl.setText(String.format(
                    "Noise(Square)=%.1f   Noise(Triangle)=%.1f   PacketLoss=%d",
                    pH.getNoise(), pV.getNoise(), controller.getPacketLoss()
            ));
            frame.repaint();
        }).start();

        frame.setVisible(true);

        SwingUtilities.invokeLater(() -> {
            Container cp = frame.getContentPane();
            Dimension dim = cp.getSize();
            horizontal.setBounds(0, 0, dim.width, dim.height);
            vertical  .setBounds(0, 0, dim.width, dim.height);
        });
    }
}

package com.blueprinthell;

import com.blueprinthell.model.*;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::createAndShow);
    }

    private static void createAndShow() {
        JFrame frame = new JFrame("Blue-Print-Hell – Demo 3 Systems (Start Sys has no inputs)");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1000, 600);
        frame.setLayout(null);

        SystemBox sysA = new SystemBox(100, 200, 140, 90, /*in=*/0, /*out=*/2);
        frame.add(sysA);

        SystemBox sysB = new SystemBox(400, 200, 140, 90, /*in=*/2, /*out=*/1);
        frame.add(sysB);

        SystemBox sysC = new SystemBox(700, 200, 140, 90, /*in=*/1, /*out=*/1);
        frame.add(sysC);


        Wire w1 = new Wire(sysA.getOutPorts().get(0), sysB.getInPorts().get(0));
        frame.add(w1);
        Wire w2 = new Wire(sysA.getOutPorts().get(1), sysB.getInPorts().get(1));
        frame.add(w2);
        Wire w3 = new Wire(sysB.getOutPorts().get(0), sysC.getInPorts().get(0));
        frame.add(w3);

        Packet pkt = new Packet(PacketType.SQUARE, 100);
        w2.attachPacket(pkt, 0.0);
        frame.add(pkt);


        Timer timer = new Timer(16, (ActionEvent e) -> {
            double dt = 0.016;
            w1.update(dt);
            w2.update(dt);
            w3.update(dt);
            frame.repaint();
        });
        timer.start();

        frame.setVisible(true);
    }
}

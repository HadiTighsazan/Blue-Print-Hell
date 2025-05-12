package com.blueprinthell;

import com.blueprinthell.engine.TimelineController;
import com.blueprinthell.engine.NetworkController;
import com.blueprinthell.model.*;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;


public class TimelineMultiPacketDemo {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(TimelineMultiPacketDemo::createAndShow);
    }

    private static void createAndShow() {
        final int W = 800, H = 600;
        JFrame frame = new JFrame("Timeline Multi-Packet Demo");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(W, H);
        frame.getContentPane().setLayout(null);

        SystemBox left   = new SystemBox(20,      H/2-40, 100, 80, 0, 1);
        SystemBox right  = new SystemBox(W-120,  H/2-40, 100, 80, 1, 0);
        SystemBox top    = new SystemBox(W/2-50,  20,     100, 80, 0, 1);
        SystemBox bottom = new SystemBox(W/2-50,  H-100,  100, 80, 1, 0);
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
        frame.add(vertical);

        Runnable syncWireBounds = () -> {
            Container cp = frame.getContentPane();
            horizontal.setBounds(0, 0, cp.getWidth(), cp.getHeight());
            vertical  .setBounds(0, 0, cp.getWidth(), cp.getHeight());
        };
        SwingUtilities.invokeLater(syncWireBounds);
        frame.getContentPane().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                syncWireBounds.run();
            }
        });

        NetworkController controller = new NetworkController(
                Arrays.asList(horizontal, vertical),
                Arrays.asList(left, right, top, bottom)
        );
        TimelineController timeline = new TimelineController(controller, 300);

        Packet squarePkt   = new Packet(PacketType.SQUARE,   120);
        Packet trianglePkt = new Packet(PacketType.TRIANGLE, 80);
        horizontal.attachPacket(squarePkt,   0.0);
        vertical.attachPacket(trianglePkt, 0.0);
        frame.add(squarePkt);
        frame.add(trianglePkt);

        JLabel lblOff = new JLabel("offset=0");
        lblOff.setBounds(10, 10, 200, 25);
        frame.add(lblOff);

        JSlider slider = new JSlider(0, 299, 0);
        slider.setBounds(220, 10, 360, 25);
        slider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (!slider.getValueIsAdjusting() && !timeline.isPlaying()) {
                    int off = slider.getValue();
                    syncWireBounds.run();
                    SwingUtilities.invokeLater(() -> {
                        timeline.scrubTo(off);
                        lblOff.setText("offset=" + off);
                        updateUIPackets(frame, horizontal, vertical);
                    });
                }
            }
        });
        frame.add(slider);

        JButton btn = new JButton("Pause");
        btn.setBounds(600, 10, 100, 25);
        btn.addActionListener((ActionEvent e) -> {
            if (timeline.isPlaying()) {
                timeline.pause();
                btn.setText("Play");
                updateUIPackets(frame, horizontal, vertical);
            } else {
                syncWireBounds.run();
                SwingUtilities.invokeLater(() -> {
                    timeline.resume();
                    slider.setValue(0);
                    lblOff.setText("offset=0");
                    updateUIPackets(frame, horizontal, vertical);
                    btn.setText("Pause");
                });
            }
        });
        frame.add(btn);

        final double dt = 1/60.0;
        new Timer((int)(dt * 1000), (ActionEvent e) -> {
            if (timeline.isPlaying()) {
                controller.tick(dt);
                timeline.recordFrame();
                if (slider.getValue() != 0) {
                    slider.setValue(0);
                    lblOff.setText("offset=0");
                }
            }
            frame.repaint();
        }).start();

        frame.setVisible(true);
    }

    private static void updateUIPackets(JFrame frame, Wire... wires) {
        Container cp = frame.getContentPane();
        for (Component c : cp.getComponents()) {
            if (c instanceof Packet) cp.remove(c);
        }
        for (Wire w : wires) {
            for (Packet p : w.getPackets()) {
                cp.add(p);
            }
        }
        cp.revalidate();
        cp.repaint();
    }
}

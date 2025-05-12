package com.blueprinthell.model;

import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;


public class SystemBox extends GameObject implements Serializable {
    private static final long serialVersionUID = 5L;

    private final List<Port> inPorts  = new ArrayList<>();
    private final List<Port> outPorts = new ArrayList<>();
    private final Queue<Packet> buffer = new ArrayDeque<>(5);

    public SystemBox(int x, int y, int w, int h, int inCount, int outCount) {
        super(x, y, w, h);
        setLayout(null);
        setOpaque(true);
        setBackground(new Color(0x444444));
        createPorts(inCount, outCount);
    }

    private void createPorts(int inCnt, int outCnt) {
        int size = 14;
        for (int i = 0; i < inCnt; i++) {
            int yOff = (i+1)*getHeight()/(inCnt+1) - size/2;
            Port p = new Port(0, yOff, size, PortShape.SQUARE, true);
            inPorts.add(p);
            add(p);
        }
        for (int i = 0; i < outCnt; i++) {
            int yOff = (i+1)*getHeight()/(outCnt+1) - size/2;
            Port p = new Port(getWidth()-size, yOff, size, PortShape.SQUARE, false);
            outPorts.add(p);
            add(p);
        }
    }

    public boolean enqueue(Packet p) {
        if (buffer.size() < 5) {
            return buffer.offer(p);
        }
        return false;
    }

    public Packet pollPacket() {
        return buffer.poll();
    }

    public List<Port> getInPorts()  { return inPorts; }
    public List<Port> getOutPorts() { return outPorts; }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(0x888888));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(Color.WHITE);
        g2.drawRect(0, 0, getWidth()-1, getHeight()-1);
        g2.dispose();
    }
}

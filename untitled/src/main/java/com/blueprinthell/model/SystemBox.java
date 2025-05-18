package com.blueprinthell.model;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;


public class SystemBox extends GameObject implements Serializable {
    private static final long serialVersionUID = 5L;

    private final List<Port> inPorts  = new ArrayList<>();
    private final List<Port> outPorts = new ArrayList<>();
    private final Queue<Packet> buffer = new ArrayDeque<>(5);

    private final PortShape inShape;
    private final PortShape outShape;

    public SystemBox(int x, int y, int w, int h, int inCount, int outCount) {
        this(x, y, w, h,
                Collections.nCopies(inCount, PortShape.SQUARE),
                Collections.nCopies(outCount, PortShape.SQUARE));
    }

    public SystemBox(int x, int y, int w, int h,
                     int inCount, PortShape inShape,
                     int outCount, PortShape outShape) {
        this(x, y, w, h,
                Collections.nCopies(inCount, inShape),
                Collections.nCopies(outCount, outShape));
    }

    public SystemBox(int x, int y, int w, int h,
                     List<PortShape> inShapes,
                     List<PortShape> outShapes) {
        super(x, y, w, h);
        this.inShape = null;
        this.outShape = null;
        setLayout(null);
        createPorts(inShapes, outShapes);
        setBackground(new Color(0x444444));
        setOpaque(true);
        MouseAdapter drag = new MouseAdapter() {
            private Point offset;
            @Override public void mousePressed(MouseEvent e) { offset = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e) {
                int newX = getX() + e.getX() - offset.x;
                int newY = getY() + e.getY() - offset.y;
                newX = Math.max(0, Math.min(newX, getParent().getWidth() - getWidth()));
                newY = Math.max(0, Math.min(newY, getParent().getHeight() - getHeight()));
                setLocation(newX, newY);
                getParent().repaint();
            }
        };
        addMouseListener(drag);
        addMouseMotionListener(drag);
    }

    private void createPorts(int inCnt, int outCnt) {
        createPorts(
                Collections.nCopies(inCnt, inShape),
                Collections.nCopies(outCnt, outShape)
        );
    }

    private void createPorts(List<PortShape> inShapes, List<PortShape> outShapes) {
        int portSize = 14;
        for (int i = 0; i < inShapes.size(); i++) {
            int yOffset = (i + 1) * getHeight() / (inShapes.size() + 1) - portSize/2;
            Port p = new Port(0, yOffset, portSize, inShapes.get(i), true);
            inPorts.add(p);
            add(p);
        }
        for (int i = 0; i < outShapes.size(); i++) {
            int yOffset = (i + 1) * getHeight() / (outShapes.size() + 1) - portSize/2;
            Port p = new Port(getWidth()-portSize, yOffset, portSize, outShapes.get(i), false);
            outPorts.add(p);
            add(p);
        }
    }

    public List<Port> getInPorts() { return inPorts; }
    public List<Port> getOutPorts() { return outPorts; }

    public boolean enqueue(Packet p) {
        if (buffer.size() < 5) {
            buffer.add(p);
            return true;
        }
        return false;
    }
    public Packet pollPacket() { return buffer.poll(); }
    public void clearBuffer()    { buffer.clear(); }
    public Queue<Packet> getBuffer() { return buffer; }

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

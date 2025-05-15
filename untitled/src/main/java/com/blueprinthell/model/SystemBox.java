// SystemBox.java
package com.blueprinthell.model;

import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * A network node with input- and output-Ports laid out along its left and right edges.
 */
public class SystemBox extends GameObject implements Serializable {
    private static final long serialVersionUID = 5L;

    private final List<Port> inPorts  = new ArrayList<>();
    private final List<Port> outPorts = new ArrayList<>();
    private final Queue<Packet> buffer = new ArrayDeque<>(5);

    public SystemBox(int x, int y, int w, int h, int inCount, int outCount) {
        super(x, y, w, h);
        setLayout(null);              // absolute positioning of ports
        createPorts(inCount, outCount);
        setBackground(new Color(0x444444));
        setOpaque(true);
    }

    private void createPorts(int inCnt, int outCnt) {
        int portSize = 14;
        // ورودی‌ها در x=0 تا x=portSize داخل کادر
        for (int i = 0; i < inCnt; i++) {
            int yOffset = (i + 1) * getHeight() / (inCnt + 1) - portSize / 2;
            Port p = new Port(
                    /*x=*/ 0,
                    /*y=*/ yOffset,
                    /*size=*/ portSize,
                    /*shape=*/ PortShape.SQUARE,
                    /*input=*/ true
            );
            inPorts.add(p);
            add(p);
        }
        // خروجی‌ها در x = width-portSize تا width داخل کادر
        for (int i = 0; i < outCnt; i++) {
            int yOffset = (i + 1) * getHeight() / (outCnt + 1) - portSize / 2;
            Port p = new Port(
                    /*x=*/ getWidth() - portSize,
                    /*y=*/ yOffset,
                    /*size=*/ portSize,
                    /*shape=*/ PortShape.SQUARE,
                    /*input=*/ false
            );
            outPorts.add(p);
            add(p);
        }
    }

    /** دریافت لیست پورت‌های ورودی */
    public List<Port> getInPorts() {
        return inPorts;
    }

    /** دریافت لیست پورت‌های خروجی */
    public List<Port> getOutPorts() {
        return outPorts;
    }

    /** تلاش برای افزودن Packet به بافر (حداکثر 5) */
    public boolean enqueue(Packet p) {
        if (buffer.size() < 5) {
            buffer.add(p);
            return true;
        }
        return false;
    }

    /** بیرون کشیدن یک Packet از بافر */
    public Packet pollPacket() {
        return buffer.poll();
    }

    /** برای پاک کردن بافر (مثلاً در restoreState) */
    public void clearBuffer() {
        buffer.clear();
    }

    /** فقط برای دسترسی به بافر در SnapshotManager */
    public Queue<Packet> getBuffer() {
        return buffer;
    }

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

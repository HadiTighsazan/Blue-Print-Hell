package com.blueprinthell.model;

import com.blueprinthell.config.Config;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

/**
 * Domain model for a system box, holding input/output ports and packet buffer.
 */
public class SystemBoxModel extends GameObjectModel implements Serializable {
    private static final long serialVersionUID = 5L;

    private final List<PortModel> inPorts = new ArrayList<>();
    private final List<PortModel> outPorts = new ArrayList<>();
    private final Queue<PacketModel> buffer;

    /**
     * Constructs a SystemBoxModel with specified position, size, and port shapes.
     */
    public SystemBoxModel(int x, int y, int width, int height,
                          List<PortShape> inShapes,
                          List<PortShape> outShapes) {
        super(x, y, width, height);
        this.buffer = new ArrayDeque<>(Config.MAX_BUFFER_CAPACITY);
        createPorts(inShapes, outShapes);
    }

    /**
     * Convenience constructor for uniform port shapes/counts.
     */
    public SystemBoxModel(int x, int y, int width, int height,
                          int inCount, PortShape inShape,
                          int outCount, PortShape outShape) {
        this(x, y, width, height,
                java.util.Collections.nCopies(inCount, inShape),
                java.util.Collections.nCopies(outCount, outShape));
    }

    private void createPorts(List<PortShape> inShapes, List<PortShape> outShapes) {
        int portSize = Config.PORT_SIZE;
        for (int i = 0; i < inShapes.size(); i++) {
            int yOffset = (i + 1) * getHeight() / (inShapes.size() + 1) - portSize / 2;
            inPorts.add(new PortModel(getX(), getY() + yOffset, inShapes.get(i), true));
        }
        for (int i = 0; i < outShapes.size(); i++) {
            int yOffset = (i + 1) * getHeight() / (outShapes.size() + 1) - portSize / 2;
            outPorts.add(new PortModel(getX() + getWidth() - portSize, getY() + yOffset, outShapes.get(i), false));
        }
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        updatePortsPosition();
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        updatePortsPosition();
    }

    private void updatePortsPosition() {
        int portSize = Config.PORT_SIZE;
        for (int i = 0; i < inPorts.size(); i++) {
            int yOffset = (i + 1) * getHeight() / (inPorts.size() + 1) - portSize / 2;
            PortModel pm = inPorts.get(i);
            pm.setX(getX());
            pm.setY(getY() + yOffset);
        }
        for (int i = 0; i < outPorts.size(); i++) {
            int yOffset = (i + 1) * getHeight() / (outPorts.size() + 1) - portSize / 2;
            PortModel pm = outPorts.get(i);
            pm.setX(getX() + getWidth() - portSize);
            pm.setY(getY() + yOffset);
        }
    }

    /**
     * Returns the sequence of input port shapes.
     */
    public List<PortShape> getInShapes() {
        return inPorts.stream().map(PortModel::getShape).collect(Collectors.toList());
    }

    /**
     * Returns the sequence of output port shapes.
     */
    public List<PortShape> getOutShapes() {
        return outPorts.stream().map(PortModel::getShape).collect(Collectors.toList());
    }

    /**
     * Returns the input port models.
     */
    public List<PortModel> getInPorts() {
        return java.util.Collections.unmodifiableList(inPorts);
    }

    /**
     * Returns the output port models.
     */
    public List<PortModel> getOutPorts() {
        return java.util.Collections.unmodifiableList(outPorts);
    }

    /**
     * Attempts to enqueue a packet; returns true if accepted, false if buffer full.
     */
    public boolean enqueue(PacketModel packet) {
        if (buffer.size() < Config.MAX_BUFFER_CAPACITY) {
            buffer.add(packet);
            return true;
        }
        return false;
    }

    public PacketModel pollPacket() {
        return buffer.poll();
    }

    public void clearBuffer() {
        buffer.clear();
    }

    public Queue<PacketModel> getBuffer() {
        return java.util.Collections.unmodifiableCollection(buffer) instanceof Queue
                ? (Queue<PacketModel>) java.util.Collections.unmodifiableCollection(buffer)
                : new ArrayDeque<>(buffer);
    }
}

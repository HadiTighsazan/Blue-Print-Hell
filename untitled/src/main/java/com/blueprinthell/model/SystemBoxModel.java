package com.blueprinthell.model;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.systems.SystemBehavior;
import com.blueprinthell.controller.systems.SystemBehaviorAdapter;
import com.blueprinthell.controller.systems.SystemKind;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class SystemBoxModel extends GameObjectModel implements Serializable, Updatable {
    private static final long serialVersionUID = 5L;

    private final List<PortModel> inPorts = new ArrayList<>();
    private final List<PortModel> outPorts = new ArrayList<>();

    private Queue<PacketModel> buffer;

    private boolean enabled = true;
    private double disableTimer = 0.0;

    private final List<SystemBehavior> behaviors = new ArrayList<>();


    private final Queue<PacketEntry> newEntries = new ConcurrentLinkedQueue<>();

    private boolean lastEnabledState = true;

    private SystemKind primaryKind = SystemKind.NORMAL;

    public static class PacketEntry {
        public final PacketModel packet;
        public final PortModel enteredPort;

        public PacketEntry(PacketModel packet, PortModel enteredPort) {
            this.packet = packet;
            this.enteredPort = enteredPort;
        }
    }


    public SystemBoxModel(int x, int y, int width, int height,
                          List<PortShape> inShapes,
                          List<PortShape> outShapes) {
        super(x, y, width, height);
        this.buffer = new ArrayDeque<>(Config.MAX_BUFFER_CAPACITY);
        createPorts(inShapes, outShapes);
    }


    public SystemBoxModel(int x, int y, int width, int height,
                          int inCount, PortShape inShape,
                          int outCount, PortShape outShape) {
        this(x, y, width, height,
                java.util.Collections.nCopies(inCount, inShape),
                java.util.Collections.nCopies(outCount, outShape));
    }
    public SystemKind getPrimaryKind() {
        return primaryKind;
    }
    public void setPrimaryKind(SystemKind kind) {
        this.primaryKind = (kind != null) ? kind : SystemKind.NORMAL;
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

    public List<PortShape> getInShapes() {
        return inPorts.stream().map(PortModel::getShape).collect(Collectors.toList());
    }

    public List<PortShape> getOutShapes() {
        return outPorts.stream().map(PortModel::getShape).collect(Collectors.toList());
    }

    public List<PortModel> getInPorts() {
        return java.util.Collections.unmodifiableList(inPorts);
    }

    public List<PortModel> getOutPorts() {
        return java.util.Collections.unmodifiableList(outPorts);
    }

    public void addBehavior(SystemBehavior behavior) {
        if (!behaviors.contains(behavior)) {
            behaviors.add(behavior);
        }
    }

    public void removeBehavior(SystemBehavior behavior) {
        behaviors.remove(behavior);
    }

    public List<SystemBehavior> getBehaviors() {
        return Collections.unmodifiableList(behaviors);
    }


    public boolean enqueue(PacketModel packet, PortModel enteredPort) {
        if (packet == null) return false;

        if (!enabled && enteredPort != null) {
            return false;
        }

        if (buffer.size() >= Config.MAX_BUFFER_CAPACITY) {
            return false;
        }

        if (enteredPort != null) {
            SystemBehaviorAdapter.EnteredPortTracker.record(packet, enteredPort);
        }

        final boolean added = buffer.offer(packet);
        if (added) {
            newEntries.offer(new PacketEntry(packet, enteredPort));
        }

        return added;
    }

    public boolean enqueue(PacketModel packet) {
        return enqueue(packet, null);
    }

    public PacketModel pollPacket() {
        return buffer.poll();
    }

    public void clearBuffer() {
        buffer.clear();
        newEntries.clear();
        SystemBehaviorAdapter.EnteredPortTracker.clear();
    }


    public Queue<PacketModel> getBuffer() {
        return buffer;
    }

    public void disable() {
        this.enabled = false;
        this.disableTimer = Config.SYSTEM_DISABLE_DURATION;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void update(double dt) {
        if (!enabled) {
            disableTimer -= dt;
            if (disableTimer <= 0) {
                enabled = true;
            }
        }

        if (enabled != lastEnabledState) {
            for (SystemBehavior behavior : behaviors) {
                behavior.onEnabledChanged(enabled);
            }
            lastEnabledState = enabled;
        }

        for (SystemBehavior behavior : behaviors) {
            behavior.update(dt);
        }

        PacketEntry entry;
        while ((entry = newEntries.poll()) != null) {
            for (SystemBehavior behavior : behaviors) {
                behavior.onPacketEnqueued(entry.packet, entry.enteredPort);
            }
        }
    }

    public void addOutputPort(PortShape shape) {
        if (outPorts.size() >= Config.MAX_OUTPUT_PORTS) return;
        int portSize = Config.PORT_SIZE;
        PortModel newPort = new PortModel(
                getX() + getWidth() - portSize,
                getY(),
                shape,
                false
        );
        outPorts.add(newPort);
        updatePortsPosition();
    }

    public boolean removeOutputPort() {
        if (outPorts.isEmpty()) return false;
        outPorts.remove(outPorts.size() - 1);
        updatePortsPosition();
        return true;
    }


    public boolean removeFromBuffer(PacketModel packet) {
        if (packet == null) return false;
        return buffer.remove(packet);
    }


    public boolean enqueueFront(PacketModel packet) {
        if (packet == null) return false;
        if (buffer instanceof java.util.Deque<PacketModel> deq) {
            if (deq.size() >= Config.MAX_BUFFER_CAPACITY) return false;
            deq.addFirst(packet);
            newEntries.offer(new PacketEntry(packet, null));
            return true;
        }
        return enqueue(packet, null);
    }
}

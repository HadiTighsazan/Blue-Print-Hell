package com.blueprinthell.model;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.systems.SystemBehavior;
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

    // ثبت behavior های متصل به این باکس
    private final List<SystemBehavior> behaviors = new ArrayList<>();

    // صف ورودی‌های جدید با اطلاعات پورت
    private final Queue<PacketEntry> newEntries = new ConcurrentLinkedQueue<>();

    // کلاس داخلی برای نگهداری پکت و پورت ورودی
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

    // متد جدید برای ثبت behavior
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

    // متد بهبود یافته enqueue با ثبت پورت ورودی
    public boolean enqueue(PacketModel packet, PortModel enteredPort) {
        if (!enabled) return false;

        if (getInPorts().isEmpty()) {
            if (buffer == null) {
                buffer = new ArrayDeque<>();
            }
            boolean added = buffer.offer(packet);
            if (added) {
                newEntries.offer(new PacketEntry(packet, enteredPort));
            }
            return added;
        }

        if (buffer.size() < Config.MAX_BUFFER_CAPACITY) {
            buffer.add(packet);
            newEntries.offer(new PacketEntry(packet, enteredPort));
            return true;
        }
        return false;
    }

    // متد قدیمی برای سازگاری
    public boolean enqueue(PacketModel packet) {
        return enqueue(packet, null);
    }

    public PacketModel pollPacket() {
        return buffer.poll();
    }

    public void clearBuffer() {
        buffer.clear();
        newEntries.clear();
    }

    public Queue<PacketModel> getBuffer() {
        return java.util.Collections.unmodifiableCollection(buffer) instanceof Queue
                ? (Queue<PacketModel>) java.util.Collections.unmodifiableCollection(buffer)
                : new ArrayDeque<>(buffer);
    }

    public void disable() {
        this.enabled = false;
        this.disableTimer = Config.SYSTEM_DISABLE_DURATION;
        // اطلاع به behavior ها
        for (SystemBehavior behavior : behaviors) {
            behavior.onEnabledChanged(false);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void update(double dt) {
        // بررسی وضعیت فعال/غیرفعال
        if (!enabled) {
            disableTimer -= dt;
            if (disableTimer <= 0) {
                enabled = true;
                // اطلاع به behavior ها
                for (SystemBehavior behavior : behaviors) {
                    behavior.onEnabledChanged(true);
                }
            }
        }

        // پردازش پکت‌های جدید و اطلاع به behavior ها
        PacketEntry entry;
        while ((entry = newEntries.poll()) != null) {
            for (SystemBehavior behavior : behaviors) {
                behavior.onPacketEnqueued(entry.packet, entry.enteredPort);
            }
        }

        // فراخوانی update behavior ها
        for (SystemBehavior behavior : behaviors) {
            behavior.update(dt);
        }
    }

    public void addOutputPort(PortShape shape) {
        if (outPorts.size() >= Config.MAX_OUTPUT_PORTS) return;
        int portSize = Config.PORT_SIZE;
        outPorts.add(new PortModel(getX() + getWidth() - portSize,
                getY(), shape, false));
        updatePortsPosition();
    }

    public boolean removeOutputPort() {
        if (outPorts.isEmpty()) return false;
        outPorts.remove(outPorts.size() - 1);
        updatePortsPosition();
        return true;
    }

    public boolean enqueueFront(PacketModel packet) {
        if (buffer == null) {
            buffer = new ArrayDeque<>(Config.MAX_BUFFER_CAPACITY);
        }
        if (buffer.size() >= Config.MAX_BUFFER_CAPACITY) return false;

        if (buffer instanceof ArrayDeque<PacketModel> dq) {
            dq.addFirst(packet);
            // برای پکت‌های برگشتی، پورت ورودی null است
            newEntries.offer(new PacketEntry(packet, null));
            return true;
        }
        if (buffer instanceof java.util.Deque<PacketModel> deq) {
            boolean added = deq.offerFirst(packet);
            if (added) {
                newEntries.offer(new PacketEntry(packet, null));
            }
            return added;
        }
        return buffer.offer(packet);
    }
}
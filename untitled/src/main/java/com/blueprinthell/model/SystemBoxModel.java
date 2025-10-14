package com.blueprinthell.model;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.systems.SystemBehavior;
import com.blueprinthell.controller.systems.SystemBehaviorAdapter;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.large.LargePacket;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;


public class SystemBoxModel extends GameObjectModel implements Serializable, Updatable {

    private static final long serialVersionUID = 5L;

    private final List<PortModel> inPorts  = new ArrayList<>();
    private final List<PortModel> outPorts = new ArrayList<>();

    private final Deque<PacketModel> bitBuffer = new ArrayDeque<>(Config.MAX_BUFFER_CAPACITY);

    private final Deque<LargePacket> largeBuffer =
            new ArrayDeque<>(Config.MAX_LARGE_BUFFER_CAPACITY);

    private final Deque<PacketModel> returnBuffer = new ArrayDeque<>(Config.MAX_BUFFER_CAPACITY);

    private boolean enabled = true;
    private double  disableTimer = 0.0;

    private final List<SystemBehavior> behaviors = new ArrayList<>();
    private final Queue<PacketEntry>   newEntries = new ConcurrentLinkedQueue<>();

    private boolean    lastEnabledState = true;
    private SystemKind primaryKind      = SystemKind.NORMAL;

    private final String id;

    public SystemBoxModel(String id,
                          int x, int y, int width, int height,
                          List<PortShape> inShapes,
                          List<PortShape> outShapes) {

        super(x, y, width, height);
        this.id = id;
        createPorts(inShapes, outShapes);
    }

    public static class PacketEntry {
        public final PacketModel packet;
        public final PortModel  enteredPort;

        public PacketEntry(PacketModel packet, PortModel enteredPort) {
            this.packet      = packet;
            this.enteredPort = enteredPort;
        }
    }


    public String getId()                      { return id;            }
    public SystemKind getPrimaryKind()        { return primaryKind;   }
    public void setPrimaryKind(SystemKind k ) { primaryKind = (k!=null)?k:SystemKind.NORMAL; }

    public List<PortModel> getInPorts () { return Collections.unmodifiableList(inPorts ); }
    public List<PortModel> getOutPorts() { return Collections.unmodifiableList(outPorts); }
    public List<PortShape> getInShapes () { return inPorts .stream().map(PortModel::getShape).collect(Collectors.toList()); }
    public List<PortShape> getOutShapes() { return outPorts.stream().map(PortModel::getShape).collect(Collectors.toList()); }

    public int  getBitBufferSize() { return bitBuffer.size(); }
    public int  getBitBufferFree() { return Config.MAX_BUFFER_CAPACITY - bitBuffer.size(); }
    public Deque<PacketModel> getBitBuffer() { return bitBuffer; }

    public int           getLargeBufferSize() { return largeBuffer.size(); }
    public int           getLargeBufferFree() { return Config.MAX_LARGE_BUFFER_CAPACITY - largeBuffer.size(); }
    public Deque<LargePacket> getLargeBuffer() { return largeBuffer; }

    public LargePacket pollLarge() { return largeBuffer.pollFirst(); }

    public boolean enqueue(PacketModel packet, PortModel enteredPort) {
        if (packet == null) return false;
        if (enteredPort != null && !enteredPort.isInput()) {
            if (returnBuffer.size() >= Config.MAX_BUFFER_CAPACITY) return false;
            boolean added = returnBuffer.offerLast(packet);
            if (added) {
                SystemBehaviorAdapter.EnteredPortTracker.record(packet, enteredPort);
                newEntries.offer(new PacketEntry(packet, enteredPort));
                            }
                        return added;
                   }
        if (!enabled && enteredPort != null) return false;

        final boolean added;

        if (!(packet instanceof LargePacket)) {
            if (bitBuffer.size() >= Config.MAX_BUFFER_CAPACITY) return false;
            added = bitBuffer.offerLast(packet);

        } else {
            if (isDistributor() || isMerger()) {
                if (largeBuffer.size() >= Config.MAX_LARGE_BUFFER_CAPACITY) return false;
                added = largeBuffer.offerLast((LargePacket) packet);
            } else {
                // در سیستم‌های عادی: بستهٔ حجیم را مثل پکت معمولی در bitBuffer قرار بده
                if (bitBuffer.size() >= Config.MAX_BUFFER_CAPACITY) return false;
                added = bitBuffer.offerLast(packet);
            }
        }

        if (added) {
            if (enteredPort != null)
                SystemBehaviorAdapter.EnteredPortTracker.record(packet, enteredPort);
            newEntries.offer(new PacketEntry(packet, enteredPort));
        }
        return added;
    }

    public boolean enqueue(PacketModel packet) { return enqueue(packet, null); }


    public PacketModel pollPacket() { return bitBuffer.pollFirst(); }

    public boolean enqueueFront(PacketModel packet) {
        if (packet == null) return false;
        if (bitBuffer.size() >= Config.MAX_BUFFER_CAPACITY) return false;
        bitBuffer.addFirst(packet);
        newEntries.offer(new PacketEntry(packet, null));
        return true;
    }

    public boolean removeFromBuffer(PacketModel packet) {
        if (packet == null) return false;
        if (packet instanceof LargePacket lp)  return largeBuffer.remove(lp);
        else                                   return bitBuffer .remove(packet);
    }

    public void clearBuffer() {
        bitBuffer  .clear();
        largeBuffer.clear();
        returnBuffer.clear();
        newEntries .clear();
        SystemBehaviorAdapter.EnteredPortTracker.clear();
    }

    public void disable()             { enabled = false; disableTimer = Config.SYSTEM_DISABLE_DURATION; }
    public void disableFor(double s ) { if (s>0) { enabled = false; disableTimer = s; } }
    public boolean isEnabled()        { return enabled; }

    @Override
    public void update(double dt) {

        if (!enabled) {
            disableTimer -= dt;
            if (disableTimer <= 0) enabled = true;
        }

        if (enabled != lastEnabledState) {
            for (SystemBehavior b : behaviors) b.onEnabledChanged(enabled);
            lastEnabledState = enabled;
        }

        for (SystemBehavior b : behaviors) b.update(dt);

        PacketEntry entry;
        while ((entry = newEntries.poll()) != null) {
            for (SystemBehavior b : behaviors)
                b.onPacketEnqueued(entry.packet, entry.enteredPort);
        }
    }


    private void createPorts(List<PortShape> inShapes, List<PortShape> outShapes) {
        int ps = Config.PORT_SIZE;
        for (int i = 0; i < inShapes.size(); i++) {
            int yOff = (i + 1) * getHeight() / (inShapes.size() + 1) - ps / 2;
            inPorts.add(new PortModel(getX(), getY() + yOff, inShapes.get(i), true));
        }
        for (int i = 0; i < outShapes.size(); i++) {
            int yOff = (i + 1) * getHeight() / (outShapes.size() + 1) - ps / 2;
            outPorts.add(new PortModel(getX() + getWidth() - ps, getY() + yOff, outShapes.get(i), false));
        }
    }
    public PacketModel pollReturned() { return returnBuffer.pollFirst(); }

    public Deque<PacketModel> getReturnBuffer() { return returnBuffer; }
    @Override public void setX(int x){ super.setX(x); updatePortsPosition(); }
    @Override public void setY(int y){ super.setY(y); updatePortsPosition(); }

    private void updatePortsPosition() {
        int ps = Config.PORT_SIZE;
        for (int i = 0; i < inPorts.size(); i++) {
            int yOff = (i + 1) * getHeight() / (inPorts.size() + 1) - ps / 2;
            PortModel p = inPorts.get(i);
            p.setX(getX()); p.setY(getY() + yOff);
        }
        for (int i = 0; i < outPorts.size(); i++) {
            int yOff = (i + 1) * getHeight() / (outPorts.size() + 1) - ps / 2;
            PortModel p = outPorts.get(i);
            p.setX(getX() + getWidth() - ps); p.setY(getY() + yOff);
        }
    }

    public void addBehavior(SystemBehavior b){ if(!behaviors.contains(b)) behaviors.add(b); }
    public void removeBehavior(SystemBehavior b){ behaviors.remove(b); }
    public List<SystemBehavior> getBehaviors(){ return Collections.unmodifiableList(behaviors); }

    public boolean hasUnprocessedEntries(){ return !newEntries.isEmpty(); }

    public void addOutputPort(PortShape shape){
        if(outPorts.size() >= Config.MAX_OUTPUT_PORTS) return;
        int ps = Config.PORT_SIZE;
        outPorts.add(new PortModel(getX() + getWidth() - ps, getY(), shape, false));
        updatePortsPosition();
    }
    public boolean removeOutputPort(){
        if(outPorts.isEmpty()) return false;
        outPorts.remove(outPorts.size()-1);
        updatePortsPosition();
        return true;
    }
    @Deprecated
    public Queue<PacketModel> getBuffer() {
        return bitBuffer;
    }


    public void addInputPort(PortShape shape){
        int ps = Config.PORT_SIZE;
        inPorts.add(new PortModel(getX(), getY(), shape, true));
        updatePortsPosition();
    }
    public boolean isDistributor() {
        return primaryKind == SystemKind.DISTRIBUTOR;
    }

    public boolean isMerger() {
        return primaryKind == SystemKind.MERGER;
    }
    public boolean enqueueFront(LargePacket lp) {
        if (lp == null) return false;
        if (largeBuffer.size() >= Config.MAX_LARGE_BUFFER_CAPACITY) return false;
        largeBuffer.addFirst(lp);
        return true;
    }


    public void enqueueBitSilently(PacketModel packet) {
        this.getBitBuffer().addLast(packet);
    }


    public void enqueueLargeSilently(LargePacket lp) {
        this.getLargeBuffer().addLast(lp);
    }


    public void clearBuffers() {
        this.getBitBuffer().clear();
        this.getLargeBuffer().clear();
        returnBuffer.clear();
    }
    public double getDisableTimer() {
        return this.disableTimer;
    }
        public boolean enqueueReturnedFront(PacketModel packet) {
                if (packet == null) return false;
                if (returnBuffer.size() >= Config.MAX_BUFFER_CAPACITY) return false;
                returnBuffer.addFirst(packet);
                return true;
            }
}

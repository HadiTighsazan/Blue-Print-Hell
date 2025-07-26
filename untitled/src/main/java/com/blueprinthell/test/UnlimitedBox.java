package com.blueprinthell.test;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PortShape;
import com.blueprinthell.model.SystemBoxModel;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * A SystemBoxModel variant with effectively unlimited buffer capacity,
 * for testing DistributorBehavior & MergerBehavior without overflow.
 */
public class UnlimitedBox extends SystemBoxModel {
    private final Deque<PacketModel> unlimitedBuffer = new ArrayDeque<>();

    public UnlimitedBox() {
        // call base constructor with empty ports
        super(0, 0, 1, 1,
                Collections.emptyList(),
                Collections.emptyList());
    }

    @Override
    public boolean enqueue(PacketModel packet) {
        // always accept
        unlimitedBuffer.addLast(packet);
        return true;
    }

    @Override
    public PacketModel pollPacket() {
        return unlimitedBuffer.pollFirst();
    }

    @Override
    public void clearBuffer() {
        unlimitedBuffer.clear();
    }
}

package com.blueprinthell.model;

import com.blueprinthell.config.Config;
import java.io.Serializable;
import com.blueprinthell.model.PacketOps; // بالای فایل




public class PortModel extends GameObjectModel implements Serializable {
    private static final long serialVersionUID = 3L;

    private  PortShape shape;
    private final boolean input;

    public PortModel(int x, int y, PortShape shape, boolean input) {
        super(x, y, Config.PORT_SIZE, Config.PORT_SIZE);
        this.shape = shape;
        this.input = input;
    }

    public PortShape getShape() {
        return shape;
    }

    public boolean isInput() {
        return input;
    }


    public PacketType getType() {
        return switch (shape) {
            case SQUARE -> PacketType.SQUARE;
            case TRIANGLE -> PacketType.TRIANGLE;
            case CIRCLE -> PacketType.CIRCLE;
        };
    }


    public boolean isCompatible(PacketModel packet) {
        if (this.input) return false;

        if (packet instanceof com.blueprinthell.model.large.LargePacket ||
                packet instanceof com.blueprinthell.model.large.BitPacket) {
            return true; // یا false - بستگی به منطق شما دارد
        }

        PortShape packetShape = switch (packet.getType()) {
            case SQUARE -> PortShape.SQUARE;
            case TRIANGLE -> PortShape.TRIANGLE;
            case CIRCLE -> PortShape.CIRCLE;
        };
        return this.shape == packetShape;
    }

    public boolean canConnectTo(PortModel other) {
        return !this.input && other.input;
    }


    public void setShape(PortShape newShape){
        this.shape = newShape;
    }

    public static PortShape shapeForPacket(PacketModel packet) {
        switch (packet.getType()) {
            case SQUARE: return PortShape.SQUARE;
            case TRIANGLE: return PortShape.TRIANGLE;
            case CIRCLE: return PortShape.CIRCLE;
            default: throw new IllegalStateException("Unknown packet type");
        }
    }


}

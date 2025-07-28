package com.blueprinthell.model;

import com.blueprinthell.config.Config;
import java.io.Serializable;


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
        return (shape == PortShape.SQUARE) ? PacketType.SQUARE : PacketType.TRIANGLE;
    }

    public boolean isCompatible(PacketModel packet) {
        PacketType t = packet.getType();
        return switch (this.shape) {
            case SQUARE   -> (t == PacketType.SQUARE || t == PacketType.CIRCLE);
            case TRIANGLE -> (t == PacketType.TRIANGLE);
        };// circle port hasn't made!!
    }


    public boolean isCompatibleWith(PortModel other) {
        return !this.input && other.input;
    }

    public void setShape(PortShape newShape){
        this.shape = newShape;
    }



}

package com.blueprinthell.level;

import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.PortShape;

import java.util.List;
import java.util.UUID;


public record LevelDefinition(List<BoxSpec> boxes, double totalWireLength) {


    public static LevelDefinition simple(List<BoxSpec> boxes, double wireLen) {
        return new LevelDefinition(List.copyOf(boxes), wireLen);
    }


    public record BoxSpec(
            String id,
            int x, int y, int width, int height,
            List<PortShape> inShapes,
            List<PortShape> outShapes,
            boolean isSource,
            boolean isSink,
            SystemKind kind // NEW
    ) {
        public BoxSpec(int x, int y, int w, int h,
                       List<PortShape> inShapes,
                       List<PortShape> outShapes,
                       boolean isSource, boolean isSink) {
            this(UUID.randomUUID().toString(), x, y, w, h, inShapes, outShapes, isSource, isSink, SystemKind.NORMAL);
        }
    }



}

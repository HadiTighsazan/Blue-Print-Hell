package com.blueprinthell.level;

import com.blueprinthell.model.PortShape;

import java.util.List;
import java.util.UUID;

/**
 * Immutable description of a level: list of boxes and global constraints (e.g., wire length).
 * The GameController will consume this object to instantiate runtime models.
 */
public record LevelDefinition(List<BoxSpec> boxes, double totalWireLength) {

    /**
     * Convenience factory for a single‑source/sink sandbox level.
     */
    public static LevelDefinition simple(List<BoxSpec> boxes, double wireLen) {
        return new LevelDefinition(List.copyOf(boxes), wireLen);
    }

    /**
     * Immutable specification of a SystemBox in the level blueprint.
     * id (UUID string) remains stable across level generations so carry‑over wires can map boxes.
     */
    public record BoxSpec(
            String id,
            int x,
            int y,
            int width,
            int height,
            List<PortShape> inShapes,
            List<PortShape> outShapes,
            boolean isSource,
            boolean isSink
    ) {
        public BoxSpec(int x, int y, int w, int h, List<PortShape> inShapes, List<PortShape> outShapes,
                       boolean isSource, boolean isSink) {
            this(UUID.randomUUID().toString(), x, y, w, h, inShapes, outShapes, isSource, isSink);
        }
    }


}

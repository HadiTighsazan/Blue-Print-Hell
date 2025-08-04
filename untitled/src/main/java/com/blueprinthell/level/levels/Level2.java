package com.blueprinthell.level.levels;

import com.blueprinthell.level.AbstractLevel;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.PortShape;
import java.util.*;


public class Level2 extends AbstractLevel {

    public Level2() {
        super(2, "Shape Sorting", 600.0);
        this.packetsPerPort = 4;
        this.maxLossRatio = 0.4;
    }

    @Override
    public String getDescription() {
        return "Route packets to matching ports. Square and Circle fit in Square ports!";
    }

    @Override
    public LevelDefinition getDefinition() {
        List<LevelDefinition.BoxSpec> boxes = new ArrayList<>();

        // Source with mixed outputs
        boxes.add(createSource(100, 250, squareTriangle()));

        // Router box in the middle
        boxes.add(createBox(350, 300,
                squareTriangle(),  // inputs
                Arrays.asList(PortShape.SQUARE, PortShape.SQUARE, PortShape.TRIANGLE),  // outputs
                SystemKind.NORMAL));

        // Two sinks - one for squares, one for triangles
        boxes.add(createSink(600, 200, square()));
        boxes.add(createSink(600, 400, triangle()));

        return new LevelDefinition(boxes, wireBudget);
    }
}
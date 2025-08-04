package com.blueprinthell.level.levels;

import com.blueprinthell.level.AbstractLevel;
import com.blueprinthell.level.LevelDefinition;

import java.util.*;


public class Level1 extends AbstractLevel {

    public Level1() {
        super(1, "Getting Started", 400.0);
        this.packetsPerPort = 3;
        this.maxLossRatio = 0.5;
    }

    @Override
    public String getDescription() {
        return "Connect the source to the sink. Watch out for packet loss!";
    }

    @Override
    public LevelDefinition getDefinition() {
        List<LevelDefinition.BoxSpec> boxes = new ArrayList<>();

        // Single source on the left with one square output
        boxes.add(createSource(150, 300, square()));

        // Single sink on the right with one square input
        boxes.add(createSink(650, 300, square()));

        return new LevelDefinition(boxes, wireBudget);
    }
}
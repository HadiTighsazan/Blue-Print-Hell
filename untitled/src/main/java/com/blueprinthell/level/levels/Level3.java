package com.blueprinthell.level.levels;

import com.blueprinthell.level.AbstractLevel;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.PortShape;
import java.util.*;


public class Level3 extends AbstractLevel {

    public Level3() {
        super(3, "VPN Protection", 800.0);
        this.packetsPerPort = 5;
        this.maxLossRatio = 0.3;
    }

    @Override
    public String getDescription() {
        return "Use VPN to protect messenger packets. Protected packets give more coins!";
    }

    @Override
    public LevelDefinition getDefinition() {
        List<LevelDefinition.BoxSpec> boxes = new ArrayList<>();

        // Keep boxes from previous levels (they will have wires preserved)
        // Source from Level 2
        boxes.add(createSource(100, 250, squareTriangle()));

        // Router from Level 2 
        boxes.add(createBox(350, 300,
                squareTriangle(),
                Arrays.asList(PortShape.SQUARE, PortShape.SQUARE, PortShape.TRIANGLE),
                SystemKind.NORMAL));

        // Sinks from Level 2
        boxes.add(createSink(600, 200, square()));
        boxes.add(createSink(600, 400, triangle()));

        // NEW: Add VPN system
        boxes.add(createBox(200, 450,
                circle(),  // Circle input for messengers
                circle(),  // Circle output for protected packets
                SystemKind.VPN));

        // NEW: Source for circle packets (messengers)
        boxes.add(createSource(100, 500, circle()));

        return new LevelDefinition(boxes, wireBudget);
    }
}
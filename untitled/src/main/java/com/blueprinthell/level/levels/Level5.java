package com.blueprinthell.level.levels;

import com.blueprinthell.level.AbstractLevel;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.PortShape;
import java.util.*;


public class Level5 extends AbstractLevel {

    public Level5() {
        super(5, "Spy Network", 1200.0);
        this.packetsPerPort = 8;
        this.maxLossRatio = 0.35;
    }

    @Override
    public String getDescription() {
        return "Spy systems teleport packets! Route confidential data carefully.";
    }

    @Override
    public LevelDefinition getDefinition() {
        List<LevelDefinition.BoxSpec> boxes = new ArrayList<>();

        // Multiple sources
        boxes.add(createSource(50, 150, square()));
        boxes.add(createSource(50, 300, triangle()));
        boxes.add(createSource(50, 450, circle()));

        // First layer - mixed systems
        boxes.add(createBox(250, 100,
                square(),
                squareCircle(),
                SystemKind.NORMAL));

        boxes.add(createBox(250, 250,
                triangleCircle(),
                all(),
                SystemKind.VPN));

        // NEW: Spy systems that can teleport packets
        boxes.add(createBox(250, 400,
                circle(),
                circle(),
                SystemKind.SPY));

        boxes.add(createBox(450, 400,
                circle(),
                circle(),
                SystemKind.SPY));

        // Defense layer
        boxes.add(createBox(450, 100,
                all(),
                all(),
                SystemKind.ANTI_TROJAN));

        // Malicious in the middle
        boxes.add(createBox(450, 250,
                all(),
                squareTriangle(),
                SystemKind.MALICIOUS));

        // Final routing
        boxes.add(createBox(650, 200,
                all(),
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE, PortShape.CIRCLE),
                SystemKind.NORMAL));

        // Multiple sinks
        boxes.add(createSink(850, 100, square()));
        boxes.add(createSink(850, 250, triangle()));
        boxes.add(createSink(850, 400, circle()));

        return new LevelDefinition(boxes, wireBudget);
    }
}
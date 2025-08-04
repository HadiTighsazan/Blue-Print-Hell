package com.blueprinthell.level.levels;

import com.blueprinthell.level.AbstractLevel;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.PortShape;
import java.util.*;


public class Level4 extends AbstractLevel {

    public Level4() {
        super(4, "Malicious Threats", 1000.0);
        this.packetsPerPort = 6;
        this.maxLossRatio = 0.4; // Higher tolerance due to malicious activity
    }

    @Override
    public String getDescription() {
        return "Beware of Malicious systems! Use Anti-Trojan to clean infected packets.";
    }

    @Override
    public LevelDefinition getDefinition() {
        List<LevelDefinition.BoxSpec> boxes = new ArrayList<>();


        boxes.add(createSource(50, 200, squareTriangle()));
        boxes.add(createSource(50, 400, circle()));

        // Previous systems
        boxes.add(createBox(200, 250,
                squareTriangle(),
                Arrays.asList(PortShape.SQUARE, PortShape.SQUARE, PortShape.TRIANGLE),
                SystemKind.NORMAL));

        boxes.add(createBox(200, 450,
                circle(),
                circle(),
                SystemKind.VPN));

        // NEW: Malicious system in the middle path
        boxes.add(createBox(400, 150,
                square(),
                square(),
                SystemKind.MALICIOUS));

        // NEW: Anti-Trojan for cleaning
        boxes.add(createBox(400, 350,
                all(),  // Can accept all types
                all(),  // Can output all types
                SystemKind.ANTI_TROJAN));

        // Sinks
        boxes.add(createSink(700, 150, square()));
        boxes.add(createSink(700, 250, triangle()));
        boxes.add(createSink(700, 350, circle()));

        return new LevelDefinition(boxes, wireBudget);
    }
}
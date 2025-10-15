package com.blueprinthell.level.levels;

import com.blueprinthell.level.AbstractLevel;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.PortShape;

import java.util.*;

public class Level3 extends AbstractLevel {

    public Level3() {
        super(3, "Level 3 - Anti-Trojan Defense", 99999.0);
        this.packetsPerPort = 4;
        this.maxLossRatio = 0.42;
    }

    @Override
    public String getDescription() {
        return "Deploy Anti-Trojan systems to clean infected packets! Stay vigilant.";
    }

    @Override
    public LevelDefinition getDefinition() {
        List<LevelDefinition.BoxSpec> boxes = new ArrayList<>();

        boxes.add(withId("L1-B1-SOURCE", createSource(100, 250, Arrays.asList(PortShape.SQUARE, PortShape.CIRCLE))));
        boxes.add(withId("L1-B2-VPN", createBox(300, 150, Collections.singletonList(PortShape.SQUARE), Collections.singletonList(PortShape.TRIANGLE), SystemKind.VPN)));
        boxes.add(withId("L1-B3-DISTRIBUTOR", createBox(300, 350, Collections.singletonList(PortShape.CIRCLE), Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE), SystemKind.DISTRIBUTOR)));
        boxes.add(withId("L1-B4-MALICIOUS", createBox(500, 300, Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE), Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE), SystemKind.MALICIOUS)));
        boxes.add(withId("L1-B5-MERGER", createBox(700, 350, Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE), Collections.singletonList(PortShape.TRIANGLE), SystemKind.MERGER)));
        boxes.add(withId("L1-B6-SINK", createSink(900, 250, Arrays.asList(PortShape.TRIANGLE, PortShape.TRIANGLE))));

        boxes.add(withId("L2-B7-SPY1", createBox(500, 100, Arrays.asList(PortShape.CIRCLE, PortShape.TRIANGLE), Arrays.asList(PortShape.SQUARE, PortShape.CIRCLE), SystemKind.SPY)));
        boxes.add(withId("L2-B8-SPY2", createBox(500, 500, Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE), Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE), SystemKind.SPY)));
        boxes.add(withId("L2-B9-NORMAL", createBox(700, 150, Collections.singletonList(PortShape.SQUARE), Collections.singletonList(PortShape.CIRCLE), SystemKind.NORMAL)));


        LevelDefinition.BoxSpec _b10 = createBox(
                700, 500,
                Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE),
                Arrays.asList(PortShape.CIRCLE, PortShape.TRIANGLE),
                SystemKind.ANTI_TROJAN
        );
        boxes.add(withId("L3-B10-ANTITROJAN", _b10));

        // L3-B11: Source #2
        LevelDefinition.BoxSpec _b11 = createSource(100, 50, Arrays.asList(
                PortShape.SQUARE
        ));
        boxes.add(withId("L3-B11-SOURCE2", _b11));

        // L3-B12: Sink #2
        LevelDefinition.BoxSpec _b12 = createSink(900, 550, Arrays.asList(
                PortShape.TRIANGLE
        ));
        boxes.add(withId("L3-B12-SINK2", _b12));


        return new LevelDefinition(boxes, wireBudget);
    }

    private static LevelDefinition.BoxSpec withId(String id, LevelDefinition.BoxSpec s) {
        return new LevelDefinition.BoxSpec(
                id, s.x(), s.y(), s.width(), s.height(),
                s.inShapes(), s.outShapes(),
                s.isSource(), s.isSink(), s.kind()
        );
    }
}
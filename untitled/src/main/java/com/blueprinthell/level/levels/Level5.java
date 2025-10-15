package com.blueprinthell.level.levels;

import com.blueprinthell.level.AbstractLevel;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.PortShape;

import java.util.*;

public class Level5 extends AbstractLevel {

    public Level5() {
        super(5, "Level 5 - Ultimate Network Mastery", 99999.0);
        this.packetsPerPort = 6;
        this.maxLossRatio = 0.35;
    }

    @Override
    public String getDescription() {
        return "The final challenge! Master all systems and route every packet perfectly.";
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
        boxes.add(withId("L3-B10-ANTITROJAN", createBox(700, 500, Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE), Arrays.asList(PortShape.CIRCLE, PortShape.TRIANGLE), SystemKind.ANTI_TROJAN)));
        boxes.add(withId("L3-B11-SOURCE2", createSource(100, 50, Arrays.asList(PortShape.SQUARE))));
        boxes.add(withId("L3-B12-SINK2", createSink(900, 550, Arrays.asList(PortShape.TRIANGLE))));
        boxes.add(withId("L4-B13-SPY3", createBox(100, 500, Arrays.asList(PortShape.TRIANGLE, PortShape.SQUARE), Arrays.asList(PortShape.CIRCLE, PortShape.TRIANGLE), SystemKind.SPY)));
        boxes.add(withId("L4-B14-SPY4", createBox(900, 100, Collections.singletonList(PortShape.CIRCLE), Collections.singletonList(PortShape.SQUARE), SystemKind.SPY)));


        LevelDefinition.BoxSpec _b15 = createBox(
                500, 650,
                Arrays.asList(PortShape.CIRCLE, PortShape.TRIANGLE),
                Arrays.asList(PortShape.SQUARE, PortShape.CIRCLE),
                SystemKind.MALICIOUS
        );
        boxes.add(withId("L5-B15-MALICIOUS2", _b15));

        LevelDefinition.BoxSpec _b16 = createBox(
                300, 650,
                Collections.singletonList(PortShape.SQUARE),
                Collections.singletonList(PortShape.TRIANGLE),
                SystemKind.VPN
        );
        boxes.add(withId("L5-B16-VPN2", _b16));


        return new LevelDefinition(boxes, wireBudget);
    }

    private static LevelDefinition.BoxSpec withId(String id, LevelDefinition.BoxSpec spec) {
        return new LevelDefinition.BoxSpec(
                id, spec.x(), spec.y(), spec.width(), spec.height(),
                spec.inShapes(), spec.outShapes(),
                spec.isSource(), spec.isSink(), spec.kind()
        );
    }
}
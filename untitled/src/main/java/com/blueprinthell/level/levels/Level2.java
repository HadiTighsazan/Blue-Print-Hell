package com.blueprinthell.level.levels;

import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.level.AbstractLevel;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.model.PortShape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Level2 extends AbstractLevel {

    public Level2() {
        super(2, "Level 2 - Security Threats", 99999.0);
        this.packetsPerPort = 4;
        this.maxLossRatio = 0.45;
    }

    @Override
    public String getDescription() {
        return "Beware of Spy systems! They can leak your packets to each other.";
    }

    @Override
    public LevelDefinition getDefinition() {
        List<LevelDefinition.BoxSpec> boxes = new ArrayList<>();


        LevelDefinition.BoxSpec _b1 = createSource(100, 250, Arrays.asList(
                PortShape.SQUARE,
                PortShape.CIRCLE
        ));
        boxes.add(withId("L1-B1-SOURCE", _b1));

        LevelDefinition.BoxSpec _b2 = createBox(
                300, 150,
                Collections.singletonList(PortShape.SQUARE),
                Collections.singletonList(PortShape.TRIANGLE),
                SystemKind.VPN
        );
        boxes.add(withId("L1-B2-VPN", _b2));

        LevelDefinition.BoxSpec _b3 = createBox(
                300, 350,
                Collections.singletonList(PortShape.CIRCLE),
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE),
                SystemKind.DISTRIBUTOR
        );
        boxes.add(withId("L1-B3-DISTRIBUTOR", _b3));

        LevelDefinition.BoxSpec _b4 = createBox(
                500, 300,
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE),
                Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE),
                SystemKind.MALICIOUS
        );
        boxes.add(withId("L1-B4-MALICIOUS", _b4));

        LevelDefinition.BoxSpec _b5 = createBox(
                700, 350,
                Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE),
                Collections.singletonList(PortShape.TRIANGLE),
                SystemKind.MERGER
        );
        boxes.add(withId("L1-B5-MERGER", _b5));

        LevelDefinition.BoxSpec _b6 = createSink(900, 250, Arrays.asList(
                PortShape.TRIANGLE,
                PortShape.TRIANGLE
        ));
        boxes.add(withId("L1-B6-SINK", _b6));


        LevelDefinition.BoxSpec _b7 = createBox(
                500, 100,
                Arrays.asList(PortShape.CIRCLE, PortShape.TRIANGLE),
                Arrays.asList(PortShape.SQUARE, PortShape.CIRCLE),
                SystemKind.SPY
        );
        boxes.add(withId("L2-B7-SPY1", _b7));

        LevelDefinition.BoxSpec _b8 = createBox(
                500, 500,
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE),
                Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE),
                SystemKind.SPY
        );
        boxes.add(withId("L2-B8-SPY2", _b8));

        LevelDefinition.BoxSpec _b9 = createBox(
                700, 150,
                Arrays.asList(PortShape.SQUARE, PortShape.CIRCLE),
                Arrays.asList(PortShape.TRIANGLE, PortShape.SQUARE),
                SystemKind.NORMAL
        );
        boxes.add(withId("L2-B9-NORMAL", _b9));

        LevelDefinition.BoxSpec _b10 = createSource(100, 450, Arrays.asList(
                PortShape.TRIANGLE,
                PortShape.SQUARE
        ));
        boxes.add(withId("L2-B10-SOURCE2", _b10));

        LevelDefinition.BoxSpec _b11 = createSink(900, 450, Arrays.asList(
                PortShape.CIRCLE,
                PortShape.SQUARE
        ));
        boxes.add(withId("L2-B11-SINK2", _b11));

        return new LevelDefinition(boxes, wireBudget);
    }

    private static LevelDefinition.BoxSpec withId(String id, LevelDefinition.BoxSpec s) {
        return new LevelDefinition.BoxSpec(
                id,
                s.x(), s.y(), s.width(), s.height(),
                s.inShapes(), s.outShapes(),
                s.isSource(), s.isSink(),
                s.kind()
        );
    }
}
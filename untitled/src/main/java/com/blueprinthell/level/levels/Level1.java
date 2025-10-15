package com.blueprinthell.level.levels;

import com.blueprinthell.level.AbstractLevel;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.PortShape;

import java.util.*;

public class Level1 extends AbstractLevel {

    public Level1() {
        super(1, "Level 1 - Large Packet Management", 999999.0);
        this.packetsPerPort = 3;
        this.maxLossRatio = 0.5;
    }

    @Override
    public String getDescription() {
        return "Learn to split and merge large packets using Distributor and Merger systems!";
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
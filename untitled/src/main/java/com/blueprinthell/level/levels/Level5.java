package com.blueprinthell.level.levels;

import com.blueprinthell.level.AbstractLevel;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.PortShape;

import java.util.*;

/**
 * Level 5: Final Challenge (Placeholder)
 * این یک پیاده‌سازی موقت برای Level5 است تا خطای کامپایل برطرف شود
 */
public class Level5 extends AbstractLevel {

    public Level5() {
        super(5, "Final Challenge", 1000.0);
        this.packetsPerPort = 6;
        this.maxLossRatio = 0.4;
    }

    @Override
    public String getDescription() {
        return "The ultimate networking challenge!";
    }

    @Override
    public LevelDefinition getDefinition() {
        List<LevelDefinition.BoxSpec> boxes = new ArrayList<>();

        // یک طراحی ساده موقت
        boxes.add(createSource(100, 300, Arrays.asList(
                PortShape.SQUARE,
                PortShape.TRIANGLE,
                PortShape.CIRCLE
        )));

        boxes.add(createBox(
                400, 300,
                all(),
                all(),
                SystemKind.NORMAL
        ));

        boxes.add(createSink(700, 300, all()));

        return new LevelDefinition(boxes, wireBudget);
    }
}
package com.blueprinthell.level.levels;

import com.blueprinthell.level.AbstractLevel;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.controller.systems.SystemKind;

import java.util.*;

public class Level1 extends AbstractLevel {

    public Level1() {
        super(1, "Getting Started (3 systems, random port shapes)", 400.0);
        this.packetsPerPort = 3;
        this.maxLossRatio = 0.5;
    }

    @Override
    public LevelDefinition getDefinition() {
        List<LevelDefinition.BoxSpec> boxes = new ArrayList<>();

        // رندم پایدار با seed ثابت تا هر اجرا نتیجه یکسان شود
        Random rng = new Random(20250804L + levelNumber);

        // انتخاب شکل لینک اول (سورس → نرمال)
        int a = rng.nextInt(3);
        // انتخاب شکل لینک دوم (نرمال → سینک)
        int b = rng.nextInt(3);

        // مپ به متدهای کمکی AbstractLevel (square()/triangle()/circle())
        var shapeA = (a == 0) ? square() : (a == 1) ? triangle() : circle();
        var shapeB = (b == 0) ? square() : (b == 1) ? triangle() : circle();

        // 1) سورس با یک خروجی از نوع shapeA
        boxes.add(createSource(150, 300, shapeA));

        // 2) سیستم نرمال با 1 ورودی shapeA و 1 خروجی shapeB
        boxes.add(createBox(
                400, 300,
                shapeA,   // inputs (1)
                shapeB,   // outputs (1)
                SystemKind.NORMAL
        ));

        // 3) سینک با یک ورودی shapeB
        boxes.add(createSink(650, 300, shapeB));

        return new LevelDefinition(boxes, wireBudget);
    }
}

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
    public LevelDefinition getDefinition() {
        List<LevelDefinition.BoxSpec> boxes = new ArrayList<>();

        // 2-1: Source (از مرحله قبل + یک خروجی جدید)
        LevelDefinition.BoxSpec _b1 = createSource(100, 250, Arrays.asList(
                PortShape.SQUARE,
                PortShape.CIRCLE,
                PortShape.TRIANGLE
        ));
        boxes.add(withId("L1-B1-SOURCE", _b1)); // مطابق نسخه بدون خطا

        // 2-2: Normal (از مرحله قبل + یک ورودی جدید)
        LevelDefinition.BoxSpec _b2 = createBox(
                300, 150,
                Arrays.asList(PortShape.SQUARE, PortShape.CIRCLE),
                Collections.singletonList(PortShape.TRIANGLE),
                SystemKind.NORMAL
        );
        boxes.add(withId("L1-B2-NORMAL", _b2)); // مطابق نسخه بدون خطا

        // 2-3: Distributor (بدون تغییر)
        LevelDefinition.BoxSpec _b3 = createBox(
                300, 350,
                Collections.singletonList(PortShape.CIRCLE),
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE),
                SystemKind.DISTRIBUTOR
        );
        boxes.add(withId("L1-B3-DISTRIBUTOR", _b3)); // مطابق نسخه بدون خطا

        // 2-4: Malicious (جدید)
        LevelDefinition.BoxSpec _b4 = createBox(
                500, 200,
                Collections.singletonList(PortShape.TRIANGLE),
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE),
                SystemKind.MALICIOUS
        );
        boxes.add(withId("L2-B4-MALICIOUS", _b4)); // این یکی از قبل هم L2 بود

        // 2-5: Spy (جدید)
        LevelDefinition.BoxSpec _b5 = createBox(
                500, 450,
                Collections.singletonList(PortShape.SQUARE),
                Collections.singletonList(PortShape.CIRCLE),
                SystemKind.SPY
        );
        boxes.add(withId("L2-B5-SPY", _b5)); // این هم L2 بود

        // 2-6: یکی از NORMALها را به SPY تبدیل کردیم (تا 2 جاسوس داشته باشیم)
        LevelDefinition.BoxSpec _b6 = createBox(
                700, 300,
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE, PortShape.CIRCLE),
                Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE, PortShape.TRIANGLE),
                SystemKind.SPY // قبلاً NORMAL بود
        );
        boxes.add(withId("L1-B4-NORMAL", _b6)); // آیدی را دست نمی‌زنیم تا وایرها کرش نکنند

        // 2-7: Merger
        LevelDefinition.BoxSpec _b7 = createBox(
                700, 500,
                Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE, PortShape.TRIANGLE),
                Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE),
                SystemKind.MERGER
        );
        boxes.add(withId("L1-B5-MERGER", _b7)); // مطابق نسخه بدون خطا

        // 2-8: Sink
        LevelDefinition.BoxSpec _b8 = createSink(900, 350, Arrays.asList(
                PortShape.TRIANGLE,
                PortShape.CIRCLE,
                PortShape.SQUARE
        ));
        boxes.add(withId("L1-B6-SINK", _b8)); // مطابق نسخه بدون خطا

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

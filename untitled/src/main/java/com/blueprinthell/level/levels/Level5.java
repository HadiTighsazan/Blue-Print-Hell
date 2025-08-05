// فایل جدید: untitled/src/main/java/com/blueprinthell/level/levels/Level5.java
package com.blueprinthell.level.levels;

import com.blueprinthell.level.AbstractLevel;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.PortShape;

import java.util.*;

public class Level5 extends AbstractLevel {

    public Level5() {
        super(5, "Level 5 - Ultimate Network Challenge", 99999.0);
        this.packetsPerPort = 6;
        this.maxLossRatio = 0.4; // سخت‌تر - تحمل کمتر برای خطا
    }

    @Override
    public String getDescription() {
        return "Master all systems in this ultimate networking challenge! Balance security, speed, and efficiency.";
    }

    @Override
    public LevelDefinition getDefinition() {
        List<LevelDefinition.BoxSpec> boxes = new ArrayList<>();

        // === لایه 1: Source و سیستم‌های ورودی ===

        // 5-1: Source با 6 خروجی (حداکثر تنوع)
        LevelDefinition.BoxSpec _b1 = createSource(50, 300, Arrays.asList(
                PortShape.SQUARE,
                PortShape.TRIANGLE,
                PortShape.CIRCLE,
                PortShape.SQUARE,
                PortShape.TRIANGLE,
                PortShape.CIRCLE
        ));
        boxes.add(withId("L5-B1-SOURCE", _b1));

        // === لایه 2: سیستم‌های اولیه (x=200-300) ===

        // 5-2: VPN #1 - مسیر امن بالا
        LevelDefinition.BoxSpec _b2 = createBox(
                250, 100,
                Arrays.asList(PortShape.SQUARE, PortShape.CIRCLE),
                Arrays.asList(PortShape.TRIANGLE, PortShape.SQUARE),
                SystemKind.VPN
        );
        boxes.add(withId("L5-B2-VPN1", _b2));

        // 5-3: Distributor - مسیر پکت حجیم
        LevelDefinition.BoxSpec _b3 = createBox(
                250, 250,
                Arrays.asList(PortShape.CIRCLE, PortShape.TRIANGLE),
                all(), // همه خروجی‌ها برای بیت‌ها
                SystemKind.DISTRIBUTOR
        );
        boxes.add(withId("L5-B3-DIST", _b3));

        // 5-4: Normal #1 - مسیر عادی
        LevelDefinition.BoxSpec _b4 = createBox(
                250, 400,
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE),
                Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE, PortShape.TRIANGLE),
                SystemKind.NORMAL
        );
        boxes.add(withId("L5-B4-NORM1", _b4));

        // 5-5: Malicious #1 - مسیر خطرناک پایین
        LevelDefinition.BoxSpec _b5 = createBox(
                250, 550,
                Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE),
                Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE),
                SystemKind.MALICIOUS
        );
        boxes.add(withId("L5-B5-MAL1", _b5));

        // === لایه 3: سیستم‌های میانی (x=400-500) ===

        // 5-6: Spy #1 - شبکه جاسوسی بالا
        LevelDefinition.BoxSpec _b6 = createBox(
                450, 50,
                Arrays.asList(PortShape.TRIANGLE, PortShape.SQUARE),
                Arrays.asList(PortShape.CIRCLE, PortShape.TRIANGLE, PortShape.SQUARE),
                SystemKind.SPY
        );
        boxes.add(withId("L5-B6-SPY1", _b6));

        // 5-7: Port Randomizer #1 - ایجاد بی‌ثباتی وسط
        LevelDefinition.BoxSpec _b7 = createBox(
                450, 200,
                all(), // پذیرش همه انواع
                all(), // خروجی همه انواع
                SystemKind.PORT_RANDOMIZER
        );
        boxes.add(withId("L5-B7-RAND1", _b7));

        // 5-8: Anti-Trojan - پاکسازی مرکزی
        LevelDefinition.BoxSpec _b8 = createBox(
                450, 350,
                all(),
                Arrays.asList(PortShape.SQUARE, PortShape.CIRCLE),
                SystemKind.ANTI_TROJAN
        );
        boxes.add(withId("L5-B8-ANTI", _b8));

        // 5-9: Spy #2 - شبکه جاسوسی پایین
        LevelDefinition.BoxSpec _b9 = createBox(
                450, 500,
                Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE),
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE),
                SystemKind.SPY
        );
        boxes.add(withId("L5-B9-SPY2", _b9));

        // 5-10: VPN #2 - محافظت ثانویه
        LevelDefinition.BoxSpec _b10 = createBox(
                450, 650,
                Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE),
                Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE),
                SystemKind.VPN
        );
        boxes.add(withId("L5-B10-VPN2", _b10));

        // === لایه 4: پردازش نهایی (x=650-750) ===

        // 5-11: Normal #2
        LevelDefinition.BoxSpec _b11 = createBox(
                700, 100,
                Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE, PortShape.TRIANGLE),
                Arrays.asList(PortShape.SQUARE, PortShape.CIRCLE),
                SystemKind.NORMAL
        );
        boxes.add(withId("L5-B11-NORM2", _b11));

        // 5-12: Merger - بازسازی پکت‌های حجیم
        LevelDefinition.BoxSpec _b12 = createBox(
                700, 250,
                all(),
                Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE),
                SystemKind.MERGER
        );
        boxes.add(withId("L5-B12-MERG", _b12));

        // 5-13: Port Randomizer #2
        LevelDefinition.BoxSpec _b13 = createBox(
                700, 400,
                Arrays.asList(PortShape.SQUARE, PortShape.CIRCLE),
                all(),
                SystemKind.PORT_RANDOMIZER
        );
        boxes.add(withId("L5-B13-RAND2", _b13));

        // 5-14: Normal #3
        LevelDefinition.BoxSpec _b14 = createBox(
                700, 550,
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE),
                Arrays.asList(PortShape.CIRCLE, PortShape.TRIANGLE, PortShape.SQUARE),
                SystemKind.NORMAL
        );
        boxes.add(withId("L5-B14-NORM3", _b14));

        // 5-15: Malicious #2 - تله نهایی
        LevelDefinition.BoxSpec _b15 = createBox(
                700, 700,
                Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE),
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE),
                SystemKind.MALICIOUS
        );
        boxes.add(withId("L5-B15-MAL2", _b15));

        // === لایه 5: خروجی‌ها (x=900+) ===

        // 5-16: Spy #3 - آخرین جاسوس
        LevelDefinition.BoxSpec _b16 = createBox(
                900, 300,
                Arrays.asList(PortShape.CIRCLE, PortShape.TRIANGLE),
                Arrays.asList(PortShape.SQUARE, PortShape.CIRCLE),
                SystemKind.SPY
        );
        boxes.add(withId("L5-B16-SPY3", _b16));

        // === Sinks - مقصدهای نهایی ===

        // 5-17: Sink #1 - بالا
        LevelDefinition.BoxSpec _b17 = createSink(1100, 100, Arrays.asList(
                PortShape.SQUARE,
                PortShape.CIRCLE
        ));
        boxes.add(withId("L5-B17-SINK1", _b17));

        // 5-18: Sink #2 - وسط بالا
        LevelDefinition.BoxSpec _b18 = createSink(1100, 250, Arrays.asList(
                PortShape.TRIANGLE,
                PortShape.CIRCLE
        ));
        boxes.add(withId("L5-B18-SINK2", _b18));

        // 5-19: Sink #3 - وسط پایین
        LevelDefinition.BoxSpec _b19 = createSink(1100, 400, Arrays.asList(
                PortShape.SQUARE,
                PortShape.TRIANGLE,
                PortShape.CIRCLE
        ));
        boxes.add(withId("L5-B19-SINK3", _b19));

        // 5-20: Sink #4 - پایین
        LevelDefinition.BoxSpec _b20 = createSink(1100, 550, Arrays.asList(
                PortShape.SQUARE,
                PortShape.TRIANGLE
        ));
        boxes.add(withId("L5-B20-SINK4", _b20));

        // 5-21: Sink #5 - خیلی پایین
        LevelDefinition.BoxSpec _b21 = createSink(1100, 700, Arrays.asList(
                PortShape.CIRCLE,
                PortShape.TRIANGLE
        ));
        boxes.add(withId("L5-B21-SINK5", _b21));

        return new LevelDefinition(boxes, wireBudget);
    }

    private static LevelDefinition.BoxSpec withId(String id, LevelDefinition.BoxSpec spec) {
        return new LevelDefinition.BoxSpec(
                id,
                spec.x(), spec.y(), spec.width(), spec.height(),
                spec.inShapes(), spec.outShapes(),
                spec.isSource(), spec.isSink(),
                spec.kind()
        );
    }
}
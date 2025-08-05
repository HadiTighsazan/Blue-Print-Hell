// فایل جدید: untitled/src/main/java/com/blueprinthell/level/levels/Level4.java
package com.blueprinthell.level.levels;

import com.blueprinthell.level.AbstractLevel;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.PortShape;

import java.util.*;

public class Level4 extends AbstractLevel {

    public Level4() {
        super(4, "Level 4 - Spy Network & Teleportation", 99999.0);
        this.packetsPerPort = 5; // افزایش تعداد پکت‌ها برای پیچیدگی بیشتر
        this.maxLossRatio = 0.45; // تحمل بیشتر به دلیل تله‌پورت
    }

    @Override
    public String getDescription() {
        return "Spy systems can teleport packets between each other! Route carefully to avoid data leaks.";
    }

    @Override
    public LevelDefinition getDefinition() {
        List<LevelDefinition.BoxSpec> boxes = new ArrayList<>();

        // === سیستم‌های موجود از مراحل قبل (با پورت‌های اضافی) ===

        // 4-1: Source - اضافه کردن یک خروجی جدید
        LevelDefinition.BoxSpec _b1 = createSource(100, 200, Arrays.asList(
                PortShape.SQUARE,
                PortShape.TRIANGLE,
                PortShape.CIRCLE,
                PortShape.SQUARE,  // خروجی از مرحله قبل
                PortShape.TRIANGLE // خروجی جدید برای مرحله 4
        ));
        boxes.add(withId("L1-B1-SOURCE", _b1));

        // 4-2: Normal از مرحله 1 - اضافه کردن پورت‌های جدید
        LevelDefinition.BoxSpec _b2 = createBox(
                300, 150,
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE, PortShape.CIRCLE), // 3 ورودی (حداکثر)
                Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE, PortShape.TRIANGLE), // 3 خروجی (حداکثر)
                SystemKind.NORMAL
        );
        boxes.add(withId("L1-B2-NORMAL", _b2));

        // 4-3: Malicious از مرحله 2
        LevelDefinition.BoxSpec _b3 = createBox(
                500, 100,
                Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE),
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE),
                SystemKind.MALICIOUS
        );
        boxes.add(withId("L2-B4-MALICIOUS", _b3));

        // 4-4: Spy قدیمی از مرحله 2 - اضافه کردن یک ورودی
        LevelDefinition.BoxSpec _b4 = createBox(
                500, 250,
                Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE, PortShape.TRIANGLE), // ورودی اضافی
                Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE, PortShape.SQUARE), // همان خروجی‌ها
                SystemKind.SPY
        );
        boxes.add(withId("L2-B5-SPY", _b4));

        // 4-5: VPN از مرحله 3
        LevelDefinition.BoxSpec _b5 = createBox(
                300, 400,
                Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE),
                Arrays.asList(PortShape.CIRCLE, PortShape.TRIANGLE),
                SystemKind.VPN
        );
        boxes.add(withId("L3-B6-VPN", _b5));

        // 4-6: Anti-Trojan از مرحله 3
        LevelDefinition.BoxSpec _b6 = createBox(
                700, 200,
                all(), // می‌تواند همه انواع را بپذیرد
                Arrays.asList(PortShape.SQUARE, PortShape.CIRCLE),
                SystemKind.ANTI_TROJAN
        );
        boxes.add(withId("L3-B7-ANTITROJAN", _b6));

        // 4-7: Port Randomizer از مرحله 3
        LevelDefinition.BoxSpec _b7 = createBox(
                300, 700,
                Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE),
                all(),
                SystemKind.PORT_RANDOMIZER
        );
        boxes.add(withId("L3-B8-PORTRANDOM", _b7));

        // 4-8: Normal از مرحله 3
        LevelDefinition.BoxSpec _b8 = createBox(
                700, 350,
                Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE),
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE, PortShape.CIRCLE),
                SystemKind.NORMAL
        );
        boxes.add(withId("L1-B4-NORMAL", _b8));

        // === سیستم‌های جدید مرحله 4 ===

        // 4-9: Spy جدید #1 - موقعیت بالا
        LevelDefinition.BoxSpec _b9 = createBox(
                500, 500,
                Arrays.asList(PortShape.TRIANGLE, PortShape.SQUARE), // 2 ورودی متنوع
                Arrays.asList(PortShape.CIRCLE, PortShape.TRIANGLE), // 2 خروجی
                SystemKind.SPY
        );
        boxes.add(withId("L4-B9-SPY2", _b9));

        // 4-10: Spy جدید #2 - موقعیت پایین
        LevelDefinition.BoxSpec _b10 = createBox(
                300, 600,
                Arrays.asList(PortShape.CIRCLE, PortShape.TRIANGLE, PortShape.SQUARE), // 3 ورودی (حداکثر)
                Arrays.asList(PortShape.SQUARE, PortShape.CIRCLE), // 2 خروجی
                SystemKind.SPY
        );
        boxes.add(withId("L4-B10-SPY3", _b10));

        // 4-11: Normal جدید #1 - برای مسیریابی بین سیستم‌های جاسوسی
        LevelDefinition.BoxSpec _b11 = createBox(
                100, 500,
                Arrays.asList(PortShape.TRIANGLE, PortShape.SQUARE), // 2 ورودی
                Arrays.asList(PortShape.CIRCLE, PortShape.TRIANGLE, PortShape.SQUARE), // 3 خروجی
                SystemKind.NORMAL
        );
        boxes.add(withId("L4-B11-NORMAL", _b11));

        // 4-12: Normal جدید #2 - برای اتصال به Sink‌ها
        LevelDefinition.BoxSpec _b12 = createBox(
                700, 550,
                Arrays.asList(PortShape.CIRCLE, PortShape.TRIANGLE), // 2 ورودی
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE), // 2 خروجی
                SystemKind.NORMAL
        );
        boxes.add(withId("L4-B12-NORMAL2", _b12));

        // 4-13: Distributor از مرحله 1
        LevelDefinition.BoxSpec _b13 = createBox(
                100, 700,
                Collections.singletonList(PortShape.CIRCLE),
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE),
                SystemKind.DISTRIBUTOR
        );
        boxes.add(withId("L1-B3-DISTRIBUTOR", _b13));

        // 4-14: Merger از مرحله 3
        LevelDefinition.BoxSpec _b14 = createBox(
                500, 700,
                Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE, PortShape.TRIANGLE),
                Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE),
                SystemKind.MERGER
        );
        boxes.add(withId("L1-B5-MERGER", _b14));

        // === Sink‌های موجود (با پورت اضافی) ===

        // 4-15: Sink اول - اضافه کردن پورت
        LevelDefinition.BoxSpec _b15 = createSink(950, 100, Arrays.asList(
                PortShape.CIRCLE,
                PortShape.SQUARE,
                PortShape.TRIANGLE
        ));
        boxes.add(withId("L1-B6-SINK", _b15));

        // 4-16: Sink دوم - اضافه کردن پورت
        LevelDefinition.BoxSpec _b16 = createSink(950, 300, Arrays.asList(
                PortShape.TRIANGLE,
                PortShape.CIRCLE
        ));
        boxes.add(withId("L3-B12-SINK", _b16));

        // 4-17: Sink سوم - جدید برای مرحله 4
        LevelDefinition.BoxSpec _b17 = createSink(950, 500, Arrays.asList(
                PortShape.SQUARE,
                PortShape.TRIANGLE
        ));
        boxes.add(withId("L4-B17-SINK", _b17));

        // 4-18: Sink چهارم - جدید برای مرحله 4
        LevelDefinition.BoxSpec _b18 = createSink(950, 700, Arrays.asList(
                PortShape.CIRCLE,
                PortShape.TRIANGLE
        ));
        boxes.add(withId("L4-B18-SINK2", _b18));

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
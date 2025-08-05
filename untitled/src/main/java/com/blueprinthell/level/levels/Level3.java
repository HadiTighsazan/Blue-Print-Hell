// فایل جدید: untitled/src/main/java/com/blueprinthell/level/levels/Level3.java
package com.blueprinthell.level.levels;

import com.blueprinthell.level.AbstractLevel;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.PortShape;

import java.util.*;

public class Level3 extends AbstractLevel {

    public Level3() {
        super(3, "Level 3 - Dynamic Networks & Port Randomizer", 800.0);
        this.packetsPerPort = 4;
        this.maxLossRatio = 0.45;
    }

    @Override
    public String getDescription() {
        return "Manage dynamic port changes with the Port Randomizer system!";
    }

    @Override
    public LevelDefinition getDefinition() {
        List<LevelDefinition.BoxSpec> boxes = new ArrayList<>();

        // === سیستم‌های موجود از مراحل قبل (Source + Normal + Sinks) ===

        // 3-1: Source (از مراحل قبل + یک خروجی جدید)
        LevelDefinition.BoxSpec _b1 = createSource(100, 200, Arrays.asList(
                PortShape.SQUARE,
                PortShape.TRIANGLE,
                PortShape.CIRCLE,
                PortShape.SQUARE  // خروجی اضافی از مرحله 2
        ));
        boxes.add(withId("L1-B1-SOURCE", _b1));

        // 3-2: Normal از مرحله 1 (با پورت اضافی)
        LevelDefinition.BoxSpec _b2 = createBox(
                300, 150,
                Arrays.asList(PortShape.SQUARE, PortShape.CIRCLE), // ورودی اضافی
                Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE, PortShape.TRIANGLE), // خروجی اضافی
                SystemKind.NORMAL
        );
        boxes.add(withId("L1-B2-NORMAL", _b2));

        // 3-3: Distributor (بدون تغییر)
        LevelDefinition.BoxSpec _b3 = createBox(
                300, 350,
                Collections.singletonList(PortShape.CIRCLE),
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE),
                SystemKind.DISTRIBUTOR
        );
        boxes.add(withId("L1-B3-DISTRIBUTOR", _b3));

        // 3-4: Malicious از مرحله 2
        LevelDefinition.BoxSpec _b4 = createBox(
                500, 250,
                Arrays.asList(PortShape.TRIANGLE),
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE),
                SystemKind.MALICIOUS
        );
        boxes.add(withId("L2-B4-MALICIOUS", _b4));

        // 3-5: Spy از مرحله 2 (با خروجی اضافی)
        LevelDefinition.BoxSpec _b5 = createBox(
                500, 400,
                Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE),
                Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE), // خروجی اضافی
                SystemKind.SPY
        );
        boxes.add(withId("L2-B5-SPY", _b5));

        // 3-6: VPN جدید برای مرحله 3
        LevelDefinition.BoxSpec _b6 = createBox(
                300, 500,
                Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE),
                Arrays.asList(PortShape.CIRCLE, PortShape.TRIANGLE),
                SystemKind.VPN
        );
        boxes.add(withId("L3-B6-VPN", _b6));

        // 3-7: Anti-Trojan (با پورت‌های بیشتر)
        LevelDefinition.BoxSpec _b7 = createBox(
                700, 250,
                all(), // می‌تواند همه انواع را بپذیرد
                Arrays.asList(PortShape.SQUARE, PortShape.CIRCLE),
                SystemKind.ANTI_TROJAN
        );
        boxes.add(withId("L3-B7-ANTITROJAN", _b7));

        // === سیستم جدید مرحله 3 ===

        // 3-8: Port Randomizer (جدید)
        LevelDefinition.BoxSpec _b8 = createBox(
                500, 550,
                Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE),
                all(), // هر سه نوع خروجی
                SystemKind.SPY
        );
        boxes.add(withId("L3-B8-SPY", _b8));

        // 3-9: Normal دوم (برای مسیریابی)
        LevelDefinition.BoxSpec _b9 = createBox(
                700, 450,
                Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE),
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE, PortShape.CIRCLE),
                SystemKind.NORMAL
        );
        boxes.add(withId("L1-B4-NORMAL", _b9)); // استفاده از ID مرحله 1

        // 3-10: Merger (با پورت اضافی)
        LevelDefinition.BoxSpec _b10 = createBox(
                700, 600,
                Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE, PortShape.TRIANGLE), // پورت اضافی
                Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE),
                SystemKind.MERGER
        );
        boxes.add(withId("L1-B5-MERGER", _b10));

        // === Sink ها ===

        // 3-11: Sink اول (از مراحل قبل + پورت اضافی)
        LevelDefinition.BoxSpec _b11 = createSink(950, 250, Arrays.asList(
                PortShape.TRIANGLE,
                PortShape.CIRCLE,
                PortShape.SQUARE // پورت اضافی
        ));
        boxes.add(withId("L1-B6-SINK", _b11));

        // 3-12: Sink دوم (جدید برای مرحله 3)
        LevelDefinition.BoxSpec _b12 = createSink(950, 500, Arrays.asList(
                PortShape.SQUARE,
                PortShape.CIRCLE
        ));
        boxes.add(withId("L3-B12-SINK", _b12));

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
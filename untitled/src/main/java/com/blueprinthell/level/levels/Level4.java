package com.blueprinthell.level.levels;

import com.blueprinthell.level.AbstractLevel;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.PortShape;

import java.util.*;

public class Level4 extends AbstractLevel {

    public Level4() {
        super(4, "Stage 4 — Spy Network & Teleportation", 900.0);
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

        // رندم پایدار برای تنوع پورت
        Random rng = new Random(20250804L + levelNumber);

        // === سیستم‌های موجود از مراحل قبل (با پورت‌های اضافی) ===

        // Source - اضافه کردن یک خروجی جدید
        boxes.add(createSource(100, 200, Arrays.asList(
                PortShape.SQUARE,
                PortShape.TRIANGLE,
                PortShape.CIRCLE,
                PortShape.SQUARE,  // خروجی از مرحله قبل
                PortShape.TRIANGLE // خروجی جدید برای مرحله 4
        )));

        // Normal0 از مرحله 1 - اضافه کردن پورت‌های جدید
        boxes.add(createBox(
                300, 150,
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE, PortShape.CIRCLE), // 3 ورودی (حداکثر)
                Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE, PortShape.TRIANGLE), // 3 خروجی (حداکثر)
                SystemKind.NORMAL
        ));

        // Malicious از مرحله 2
        boxes.add(createBox(
                500, 100,
                Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE),
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE),
                SystemKind.MALICIOUS
        ));

        // Spy قدیمی از مرحله 2 - اضافه کردن یک خروجی
        boxes.add(createBox(
                500, 250,
                Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE), // ورودی اضافی
                Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE, PortShape.SQUARE), // خروجی اضافی
                SystemKind.SPY
        ));

        // VPN از مرحله 3
        boxes.add(createBox(
                300, 400,
                Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE),
                Arrays.asList(PortShape.CIRCLE, PortShape.TRIANGLE),
                SystemKind.VPN
        ));

        // Anti-Trojan از مرحله 3
        boxes.add(createBox(
                700, 200,
                all(), // می‌تواند همه انواع را بپذیرد
                Arrays.asList(PortShape.SQUARE, PortShape.CIRCLE),
                SystemKind.ANTI_TROJAN
        ));

        // Normal از مرحله 3
        boxes.add(createBox(
                700, 350,
                Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE),
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE, PortShape.CIRCLE),
                SystemKind.NORMAL
        ));

        // === سیستم‌های جدید مرحله 4 ===

        // 1) Spy جدید #1 - موقعیت بالا
        boxes.add(createBox(
                500, 500,
                Arrays.asList(PortShape.TRIANGLE, PortShape.SQUARE), // 2 ورودی متنوع
                Arrays.asList(PortShape.CIRCLE, PortShape.TRIANGLE), // 2 خروجی
                SystemKind.SPY
        ));

        // 2) Spy جدید #2 - موقعیت وسط
        boxes.add(createBox(
                300, 600,
                Arrays.asList(PortShape.CIRCLE, PortShape.TRIANGLE, PortShape.SQUARE), // 3 ورودی (حداکثر)
                Arrays.asList(PortShape.SQUARE, PortShape.CIRCLE), // 2 خروجی
                SystemKind.SPY
        ));

        // 3) Normal جدید #1 - برای مسیریابی بین سیستم‌های جاسوسی
        boxes.add(createBox(
                100, 500,
                Arrays.asList(PortShape.TRIANGLE, PortShape.SQUARE), // 2 ورودی
                Arrays.asList(PortShape.CIRCLE, PortShape.TRIANGLE, PortShape.SQUARE), // 3 خروجی
                SystemKind.NORMAL
        ));

        // 4) Normal جدید #2 - برای اتصال به Sink‌ها
        boxes.add(createBox(
                700, 550,
                Arrays.asList(PortShape.CIRCLE, PortShape.TRIANGLE), // 2 ورودی
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE), // 2 خروجی
                SystemKind.NORMAL
        ));

        // === Sink‌های موجود (با پورت اضافی) ===

        // Sink0 قدیمی - اضافه کردن پورت
        boxes.add(createSink(950, 100, Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE, PortShape.TRIANGLE)));

        // Sink1 قدیمی - اضافه کردن پورت
        boxes.add(createSink(950, 300, Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE)));

        // Sink2 از مرحله 3
        boxes.add(createSink(950, 450, Arrays.asList(PortShape.SQUARE, PortShape.CIRCLE)));

        // === Sink جدید مرحله 4 ===
        boxes.add(createSink(950, 600, Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE)));

        return new LevelDefinition(boxes, wireBudget);
    }
}
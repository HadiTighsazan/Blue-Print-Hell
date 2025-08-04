package com.blueprinthell.level.levels;

import com.blueprinthell.level.AbstractLevel;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.PortShape;

import java.util.*;

public class Level3 extends AbstractLevel {

    public Level3() {
        super(3, "Stage 3 — VPN & Anti-Trojan Protection", 800.0);
        this.packetsPerPort = 4;
        this.maxLossRatio = 0.4;
    }

    @Override
    public String getDescription() {
        return "Use VPN to protect messenger packets and Anti-Trojan to clean infected packets!";
    }

    @Override
    public LevelDefinition getDefinition() {
        List<LevelDefinition.BoxSpec> boxes = new ArrayList<>();

        // رندم پایدار برای تنوع در پورت‌ها
        Random rng = new Random(20250804L + levelNumber);
        PortShape[] SHAPES = {PortShape.SQUARE, PortShape.TRIANGLE, PortShape.CIRCLE};

        // === سیستم‌های مرحله قبل (Source + Normal0 + Sinks) ===
        // Source با 4 خروجی (برای اتصال به سیستم‌های جدید)
        boxes.add(createSource(100, 200, Arrays.asList(
                PortShape.SQUARE,
                PortShape.TRIANGLE,
                PortShape.CIRCLE,
                PortShape.SQUARE  // خروجی اضافی برای سیستم‌های جدید
        )));

        // Normal0 از مرحله قبل - اضافه کردن یک پورت خروجی جدید
        boxes.add(createBox(
                300, 150,
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE),  // 2 ورودی
                Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE, PortShape.TRIANGLE),  // 3 خروجی (یکی اضافه شد)
                SystemKind.NORMAL
        ));

        // Sink0 قدیمی
        boxes.add(createSink(950, 100, Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE)));

        // === سیستم‌های مرحله قبل - مسیر پایین ===
        // Malicious از مرحله قبل
        boxes.add(createBox(
                500, 250,
                Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE),
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE),
                SystemKind.MALICIOUS
        ));

        // Spy از مرحله قبل
        boxes.add(createBox(
                500, 400,
                Arrays.asList(PortShape.CIRCLE),
                Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE),  // یک خروجی اضافه
                SystemKind.SPY
        ));

        // Sink1 قدیمی
        boxes.add(createSink(950, 350, Arrays.asList(PortShape.TRIANGLE)));

        // === سیستم‌های جدید مرحله 3 ===

        // 1) VPN System - محافظت از پکت‌های پیام‌رسان
        boxes.add(createBox(
                300, 500,
                Arrays.asList(PortShape.CIRCLE, PortShape.SQUARE),  // 2 ورودی متنوع
                Arrays.asList(PortShape.CIRCLE, PortShape.TRIANGLE),  // 2 خروجی محافظت‌شده
                SystemKind.VPN
        ));

        // 2) Anti-Trojan System - پاکسازی پکت‌های آلوده
        boxes.add(createBox(
                700, 250,
                all(),  // می‌تواند همه انواع را بپذیرد (3 ورودی)
                Arrays.asList(PortShape.SQUARE, PortShape.CIRCLE),  // 2 خروجی پاکسازی‌شده
                SystemKind.ANTI_TROJAN
        ));

        // 3) Normal System جدید - برای مسیریابی اضافی
        boxes.add(createBox(
                700, 450,
                Arrays.asList(PortShape.TRIANGLE, PortShape.CIRCLE),  // 2 ورودی
                Arrays.asList(PortShape.SQUARE, PortShape.TRIANGLE, PortShape.CIRCLE),  // 3 خروجی (حداکثر)
                SystemKind.NORMAL
        ));

        // === Sink جدید برای مرحله 3 ===
        boxes.add(createSink(950, 500, Arrays.asList(PortShape.SQUARE, PortShape.CIRCLE)));

        return new LevelDefinition(boxes, wireBudget);
    }
}
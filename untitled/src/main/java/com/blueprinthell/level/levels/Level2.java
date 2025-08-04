package com.blueprinthell.level.levels;

import com.blueprinthell.level.AbstractLevel;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.PortShape;

import java.util.*;

public class Level2 extends AbstractLevel {

    public Level2() {
        super(2, "Stage 2 — Malicious + Spy + New Sink", 600.0);
        this.packetsPerPort = 3;
        this.maxLossRatio = 0.5;
    }

    @Override
    public LevelDefinition getDefinition() {
        List<LevelDefinition.BoxSpec> boxes = new ArrayList<>();

        // رندم پایدار
        Random rng = new Random(20250804L + levelNumber);
        PortShape[] SHAPES = {PortShape.SQUARE, PortShape.TRIANGLE, PortShape.CIRCLE};

        // مسیر اصلی مرحله 1
        PortShape A = SHAPES[rng.nextInt(3)]; // ورودی Normal0 و یکی از خروجی‌های Source
        PortShape B = SHAPES[rng.nextInt(3)]; // خروجی اصلی Normal0 (برای مقصد)

        int ia = (A == PortShape.SQUARE ? 0 : A == PortShape.TRIANGLE ? 1 : 2);
        PortShape S1 = SHAPES[ia];             // = A
        PortShape S2 = SHAPES[(ia + 1) % 3];
        PortShape S3 = SHAPES[(ia + 2) % 3];

        // --- سیستم‌های قبلی با پورت‌های مورد نظر ---
        // Source (قدیمی): 1 خروجی مثل مرحله 1
        boxes.add(createSource(120, 300, Collections.singletonList(S1)));

        boxes.add(createBox(
                360, 260,
                Arrays.asList(S1),
                Arrays.asList(B, S1, S2),
                SystemKind.NORMAL
        ));

        // Sink0 (قدیمی): 1 ورودی مثل مرحله 1
        boxes.add(createSink(980, 300, Collections.singletonList(B)));

        // --- سه سیستم مرحله 2 (جدید) — بدون تغییر ---
        // 1) MALICIOUS: 2 in (S1, S3) → 2 out (B, SQUARE)
        boxes.add(createBox(
                560, 200,
                Arrays.asList(S1, S3),
                Arrays.asList(B, PortShape.SQUARE),
                SystemKind.MALICIOUS
        ));

        // 2) SPY: 1 in (S2) → 1 out (S3)
        boxes.add(createBox(
                520, 420,
                Collections.singletonList(S2),
                Collections.singletonList(S3),
                SystemKind.SPY
        ));

        // 3) Sink1 (جدید)
        boxes.add(createSink(820, 440, Arrays.asList(S3, PortShape.CIRCLE)));

        return new LevelDefinition(boxes, wireBudget);
    }
}

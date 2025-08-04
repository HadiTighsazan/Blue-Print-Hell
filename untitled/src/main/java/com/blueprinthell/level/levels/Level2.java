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
        // Source: قبلاً 3 خروجی (S1,S2,S3) داشت. حالا یک خروجی مربعی صریح هم اضافه می‌کنیم.
        boxes.add(createSource(120, 300, Arrays.asList(S1, S2, S3, PortShape.SQUARE))); // Source: 4 خروجی

        boxes.add(createBox( // Normal0: 1 in (S1) → 2 out (B, S1)
                360, 260,
                Collections.singletonList(S1),
                Arrays.asList(B, S1),
                SystemKind.NORMAL
        ));

        // Sink0 (قدیمی): همان ورودی‌های قبلی (مثلاً دو تا B) بدون تغییر باقی می‌ماند
        boxes.add(createSink(980, 300, Arrays.asList(B, B)));

        // --- سه سیستم مرحله 2 ---
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

        // 3) Sink1 (جدید): 1 in (S3) بوده → حالا یک ورودی دایره‌ای هم اضافه می‌شه
        boxes.add(createSink(820, 440, Arrays.asList(S3, PortShape.CIRCLE)));

        return new LevelDefinition(boxes, wireBudget);
    }
}

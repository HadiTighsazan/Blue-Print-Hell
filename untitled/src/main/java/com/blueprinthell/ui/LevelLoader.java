package com.blueprinthell.ui;

import com.blueprinthell.model.PortShape;
import com.blueprinthell.model.SystemBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * LevelLoader مسئول تولید لیست SystemBox برای هر لول است.
 * این کلاس فعلاً دقیقاً منطق موجود در GameScreen.loadLevel را کپسوله می‌کند.
 */
public final class LevelLoader {

    private LevelLoader() { /* Utility class */ }

    public static List<SystemBox> load(int levelIndex, int centerX, int centerY) {
        switch (levelIndex) {
            case 1: return buildLevel1(centerX, centerY);
            case 2: return buildLevel2(centerX, centerY);
            default: return buildLevelDefault();
        }
    }

    private static List<SystemBox> buildLevel1(int cx, int cy) {
        int w1 = 100, h1 = 60, g1 = 150;
        return Arrays.asList(
                new SystemBox(cx - g1, cy - g1, w1, h1,
                        List.of(),
                        List.of(PortShape.SQUARE, PortShape.TRIANGLE)),
                new SystemBox(cx + g1, cy - g1, w1, h1,
                        List.of(PortShape.TRIANGLE, PortShape.SQUARE),
                        List.of()),
                new SystemBox(cx - g1, cy + g1, w1, h1,
                        List.of(PortShape.SQUARE),
                        List.of(PortShape.SQUARE)),
                new SystemBox(cx + g1, cy + g1, w1, h1,
                        List.of(PortShape.TRIANGLE),
                        List.of(PortShape.TRIANGLE))
        );
    }

    private static List<SystemBox> buildLevel2(int cx, int cy) {
        int w2 = 80, h2 = 50, g2 = 200;
        return Arrays.asList(
                new SystemBox(cx + g2, cy, w2, h2,
                        List.of(),
                        List.of(PortShape.SQUARE, PortShape.SQUARE)),
                new SystemBox(cx - g2, cy, w2, h2,
                        List.of(PortShape.TRIANGLE, PortShape.TRIANGLE),
                        List.of()),
                new SystemBox(cx, cy + g2, w2, h2,
                        List.of(PortShape.SQUARE),
                        List.of(PortShape.TRIANGLE, PortShape.SQUARE)),
                new SystemBox(cx, cy - g2, w2, h2,
                        List.of(PortShape.TRIANGLE),
                        List.of(PortShape.SQUARE, PortShape.TRIANGLE)),
                new SystemBox(cx + g2, cy + g2, w2, h2,
                        List.of(PortShape.SQUARE, PortShape.TRIANGLE),
                        List.of(PortShape.SQUARE, PortShape.TRIANGLE)),
                new SystemBox(cx + g2, cy - g2, w2, h2,
                        List.of(PortShape.SQUARE, PortShape.SQUARE),
                        List.of(PortShape.TRIANGLE)),
                new SystemBox(cx - g2, cy + g2, w2, h2,
                        List.of(PortShape.TRIANGLE),
                        List.of(PortShape.TRIANGLE, PortShape.SQUARE)),
                new SystemBox(cx - g2, cy - g2, w2, h2,
                        List.of(PortShape.SQUARE),
                        List.of(PortShape.SQUARE, PortShape.TRIANGLE))
        );
    }

    private static List<SystemBox> buildLevelDefault() {
        return new ArrayList<>(Arrays.asList(
                new SystemBox(80, 80, 100, 60,
                        List.of(),
                        List.of(PortShape.SQUARE)),
                new SystemBox(280, 80, 100, 60,
                        List.of(PortShape.TRIANGLE),
                        List.of(PortShape.SQUARE)),
                new SystemBox(480, 80, 100, 60,
                        List.of(PortShape.SQUARE),
                        List.of(PortShape.TRIANGLE)),
                new SystemBox(80, 280, 100, 60,
                        List.of(PortShape.SQUARE),
                        List.of(PortShape.SQUARE)),
                new SystemBox(280, 280, 100, 60,
                        List.of(PortShape.TRIANGLE),
                        List.of(PortShape.TRIANGLE)),
                new SystemBox(480, 280, 100, 60,
                        List.of(PortShape.SQUARE),
                        List.of())
        ));
    }
}

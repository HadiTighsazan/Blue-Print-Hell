// فایل جدید: untitled/src/main/java/com/blueprinthell/level/AbstractLevel.java
package com.blueprinthell.level;

import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.PortShape;
import java.util.*;


public abstract class AbstractLevel implements Level {
    protected final int levelNumber;
    protected final String name;
    protected final double wireBudget;

    // Common level parameters
    protected int packetsPerPort = 3;
    protected double maxLossRatio = 0.5;

    protected AbstractLevel(int levelNumber, String name, double wireBudget) {
        this.levelNumber = levelNumber;
        this.name = name;
        this.wireBudget = wireBudget;
    }

    @Override
    public int getLevelNumber() {
        return levelNumber;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public double getWireBudget() {
        return wireBudget;
    }

    @Override
    public int getPacketsPerPort() {
        return packetsPerPort;
    }

    @Override
    public double getMaxLossRatio() {
        return maxLossRatio;
    }


    protected LevelDefinition.BoxSpec createBox(int x, int y,
                                                List<PortShape> inShapes,
                                                List<PortShape> outShapes,
                                                SystemKind kind) {
        return new LevelDefinition.BoxSpec(
                "box-" + UUID.randomUUID().toString().substring(0, 8),
                x, y, 96, 96,
                new ArrayList<>(inShapes),
                new ArrayList<>(outShapes),
                false, false,
                kind
        );
    }


    protected LevelDefinition.BoxSpec createSource(int x, int y, List<PortShape> outShapes) {
        return new LevelDefinition.BoxSpec(
                "source-" + UUID.randomUUID().toString().substring(0, 8),
                x, y, 96, 96,
                List.of(),
                new ArrayList<>(outShapes),
                true, false,
                SystemKind.NORMAL
        );
    }


    protected LevelDefinition.BoxSpec createSink(int x, int y, List<PortShape> inShapes) {
        return new LevelDefinition.BoxSpec(
                "sink-" + UUID.randomUUID().toString().substring(0, 8),
                x, y, 96, 96,
                new ArrayList<>(inShapes),
                List.of(),
                false, true,
                SystemKind.NORMAL
        );
    }


    protected List<PortShape> square() {
        return List.of(PortShape.SQUARE);
    }

    protected List<PortShape> triangle() {
        return List.of(PortShape.TRIANGLE);
    }

    protected List<PortShape> circle() {
        return List.of(PortShape.CIRCLE);
    }

    protected List<PortShape> squareTriangle() {
        return List.of(PortShape.SQUARE, PortShape.TRIANGLE);
    }

    protected List<PortShape> squareCircle() {
        return List.of(PortShape.SQUARE, PortShape.CIRCLE);
    }

    protected List<PortShape> triangleCircle() {
        return List.of(PortShape.TRIANGLE, PortShape.CIRCLE);
    }

    protected List<PortShape> all() {
        return List.of(PortShape.SQUARE, PortShape.TRIANGLE, PortShape.CIRCLE);
    }
}
package com.blueprinthell.level;

import com.blueprinthell.level.generation.*;
import com.blueprinthell.controller.systems.SystemKind;

import java.util.*;


public final class LevelGenerator {

    private static final LevelBuilder levelBuilder = new LevelBuilder();

    private LevelGenerator() {}


    public static LevelDefinition firstLevel() {
        LevelBuilder.LevelParameters params = new LevelBuilder.LevelParameters();
        params.levelNumber = 0;
        params.sourceCount = 1;
        params.sinkCount = 1;
        params.minBoxCount = 2;
        params.wireBudget = 500.0;
        params.layoutStrategy = LevelBuilder.LayoutStrategy.ROLE_BASED;

        return levelBuilder.buildLevel(params);
    }


    public static LevelDefinition nextLevel(LevelDefinition prev) {
        // Extract level number from previous level
        int levelNum = estimateLevelNumber(prev);

        // Create parameters for next level
        LevelBuilder.LevelParameters params = LevelBuilder.LevelParameters.forLevel(levelNum + 1);

        // Copy existing boxes for incremental building
        params.existingBoxes = new ArrayList<>(prev.boxes());

        // Increase wire budget
        params.wireBudget = prev.totalWireLength() * 1.5;

        // Build the level
        LevelDefinition candidate = levelBuilder.buildLevel(params);

        // Ensure we don't exceed port capacity of existing boxes
        if (!hasEnoughPortCapacity(prev.boxes(), candidate.boxes())) {
            // Retry with adjusted parameters
            params.minBoxCount = prev.boxes().size() + 1;
            candidate = levelBuilder.buildLevel(params);
        }

        return candidate;
    }


    private static int estimateLevelNumber(LevelDefinition level) {
        // Simple heuristic: count special systems
        long specialSystems = level.boxes().stream()
                .filter(box -> box.kind() != null && box.kind() != SystemKind.NORMAL)
                .count();

        return (int) Math.min(specialSystems, level.boxes().size() - 2);
    }


    private static boolean hasEnoughPortCapacity(List<LevelDefinition.BoxSpec> existing,
                                                 List<LevelDefinition.BoxSpec> allBoxes) {
        int existingCount = existing.size();
        if (existingCount >= allBoxes.size()) {
            return true;
        }

        List<LevelDefinition.BoxSpec> newBoxes = allBoxes.subList(existingCount, allBoxes.size());

        int newInputPorts = newBoxes.stream()
                .mapToInt(box -> box.inShapes().size())
                .sum();

        int newOutputPorts = newBoxes.stream()
                .mapToInt(box -> box.outShapes().size())
                .sum();

        int requiredPorts = Math.max(0, newInputPorts - newOutputPorts);

        int availableCapacity = existing.stream()
                .mapToInt(box -> 3 - box.outShapes().size()) // Max 3 ports per box
                .sum();

        return requiredPorts <= availableCapacity;
    }

    // Maintain these methods for backward compatibility if needed
    @Deprecated
    private static List<LevelDefinition.BoxSpec> smartBalancePortCounts(
            List<LevelDefinition.BoxSpec> original) {
        // Delegate to new components
        ConnectivityValidator validator = new ConnectivityValidator();
        PortBalancer balancer = new PortBalancer(validator);
        return balancer.balancePorts(original);
    }

    @Deprecated
    private static boolean isValidLevel(List<LevelDefinition.BoxSpec> boxes) {
        ConnectivityValidator validator = new ConnectivityValidator();
        return validator.isFullyConnected(boxes) && !validator.requiresSelfLoop(boxes);
    }

    @Deprecated
    private static List<LevelDefinition.BoxSpec> layoutBoxes(
            List<LevelDefinition.BoxSpec> original) {
        BoxLayoutManager layoutManager = new BoxLayoutManager();
        return layoutManager.layoutBoxes(original);
    }
}
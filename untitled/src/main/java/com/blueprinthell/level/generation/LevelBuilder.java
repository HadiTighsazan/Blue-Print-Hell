package com.blueprinthell.level.generation;

import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.model.PortShape;

import java.util.*;


public class LevelBuilder {

    private final ConnectivityValidator connectivityValidator;
    private final PortBalancer portBalancer;
    private final BoxLayoutManager layoutManager;
    private final SystemTypeDistributor typeDistributor;
    private final Random random = new Random();

    public LevelBuilder() {
        this.connectivityValidator = new ConnectivityValidator();
        this.portBalancer = new PortBalancer(connectivityValidator);
        this.layoutManager = new BoxLayoutManager();
        this.typeDistributor = new SystemTypeDistributor();
    }


    public LevelDefinition buildLevel(LevelParameters parameters) {
        List<LevelDefinition.BoxSpec> boxes;
        int attempts = 0;

        do {
            // Step 1: Create initial box configuration
            boxes = createInitialBoxes(parameters);

            // Step 2: Balance ports to ensure valid connections
            boxes = portBalancer.balancePorts(boxes);

            attempts++;
        } while (!isValidLevel(boxes) && attempts < parameters.maxAttempts);

        // Step 3: Apply layout
        boxes = applyLayout(boxes, parameters.layoutStrategy);

        return new LevelDefinition(boxes, parameters.wireBudget);
    }


    private List<LevelDefinition.BoxSpec> createInitialBoxes(LevelParameters params) {
        List<LevelDefinition.BoxSpec> boxes = new ArrayList<>();

        // Add existing boxes if this is a continuation
        if (params.existingBoxes != null) {
            boxes.addAll(params.existingBoxes);
        }

        // Add source boxes
        for (int i = 0; i < params.sourceCount; i++) {
            boxes.add(createSourceBox(params));
        }

        // Add intermediate boxes with special systems
        List<SystemKind> systemTypes = typeDistributor.getSystemsForLevel(params.levelNumber);
        for (SystemKind kind : systemTypes) {
            boxes.add(createSystemBox(kind, params));
        }

        // Add additional normal boxes if needed
        int normalBoxes = params.minBoxCount - boxes.size() - params.sinkCount;
        for (int i = 0; i < normalBoxes; i++) {
            boxes.add(createSystemBox(SystemKind.NORMAL, params));
        }

        // Add sink boxes
        for (int i = 0; i < params.sinkCount; i++) {
            boxes.add(createSinkBox(params));
        }

        return boxes;
    }


    private LevelDefinition.BoxSpec createSourceBox(LevelParameters params) {
        int portCount = 1 + random.nextInt(Math.min(3, params.maxPortsPerBox));
        List<PortShape> outShapes = randomShapes(portCount);

        return new LevelDefinition.BoxSpec(
                "source-" + UUID.randomUUID().toString().substring(0, 8),
                0, 0, // Position will be set by layout manager
                params.boxWidth,
                params.boxHeight,
                List.of(), // No inputs for source
                outShapes,
                true, // isSource
                false, // isSink
                SystemKind.NORMAL
        );
    }


    private LevelDefinition.BoxSpec createSinkBox(LevelParameters params) {
        int portCount = 1 + random.nextInt(Math.min(3, params.maxPortsPerBox));
        List<PortShape> inShapes = randomShapes(portCount);

        return new LevelDefinition.BoxSpec(
                "sink-" + UUID.randomUUID().toString().substring(0, 8),
                0, 0, // Position will be set by layout manager
                params.boxWidth,
                params.boxHeight,
                inShapes,
                List.of(), // No outputs for sink
                false, // isSource
                true, // isSink
                SystemKind.NORMAL
        );
    }


    private LevelDefinition.BoxSpec createSystemBox(SystemKind kind, LevelParameters params) {
        SystemTypeDistributor.PortConfiguration portConfig =
                typeDistributor.getPortConfigForSystemKind(kind);

        // Limit ports based on parameters
        List<PortShape> inShapes = limitPorts(portConfig.inShapes, params.maxPortsPerBox);
        List<PortShape> outShapes = limitPorts(portConfig.outShapes, params.maxPortsPerBox);

        return new LevelDefinition.BoxSpec(
                kind.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8),
                0, 0, // Position will be set by layout manager
                params.boxWidth,
                params.boxHeight,
                inShapes,
                outShapes,
                false, // isSource
                false, // isSink
                kind
        );
    }


    private boolean isValidLevel(List<LevelDefinition.BoxSpec> boxes) {
        // Must be fully connected
        if (!connectivityValidator.isFullyConnected(boxes)) {
            return false;
        }

        // Must not require self-loops
        if (connectivityValidator.requiresSelfLoop(boxes)) {
            return false;
        }

        // Must have balanced ports
        PortBalancer.PortBalance balance = portBalancer.calculateBalance(boxes);
        if (!balance.isBalanced()) {
            return false;
        }

        return true;
    }


    private List<LevelDefinition.BoxSpec> applyLayout(List<LevelDefinition.BoxSpec> boxes,
                                                      LayoutStrategy strategy) {
        switch (strategy) {
            case HIERARCHICAL:
                return layoutManager.layoutBoxesHierarchical(boxes, connectivityValidator);

            case ROLE_BASED:
                return layoutManager.layoutBoxesWithRoles(boxes);

            case GRID:
            default:
                return layoutManager.layoutBoxes(boxes);
        }
    }

    private List<PortShape> limitPorts(List<PortShape> ports, int maxPorts) {
        if (ports.size() <= maxPorts) {
            return ports;
        }
        return new ArrayList<>(ports.subList(0, maxPorts));
    }

    private PortShape randomShape() {
        int r = random.nextInt(3);
        return (r == 0) ? PortShape.SQUARE
                : (r == 1) ? PortShape.TRIANGLE
                : PortShape.CIRCLE;
    }

    private List<PortShape> randomShapes(int count) {
        List<PortShape> shapes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            shapes.add(randomShape());
        }
        return shapes;
    }


    public static class LevelParameters {
        public int levelNumber;
        public int sourceCount = 1;
        public int sinkCount = 1;
        public int minBoxCount = 2;
        public int maxPortsPerBox = 3;
        public int boxWidth = 96;
        public int boxHeight = 96;
        public double wireBudget = 500.0;
        public int maxAttempts = 100;
        public LayoutStrategy layoutStrategy = LayoutStrategy.GRID;
        public List<LevelDefinition.BoxSpec> existingBoxes = null;

        public static LevelParameters forLevel(int level) {
            LevelParameters params = new LevelParameters();
            params.levelNumber = level;

            // Increase complexity with level
            params.minBoxCount = 2 + level;
            params.wireBudget = 500.0 * (1 + level * 0.5);

            // Use hierarchical layout for complex levels
            if (level >= 3) {
                params.layoutStrategy = LayoutStrategy.HIERARCHICAL;
            }

            return params;
        }
    }


    public enum LayoutStrategy {
        GRID,
        HIERARCHICAL,
        ROLE_BASED
    }
}
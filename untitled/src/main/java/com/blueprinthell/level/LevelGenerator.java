package com.blueprinthell.level;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.PortShape;
import java.util.*;

public final class LevelGenerator {

    private static final int BOX_W = Config.SYSTEM_WIDTH;
    private static final int BOX_H = Config.SYSTEM_HEIGHT;
    private static final Random RND = new Random();

    private LevelGenerator() {}

    /**
     * Generate first level with basic systems
     */
    public static LevelDefinition firstLevel() {
        List<LevelDefinition.BoxSpec> boxes;
        int attempts = 0;
        do {
            boxes = new ArrayList<>();

            // Source (Normal)
            int portCount = 1 + RND.nextInt(3);
            List<PortShape> shapes = randomShapes(portCount);
            boxes.add(new LevelDefinition.BoxSpec(
                    "source-1",
                    50, 100, BOX_W, BOX_H,
                    List.of(), shapes,
                    true, false,
                    SystemKind.NORMAL
            ));

            // Sink (Normal)
            boxes.add(new LevelDefinition.BoxSpec(
                    "sink-1",
                    350, 100, BOX_W, BOX_H,
                    shapes, List.of(),
                    false, true,
                    SystemKind.NORMAL
            ));

            attempts++;
        } while (!isValidLevel(boxes) && attempts < 100);

        boxes = layoutBoxes(boxes);
        double budget = 500;
        return new LevelDefinition(boxes, budget);
    }

    /**
     * Generate next level with increasing complexity and special systems
     */
    public static LevelDefinition nextLevel(LevelDefinition prev) {
        List<LevelDefinition.BoxSpec> boxes;
        int levelNum = prev.boxes().size() - 1; // Approximate level number

        int attempts = 0;
        do {
            boxes = new ArrayList<>();

            // Copy existing boxes
            copyExistingBoxes(prev, boxes);

            // Add new boxes based on level progression
            addNewBoxesForLevel(boxes, levelNum);

            attempts++;
        } while (!isValidLevel(boxes) && attempts < 100);

        boxes = smartBalancePortCounts(boxes);
        boxes = layoutBoxes(boxes);

        double budget = prev.totalWireLength() * 1.5;
        return new LevelDefinition(boxes, budget);
    }

    /**
     * Copy existing boxes from previous level
     */
    private static void copyExistingBoxes(LevelDefinition prev, List<LevelDefinition.BoxSpec> boxes) {
        for (LevelDefinition.BoxSpec b : prev.boxes()) {
            boolean isSource = b.isSource();
            int inCount = isSource ? 0 : 1 + RND.nextInt(3);
            int outCount = 1 + RND.nextInt(3);
            List<PortShape> inSh = isSource ? List.of() : randomShapes(inCount);
            List<PortShape> outSh = randomShapes(outCount);

            boxes.add(new LevelDefinition.BoxSpec(
                    b.id(), b.x(), b.y(), b.width(), b.height(),
                    inSh, outSh,
                    isSource, false,
                    b.kind() // Preserve original kind
            ));
        }
    }

    /**
     * Add new boxes based on level progression
     */
    private static void addNewBoxesForLevel(List<LevelDefinition.BoxSpec> boxes, int levelNum) {
        LevelDefinition.BoxSpec lastBox = boxes.get(boxes.size() - 1);
        int baseX = lastBox.x() + 150;
        int baseY = lastBox.y();

        // Determine which special systems to add based on level
        List<SystemKind> systemsToAdd = determineSystemsForLevel(levelNum);

        int offsetY = 0;
        for (int i = 0; i < systemsToAdd.size(); i++) {
            SystemKind kind = systemsToAdd.get(i);
            int x = baseX + (i % 2) * 200;
            int y = baseY + offsetY;

            if (i % 2 == 1) offsetY += 150;

            // Create appropriate port configuration for system type
            PortConfig portConfig = getPortConfigForSystemKind(kind);

            String id = kind.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8);

            boxes.add(new LevelDefinition.BoxSpec(
                    id, x, y, BOX_W, BOX_H,
                    portConfig.inShapes, portConfig.outShapes,
                    false, false,
                    kind
            ));
        }

        // Add final sink
        int sinkX = baseX + 400;
        int sinkY = baseY;
        List<PortShape> sinkIns = randomShapes(2 + RND.nextInt(2));
        boxes.add(new LevelDefinition.BoxSpec(
                "sink-" + levelNum,
                sinkX, sinkY, BOX_W, BOX_H,
                sinkIns, List.of(),
                false, true,
                SystemKind.NORMAL
        ));
    }

    /**
     * Determine which special systems to introduce at each level
     */
    private static List<SystemKind> determineSystemsForLevel(int levelNum) {
        List<SystemKind> systems = new ArrayList<>();

        // Progressive introduction of special systems
        switch (levelNum) {
            case 0:
                // Level 1: Just normal systems
                systems.add(SystemKind.NORMAL);
                break;

            case 1:
                // Level 2: Introduce VPN
                systems.add(SystemKind.VPN);
                systems.add(SystemKind.NORMAL);
                break;

            case 2:
                // Level 3: Add Malicious
                systems.add(SystemKind.MALICIOUS);
                systems.add(SystemKind.VPN);
                break;

            case 3:
                // Level 4: Add Anti-Trojan
                systems.add(SystemKind.ANTI_TROJAN);
                systems.add(SystemKind.MALICIOUS);
                systems.add(SystemKind.NORMAL);
                break;

            case 4:
                // Level 5: Add Spy
                systems.add(SystemKind.SPY);
                systems.add(SystemKind.VPN);
                systems.add(SystemKind.ANTI_TROJAN);
                break;

            case 5:
                // Level 6: Add Distributor/Merger
                systems.add(SystemKind.DISTRIBUTOR);
                systems.add(SystemKind.SPY);
                systems.add(SystemKind.MERGER);
                break;

            default:
                // Level 7+: Random mix of special systems
                List<SystemKind> pool = Arrays.asList(
                        SystemKind.SPY,
                        SystemKind.MALICIOUS,
                        SystemKind.VPN,
                        SystemKind.ANTI_TROJAN,
                        SystemKind.DISTRIBUTOR,
                        SystemKind.MERGER,
                        SystemKind.PORT_RANDOMIZER
                );

                // Add 2-4 random special systems
                int count = 2 + RND.nextInt(3);
                Collections.shuffle(pool);
                systems.addAll(pool.subList(0, Math.min(count, pool.size())));

                // Always add some normal systems for balance
                if (RND.nextBoolean()) {
                    systems.add(SystemKind.NORMAL);
                }
                break;
        }

        return systems;
    }

    /**
     * Get appropriate port configuration for system kind
     */
    private static PortConfig getPortConfigForSystemKind(SystemKind kind) {
        switch (kind) {
            case SPY:
                // Spies need multiple outputs for teleportation
                return new PortConfig(
                        randomShapes(1 + RND.nextInt(2)),
                        randomShapes(2 + RND.nextInt(2))
                );

            case MALICIOUS:
                // Malicious systems work with any configuration
                return new PortConfig(
                        randomShapes(1 + RND.nextInt(3)),
                        randomShapes(1 + RND.nextInt(3))
                );

            case VPN:
                // VPN systems need balanced I/O
                int count = 1 + RND.nextInt(3);
                return new PortConfig(
                        randomShapes(count),
                        randomShapes(count)
                );

            case ANTI_TROJAN:
                // Anti-Trojan works as a passthrough
                return new PortConfig(
                        randomShapes(1 + RND.nextInt(2)),
                        randomShapes(1 + RND.nextInt(2))
                );

            case DISTRIBUTOR:
                // Distributor: few inputs, many outputs
                return new PortConfig(
                        randomShapes(1),
                        randomShapes(3)
                );

            case MERGER:
                // Merger: many inputs, few outputs
                return new PortConfig(
                        randomShapes(3),
                        randomShapes(1)
                );

            case PORT_RANDOMIZER:
                // Port randomizer needs multiple ports to randomize
                return new PortConfig(
                        randomShapes(2 + RND.nextInt(2)),
                        randomShapes(2 + RND.nextInt(2))
                );

            case NORMAL:
            default:
                return new PortConfig(
                        randomShapes(1 + RND.nextInt(3)),
                        randomShapes(1 + RND.nextInt(3))
                );
        }
    }

    // Rest of the methods remain the same...
    private static boolean isValidLevel(List<LevelDefinition.BoxSpec> boxes) {
        return isFullyConnected(boxes) && !requiresSelfLoop(boxes);
    }

    private static boolean isFullyConnected(List<LevelDefinition.BoxSpec> boxes) {
        int n = boxes.size();
        if (n == 0) return true;

        List<List<Integer>> adj = buildAdjacencyList(boxes);
        boolean[] visited = new boolean[n];
        Queue<Integer> queue = new ArrayDeque<>();
        queue.add(0);
        visited[0] = true;
        int visitedCount = 1;

        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (int v : adj.get(u)) {
                if (!visited[v]) {
                    visited[v] = true;
                    visitedCount++;
                    queue.add(v);
                }
            }
        }

        return visitedCount == n;
    }

    private static boolean requiresSelfLoop(List<LevelDefinition.BoxSpec> boxes) {
        int n = boxes.size();

        for (int i = 0; i < n; i++) {
            LevelDefinition.BoxSpec box = boxes.get(i);
            int myOutputs = box.outShapes().size();

            if (myOutputs == 0) continue;

            int availableInputs = countAvailableDownstreamInputs(boxes, i);

            if (myOutputs > availableInputs) {
                return true;
            }
        }

        return false;
    }

    private static int countAvailableDownstreamInputs(List<LevelDefinition.BoxSpec> boxes, int fromIndex) {
        LevelDefinition.BoxSpec fromBox = boxes.get(fromIndex);
        int count = 0;

        for (int i = 0; i < boxes.size(); i++) {
            if (i == fromIndex) continue;

            LevelDefinition.BoxSpec toBox = boxes.get(i);

            for (PortShape outShape : fromBox.outShapes()) {
                count += Collections.frequency(toBox.inShapes(), outShape);
            }
        }

        return count;
    }

    private static List<List<Integer>> buildAdjacencyList(List<LevelDefinition.BoxSpec> boxes) {
        int n = boxes.size();
        List<List<Integer>> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());

        for (int i = 0; i < n; i++) {
            List<PortShape> outs = boxes.get(i).outShapes();
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                List<PortShape> ins = boxes.get(j).inShapes();
                if (outs.stream().anyMatch(ins::contains)) {
                    adj.get(i).add(j);
                }
            }
        }

        return adj;
    }

    private static List<LevelDefinition.BoxSpec> smartBalancePortCounts(List<LevelDefinition.BoxSpec> orig) {
        List<LevelDefinition.BoxSpec> boxes = new ArrayList<>(orig);
        int maxIterations = 100;
        int iteration = 0;

        while (iteration < maxIterations) {
            if (requiresSelfLoop(boxes)) {
                if (!fixSelfLoopRequirement(boxes)) {
                    break;
                }
            }

            int inTotal = boxes.stream().mapToInt(b -> b.inShapes().size()).sum();
            int outTotal = boxes.stream().mapToInt(b -> b.outShapes().size()).sum();
            int delta = inTotal - outTotal;

            if (delta == 0) {
                if (isValidLevel(boxes)) {
                    return boxes;
                }
                if (!addStrategicPorts(boxes)) {
                    break;
                }
            } else if (delta > 0) {
                if (!addOutputsSmartly(boxes, delta)) {
                    break;
                }
            } else {
                if (!addInputsSmartly(boxes, -delta)) {
                    break;
                }
            }

            iteration++;
        }

        return boxes;
    }

    private static boolean fixSelfLoopRequirement(List<LevelDefinition.BoxSpec> boxes) {
        for (int i = 0; i < boxes.size(); i++) {
            LevelDefinition.BoxSpec box = boxes.get(i);
            int outputs = box.outShapes().size();
            int availableInputs = countAvailableDownstreamInputs(boxes, i);

            if (outputs > availableInputs) {
                if (addCompatibleInputsDownstream(boxes, i, outputs - availableInputs)) {
                    return true;
                }

                if (outputs > 1 && !box.isSource()) {
                    List<PortShape> newOuts = new ArrayList<>(box.outShapes());
                    newOuts.remove(newOuts.size() - 1);
                    boxes.set(i, new LevelDefinition.BoxSpec(
                            box.id(), box.x(), box.y(), box.width(), box.height(),
                            box.inShapes(), newOuts,
                            box.isSource(), box.isSink(),
                            box.kind()));
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean addCompatibleInputsDownstream(List<LevelDefinition.BoxSpec> boxes,
                                                         int fromIndex, int needed) {
        LevelDefinition.BoxSpec fromBox = boxes.get(fromIndex);
        List<PortShape> outputShapes = fromBox.outShapes();

        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            if (i != fromIndex && boxes.get(i).inShapes().size() < 3 && !boxes.get(i).isSource()) {
                candidates.add(i);
            }
        }

        if (candidates.isEmpty()) return false;

        int added = 0;
        for (int idx : candidates) {
            if (added >= needed) break;

            LevelDefinition.BoxSpec candidate = boxes.get(idx);
            PortShape shapeToAdd = outputShapes.get(RND.nextInt(outputShapes.size()));

            List<PortShape> newIns = new ArrayList<>(candidate.inShapes());
            newIns.add(shapeToAdd);

            boxes.set(idx, new LevelDefinition.BoxSpec(
                    candidate.id(), candidate.x(), candidate.y(),
                    candidate.width(), candidate.height(),
                    newIns, candidate.outShapes(),
                    candidate.isSource(), candidate.isSink(),
                    candidate.kind()));

            added++;
        }

        return added > 0;
    }

    private static boolean addOutputsSmartly(List<LevelDefinition.BoxSpec> boxes, int needed) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            LevelDefinition.BoxSpec b = boxes.get(i);
            if (b.outShapes().size() < 3 && !b.isSink()) {
                int currentOutputs = b.outShapes().size();
                int availableInputs = countAvailableDownstreamInputs(boxes, i);
                if (currentOutputs < availableInputs) {
                    candidates.add(i);
                }
            }
        }

        if (candidates.isEmpty()) {
            boxes.add(new LevelDefinition.BoxSpec(
                    "router-" + UUID.randomUUID().toString().substring(0, 8),
                    50, 50, BOX_W, BOX_H,
                    List.of(), randomShapes(Math.min(3, needed)),
                    false, false,
                    SystemKind.NORMAL));
            return true;
        }

        int added = 0;
        for (int idx : candidates) {
            if (added >= needed) break;
            LevelDefinition.BoxSpec b = boxes.get(idx);
            List<PortShape> newOut = new ArrayList<>(b.outShapes());
            newOut.add(chooseShapeForConnectivity(boxes, idx, false));
            boxes.set(idx, new LevelDefinition.BoxSpec(
                    b.id(), b.x(), b.y(), b.width(), b.height(),
                    b.inShapes(), newOut,
                    b.isSource(), b.isSink(),
                    b.kind()));
            added++;
        }

        return added > 0;
    }

    private static boolean addInputsSmartly(List<LevelDefinition.BoxSpec> boxes, int needed) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            LevelDefinition.BoxSpec b = boxes.get(i);
            if (b.inShapes().size() < 3 && !b.isSource()) {
                candidates.add(i);
            }
        }

        if (candidates.isEmpty()) {
            boxes.add(new LevelDefinition.BoxSpec(
                    "collector-" + UUID.randomUUID().toString().substring(0, 8),
                    50, 50, BOX_W, BOX_H,
                    randomShapes(Math.min(3, needed)), List.of(),
                    false, true,
                    SystemKind.NORMAL));
            return true;
        }

        int added = 0;
        for (int idx : candidates) {
            if (added >= needed) break;
            LevelDefinition.BoxSpec b = boxes.get(idx);
            List<PortShape> newIn = new ArrayList<>(b.inShapes());
            newIn.add(chooseShapeForConnectivity(boxes, idx, true));
            boxes.set(idx, new LevelDefinition.BoxSpec(
                    b.id(), b.x(), b.y(), b.width(), b.height(),
                    newIn, b.outShapes(),
                    b.isSource(), b.isSink(),
                    b.kind()));
            added++;
        }

        return added > 0;
    }

    private static boolean addStrategicPorts(List<LevelDefinition.BoxSpec> boxes) {
        List<Set<Integer>> components = findComponents(boxes);
        if (components.size() <= 1 && !requiresSelfLoop(boxes)) {
            return false;
        }

        if (components.size() > 1) {
            Set<Integer> comp1 = components.get(0);
            Set<Integer> comp2 = components.get(1);

            for (int i : comp1) {
                for (int j : comp2) {
                    LevelDefinition.BoxSpec bi = boxes.get(i);
                    LevelDefinition.BoxSpec bj = boxes.get(j);

                    if (bi.outShapes().size() < 3 && bj.inShapes().size() < 3 && !bj.isSource()) {
                        PortShape shape = randomShape();

                        List<PortShape> newOutI = new ArrayList<>(bi.outShapes());
                        newOutI.add(shape);
                        boxes.set(i, new LevelDefinition.BoxSpec(
                                bi.id(), bi.x(), bi.y(), bi.width(), bi.height(),
                                bi.inShapes(), newOutI,
                                bi.isSource(), bi.isSink(),
                                bi.kind()));

                        List<PortShape> newInJ = new ArrayList<>(bj.inShapes());
                        newInJ.add(shape);
                        boxes.set(j, new LevelDefinition.BoxSpec(
                                bj.id(), bj.x(), bj.y(), bj.width(), bj.height(),
                                newInJ, bj.outShapes(),
                                bj.isSource(), bj.isSink(),
                                bj.kind()));

                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static List<Set<Integer>> findComponents(List<LevelDefinition.BoxSpec> boxes) {
        int n = boxes.size();
        List<List<Integer>> adj = buildAdjacencyList(boxes);
        boolean[] visited = new boolean[n];
        List<Set<Integer>> components = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                Set<Integer> component = new HashSet<>();
                dfs(i, adj, visited, component);
                components.add(component);
            }
        }

        return components;
    }

    private static void dfs(int u, List<List<Integer>> adj, boolean[] visited, Set<Integer> component) {
        visited[u] = true;
        component.add(u);
        for (int v : adj.get(u)) {
            if (!visited[v]) {
                dfs(v, adj, visited, component);
            }
        }
    }

    private static PortShape chooseShapeForConnectivity(List<LevelDefinition.BoxSpec> boxes, int idx, boolean forInput) {
        Map<PortShape, Integer> shapeFreq = new HashMap<>();

        for (int i = 0; i < boxes.size(); i++) {
            if (i == idx) continue;
            LevelDefinition.BoxSpec other = boxes.get(i);

            if (forInput) {
                for (PortShape s : other.outShapes()) {
                    shapeFreq.merge(s, 1, Integer::sum);
                }
            } else {
                for (PortShape s : other.inShapes()) {
                    shapeFreq.merge(s, 1, Integer::sum);
                }
            }
        }

        return shapeFreq.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(randomShape());
    }

    private static PortShape randomShape() {
        int r = RND.nextInt(3);
        return (r == 0) ? PortShape.SQUARE
                : (r == 1) ? PortShape.TRIANGLE
                : PortShape.CIRCLE;
    }

    private static List<PortShape> randomShapes(int count) {
        if (count > 3) count = 3;
        List<PortShape> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(randomShape());
        return list;
    }

    private static List<LevelDefinition.BoxSpec> layoutBoxes(List<LevelDefinition.BoxSpec> original) {
        List<LevelDefinition.BoxSpec> res = new ArrayList<>(original.size());
        int i = 0;
        for (LevelDefinition.BoxSpec b : original) {
            int col = i % 4;
            int row = i / 4;
            int nx = 50 + col * 200;
            int ny = 100 + row * 150;
            res.add(new LevelDefinition.BoxSpec(
                    b.id(), nx, ny, BOX_W, BOX_H,
                    b.inShapes(), b.outShapes(),
                    b.isSource(), b.isSink(),
                    b.kind()));
            i++;
        }
        return res;
    }

    // Helper class for port configuration
    private static class PortConfig {
        final List<PortShape> inShapes;
        final List<PortShape> outShapes;

        PortConfig(List<PortShape> inShapes, List<PortShape> outShapes) {
            this.inShapes = inShapes;
            this.outShapes = outShapes;
        }
    }
}
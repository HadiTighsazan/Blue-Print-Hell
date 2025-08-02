package com.blueprinthell.level;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PortShape;
import java.util.*;

public final class LevelGenerator {

    private static final int BOX_W = Config.SYSTEM_WIDTH;
    private static final int BOX_H = Config.SYSTEM_HEIGHT;
    private static final Random RND = new Random();

    private LevelGenerator() {}

    public static LevelDefinition firstLevel() {
        List<LevelDefinition.BoxSpec> boxes;
        int attempts = 0;
        do {
            boxes = new ArrayList<>();
            int portCount = 1 + RND.nextInt(3);
            List<PortShape> shapes = randomShapes(portCount);
            boxes.add(new LevelDefinition.BoxSpec(
                    50, 100, BOX_W, BOX_H,
                    List.of(), shapes,
                    true, false));
            boxes.add(new LevelDefinition.BoxSpec(
                    350, 100, BOX_W, BOX_H,
                    shapes, List.of(),
                    false, true));
            attempts++;
        } while (!isValidLevel(boxes) && attempts < 100);

        boxes = layoutBoxes(boxes);
        double budget = 500;
        return new LevelDefinition(boxes, budget);
    }

    public static LevelDefinition nextLevel(LevelDefinition prev) {
        List<LevelDefinition.BoxSpec> boxes;
        int attempts = 0;
        do {
            boxes = new ArrayList<>();
            LevelDefinition.BoxSpec oldSink = prev.boxes().stream()
                    .filter(LevelDefinition.BoxSpec::isSink)
                    .findFirst().orElseThrow();

            // Copy existing boxes with new ports
            for (LevelDefinition.BoxSpec b : prev.boxes()) {
                boolean isSource = b.isSource();
                boolean newSinkFlag = false;
                int inCount  = isSource ? 0 : 1 + RND.nextInt(3);
                int outCount = 1 + RND.nextInt(3);
                List<PortShape> inSh  = isSource ? List.of() : randomShapes(inCount);
                List<PortShape> outSh = randomShapes(outCount);
                boxes.add(new LevelDefinition.BoxSpec(
                        b.id(), b.x(), b.y(), b.width(), b.height(),
                        inSh, outSh,
                        isSource, newSinkFlag,
                        b.kind()));
            }

            // Add new intermediate box
            int ix = oldSink.x() + 150;
            int iy = oldSink.y() + (RND.nextBoolean() ? 120 : -120);
            List<PortShape> midIns  = randomShapes(1 + RND.nextInt(3));
            List<PortShape> midOuts = randomShapes(1 + RND.nextInt(3));
            boxes.add(new LevelDefinition.BoxSpec(
                    ix, iy, BOX_W, BOX_H,
                    midIns, midOuts,
                    false, false));

            // Add new sink
            int sx = ix + 300;
            int sy = iy;
            List<PortShape> sinkIns = randomShapes(1 + RND.nextInt(3));
            boxes.add(new LevelDefinition.BoxSpec(
                    sx, sy, BOX_W, BOX_H,
                    sinkIns, List.of(),
                    false, true));

            attempts++;
        } while (!isValidLevel(boxes) && attempts < 100);

        boxes = smartBalancePortCounts(boxes);
        boxes = layoutBoxes(boxes);

        double budget = prev.totalWireLength() * 1.5;
        return new LevelDefinition(boxes, budget);
    }

    private static boolean isValidLevel(List<LevelDefinition.BoxSpec> boxes) {
        return isFullyConnected(boxes) && !requiresSelfLoop(boxes);
    }

    // Check if the graph is fully connected (all nodes reachable)
    private static boolean isFullyConnected(List<LevelDefinition.BoxSpec> boxes) {
        int n = boxes.size();
        if (n == 0) return true;

        // Build adjacency list
        List<List<Integer>> adj = buildAdjacencyList(boxes);

        // BFS from node 0 to check if all nodes are reachable
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

    // NEW: Check if any box would require self-loop using flow analysis
    private static boolean requiresSelfLoop(List<LevelDefinition.BoxSpec> boxes) {
        int n = boxes.size();

        // For each box, calculate available inputs downstream
        for (int i = 0; i < n; i++) {
            LevelDefinition.BoxSpec box = boxes.get(i);
            int myOutputs = box.outShapes().size();

            if (myOutputs == 0) continue; // No outputs, no problem

            // Count available compatible inputs in downstream boxes
            int availableInputs = countAvailableDownstreamInputs(boxes, i);

            // If my outputs exceed available downstream inputs, I need self-loop
            if (myOutputs > availableInputs) {
                return true;
            }
        }

        return false;
    }

    // Count how many compatible input ports are available downstream
    private static int countAvailableDownstreamInputs(List<LevelDefinition.BoxSpec> boxes, int fromIndex) {
        LevelDefinition.BoxSpec fromBox = boxes.get(fromIndex);
        int count = 0;

        // Check all boxes that could be downstream (topologically after this box)
        for (int i = 0; i < boxes.size(); i++) {
            if (i == fromIndex) continue; // Skip self

            LevelDefinition.BoxSpec toBox = boxes.get(i);

            // Count compatible input ports
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
                if (i == j) continue; // Skip self-connections
                List<PortShape> ins = boxes.get(j).inShapes();
                // Check if any output shape matches any input shape
                if (outs.stream().anyMatch(ins::contains)) {
                    adj.get(i).add(j);
                }
            }
        }

        return adj;
    }

    // Smart balancing that maintains connectivity and avoids self-loops
    private static List<LevelDefinition.BoxSpec> smartBalancePortCounts(List<LevelDefinition.BoxSpec> orig) {
        List<LevelDefinition.BoxSpec> boxes = new ArrayList<>(orig);
        int maxIterations = 100;
        int iteration = 0;

        while (iteration < maxIterations) {
            // First, ensure no self-loops needed
            if (requiresSelfLoop(boxes)) {
                if (!fixSelfLoopRequirement(boxes)) {
                    break;
                }
            }

            // Then balance total counts
            int inTotal = boxes.stream().mapToInt(b -> b.inShapes().size()).sum();
            int outTotal = boxes.stream().mapToInt(b -> b.outShapes().size()).sum();
            int delta = inTotal - outTotal;

            if (delta == 0) {
                // Check if current configuration is valid
                if (isValidLevel(boxes)) {
                    return boxes;
                }
                // If not connected, try to fix
                if (!addStrategicPorts(boxes)) {
                    break;
                }
            } else if (delta > 0) {
                // Need more outputs
                if (!addOutputsSmartly(boxes, delta)) {
                    break;
                }
            } else {
                // Need more inputs
                if (!addInputsSmartly(boxes, -delta)) {
                    break;
                }
            }

            iteration++;
        }

        return boxes;
    }

    // NEW: Fix boxes that would require self-loops
    private static boolean fixSelfLoopRequirement(List<LevelDefinition.BoxSpec> boxes) {
        for (int i = 0; i < boxes.size(); i++) {
            LevelDefinition.BoxSpec box = boxes.get(i);
            int outputs = box.outShapes().size();
            int availableInputs = countAvailableDownstreamInputs(boxes, i);

            if (outputs > availableInputs) {
                // This box has too many outputs
                // Option 1: Add compatible inputs to downstream boxes
                if (addCompatibleInputsDownstream(boxes, i, outputs - availableInputs)) {
                    return true;
                }

                // Option 2: Remove some outputs from this box
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

    // Add compatible inputs to downstream boxes
    private static boolean addCompatibleInputsDownstream(List<LevelDefinition.BoxSpec> boxes,
                                                         int fromIndex, int needed) {
        LevelDefinition.BoxSpec fromBox = boxes.get(fromIndex);
        List<PortShape> outputShapes = fromBox.outShapes();

        // Find boxes that can accept more inputs
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            if (i != fromIndex && boxes.get(i).inShapes().size() < 3 && !boxes.get(i).isSource()) {
                candidates.add(i);
            }
        }

        if (candidates.isEmpty()) return false;

        // Add inputs to candidates
        int added = 0;
        for (int idx : candidates) {
            if (added >= needed) break;

            LevelDefinition.BoxSpec candidate = boxes.get(idx);
            // Choose a shape that fromBox outputs
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
        // When adding outputs, make sure we don't create self-loop requirements
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            LevelDefinition.BoxSpec b = boxes.get(i);
            if (b.outShapes().size() < 3 && !b.isSink()) {
                // Check if adding output would require self-loop
                int currentOutputs = b.outShapes().size();
                int availableInputs = countAvailableDownstreamInputs(boxes, i);
                if (currentOutputs < availableInputs) {
                    candidates.add(i);
                }
            }
        }

        if (candidates.isEmpty()) {
            // Add a new router box
            boxes.add(new LevelDefinition.BoxSpec(
                    50, 50, BOX_W, BOX_H,
                    List.of(), randomShapes(Math.min(3, needed)),
                    false, false));
            return true;
        }

        // Add outputs to candidates
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
            // Add a new collector box
            boxes.add(new LevelDefinition.BoxSpec(
                    50, 50, BOX_W, BOX_H,
                    randomShapes(Math.min(3, needed)), List.of(),
                    false, true));
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
        // Find disconnected components
        List<Set<Integer>> components = findComponents(boxes);
        if (components.size() <= 1 && !requiresSelfLoop(boxes)) {
            return false; // Already good
        }

        // Try to connect components
        if (components.size() > 1) {
            Set<Integer> comp1 = components.get(0);
            Set<Integer> comp2 = components.get(1);

            // Find best boxes to connect
            for (int i : comp1) {
                for (int j : comp2) {
                    LevelDefinition.BoxSpec bi = boxes.get(i);
                    LevelDefinition.BoxSpec bj = boxes.get(j);

                    // Try adding matching ports
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
                // Count shapes in other outputs
                for (PortShape s : other.outShapes()) {
                    shapeFreq.merge(s, 1, Integer::sum);
                }
            } else {
                // Count shapes in other inputs
                for (PortShape s : other.inShapes()) {
                    shapeFreq.merge(s, 1, Integer::sum);
                }
            }
        }

        // Choose most common shape for better connectivity
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
            int nx = 50 + col * 150;
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
}
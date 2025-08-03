package com.blueprinthell.level.generation;

import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.config.Config;
import com.blueprinthell.model.PortShape;

import java.util.*;


public class BoxLayoutManager {

    private static final int BOX_WIDTH = Config.SYSTEM_WIDTH;
    private static final int BOX_HEIGHT = Config.SYSTEM_HEIGHT;

    private static final int MARGIN_X = 50;
    private static final int MARGIN_Y = 100;
    private static final int SPACING_X = 200;
    private static final int SPACING_Y = 150;
    private static final int MAX_COLUMNS = 4;


    public List<LevelDefinition.BoxSpec> layoutBoxes(List<LevelDefinition.BoxSpec> boxes) {
        List<LevelDefinition.BoxSpec> result = new ArrayList<>();

        for (int i = 0; i < boxes.size(); i++) {
            LevelDefinition.BoxSpec original = boxes.get(i);
            Point position = calculatePosition(i, boxes.size());

            result.add(new LevelDefinition.BoxSpec(
                    original.id(),
                    position.x,
                    position.y,
                    original.width(),
                    original.height(),
                    original.inShapes(),
                    original.outShapes(),
                    original.isSource(),
                    original.isSink(),
                    original.kind()
            ));
        }

        return result;
    }


    public List<LevelDefinition.BoxSpec> layoutBoxesWithRoles(List<LevelDefinition.BoxSpec> boxes) {
        List<LevelDefinition.BoxSpec> sources = new ArrayList<>();
        List<LevelDefinition.BoxSpec> sinks = new ArrayList<>();
        List<LevelDefinition.BoxSpec> intermediates = new ArrayList<>();

        // Categorize boxes
        for (LevelDefinition.BoxSpec box : boxes) {
            if (box.isSource()) {
                sources.add(box);
            } else if (box.isSink()) {
                sinks.add(box);
            } else {
                intermediates.add(box);
            }
        }

        List<LevelDefinition.BoxSpec> result = new ArrayList<>();

        // Layout sources on the left
        int sourceY = MARGIN_Y;
        for (LevelDefinition.BoxSpec source : sources) {
            result.add(new LevelDefinition.BoxSpec(
                    source.id(),
                    MARGIN_X,
                    sourceY,
                    source.width(),
                    source.height(),
                    source.inShapes(),
                    source.outShapes(),
                    source.isSource(),
                    source.isSink(),
                    source.kind()
            ));
            sourceY += SPACING_Y;
        }

        // Layout intermediates in the middle
        int cols = Math.min(MAX_COLUMNS - 2, Math.max(1, intermediates.size() / 3));
        int startX = MARGIN_X + SPACING_X;

        for (int i = 0; i < intermediates.size(); i++) {
            LevelDefinition.BoxSpec box = intermediates.get(i);
            int col = i % cols;
            int row = i / cols;

            int x = startX + col * SPACING_X;
            int y = MARGIN_Y + row * SPACING_Y;

            result.add(new LevelDefinition.BoxSpec(
                    box.id(),
                    x,
                    y,
                    box.width(),
                    box.height(),
                    box.inShapes(),
                    box.outShapes(),
                    box.isSource(),
                    box.isSink(),
                    box.kind()
            ));
        }

        // Layout sinks on the right
        int sinkX = startX + cols * SPACING_X;
        int sinkY = MARGIN_Y;

        for (LevelDefinition.BoxSpec sink : sinks) {
            result.add(new LevelDefinition.BoxSpec(
                    sink.id(),
                    sinkX,
                    sinkY,
                    sink.width(),
                    sink.height(),
                    sink.inShapes(),
                    sink.outShapes(),
                    sink.isSource(),
                    sink.isSink(),
                    sink.kind()
            ));
            sinkY += SPACING_Y;
        }

        return result;
    }


    public List<LevelDefinition.BoxSpec> layoutBoxesHierarchical(List<LevelDefinition.BoxSpec> boxes,
                                                                 ConnectivityValidator validator) {
        // Build adjacency information
        List<List<Integer>> adj = buildAdjacencyList(boxes);

        // Find topological layers
        List<List<Integer>> layers = computeTopologicalLayers(boxes, adj);

        List<LevelDefinition.BoxSpec> result = new ArrayList<>();

        // Position boxes layer by layer
        int currentX = MARGIN_X;

        for (List<Integer> layer : layers) {
            int currentY = MARGIN_Y;
            int layerHeight = layer.size();
            int totalHeight = (layerHeight - 1) * SPACING_Y;
            int startY = MARGIN_Y + (getMaxHeight() - totalHeight) / 2;

            for (int idx : layer) {
                LevelDefinition.BoxSpec box = boxes.get(idx);

                result.add(new LevelDefinition.BoxSpec(
                        box.id(),
                        currentX,
                        startY + currentY,
                        box.width(),
                        box.height(),
                        box.inShapes(),
                        box.outShapes(),
                        box.isSource(),
                        box.isSink(),
                        box.kind()
                ));

                currentY += SPACING_Y;
            }

            currentX += SPACING_X;
        }

        return result;
    }


    private Point calculatePosition(int index, int totalBoxes) {
        int col = index % MAX_COLUMNS;
        int row = index / MAX_COLUMNS;

        int x = MARGIN_X + col * SPACING_X;
        int y = MARGIN_Y + row * SPACING_Y;

        return new Point(x, y);
    }


    private List<List<Integer>> computeTopologicalLayers(List<LevelDefinition.BoxSpec> boxes,
                                                         List<List<Integer>> adj) {
        int n = boxes.size();
        int[] inDegree = new int[n];

        // Calculate in-degrees
        for (List<Integer> neighbors : adj) {
            for (int v : neighbors) {
                inDegree[v]++;
            }
        }

        List<List<Integer>> layers = new ArrayList<>();
        boolean[] processed = new boolean[n];

        // Process nodes layer by layer
        while (true) {
            List<Integer> currentLayer = new ArrayList<>();

            // Find all nodes with in-degree 0
            for (int i = 0; i < n; i++) {
                if (!processed[i] && inDegree[i] == 0) {
                    currentLayer.add(i);
                    processed[i] = true;
                }
            }

            if (currentLayer.isEmpty()) {
                // Handle cycles by adding remaining nodes
                for (int i = 0; i < n; i++) {
                    if (!processed[i]) {
                        currentLayer.add(i);
                        processed[i] = true;
                        break;
                    }
                }

                if (currentLayer.isEmpty()) {
                    break;
                }
            }

            // Update in-degrees
            for (int u : currentLayer) {
                for (int v : adj.get(u)) {
                    inDegree[v]--;
                }
            }

            layers.add(currentLayer);
        }

        return layers;
    }

    private List<List<Integer>> buildAdjacencyList(List<LevelDefinition.BoxSpec> boxes) {
        int n = boxes.size();
        List<List<Integer>> adj = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<>());
        }

        for (int i = 0; i < n; i++) {
            List<PortShape> outputs = boxes.get(i).outShapes();
            for (int j = 0; j < n; j++) {
                if (i == j) continue;

                List<PortShape> inputs = boxes.get(j).inShapes();
                if (canConnect(outputs, inputs)) {
                    adj.get(i).add(j);
                }
            }
        }

        return adj;
    }

    private boolean canConnect(List<PortShape> outputs, List<PortShape> inputs) {
        for (PortShape out : outputs) {
            if (inputs.contains(out)) {
                return true;
            }
        }
        return false;
    }

    private int getMaxHeight() {
        // Estimate maximum height based on typical screen size
        return 600;
    }


    private static class Point {
        final int x;
        final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
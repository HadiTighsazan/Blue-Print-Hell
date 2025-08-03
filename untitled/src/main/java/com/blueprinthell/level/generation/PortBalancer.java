package com.blueprinthell.level.generation;

import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.model.PortShape;

import java.util.*;


public class PortBalancer {

    private final ConnectivityValidator connectivityValidator;
    private final Random random = new Random();

    public PortBalancer(ConnectivityValidator validator) {
        this.connectivityValidator = validator;
    }


    public List<LevelDefinition.BoxSpec> balancePorts(List<LevelDefinition.BoxSpec> original) {
        List<LevelDefinition.BoxSpec> boxes = new ArrayList<>(original);
        int maxIterations = 100;
        int iteration = 0;

        while (iteration < maxIterations) {
            // Fix self-loop requirements first
            if (connectivityValidator.requiresSelfLoop(boxes)) {
                if (!fixSelfLoopRequirement(boxes)) {
                    break;
                }
            }

            // Calculate port balance
            PortBalance balance = calculateBalance(boxes);

            if (balance.isBalanced()) {
                // Check connectivity
                if (connectivityValidator.isFullyConnected(boxes)) {
                    return boxes;
                }

                // Add strategic ports to improve connectivity
                if (!addStrategicPorts(boxes)) {
                    break;
                }
            } else if (balance.needsMoreOutputs()) {
                if (!addOutputsSmartly(boxes, balance.deficit)) {
                    break;
                }
            } else {
                if (!addInputsSmartly(boxes, -balance.deficit)) {
                    break;
                }
            }

            iteration++;
        }

        return boxes;
    }


    public PortBalance calculateBalance(List<LevelDefinition.BoxSpec> boxes) {
        int totalInputs = boxes.stream()
                .mapToInt(b -> b.inShapes().size())
                .sum();

        int totalOutputs = boxes.stream()
                .mapToInt(b -> b.outShapes().size())
                .sum();

        return new PortBalance(totalInputs, totalOutputs);
    }

    private boolean fixSelfLoopRequirement(List<LevelDefinition.BoxSpec> boxes) {
        for (int i = 0; i < boxes.size(); i++) {
            LevelDefinition.BoxSpec box = boxes.get(i);
            int outputs = box.outShapes().size();
            int availableInputs = countAvailableDownstreamInputs(boxes, i);

            if (outputs > availableInputs) {
                // Try to add compatible inputs downstream
                if (addCompatibleInputsDownstream(boxes, i, outputs - availableInputs)) {
                    return true;
                }

                // If that fails, reduce outputs (if possible)
                if (outputs > 1 && !box.isSource()) {
                    List<PortShape> newOuts = new ArrayList<>(box.outShapes());
                    newOuts.remove(newOuts.size() - 1);

                    boxes.set(i, new LevelDefinition.BoxSpec(
                            box.id(), box.x(), box.y(), box.width(), box.height(),
                            box.inShapes(), newOuts,
                            box.isSource(), box.isSink(),
                            box.kind()
                    ));
                    return true;
                }
            }
        }

        return false;
    }

    private boolean addCompatibleInputsDownstream(List<LevelDefinition.BoxSpec> boxes,
                                                  int fromIndex, int needed) {
        LevelDefinition.BoxSpec fromBox = boxes.get(fromIndex);
        List<PortShape> outputShapes = fromBox.outShapes();

        // Find candidate boxes that can accept more inputs
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            if (i != fromIndex &&
                    boxes.get(i).inShapes().size() < 3 &&
                    !boxes.get(i).isSource()) {
                candidates.add(i);
            }
        }

        if (candidates.isEmpty()) return false;

        // Add inputs to candidates
        int added = 0;
        for (int idx : candidates) {
            if (added >= needed) break;

            LevelDefinition.BoxSpec candidate = boxes.get(idx);
            PortShape shapeToAdd = outputShapes.get(random.nextInt(outputShapes.size()));

            List<PortShape> newIns = new ArrayList<>(candidate.inShapes());
            newIns.add(shapeToAdd);

            boxes.set(idx, new LevelDefinition.BoxSpec(
                    candidate.id(), candidate.x(), candidate.y(),
                    candidate.width(), candidate.height(),
                    newIns, candidate.outShapes(),
                    candidate.isSource(), candidate.isSink(),
                    candidate.kind()
            ));

            added++;
        }

        return added > 0;
    }

    private boolean addOutputsSmartly(List<LevelDefinition.BoxSpec> boxes, int needed) {
        // Find boxes that can add outputs without creating self-loops
        List<Integer> candidates = new ArrayList<>();

        for (int i = 0; i < boxes.size(); i++) {
            LevelDefinition.BoxSpec box = boxes.get(i);
            if (box.outShapes().size() < 3 && !box.isSink()) {
                int currentOutputs = box.outShapes().size();
                int availableInputs = countAvailableDownstreamInputs(boxes, i);

                if (currentOutputs < availableInputs) {
                    candidates.add(i);
                }
            }
        }

        if (candidates.isEmpty()) {
            // Add a new router box
            addRouterBox(boxes, needed);
            return true;
        }

        // Add outputs to candidates
        int added = 0;
        for (int idx : candidates) {
            if (added >= needed) break;

            LevelDefinition.BoxSpec box = boxes.get(idx);
            List<PortShape> newOut = new ArrayList<>(box.outShapes());
            newOut.add(chooseShapeForConnectivity(boxes, idx, false));

            boxes.set(idx, new LevelDefinition.BoxSpec(
                    box.id(), box.x(), box.y(), box.width(), box.height(),
                    box.inShapes(), newOut,
                    box.isSource(), box.isSink(),
                    box.kind()
            ));

            added++;
        }

        return added > 0;
    }

    private boolean addInputsSmartly(List<LevelDefinition.BoxSpec> boxes, int needed) {
        // Find boxes that can add inputs
        List<Integer> candidates = new ArrayList<>();

        for (int i = 0; i < boxes.size(); i++) {
            LevelDefinition.BoxSpec box = boxes.get(i);
            if (box.inShapes().size() < 3 && !box.isSource()) {
                candidates.add(i);
            }
        }

        if (candidates.isEmpty()) {
            // Add a new collector box
            addCollectorBox(boxes, needed);
            return true;
        }

        // Add inputs to candidates
        int added = 0;
        for (int idx : candidates) {
            if (added >= needed) break;

            LevelDefinition.BoxSpec box = boxes.get(idx);
            List<PortShape> newIn = new ArrayList<>(box.inShapes());
            newIn.add(chooseShapeForConnectivity(boxes, idx, true));

            boxes.set(idx, new LevelDefinition.BoxSpec(
                    box.id(), box.x(), box.y(), box.width(), box.height(),
                    newIn, box.outShapes(),
                    box.isSource(), box.isSink(),
                    box.kind()
            ));

            added++;
        }

        return added > 0;
    }

    private boolean addStrategicPorts(List<LevelDefinition.BoxSpec> boxes) {
        // Get connection suggestions from connectivity validator
        List<ConnectivityValidator.ConnectionSuggestion> suggestions =
                connectivityValidator.suggestConnections(boxes);

        if (suggestions.isEmpty()) return false;

        // Apply first suggestion
        ConnectivityValidator.ConnectionSuggestion suggestion = suggestions.get(0);

        if (suggestion.addPorts) {
            LevelDefinition.BoxSpec fromBox = boxes.get(suggestion.fromBoxIndex);
            LevelDefinition.BoxSpec toBox = boxes.get(suggestion.toBoxIndex);

            // Add output to fromBox
            List<PortShape> newOutFrom = new ArrayList<>(fromBox.outShapes());
            newOutFrom.add(suggestion.shape);

            boxes.set(suggestion.fromBoxIndex, new LevelDefinition.BoxSpec(
                    fromBox.id(), fromBox.x(), fromBox.y(), fromBox.width(), fromBox.height(),
                    fromBox.inShapes(), newOutFrom,
                    fromBox.isSource(), fromBox.isSink(),
                    fromBox.kind()
            ));

            // Add input to toBox
            List<PortShape> newInTo = new ArrayList<>(toBox.inShapes());
            newInTo.add(suggestion.shape);

            boxes.set(suggestion.toBoxIndex, new LevelDefinition.BoxSpec(
                    toBox.id(), toBox.x(), toBox.y(), toBox.width(), toBox.height(),
                    newInTo, toBox.outShapes(),
                    toBox.isSource(), toBox.isSink(),
                    toBox.kind()
            ));

            return true;
        }

        return false;
    }

    private PortShape chooseShapeForConnectivity(List<LevelDefinition.BoxSpec> boxes,
                                                 int boxIndex, boolean forInput) {
        Map<PortShape, Integer> shapeFrequency = new HashMap<>();

        // Count shape frequencies in potential connections
        for (int i = 0; i < boxes.size(); i++) {
            if (i == boxIndex) continue;

            LevelDefinition.BoxSpec other = boxes.get(i);

            if (forInput) {
                // Looking for shapes in outputs of other boxes
                for (PortShape s : other.outShapes()) {
                    shapeFrequency.merge(s, 1, Integer::sum);
                }
            } else {
                // Looking for shapes in inputs of other boxes
                for (PortShape s : other.inShapes()) {
                    shapeFrequency.merge(s, 1, Integer::sum);
                }
            }
        }

        // Return most frequent shape
        return shapeFrequency.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(randomShape());
    }

    private int countAvailableDownstreamInputs(List<LevelDefinition.BoxSpec> boxes, int fromIndex) {
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

    private void addRouterBox(List<LevelDefinition.BoxSpec> boxes, int outputCount) {
        boxes.add(new LevelDefinition.BoxSpec(
                "router-" + UUID.randomUUID().toString().substring(0, 8),
                50, 50, 96, 96,
                List.of(),
                randomShapes(Math.min(3, outputCount)),
                false, false,
                com.blueprinthell.controller.systems.SystemKind.NORMAL
        ));
    }

    private void addCollectorBox(List<LevelDefinition.BoxSpec> boxes, int inputCount) {
        boxes.add(new LevelDefinition.BoxSpec(
                "collector-" + UUID.randomUUID().toString().substring(0, 8),
                50, 50, 96, 96,
                randomShapes(Math.min(3, inputCount)),
                List.of(),
                false, true,
                com.blueprinthell.controller.systems.SystemKind.NORMAL
        ));
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


    public static class PortBalance {
        public final int totalInputs;
        public final int totalOutputs;
        public final int deficit; // positive = need more outputs, negative = need more inputs

        public PortBalance(int inputs, int outputs) {
            this.totalInputs = inputs;
            this.totalOutputs = outputs;
            this.deficit = inputs - outputs;
        }

        public boolean isBalanced() {
            return deficit == 0;
        }

        public boolean needsMoreOutputs() {
            return deficit > 0;
        }

        public boolean needsMoreInputs() {
            return deficit < 0;
        }
    }
}
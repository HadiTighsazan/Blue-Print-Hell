package com.blueprinthell.level.generation;

import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.model.PortShape;

import java.util.*;


public class ConnectivityValidator {


    public boolean isFullyConnected(List<LevelDefinition.BoxSpec> boxes) {
        int n = boxes.size();
        if (n == 0) return true;

        List<List<Integer>> adj = buildAdjacencyList(boxes);
        return countReachableNodes(adj, 0) == n;
    }


    public List<Set<Integer>> findComponents(List<LevelDefinition.BoxSpec> boxes) {
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


    public boolean requiresSelfLoop(List<LevelDefinition.BoxSpec> boxes) {
        for (int i = 0; i < boxes.size(); i++) {
            LevelDefinition.BoxSpec box = boxes.get(i);
            int outputs = box.outShapes().size();

            if (outputs == 0) continue;

            int availableInputs = countAvailableDownstreamInputs(boxes, i);
            if (outputs > availableInputs) {
                return true;
            }
        }
        return false;
    }

    public List<ConnectionSuggestion> suggestConnections(List<LevelDefinition.BoxSpec> boxes) {
        List<ConnectionSuggestion> suggestions = new ArrayList<>();
        List<Set<Integer>> components = findComponents(boxes);

        if (components.size() <= 1) return suggestions;

        // Suggest connections between disconnected components
        for (int i = 0; i < components.size() - 1; i++) {
            Set<Integer> comp1 = components.get(i);
            Set<Integer> comp2 = components.get(i + 1);

            // Find best connection point between components
            ConnectionSuggestion suggestion = findBestConnection(boxes, comp1, comp2);
            if (suggestion != null) {
                suggestions.add(suggestion);
            }
        }

        return suggestions;
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

    private int countReachableNodes(List<List<Integer>> adj, int start) {
        boolean[] visited = new boolean[adj.size()];
        Queue<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        visited[start] = true;
        int count = 1;

        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (int v : adj.get(u)) {
                if (!visited[v]) {
                    visited[v] = true;
                    count++;
                    queue.add(v);
                }
            }
        }

        return count;
    }

    private void dfs(int u, List<List<Integer>> adj, boolean[] visited, Set<Integer> component) {
        visited[u] = true;
        component.add(u);

        for (int v : adj.get(u)) {
            if (!visited[v]) {
                dfs(v, adj, visited, component);
            }
        }
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

    private ConnectionSuggestion findBestConnection(List<LevelDefinition.BoxSpec> boxes,
                                                    Set<Integer> comp1, Set<Integer> comp2) {
        for (int i : comp1) {
            for (int j : comp2) {
                LevelDefinition.BoxSpec box1 = boxes.get(i);
                LevelDefinition.BoxSpec box2 = boxes.get(j);

                // Check if we can add output to box1 and input to box2
                if (box1.outShapes().size() < 3 && box2.inShapes().size() < 3 && !box2.isSource()) {
                    // Find a compatible shape
                    PortShape shape = findCompatibleShape(box1, box2);
                    return new ConnectionSuggestion(i, j, shape, true);
                }

                // Check reverse direction
                if (box2.outShapes().size() < 3 && box1.inShapes().size() < 3 && !box1.isSource()) {
                    PortShape shape = findCompatibleShape(box2, box1);
                    return new ConnectionSuggestion(j, i, shape, true);
                }
            }
        }

        return null;
    }

    private PortShape findCompatibleShape(LevelDefinition.BoxSpec from, LevelDefinition.BoxSpec to) {
        // Prefer shapes that already exist in the system
        Map<PortShape, Integer> frequency = new HashMap<>();

        for (PortShape shape : from.outShapes()) {
            frequency.merge(shape, 1, Integer::sum);
        }

        for (PortShape shape : to.inShapes()) {
            frequency.merge(shape, 1, Integer::sum);
        }

        // Return most frequent shape, or default to SQUARE
        return frequency.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(PortShape.SQUARE);
    }


    public static class ConnectionSuggestion {
        public final int fromBoxIndex;
        public final int toBoxIndex;
        public final PortShape shape;
        public final boolean addPorts; // true if ports need to be added

        public ConnectionSuggestion(int from, int to, PortShape shape, boolean addPorts) {
            this.fromBoxIndex = from;
            this.toBoxIndex = to;
            this.shape = shape;
            this.addPorts = addPorts;
        }
    }
}
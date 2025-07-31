package com.blueprinthell.level;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.PortShape;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public final class LevelGenerator {

    private static final int BOX_W = Config.SYSTEM_WIDTH;
    private static final int BOX_H = Config.SYSTEM_HEIGHT;
    private static final Random RND = new Random();

    private LevelGenerator() {}

    public static LevelDefinition firstLevel() {
        List<LevelDefinition.BoxSpec> boxes;
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
        } while (!isConnected(boxes));

        boxes = layoutBoxes(boxes);

        double budget = 500;
        return new LevelDefinition(boxes, budget);
    }

    public static LevelDefinition nextLevel(LevelDefinition prev) {
        List<LevelDefinition.BoxSpec> boxes;
        do {
            boxes = new ArrayList<>();
            LevelDefinition.BoxSpec oldSink = prev.boxes().stream()
                    .filter(LevelDefinition.BoxSpec::isSink)
                    .findFirst().orElseThrow();
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
            int ix = oldSink.x() + 150;
            int iy = oldSink.y() + (RND.nextBoolean() ? 120 : -120);
            List<PortShape> midIns  = randomShapes(1 + RND.nextInt(3));
            List<PortShape> midOuts = randomShapes(1 + RND.nextInt(3));
            boxes.add(new LevelDefinition.BoxSpec(
                    ix, iy, BOX_W, BOX_H,
                    midIns, midOuts,
                    false, false));
            int sx = ix + 300;
            int sy = iy;
            List<PortShape> sinkIns = randomShapes(1 + RND.nextInt(3));
            boxes.add(new LevelDefinition.BoxSpec(
                    sx, sy, BOX_W, BOX_H,
                    sinkIns, List.of(),
                    false, true));
        }
        while (!isConnected(boxes));

        boxes = balancePortCounts(boxes);
        boxes = layoutBoxes(boxes);

        double budget = prev.totalWireLength() * 1.5;
        return new LevelDefinition(boxes, budget);
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


    private static boolean isConnected(List<LevelDefinition.BoxSpec> boxes) {
        int n = boxes.size();
        List<List<Integer>> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
        for (int i = 0; i < n; i++) {
            List<PortShape> outs = boxes.get(i).outShapes();
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                int finalJ = j;
                if (outs.stream().anyMatch(boxes.get(j).inShapes()::contains)) {
                    adj.get(i).add(j);
                }
            }
        }

        int src = -1, sink = -1;
        for (int i = 0; i < n; i++) {
            if (boxes.get(i).isSource()) src = i;
            if (boxes.get(i).isSink())   sink = i;
        }
        if (src < 0 || sink < 0) return false;
        boolean[] vis = new boolean[n];
        java.util.Queue<Integer> q = new java.util.ArrayDeque<>();
        q.add(src); vis[src] = true;
        while (!q.isEmpty()) {
            int u = q.poll();
            if (u == sink) return true;
            for (int v : adj.get(u)) {
                if (!vis[v]) { vis[v] = true; q.add(v); }
            }
        }
        return false;
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


    private static List<LevelDefinition.BoxSpec> balancePortCounts(List<LevelDefinition.BoxSpec> orig) {
        List<LevelDefinition.BoxSpec> boxes = new ArrayList<>(orig);
        while (true) {
            int inTotal = boxes.stream().mapToInt(b -> b.inShapes().size()).sum();
            int outTotal = boxes.stream().mapToInt(b -> b.outShapes().size()).sum();
            int delta = inTotal - outTotal;
            if (delta == 0) return boxes;
            if (delta > 0) {
                boolean added = false;
                for (int i = 0; i < boxes.size() && delta > 0; i++) {
                    LevelDefinition.BoxSpec b = boxes.get(i);
                    if (b.outShapes().size() < 3) {
                        List<PortShape> newOut = new ArrayList<>(b.outShapes());
                        newOut.add(randomShape());
                        boxes.set(i, new LevelDefinition.BoxSpec(
                                b.id(), b.x(), b.y(), b.width(), b.height(),
                                b.inShapes(), newOut,
                                b.isSource(), b.isSink(),
                                b.kind()));
                        delta--; added = true;
                    }
                }
                if (!added) {
                    boxes.add(new LevelDefinition.BoxSpec(
                            50, 50, BOX_W, BOX_H,
                            List.of(), randomShapes(Math.min(3, delta)),
                            false, false));
                }
            } else {
                delta = -delta;
                boolean added = false;
                for (int i = 0; i < boxes.size() && delta > 0; i++) {
                    LevelDefinition.BoxSpec b = boxes.get(i);
                    if (b.inShapes().size() < 3 && !b.isSource()) {
                        List<PortShape> newIn = new ArrayList<>(b.inShapes());
                        newIn.add(randomShape());
                        boxes.set(i, new LevelDefinition.BoxSpec(
                                b.id(), b.x(), b.y(), b.width(), b.height(),
                                newIn, b.outShapes(),
                                b.isSource(), b.isSink(),
                                b.kind()));
                        delta--; added = true;
                    }
                }
                if (!added) {                    boxes.add(new LevelDefinition.BoxSpec(
                        50, 50, BOX_W, BOX_H,
                        randomShapes(Math.min(3, delta)), List.of(),
                        false, true));
                }
            }
        }
    }
}

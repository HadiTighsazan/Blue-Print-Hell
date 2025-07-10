package com.blueprinthell.controller;

import java.util.*;

/**
 * A spatial-hash grid for efficient broad-phase collision detection.
 * @param <T> type of item to store (e.g., PacketModel)
 */
public class SpatialHashGrid<T> {
    private final int cellSize;
    private final Map<Long, List<T>> grid = new HashMap<>();

    /**
     * Constructs a grid with specified cell size.
     * @param cellSize width/height of each cell
     */
    public SpatialHashGrid(int cellSize) {
        if (cellSize <= 0) throw new IllegalArgumentException("cellSize > 0");
        this.cellSize = cellSize;
    }

    private long keyFor(int hx, int hy) {
        return (((long) hx) << 32) ^ (hy & 0xffffffffL);
    }

    /**
     * Inserts an item at world coordinates (x,y).
     */
    public void insert(int x, int y, T item) {
        int hx = x >= 0 ? x / cellSize : ((x + 1) / cellSize - 1);
        int hy = y >= 0 ? y / cellSize : ((y + 1) / cellSize - 1);
        long key = keyFor(hx, hy);
        grid.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
    }

    /**
     * Retrieves all items in the neighboring 3x3 cells around (x,y).
     */
    public List<T> retrieve(int x, int y) {
        List<T> result = new ArrayList<>();
        int hx = x >= 0 ? x / cellSize : ((x + 1) / cellSize - 1);
        int hy = y >= 0 ? y / cellSize : ((y + 1) / cellSize - 1);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                long key = keyFor(hx + dx, hy + dy);
                List<T> cell = grid.get(key);
                if (cell != null) result.addAll(cell);
            }
        }
        return result;
    }

    /**
     * Clears all entries from the grid.
     */
    public void clear() {
        grid.clear();
    }
}

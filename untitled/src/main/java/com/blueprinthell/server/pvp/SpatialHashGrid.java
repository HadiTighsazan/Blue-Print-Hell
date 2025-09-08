package com.blueprinthell.server.pvp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ساختار داده گرید فضایی برای بهینه‌سازی تشخیص برخورد در سرور.
 * این کلاس یک کپی از نسخه کلاینت است.
 * @param <T> نوع آیتمی که در گرید ذخیره می‌شود (معمولاً SimPacket).
 */
public class SpatialHashGrid<T> {
    private final int cellSize;
    private final Map<Long, List<T>> grid = new HashMap<>();

    public SpatialHashGrid(int cellSize) {
        if (cellSize <= 0) throw new IllegalArgumentException("Cell size must be greater than 0");
        this.cellSize = cellSize;
    }

    private long getKey(int hx, int hy) {
        return ((long) hx << 32) | (hy & 0xffffffffL);
    }

    public void insert(int x, int y, T item) {
        int hx = x / cellSize;
        int hy = y / cellSize;
        grid.computeIfAbsent(getKey(hx, hy), k -> new ArrayList<>()).add(item);
    }

    public List<T> retrieve(int x, int y) {
        List<T> result = new ArrayList<>();
        int hx = x / cellSize;
        int hy = y / cellSize;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                List<T> cellItems = grid.get(getKey(hx + dx, hy + dy));
                if (cellItems != null) {
                    result.addAll(cellItems);
                }
            }
        }
        return result;
    }

    public void clear() {
        grid.clear();
    }
}
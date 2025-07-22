package com.blueprinthell.controller.systems;

import com.blueprinthell.model.SystemBoxModel;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class BehaviorRegistry {
    private final Map<SystemBoxModel, SystemBehavior> map = new HashMap<>();

    public void register(SystemBoxModel box, SystemBehavior behavior) {
        map.put(Objects.requireNonNull(box), Objects.requireNonNull(behavior));
    }

    public SystemBehavior get(SystemBoxModel box) {
        return map.get(box);
    }

    public boolean has(SystemBoxModel box) {
        return map.containsKey(box);
    }

    public Map<SystemBoxModel, SystemBehavior> view() {
        return Map.copyOf(map);
    }
}
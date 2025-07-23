package com.blueprinthell.controller.systems;

import com.blueprinthell.model.SystemBoxModel;

import java.util.*;

public class BehaviorRegistry {
    private final Map<SystemBoxModel, List<SystemBehavior>> map = new HashMap<>();

    public void register(SystemBoxModel box, SystemBehavior behavior) {
        map.computeIfAbsent(box, b -> new ArrayList<>()).add(behavior);
    }

    public List<SystemBehavior> get(SystemBoxModel box) {
        return map.getOrDefault(box, List.of());
    }

    public boolean has(SystemBoxModel box) {
        return map.containsKey(box);
    }

    public Map<SystemBoxModel, List<SystemBehavior>> view() {
        return Collections.unmodifiableMap(map);
    }
}

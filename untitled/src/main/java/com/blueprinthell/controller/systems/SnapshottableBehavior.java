package com.blueprinthell.controller.systems;

import java.util.Map;

public interface SnapshottableBehavior {
    Map<String, Object> captureState();
    void restoreState(Map<String, Object> state);
}
package com.blueprinthell.controller.systems;

import com.blueprinthell.model.SystemBoxModel;

import java.util.Objects;

public final class NormalBehavior implements SystemBehavior {

    private final SystemBoxModel box;

    public NormalBehavior(SystemBoxModel box) {
        this.box = Objects.requireNonNull(box);
    }

    @Override
    public void update(double dt) {
    }
}
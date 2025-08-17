package com.blueprinthell.controller.gameplay;

import com.blueprinthell.controller.ui.editor.SystemBoxDragController;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.view.SystemBoxView;
import com.blueprinthell.view.screens.GameScreenView;

import java.awt.*;
import java.util.List;
import java.util.function.Predicate;

public final class SisyphusScrollController {

    private static final int DEFAULT_RADIUS_PX = 120;

    private final GameScreenView gameView;
    private final List<WireModel> wires;

    public SisyphusScrollController(GameScreenView gameView, List<WireModel> wires) {
        this.gameView = gameView;
        this.wires = wires;
    }

    public void activateOnce() {
        activateOnce(DEFAULT_RADIUS_PX);
    }

    public void activateOnce(int radiusPx) {
        // فقط باکس‌های غیرمرجع (هم ورودی هم خروجی)
        Predicate<SystemBoxModel> nonReference = m ->
                m != null && m.getInPorts() != null && m.getOutPorts() != null
                        && !m.getInPorts().isEmpty() && !m.getOutPorts().isEmpty();

        List<SystemBoxView> obstacles = gameView.getSystemBoxViews();

        // فعال‌سازی حالت «یک‌بار درگ»
        SystemBoxDragController.enableSisyphusOneShot(
                radiusPx,
                nonReference,
                wires,
                obstacles,
                () -> {
                    // پس از اولین درگ موفق یا رها شدن ماوس، حالت یک‌بار مصرف تمام می‌شود
                    // (نیازی به کاری اینجا نیست؛ فقط برای نمایش یا لاگ قابل استفاده است)
                    Toolkit.getDefaultToolkit().beep();
                }
        );
    }
}

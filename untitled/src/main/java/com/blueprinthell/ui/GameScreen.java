package com.blueprinthell.ui;

import com.blueprinthell.engine.NetworkController;
import com.blueprinthell.model.Port;
import com.blueprinthell.model.SystemBox;
import com.blueprinthell.model.Wire;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * نمای اصلی بازی: سیستم‌ها، سیم‌ها و پیش‌نمایشِ سیم.
 * loadLevel هر بار محیط را برای یک مرحله تنظیم می‌کند.
 */
public class GameScreen extends JLayeredPane {
    private static final double DEFAULT_MAX_WIRE = 1000.0;

    private List<SystemBox> systems;
    private List<Wire>      wires;
    private NetworkController networkController;
    private InputManager      inputManager;
    private WirePreviewLayer  previewLayer;

    public GameScreen() {
        setLayout(null);
    }

    /**
     * بارگذاری محیط برای مرحله‌ی مشخص‌شده (1 یا 2).
     */
    public void loadLevel(int levelIndex) {
        removeAll();

        systems = new ArrayList<>();
        wires   = new ArrayList<>();

        int W = getWidth(), H = getHeight();
        // تعریف مکان‌های پیش‌فرض سیستم‌ها
        if (levelIndex == 1) {
            // دو سیستم چپ و راست
            SystemBox left  = new SystemBox(  50, H/2-40, 100, 80, 0, 1);
            SystemBox right = new SystemBox(W-150, H/2-40, 100, 80, 1, 0);
            systems.add(left);
            systems.add(right);
            // سیم بین خروجی چپ و ورودی راست
            wires.add(new Wire(
                    left.getOutPorts().get(0),
                    right.getInPorts().get(0)
            ));
        } else {
            // سه سیستم: چپ -> وسط -> راست
            SystemBox left   = new SystemBox(  50, H/2-40, 100, 80, 0, 1);
            SystemBox middle = new SystemBox(W/2-50, H/2-40, 100, 80, 1, 1);
            SystemBox right  = new SystemBox(W-150, H/2-40, 100, 80, 1, 0);
            systems.add(left);
            systems.add(middle);
            systems.add(right);
            // سیم‌ها
            wires.add(new Wire(
                    left.getOutPorts().get(0),
                    middle.getInPorts().get(0)
            ));
            wires.add(new Wire(
                    middle.getOutPorts().get(0),
                    right.getInPorts().get(0)
            ));
        }

        // اضافه‌کردنِ سیستم‌ها و سیم‌ها به لایه‌ی DEFAULT
        for (SystemBox sys : systems) {
            add(sys, JLayeredPane.DEFAULT_LAYER);
        }
        for (Wire w : wires) {
            add(w, JLayeredPane.DEFAULT_LAYER);
        }

        // مقداردهی کنترلر شبکه
        networkController = new NetworkController(
                wires, systems, DEFAULT_MAX_WIRE
        );

        // راه‌اندازی InputManager و PreviewLayer
        inputManager = new InputManager(networkController);
        previewLayer = new WirePreviewLayer(inputManager);
        previewLayer.setOpaque(false);
        previewLayer.setBounds(0, 0, W, H);
        add(previewLayer, JLayeredPane.DRAG_LAYER);

        // ثبت کانتینرها و پورت‌ها
        inputManager.registerHitContainer(this);
        inputManager.registerEventContainer(previewLayer);
        for (SystemBox sys : systems) {
            for (Port p : sys.getInPorts())  inputManager.registerPort(p);
            for (Port p : sys.getOutPorts()) inputManager.registerPort(p);
        }

        revalidate();
        repaint();
    }
}
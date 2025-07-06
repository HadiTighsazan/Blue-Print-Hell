package com.blueprinthell.ui;

import com.blueprinthell.model.Packet;
import com.blueprinthell.model.SystemBox;
import com.blueprinthell.model.Wire;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.*;
import java.util.List;

/**
 * NetworkView صرفاً مسئول رندر گرافیکی SystemBox‏ها، Wire‏ها و Packet‏هاست.
 * هیچ منطق بازی یا HUD در این کلاس قرار نمی‌گیرد.
 */
public class NetworkView extends JLayeredPane {

    private List<SystemBox> systems = new ArrayList<>();
    private List<Wire>      wires   = new ArrayList<>();
    private final WirePreviewLayer  previewLayer;

    public NetworkView(InputManager inputManager) {
        setLayout(null);
        previewLayer = new WirePreviewLayer(inputManager);

        previewLayer.setBounds(0, 0, getWidth(), getHeight());
        add(previewLayer, PALETTE_LAYER);

        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                previewLayer.setBounds(0, 0, getWidth(), getHeight());
            }
        });
    }

    /**
     * برای دسترسی بیرونی (GameScreen) به لایهٔ پیش‌نمایش
     */
    public WirePreviewLayer getPreviewLayer() {
        return previewLayer;
    }

    public void setSystemsAndWires(List<SystemBox> systems, List<Wire> wires) {
        removeAll();
        this.systems = systems;
        this.wires   = wires;
        systems.forEach(s -> add(s, DEFAULT_LAYER));
        wires.forEach(w -> add(w, DEFAULT_LAYER));
        add(previewLayer, PALETTE_LAYER);
        previewLayer.setBounds(0, 0, getWidth(), getHeight());   // ✨
        revalidate(); repaint();
    }

    /**
     * مدل پکت‌ها را از NetworkController می‌گیرد و موقعیت/حضورشان را روی صحنه همگام می‌کند.
     */
    public void syncToModel(Collection<Packet> modelPackets) {
        // ۱) حذف پکت‌هایی که دیگر در مدل نیستند
        Set<Packet> modelSet = new HashSet<>(modelPackets);
        for (Component comp : getComponents()) {
            if (comp instanceof Packet p && !modelSet.contains(p)) {
                remove(p);
            }
        }

        // ۲) افزودن پکت‌های جدید و به‌روزرسانی موقعیت پکت‌های موجود
        for (Packet p : modelSet) {
            if (p.getParent() == null) {
                add(p, DEFAULT_LAYER);
            }
            p.updatePosition();
        }

        revalidate(); repaint();
    }
}

package com.blueprinthell.controller;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.view.PacketView;

import javax.swing.*;
import java.util.*;

/**
 * Controller to render PacketModels as PacketViews on a container.
 * Provides both incremental updates each tick and full refresh after snapshot restore.
 */
public class PacketRenderController implements Updatable {
    private final JComponent container;
    private final List<WireModel> wires;
    private final Map<PacketModel, PacketView> viewMap = new HashMap<>();

    /**
     * Constructs a renderer for packet models.
     * @param container panel (or frame's content pane) to add/remove PacketViews
     * @param wires the wires whose packets should be rendered
     */
    public PacketRenderController(JComponent container, List<WireModel> wires) {
        this.container = container;
        this.wires = wires;
    }

    /**
     * Performs an incremental update: adds new views, removes delivered, and refreshes existing positions.
     * Called each simulation tick.
     */
    @Override
    public void update(double dt) {
        // Add new packet views
        for (WireModel wire : wires) {
            for (PacketModel pm : wire.getPackets()) {
                if (!viewMap.containsKey(pm)) {
                    PacketView pv = new PacketView(pm);
                    viewMap.put(pm, pv);
                    container.add(pv);
                }
            }
        }

        // Remove views for delivered packets
        Iterator<Map.Entry<PacketModel, PacketView>> it = viewMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<PacketModel, PacketView> entry = it.next();
            PacketModel pm = entry.getKey();
            boolean stillOnWire = false;
            for (WireModel wire : wires) {
                if (wire.getPackets().contains(pm)) {
                    stillOnWire = true;
                    break;
                }
            }
            if (!stillOnWire) {
                container.remove(entry.getValue());
                it.remove();
            }
        }

        // Refresh positions of all packet views
        for (PacketView pv : viewMap.values()) {
            pv.refreshView();
        }
        container.revalidate();
        container.repaint();
    }

    /**
     * Clears and rebuilds all PacketViews to reflect the current packet-model state.
     * Should be called after restoring a snapshot.
     */
    public void refreshAll() {
        // Remove all existing packet views
        for (PacketView pv : viewMap.values()) {
            container.remove(pv);
        }
        viewMap.clear();

        // Add views for all current packets
        for (WireModel wire : wires) {
            for (PacketModel pm : wire.getPackets()) {
                PacketView pv = new PacketView(pm);
                viewMap.put(pm, pv);
                container.add(pv);
            }
        }

        container.revalidate();
        container.repaint();
    }
}

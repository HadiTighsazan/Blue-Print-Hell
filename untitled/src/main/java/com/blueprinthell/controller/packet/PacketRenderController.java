package com.blueprinthell.controller.packet;

import com.blueprinthell.controller.simulation.SimulationController;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.view.PacketView;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

public class PacketRenderController implements Updatable {
    private final JComponent container;
    private final List<WireModel> wires;
    private final SimulationController simulation;

    // A pool of PacketView objects to reuse, avoiding constant remove/add operations.
    private final List<PacketView> viewPool = new ArrayList<>();

    public PacketRenderController(JComponent container, List<WireModel> wires, SimulationController simulation) {
        this.container = container;
        this.wires = wires;
        this.simulation = simulation;
    }

    @Override
    public void update(double dt) {
        // --- START OF NEW ROBUST LOGIC ---

        // 1. Collect all current PacketModel instances from the wires.
        List<PacketModel> currentPackets = new ArrayList<>();
        for (WireModel wire : wires) {
            currentPackets.addAll(wire.getPackets());
        }

        // 2. Synchronize the number of views in the pool with the number of current packets.
        // Add new views if there are more packets than views.
        while (viewPool.size() < currentPackets.size()) {
            // Create a placeholder view. It will be updated with a real model shortly.
            PacketView pv = new PacketView(new PacketModel(PacketType.CIRCLE, 0));
            viewPool.add(pv);
            container.add(pv);
        }
        // Remove excess views if there are fewer packets than views.
        while (viewPool.size() > currentPackets.size()) {
            PacketView pv = viewPool.remove(viewPool.size() - 1);
            container.remove(pv);
        }

        // 3. Update the model for each view in the pool to match the current packets.
        for (int i = 0; i < currentPackets.size(); i++) {
            PacketView viewToUpdate = viewPool.get(i);
            PacketModel modelToShow = currentPackets.get(i);

            // Set the correct model and refresh the view's position and appearance.
            viewToUpdate.setModel(modelToShow);
            viewToUpdate.refreshView();
        }

        // --- END OF NEW ROBUST LOGIC ---

        container.revalidate();
        container.repaint();
    }

    public void refreshAll() {
        update(0);
    }
}
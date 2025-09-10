package com.blueprinthell.controller.packet;

import com.blueprinthell.config.Config;
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
        // 1. تمام PacketModel های فعلی را از سیم‌ها جمع‌آوری کن
        List<PacketModel> currentPackets = new ArrayList<>();
        for (WireModel wire : wires) {
            currentPackets.addAll(wire.getPackets());
        }

        // 2. تعداد ویوها را با تعداد پکت‌ها همگام کن
        // اگر پکت بیشتر از ویو داریم، ویوی جدید اضافه کن
        while (viewPool.size() < currentPackets.size()) {
            // از یک سرعت پیش‌فرض مثبت استفاده کنید
            PacketView pv = new PacketView(new PacketModel(PacketType.CIRCLE, Config.DEFAULT_PACKET_SPEED));
            viewPool.add(pv);
            container.add(pv);
        }
        // اگر ویو بیشتر از پکت داریم, آنها را مخفی کن
        for (int i = currentPackets.size(); i < viewPool.size(); i++) {
            viewPool.get(i).setVisible(false);
        }


        // 3. مدل هر ویو را با پکت متناظر به‌روز کن
        for (int i = 0; i < currentPackets.size(); i++) {
            PacketView viewToUpdate = viewPool.get(i);
            PacketModel modelToShow = currentPackets.get(i);

            viewToUpdate.setModel(modelToShow);
            viewToUpdate.setVisible(true);
            viewToUpdate.refreshView(); // موقعیت و ظاهر را به‌روز می‌کند
        }

        container.revalidate();
        container.repaint();
    }

    public void refreshAll() {
        update(0);
    }
}
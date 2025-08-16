package com.blueprinthell.controller.packet;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.view.PacketView;

import javax.swing.*;
import java.util.*;


public class PacketRenderController implements Updatable {
    private final JComponent container;
    private final List<WireModel> wires;
    private final Map<PacketModel, PacketView> viewMap = new HashMap<>();


    public PacketRenderController(JComponent container, List<WireModel> wires) {
        this.container = container;
        this.wires = wires;
    }


    @Override
    public void update(double dt) {
        for (WireModel wire : wires) {
            for (PacketModel pm : wire.getPackets()) {
                if (!viewMap.containsKey(pm)) {
                    PacketView pv = new PacketView(pm);
                    viewMap.put(pm, pv);
                    container.add(pv);
                }
            }
        }

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

        for (PacketView pv : viewMap.values()) {
            pv.refreshView();
        }
        container.revalidate();
        container.repaint();
    }


    public void refreshAll() {
        for (PacketView pv : viewMap.values()) {
            container.remove(pv);
        }
        viewMap.clear();

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

package com.blueprinthell.controller.simulation;

import com.blueprinthell.controller.packet.PacketProducerController;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.SystemBoxModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimulationController {
    private final List<Updatable> updatables = new ArrayList<>();
    private final List<Updatable> renderUpdatables = new ArrayList<>();
    private TimelineController timelineController;
    private double elapsedSeconds = 0.0;
    private PacketProducerController packetProducer;
    private final Map<PortModel, SystemBoxModel> portToSystem = new HashMap<>();

    private boolean running = false;

    public SimulationController(int fps) {
        // Constructor remains the same
    }

    public void update(double dt) {
        if (dt <= 0) return;

        // Step 1: Always update render-related controllers
        List<Updatable> renderSnapshot;
        synchronized (renderUpdatables) {
            renderSnapshot = new ArrayList<>(renderUpdatables);
        }
        for (Updatable u : renderSnapshot) {
            u.update(dt);
        }

        // Step 2: Conditionally update the main simulation logic
        if (running) {
            List<Updatable> simulationSnapshot;
            synchronized (updatables) {
                simulationSnapshot = new ArrayList<>(updatables);
            }
            for (Updatable u : simulationSnapshot) {
                u.update(dt);
            }
            if (timelineController != null) {
                elapsedSeconds += dt;
                if (elapsedSeconds >= 1.0) {
                    elapsedSeconds -= 1.0;
                    timelineController.recordFrame();
                }
            }
        }
    }


    public void register(Updatable u) {
        synchronized (updatables) {
            if (!updatables.contains(u)) {
                updatables.add(u);
            }
        }
    }

    /**
     * <-- پچ ۲: متد جدید برای ثبت کنترلرهای گرافیکی -->
     * Registers an updatable that should run even when the simulation is paused.
     * @param u The renderer updatable to register.
     */
    public void registerRenderer(Updatable u) {
        synchronized (renderUpdatables) {
            if (!renderUpdatables.contains(u)) {
                renderUpdatables.add(u);
            }
        }
    }


    public void unregister(Updatable u) {
        synchronized (updatables) {
            updatables.remove(u);
        }
    }

    public void setTimelineController(TimelineController tc) {
        this.timelineController = tc;
    }

    public void start() {
        this.running = true;
    }

    public void stop() {
        this.running = false;
    }

    public boolean isRunning() {
        return this.running;
    }

    public void clearUpdatables() {
        synchronized (updatables) {
            updatables.clear();
        }
        // Also clear render updatables
        synchronized (renderUpdatables) {
            renderUpdatables.clear();
        }
        elapsedSeconds = 0.0;
    }

    public void setPacketProducerController(PacketProducerController producer) {
        this.packetProducer = producer;
        register(producer);
    }

    public void registerSystemPort(SystemBoxModel system, PortModel inPort) {
        portToSystem.put(inPort, system);
    }

    public boolean isSystemEnabled(PortModel inPort) {
        SystemBoxModel sys = portToSystem.get(inPort);
        return sys == null || sys.isEnabled();
    }

    public void onPacketReturned() {
        if (packetProducer != null) {
            packetProducer.onPacketReturned();
        }
    }

    public PacketProducerController getPacketProducerController() {
        return packetProducer;
    }
}
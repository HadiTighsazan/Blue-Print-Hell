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
    private TimelineController timelineController;
    private double elapsedSeconds = 0.0;
    private PacketProducerController packetProducer;
    private final Map<PortModel, SystemBoxModel> portToSystem = new HashMap<>();

    // The Swing Timer is removed. A simple running flag is used instead.
    private boolean running = false;

    public SimulationController(int fps) {
        // The constructor no longer creates a javax.swing.Timer.
        // The 'fps' parameter is kept for potential future use (e.g., fixed-step simulation)
    }

    /**
     * This method is now public and replaces the old private 'tick'.
     * It manually advances the simulation by a given delta time.
     * This is the new core of the simulation loop, callable from any context (client or server).
     * @param dt Delta time in seconds.
     */
    public void update(double dt) {
        if (!running || dt <= 0) return;

        List<Updatable> snapshot;
        synchronized (updatables) {
            snapshot = new ArrayList<>(updatables);
        }
        for (Updatable u : snapshot) {
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

    public void register(Updatable u) {
        synchronized (updatables) {
            if (!updatables.contains(u)) {
                updatables.add(u);
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

    // Start and stop methods now just control the 'running' flag.
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
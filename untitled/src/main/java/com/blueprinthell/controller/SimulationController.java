package com.blueprinthell.controller;

import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.SystemBoxModel;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SimulationController {
    private final List<Updatable> updatables = new ArrayList<>();
    private final Timer timer;
    private TimelineController timelineController;
    private double elapsedSeconds = 0.0;

    private PacketProducerController packetProducer;

    private final Map<PortModel, SystemBoxModel> portToSystem = new HashMap<>();


    public SimulationController(int fps) {
        int delay = 1000 / fps;
        this.timer = new Timer(delay, e -> tick(delay));
    }

    private void tick(int delay) {
        double dt = delay / 1000.0;
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


    public void start() {
        if (!timer.isRunning()) {
            timer.start();
        }
    }


    public void stop() {
        if (timer.isRunning()) {
            timer.stop();
        }
    }


    public boolean isRunning() {
        return timer.isRunning();
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

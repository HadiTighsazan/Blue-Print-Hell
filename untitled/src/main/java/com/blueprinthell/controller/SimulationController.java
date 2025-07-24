package com.blueprinthell.controller;

import com.blueprinthell.model.Updatable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Main simulation controller that triggers update(dt) on all registered Updatable models
 * and optionally records snapshots each second via a TimelineController.
 * همچنین وظیفه‌ی مدیریت بازتولید پکت‌های بازگشتی را به عهده دارد.
 */
public class SimulationController {
    private final List<Updatable> updatables = new ArrayList<>(); // guarded by itself
    private final Timer timer;
    private TimelineController timelineController;
    private double elapsedSeconds = 0.0;

    // برای نگهداری مرجع به PacketProducerController جهت بازتولید پکت‌های بازگشتی
    private PacketProducerController packetProducer;

    /**
     * Constructs a simulation controller with the given frame rate.
     * @param fps frames per second for simulation ticks
     */
    public SimulationController(int fps) {
        int delay = 1000 / fps;
        this.timer = new Timer(delay, e -> tick(delay));
    }

    private void tick(int delay) {
        double dt = delay / 1000.0;
        // Update all registered updatables
        List<Updatable> snapshot;
        synchronized (updatables) {
            snapshot = new ArrayList<>(updatables);
        }
        for (Updatable u : snapshot) {
            u.update(dt);
        }
        // Record snapshot each second if a timeline controller is set
        if (timelineController != null) {
            elapsedSeconds += dt;
            if (elapsedSeconds >= 1.0) {
                elapsedSeconds -= 1.0;
                timelineController.recordFrame();
            }
        }
    }

    /**
     * Registers an Updatable to be updated each tick.
     * @param u the updatable instance
     */
    public void register(Updatable u) {
        if (u == null) {
            return;
        }
        synchronized (updatables) {
            if (!updatables.contains(u)) {
                updatables.add(u);
            }
        }
    }

    /**
     * Unregisters a previously registered Updatable.
     * @param u the updatable to remove
     */
    public void unregister(Updatable u) {
        synchronized (updatables) {
            updatables.remove(u);
        }
    }

    /**
     * Sets the TimelineController to record snapshots periodically.
     * @param tc timeline controller instance
     */
    public void setTimelineController(TimelineController tc) {
        this.timelineController = tc;
    }

    /**
     * Starts the simulation timer if not already running.
     */
    public void start() {
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    /**
     * Stops the simulation timer if running.
     */
    public void stop() {
        if (timer.isRunning()) {
            timer.stop();
        }
    }

    /**
     * @return true if the simulation timer is currently running.
     */
    public boolean isRunning() {
        return timer.isRunning();
    }

    /**
     * Clears all registered Updatable instances and resets elapsed time.
     * Used when a new level starts.
     */
    public void clearUpdatables() {
        synchronized (updatables) {
            updatables.clear();
        }
        elapsedSeconds = 0.0;
    }

    /**
     * Sets the PacketProducerController so that SimulationController
     * can forward returned-packet notifications.
     * @param producer the packet producer controller
     */
    public void setPacketProducerController(PacketProducerController producer) {
        System.out.println("[DEBUG] PacketProducerController registered: " + producer);

        this.packetProducer = producer;
        register(producer);
    }

    /**
     * هر زمان که یک پکت به مبدا برگشت باید این متد فراخوانی شود
     * تا اعتبار بازگشتی در تولیدکننده افزایش یابد.
     */
    public void onPacketReturned() {
        if (packetProducer != null) {
            packetProducer.onPacketReturned();
        }
    }
}

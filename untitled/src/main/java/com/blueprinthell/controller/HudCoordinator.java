package com.blueprinthell.controller;

import com.blueprinthell.model.CoinModel;
import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.ScoreModel;
import com.blueprinthell.view.HudView;

import javax.swing.*;
import java.awt.event.ActionListener;

/**
 * Single‑responsibility helper that keeps the HUD (labels & buttons) in sync with
 * the running simulation.  Responsibilities:
 * <ul>
 *     <li>Attach / detach listeners on Start & Pause buttons per level</li>
 *     <li>Enable/disable Start button based on an external predicate</li>
 *     <li>Lightweight refresh of score / coins / loss each tick (or on‑demand)</li>
 * </ul>
 * This class knows **nothing** about packets, boxes یا wires؛ فقط با مدل‌های عددی کار می‌کند.
 */
public final class HudCoordinator {

    private  HudView hud ;
    private  ScoreModel scoreModel;
    private  CoinModel  coinModel;
    private  PacketLossModel lossModel;
    private  SimulationController simulation;
    private  TimelineController   timeline;
    private PacketProducerController   producer; // set per‑level

    public HudCoordinator(HudView hud,
                          ScoreModel scoreModel,
                          CoinModel coinModel,
                          PacketLossModel lossModel,
                          SimulationController simulation,
                          TimelineController timeline) {
        this.hud = hud;
        this.scoreModel = scoreModel;
        this.coinModel = coinModel;
        this.lossModel = lossModel;
        this.simulation = simulation;
        this.timeline   = timeline;
    }

    /* ----------------------------- Wiring per level ----------------------------- */
    public void wireLevel(PacketProducerController producer) {
        this.producer = producer;
        clearButtonListeners();

        hud.addStartListener(startListener);
        hud.addToggleListener(toggleListener);
        hud.getStartButton().setEnabled(false); // گیم کنترلر بعداً فعال می‌کند
        hud.setToggleText("Pause");
    }

    private final ActionListener startListener = e -> {
        if (producer != null) producer.startProduction();
        hud.getStartButton().setEnabled(false);
        simulation.start();
    };

    private final ActionListener toggleListener = e -> {
        if (simulation.isRunning()) {
            simulation.stop();
            if (producer != null) producer.stopProduction();
            timeline.pause();
            hud.setToggleText("Resume");
        } else {
            if (producer != null && !producer.isFinished()) producer.startProduction();
            timeline.resume();
            simulation.start();
            hud.setToggleText("Pause");
        }
    };

    private void clearButtonListeners() {
        for (var l : hud.getStartButton().getActionListeners())
            hud.getStartButton().removeActionListener(l);
        for (var l : hud.getToggleButton().getActionListeners())
            hud.getToggleButton().removeActionListener(l);
    }

    /* ----------------------------- Refresh labels ----------------------------- */
    public void refresh() {
        hud.setCoins(coinModel.getCoins());
        hud.setPacketLoss(lossModel.getLostCount());
    }

    /*  enable / disable Start button from outside */
    public void setStartEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> hud.getStartButton().setEnabled(enabled));
    }
}

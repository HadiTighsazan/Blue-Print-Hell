package com.blueprinthell.controller;

import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.CoinModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.view.screens.ShopView;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * Controller to manage the in‑game shop: pausing the simulation, handling purchases,
 * applying temporary power‑ups, and resuming the simulation when done.
 */
public class ShopController {
    private final SimulationController simulation;
    private final CoinModel coinModel;
    private final CollisionController collisionController;
    private final PacketLossModel lossModel;
    private final List<WireModel> wires; // all active wires, used for O’Anahita

    private final ShopView shopView;
    private final JDialog dialog;

    public ShopController(JFrame parentFrame,
                          SimulationController simulation,
                          CoinModel coinModel,
                          CollisionController collisionController,
                          PacketLossModel lossModel,
                          List<WireModel> wires) {
        this.simulation = simulation;
        this.coinModel = coinModel;
        this.collisionController = collisionController;
        this.lossModel = lossModel;
        this.wires = wires;
        this.shopView = new ShopView();

        // Setup modal dialog
        dialog = new JDialog(parentFrame, "Store", true);
        dialog.setContentPane(shopView);
        dialog.pack();
        dialog.setLocationRelativeTo(parentFrame);

        // Hook up listeners
        shopView.addBuyOAtarListener(e -> buyOAtar());
        shopView.addBuyOAiryamanListener(e -> buyOAiryaman());
        shopView.addBuyOAnahitaListener(e -> buyOAnahita());
        shopView.addCloseListener(e -> closeShop());
    }

    /** Opens the shop: pauses simulation and shows dialog. */
    public void openShop() {
        simulation.stop();
        shopView.setMessage("Welcome to the store!");
        dialog.setVisible(true);
    }

    private void closeShop() {
        dialog.setVisible(false);
        simulation.start();
    }

    /**
     * O’Atar – Disable impact wave noise propagation for 10 seconds (cost: 3 coins).
     */
    private void buyOAtar() {
        int cost = 3;
        if (!deductCoins(cost)) return;
        shopView.setMessage("O’Atar purchased: Impact waves disabled for 10 s");

        collisionController.setImpactWaveEnabled(false);
        Timer t = new Timer(10_000, new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                collisionController.setImpactWaveEnabled(true);
            }
        });
        t.setRepeats(false);
        t.start();
    }

    /**
     * O’Airyaman – Disable all collisions for 5 seconds (cost: 4 coins).
     */
    private void buyOAiryaman() {
        int cost = 4;
        if (!deductCoins(cost)) return;
        shopView.setMessage("O’Airyaman purchased: Collisions disabled for 5 s");

        collisionController.pauseCollisions();
        Timer t = new Timer(5_000, e -> collisionController.resumeCollisions());
        t.setRepeats(false);
        t.start();
    }

    /**
     * O’Anahita – Clear noise from all packets and reset packet‑loss counter (cost: 5 coins).
     */
    private void buyOAnahita() {
        int cost = 5;
        if (!deductCoins(cost)) return;
        shopView.setMessage("O’Anahita purchased: Noise cleared");

        // Reset noise on every packet currently travelling
        for (WireModel w : wires) {
            for (PacketModel p : w.getPackets()) {
                p.resetNoise();
            }
        }
        // Reset accumulated packet loss metric
        lossModel.reset();
    }

    /** Helper: tries to deduct coins; returns true on success, false if not enough. */
    private boolean deductCoins(int cost) {
        if (!coinModel.spend(cost)) {
            shopView.setMessage("Not enough coins (need " + cost + ")");
            return false;
        }
        return true;
    }
}

package com.blueprinthell.controller;

import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.CoinModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.view.screens.ShopView;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller to manage the in‑game shop: pausing simulation, handling purchases,
 * applying temporary power‑ups, and updating the HUD with active effects.
 */
public class ShopController {
    private final SimulationController simulation;
    private final CoinModel coinModel;
    private final CollisionController collisionController;
    private final PacketLossModel lossModel;
    private final List<WireModel> wires;
    private final HudController hudController;

    private final ShopView shopView;
    private final JDialog dialog;

    // Track active scroll effects and their remaining seconds
    private final List<String> activeNames  = new ArrayList<>();
    private final List<Integer> activeTimes = new ArrayList<>();

    public ShopController(JFrame parentFrame,
                          SimulationController simulation,
                          CoinModel coinModel,
                          CollisionController collisionController,
                          PacketLossModel lossModel,
                          List<WireModel> wires,
                          HudController hudController) {
        this.simulation          = simulation;
        this.coinModel          = coinModel;
        this.collisionController = collisionController;
        this.lossModel          = lossModel;
        this.wires               = wires;
        this.hudController       = hudController;
        this.shopView            = new ShopView();

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

    /** O’Atar – Disable impact wave noise propagation for 10 seconds (cost: 3 coins). */
    private void buyOAtar() {
        int cost = 3, duration = 10;
        if (!deductCoins(cost)) return;
        shopView.setMessage("O’Atar purchased: Impact waves disabled for " + duration + " s");

        activateFeature("O’Atar", duration);
        collisionController.setImpactWaveEnabled(false);
        Timer t = new Timer(duration * 1000, (ActionEvent e) -> {
            collisionController.setImpactWaveEnabled(true);
            deactivateFeature("O’Atar");
        });
        t.setRepeats(false);
        t.start();
    }

    /** O’Airyaman – Disable collisions for 5 seconds (cost: 4 coins). */
    private void buyOAiryaman() {
        int cost = 4, duration = 5;
        if (!deductCoins(cost)) return;
        shopView.setMessage("O’Airyaman purchased: Collisions disabled for " + duration + " s");

        activateFeature("O’Airyaman", duration);
        collisionController.pauseCollisions();
        Timer t = new Timer(duration * 1000, e -> {
            collisionController.resumeCollisions();
            deactivateFeature("O’Airyaman");
        });
        t.setRepeats(false);
        t.start();
    }

    /** O’Anahita – Clear noise from all packets and reset packet‑loss counter (cost: 5 coins). */
    private void buyOAnahita() {
        int cost = 5;
        if (!deductCoins(cost)) return;
        shopView.setMessage("O’Anahita purchased: Noise cleared");

        activateFeature("O’Anahita", 0); // instantaneous effect
        // Reset noise on every packet currently travelling
        for (WireModel w : wires) {
            for (PacketModel p : w.getPackets()) {
                p.resetNoise();
            }
        }
        // Reset accumulated packet loss metric
        lossModel.reset();
        deactivateFeature("O’Anahita");
    }

    /** Helper: deducts coins or shows error. */
    private boolean deductCoins(int cost) {
        if (!coinModel.spend(cost)) {
            shopView.setMessage("Not enough coins (need " + cost + ")");
            return false;
        }
        return true;
    }

    /** Marks a scroll as active, updates HUD. */
    private void activateFeature(String name, int seconds) {
        activeNames.add(name);
        activeTimes.add(seconds);
        hudController.setActiveFeatures(List.copyOf(activeNames), List.copyOf(activeTimes));
    }

    /** Removes a scroll from active list and updates HUD. */
    private void deactivateFeature(String name) {
        int idx = activeNames.indexOf(name);
        if (idx >= 0) {
            activeNames.remove(idx);
            activeTimes.remove(idx);
            hudController.setActiveFeatures(List.copyOf(activeNames), List.copyOf(activeTimes));
        }
    }
}

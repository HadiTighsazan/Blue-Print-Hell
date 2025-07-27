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


public class ShopController {
    private final SimulationController simulation;
    private final CoinModel coinModel;
    private final CollisionController collisionController;
    private final PacketLossModel lossModel;
    private final List<WireModel> wires;
    private final HudController hudController;

    private final ShopView shopView;
    private final JDialog dialog;

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

        dialog = new JDialog(parentFrame, "Store", true);
        dialog.setContentPane(shopView);
        dialog.pack();
        dialog.setLocationRelativeTo(parentFrame);

        shopView.addBuyOAtarListener(e -> buyOAtar());
        shopView.addBuyOAiryamanListener(e -> buyOAiryaman());
        shopView.addBuyOAnahitaListener(e -> buyOAnahita());
        shopView.addCloseListener(e -> closeShop());
    }

    public void openShop() {
        simulation.stop();
        shopView.setMessage("Welcome to the store!");
        dialog.setVisible(true);
    }

    private void closeShop() {
        dialog.setVisible(false);
        simulation.start();
    }

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

    private void buyOAnahita() {
        int cost = 5;
        if (!deductCoins(cost)) return;
        shopView.setMessage("O’Anahita purchased: Noise cleared");

        activateFeature("O’Anahita", 0);
        for (WireModel w : wires) {
            for (PacketModel p : w.getPackets()) {
                p.resetNoise();
            }
        }
        lossModel.reset();
        deactivateFeature("O’Anahita");
    }

    private boolean deductCoins(int cost) {
        if (!coinModel.spend(cost)) {
            shopView.setMessage("Not enough coins (need " + cost + ")");
            return false;
        }
        return true;
    }

    private void activateFeature(String name, int seconds) {
        activeNames.add(name);
        activeTimes.add(seconds);
        hudController.setActiveFeatures(List.copyOf(activeNames), List.copyOf(activeTimes));
    }

    private void deactivateFeature(String name) {
        int idx = activeNames.indexOf(name);
        if (idx >= 0) {
            activeNames.remove(idx);
            activeTimes.remove(idx);
            hudController.setActiveFeatures(List.copyOf(activeNames), List.copyOf(activeTimes));
        }
    }
}

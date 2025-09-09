package com.blueprinthell.controller;

import com.blueprinthell.controller.gameplay.*;
import com.blueprinthell.controller.physics.CollisionController;
import com.blueprinthell.controller.pvp.PvPClientController; // <-- وارد کردن کلاس جدید
import com.blueprinthell.controller.simulation.SimulationController;
import com.blueprinthell.controller.ui.editor.SystemBoxDragController;
import com.blueprinthell.controller.ui.hud.HudController;
import com.blueprinthell.model.CoinModel;
import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.view.screens.GameScreenView;
import com.blueprinthell.view.screens.ShopView;

import javax.swing.*;
import java.awt.*;
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
    private final GameScreenView gameView;

    private final ShopView shopView;
    private final JDialog dialog;

    // --- NEW: Fields for PvP Mode ---
    private PvPClientController pvpController;
    private boolean isPvPMode = false;
    // --- End of New Fields ---

    private AccelerationFreezeController freezeController;
    private EliphasCenteringController eliphasController;

    public ShopController(JFrame parentFrame,
                          SimulationController simulation,
                          CoinModel coinModel,
                          CollisionController collisionController,
                          PacketLossModel lossModel,
                          List<WireModel> wires,
                          HudController hudController, GameScreenView gameView) {
        this.simulation = simulation;
        this.coinModel = coinModel;
        this.collisionController = collisionController;
        this.lossModel = lossModel;
        this.wires = wires;
        this.hudController = hudController;
        this.shopView = new ShopView();
        this.gameView = gameView;
        dialog = new JDialog(parentFrame, "Store", true);
        dialog.setContentPane(shopView);
        dialog.pack();
        dialog.setLocationRelativeTo(parentFrame);

        shopView.addBuyOAtarListener(e -> buyOAtar());
        shopView.addBuyOAiryamanListener(e -> buyOAiryaman());
        shopView.addBuyOAnahitaListener(e -> buyOAnahita());
        shopView.addCloseListener(e -> closeShop());
        shopView.addBuyFreezeAccelListener(e -> buyFreezeAcceleration());
        shopView.addBuySisyphusListener(e -> buySisyphus());
        shopView.addBuyEliphasListener(e -> buyEliphas());
    }

    /**
     * Injects the PvP controller to switch the shop into network mode.
     * @param pvpController The active PvP client controller, or null to revert to single-player.
     */
    public void setPvpController(PvPClientController pvpController) {
        this.pvpController = pvpController;
        this.isPvPMode = (pvpController != null);
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

    private boolean deductCoins(int cost) {
        if (!coinModel.spend(cost)) {
            shopView.setMessage("Not enough coins (need " + cost + ")");
            return false;
        }
        return true;
    }

    private void refundCoins(int amount, String reason) {
        if (amount <= 0) return;
        coinModel.add(amount);
        String msg = (reason != null && !reason.isBlank())
                ? reason + " — " + amount + " coins refunded"
                : amount + " coins refunded";
        shopView.setMessage(msg);
    }

    // --- Item Purchase Logic ---

    private void buyOAtar() {
        if (isPvPMode) {
            pvpController.sendBuyItemAction("OATAR");
            shopView.setMessage("Purchase request for O'Atar sent...");
        } else {
            int cost = 3, duration = 10;
            if (!deductCoins(cost)) return;
            shopView.setMessage("O’Atar purchased: Impact waves disabled for " + duration + " s");
            collisionController.setImpactWaveEnabled(false);
            Timer t = new Timer(duration * 1000, e -> collisionController.setImpactWaveEnabled(true));
            t.setRepeats(false);
            t.start();
        }
    }

    private void buyOAiryaman() {
        if (isPvPMode) {
            pvpController.sendBuyItemAction("OAIRYAMAN");
            shopView.setMessage("Purchase request for O'Airyaman sent...");
        } else {
            int cost = 4, duration = 5;
            if (!deductCoins(cost)) return;
            shopView.setMessage("O’Airyaman purchased: Collisions disabled for " + duration + " s");
            collisionController.pauseCollisions();
            Timer t = new Timer(duration * 1000, e -> collisionController.resumeCollisions());
            t.setRepeats(false);
            t.start();
        }
    }

    private void buyOAnahita() {
        if (isPvPMode) {
            pvpController.sendBuyItemAction("OANAHITA");
            shopView.setMessage("Purchase request for O'Anahita sent...");
        } else {
            int cost = 5;
            if (!deductCoins(cost)) return;
            shopView.setMessage("O’Anahita purchased: Noise cleared");
            for (WireModel w : wires) {
                for (PacketModel p : w.getPackets()) {
                    p.resetNoise();
                }
            }
            lossModel.reset();
        }
    }

    private void buyFreezeAcceleration() {
        if (isPvPMode) {
            // Note: Abilities requiring targeting need a more complex protocol message
            // For now, we send a simple buy request.
            pvpController.sendBuyItemAction("FREEZE_ACCEL");
            shopView.setMessage("Freeze request sent...");
            closeShop();
        } else {
            int cost = 10;
            if (freezeController == null) {
                shopView.setMessage("Freeze acceleration not available!");
                return;
            }
            if (!freezeController.canActivate()) {
                double cooldown = freezeController.getCooldownRemaining();
                shopView.setMessage("On cooldown! Wait " + String.format("%.1f", cooldown) + " seconds");
                return;
            }
            if (!deductCoins(cost)) return;

            shopView.setMessage("Select a point on a wire...");
            closeShop();

            SwingUtilities.invokeLater(() -> {
                new FreezePointSelector(
                        gameView, wires,
                        point -> {
                            if (!freezeController.activateFreezeAt(point)) {
                                refundCoins(cost, "Freeze failed");
                            }
                        },
                        () -> refundCoins(cost, "Freeze canceled")
                ).startSelection();
            });
        }
    }

    private void buySisyphus() {
        if (isPvPMode) {
            pvpController.sendBuyItemAction("SISYPHUS_SCROLL");
            shopView.setMessage("Sisyphus Scroll request sent...");
        } else {
            int cost = 15;
            if (!deductCoins(cost)) return;
            shopView.setMessage("Scroll of Sisyphus activated: One limited drag available.");
            SystemBoxDragController.enableSisyphusOneShot(
                    120,
                    m -> m != null && m.getInPorts() != null && !m.getInPorts().isEmpty()
                            && m.getOutPorts() != null && !m.getOutPorts().isEmpty(),
                    wires,
                    gameView.getSystemBoxViews(),
                    () -> Toolkit.getDefaultToolkit().beep()
            );
        }
    }

    private void buyEliphas() {
        if (isPvPMode) {
            pvpController.sendBuyItemAction("ELIPHAS_SCROLL");
            shopView.setMessage("Eliphas Scroll request sent...");
            closeShop();
        } else {
            int cost = 20;
            if (!deductCoins(cost)) return;
            shopView.setMessage("Eliphas: Click on a wire's centerline (ESC to cancel)...");
            closeShop();

            SwingUtilities.invokeLater(() -> {
                new EliphasPointSelector(
                        gameView, wires,
                        point -> {
                            if (eliphasController == null || !eliphasController.activateAt(point)) {
                                refundCoins(cost, "Eliphas failed");
                            }
                        },
                        () -> refundCoins(cost, "Eliphas canceled")
                ).start();
            });
        }
    }

    // --- Setters for dependencies ---

    public void setFreezeController(AccelerationFreezeController controller) {
        this.freezeController = controller;
    }

    public void setEliphasController(EliphasCenteringController c) {
        this.eliphasController = c;
    }
}
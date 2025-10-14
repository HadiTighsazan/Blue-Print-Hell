package com.blueprinthell.controller.ui;

import com.blueprinthell.controller.physics.CollisionController;
import com.blueprinthell.controller.ui.hud.HudController;
import com.blueprinthell.controller.ShopController;
import com.blueprinthell.controller.simulation.SimulationController;
import com.blueprinthell.media.AudioController;
import com.blueprinthell.view.HudView;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.CoinModel;
import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.view.screens.GameScreenView;
import javax.swing.*;
import java.util.List;

public class UIController {
    private final JFrame          mainFrame;
    private final AudioController audioController;

    public UIController(JFrame parentFrame,
                        HudView hudView,
                        SimulationController simulation,
                        CoinModel coinModel,
                        CollisionController collisionCtrl,
                        PacketLossModel lossModel,
                        List<WireModel> wires,
                        HudController hudController,
                        ShopController shopController,
                        GameScreenView gameView) {  // اضافه کردن پارامتر
        this.mainFrame = parentFrame;


        this.audioController = new AudioController();

        hudView.addStoreListener(e -> shopController.openShop());
    }

    public JFrame getMainFrame() {
        return mainFrame;
    }


    public AudioController getAudioController() {
        return audioController;
    }
}
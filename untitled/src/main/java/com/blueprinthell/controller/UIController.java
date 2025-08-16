// فایل: untitled/src/main/java/com/blueprinthell/controller/UIController.java
package com.blueprinthell.controller;

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
    private final ShopController  shopController;
    private final AudioController audioController;

    public UIController(JFrame parentFrame,
                        HudView hudView,
                        SimulationController simulation,
                        CoinModel coinModel,
                        CollisionController collisionCtrl,
                        PacketLossModel lossModel,
                        List<WireModel> wires,
                        HudController hudController,
                        GameScreenView gameView) {  // اضافه کردن پارامتر
        this.mainFrame = parentFrame;

        this.shopController = new ShopController(
                parentFrame,
                simulation,
                coinModel,
                collisionCtrl,
                lossModel,
                wires,
                hudController,
                gameView  // پاس دادن به ShopController
        );
        this.audioController = new AudioController();

        hudView.addStoreListener(e -> shopController.openShop());
    }

    public JFrame getMainFrame() {
        return mainFrame;
    }

    public ShopController getShopController() {
        return shopController;
    }

    public AudioController getAudioController() {
        return audioController;
    }
}
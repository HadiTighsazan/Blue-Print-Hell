package com.blueprinthell.controller;

import com.blueprinthell.media.AudioController;
import com.blueprinthell.view.HudView;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.CoinModel;
import com.blueprinthell.model.PacketLossModel;

import javax.swing.*;
import java.util.List;

/**
 * UIController wires together high‑level UI interactions that should not live in GameController.
 * <p>
 * مسئولیت‌ها:
 * <ul>
 *     <li>اتصال دکمهٔ Store در HUD به ShopController</li>
 *     <li>ایجاد AudioController سراسری برای پخش موسیقی و کنترل حجم</li>
 * </ul>
 */
public class UIController {
    private final ShopController shopController;
    private final AudioController audioController;

    public UIController(JFrame parentFrame,
                        HudView hudView,
                        SimulationController simulation,
                        CoinModel coinModel,
                        CollisionController collisionCtrl,
                        PacketLossModel lossModel,
                        List<WireModel> wires) {

        // ساخت کنترلر فروشگاه (مودال)
        this.shopController = new ShopController(parentFrame,
                simulation,
                coinModel,
                collisionCtrl,
                lossModel,
                wires);
        // کنترلر صدا (پس‌زمینه و حجم)
        this.audioController = new AudioController();

        // اتصال HUD → فروشگاه
        hudView.addStoreListener(e -> shopController.openShop());
    }

    public ShopController getShopController() {
        return shopController;
    }

    public AudioController getAudioController() {
        return audioController;
    }
}

package com.blueprinthell.controller;

import com.blueprinthell.level.LevelManager;
import com.blueprinthell.model.CoinModel;
import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireUsageModel;
import com.blueprinthell.view.HudView;

import java.util.List;

/**
 * Keeps the HUD in sync with game‑state models. It also provides {@link #refreshOnce()} to force a repaint
 * immediately after constructing a level (before the simulation starts).
 */
public class HudController implements Updatable {

    private final WireUsageModel  usageModel;
    private final PacketLossModel lossModel;
    private final CoinModel       coinModel;
    private final LevelManager    levelManager;
    private final HudView         hudView;

    public HudController(
            WireUsageModel usageModel,
            PacketLossModel lossModel,
            CoinModel coinModel,
            LevelManager levelManager,
            HudView hudView) {
        this.usageModel   = usageModel;
        this.lossModel    = lossModel;
        this.coinModel    = coinModel;
        this.levelManager = levelManager;
        this.hudView      = hudView;

        // update HUD whenever wire usage or coins change
        this.usageModel.addListener(this::refreshOnce);
        this.coinModel.addListener(c -> refreshOnce());

        // initial paint
        refreshOnce();
    }

    @Override
    public void update(double dt) {
        refreshOnce();
    }

    /** Updates HUD labels based on current model values. */
    public void refreshOnce() {
        hudView.setLevel(levelManager.getLevelIndex() + 1);
        hudView.setWireLength(usageModel.getRemainingWireLength());
        hudView.setPacketLoss(lossModel.getLostCount());
        hudView.setCoins(coinModel.getCoins());
    }

    /**
     * Externally called to update the list of active scroll effects in the HUD.
     * @param names list of scroll effect names
     * @param remainingSeconds list of seconds remaining paralleling names
     */
    public void setActiveFeatures(List<String> names, List<Integer> remainingSeconds) {
        hudView.setActiveFeatures(names, remainingSeconds);
    }
}

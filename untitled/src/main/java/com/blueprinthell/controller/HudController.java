package com.blueprinthell.controller;

import com.blueprinthell.level.LevelManager;
import com.blueprinthell.model.CoinModel;
import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.model.WireUsageModel;
import com.blueprinthell.view.HudView;

import java.util.List;


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

        this.usageModel.addListener(this::refreshOnce);
        this.coinModel.addListener(c -> refreshOnce());

        refreshOnce();
    }

    @Override
    public void update(double dt) {
        refreshOnce();
    }

    public void refreshOnce() {
        hudView.setLevel(levelManager.getLevelIndex() + 1);
        hudView.setWireLength(usageModel.getRemainingWireLength());
        hudView.setPacketLoss(lossModel.getLostCount());
        hudView.setCoins(coinModel.getCoins());
    }

    public void setActiveFeatures(List<String> names, List<Integer> remainingSeconds) {
        hudView.setActiveFeatures(names, remainingSeconds);
    }
}

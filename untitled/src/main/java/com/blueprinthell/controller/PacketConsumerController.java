package com.blueprinthell.controller;

import com.blueprinthell.model.*;
import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargeGroupRegistry;

import java.util.Objects;

/**
 * <h2>PacketConsumerController</h2>
 * <ul>
 *   <li>تمام پکت‌هایی که در بافر سیستم Sink قرار می‌گیرند را مصرف می‌کند و سکه/امتیاز می‌دهد.</li>
 *   <li>استثناء: اگر پکت <b>BitPacket</b> باشد، به جای امتیاز، Loss محسوب می‌شود (طبق فاز حجیم).</li>
 *   <li>در صورت Bit → Loss، در {@link LargeGroupRegistry#markBitLost(int, int)} نیز ثبت می‌شود.</li>
 * </ul>
 */
public class PacketConsumerController implements Updatable {

    private final SystemBoxModel      box;
    private final ScoreModel          scoreModel;
    private final CoinModel           coinModel;
    private final PacketLossModel     lossModel;        // جدید
    private final LargeGroupRegistry  groupRegistry;    // می‌تواند null باشد

    /* ----------------------- سازنده‌ها ----------------------- */
    public PacketConsumerController(SystemBoxModel box,
                                    ScoreModel scoreModel,
                                    CoinModel coinModel) {
        this(box, scoreModel, coinModel, null, null);
    }

    public PacketConsumerController(SystemBoxModel box,
                                    ScoreModel scoreModel,
                                    CoinModel coinModel,
                                    PacketLossModel lossModel,
                                    LargeGroupRegistry registry) {
        this.box           = Objects.requireNonNull(box, "box");
        this.scoreModel    = Objects.requireNonNull(scoreModel, "scoreModel");
        this.coinModel     = Objects.requireNonNull(coinModel, "coinModel");
        this.lossModel     = lossModel;     // مجاز به null برای سازگاری
        this.groupRegistry = registry;      // مجاز به null
    }

    /* --------------------------------------------------------- */
    @Override
    public void update(double dt) {
        PacketModel packet;
        while ((packet = box.pollPacket()) != null) {
            if (packet instanceof BitPacket bp) {
                // Drop & Loss
                if (lossModel != null) lossModel.increment();
                if (groupRegistry != null) {
                    groupRegistry.markBitLost(bp.getGroupId(), 1);
                }
                continue; // امتیاز/سکه‌ای تعلق نمی‌گیرد
            }

            packet.resetNoise();
            int value = packet.getType().coins;
            scoreModel.addPoints(value);
            coinModel.add(value);
        }
    }
}

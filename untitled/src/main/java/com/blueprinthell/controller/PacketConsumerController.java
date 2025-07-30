package com.blueprinthell.controller;

import com.blueprinthell.model.*;
import com.blueprinthell.model.large.BitPacket;

/**
 * Phase-1: Pure logic completion without wiring/ UI changes.
 * - BitPacket at sink => counts as Loss (no coins/score).
 * - Other packets => coins/score via PacketOps.coinValue(...).
 *
 * NOTE: Original logic lines are preserved inside a LEGACY block (non-executed)
 * to respect the "no deletions" request.
 */
public class PacketConsumerController implements Updatable {

    private final SystemBoxModel box;
    private final ScoreModel scoreModel;
    private final CoinModel  coinModel;

    // OPTIONAL: loss model (can be set later; kept null-safe)
    private PacketLossModel lossModel;

    public PacketConsumerController(SystemBoxModel box,
                                    ScoreModel scoreModel,
                                    CoinModel coinModel) {
        this.box        = box;
        this.scoreModel = scoreModel;
        this.coinModel  = coinModel;
    }

    /** Overloaded ctor that also accepts a loss model (optional). */
    public PacketConsumerController(SystemBoxModel box,
                                    ScoreModel scoreModel,
                                    CoinModel coinModel,
                                    PacketLossModel lossModel) {
        this(box, scoreModel, coinModel);
        this.lossModel = lossModel;
    }

    /** Setter to provide/replace loss model later without changing wiring. */
    public void setLossModel(PacketLossModel lossModel) {
        this.lossModel = lossModel;
    }

    /**
     * PURE logic for consuming a packet at a sink.
     * - BitPacket => loss++ and return.
     * - Otherwise => coin/score via PacketOps.coinValue.
     */
    public static void applyConsumeLogic(PacketModel packet,
                                         ScoreModel scoreModel,
                                         CoinModel coinModel,
                                         PacketLossModel lossModel) {
        if (packet == null) return;

        if (packet instanceof BitPacket) {
            if (lossModel != null) {
                lossModel.increment();
            }
            // No coins/score for BitPackets
            return;
        }

        int coins = PacketOps.coinValue(packet);
        if (coins > 0) {
            if (coinModel != null) coinModel.add(coins);
            if (scoreModel != null) scoreModel.addPoints(coins);
        }
    }

    @Override
    public void update(double dt) {
        PacketModel packet;
        while ((packet = box.pollPacket()) != null) {
            // New logic (Phase-1):
            packet.resetNoise();
            applyConsumeLogic(packet, scoreModel, coinModel, lossModel);

            // ---------------- LEGACY (preserved, non-executed) ----------------
            // Kept to satisfy the "no deletion" requirement.
            if (false) {
                packet.resetNoise();
                int value = packet.getType().coins;
                scoreModel.addPoints(value);
                coinModel.add(value);
            }
            // -----------------------------------------------------------------
        }
    }
}

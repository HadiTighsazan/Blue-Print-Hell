package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;
import com.blueprinthell.view.HudView;
import com.blueprinthell.view.screens.GameScreenView;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates capture/restore logic for {@link NetworkSnapshot}s.
 * <p>
 * علاوه بر مدل‌ها، لیستی از {@link PacketProducerController} نیز دریافت می‌کند تا در زمان
 * ریستور شمارنده‌های داخلی آن‌ها را Reset کند؛ در غیر این صورت حلقهٔ بی‌پایان تولید سکه
 * در حالت Time‑Travel رخ می‌داد.
 */
public final class SnapshotService {

    /* ---------------- Dependencies (immutable) ---------------- */
    private final List<SystemBoxModel>           boxes;
    private final List<WireModel>                wires;
    private final ScoreModel                     scoreModel;
    private final CoinModel                      coinModel;
    private final PacketLossModel                lossModel;
    private final WireUsageModel                 usageModel;
    private final SnapshotManager                snapshotManager;
    private final HudView                        hudView;
    private final GameScreenView                 gameView;
    private final PacketRenderController         packetRenderer;
    private final List<PacketProducerController> producers; // can be empty but never null

    public SnapshotService(List<SystemBoxModel> boxes,
                           List<WireModel> wires,
                           ScoreModel scoreModel,
                           CoinModel coinModel,
                           PacketLossModel lossModel,
                           WireUsageModel usageModel,
                           SnapshotManager snapshotManager,
                           HudView hudView,
                           GameScreenView gameView,
                           PacketRenderController renderer,
                           List<PacketProducerController> producers) {
        this.boxes            = boxes;
        this.wires            = wires;
        this.scoreModel       = scoreModel;
        this.coinModel        = coinModel;
        this.lossModel        = lossModel;
        this.usageModel       = usageModel;
        this.snapshotManager  = snapshotManager;
        this.hudView          = hudView;
        this.gameView         = gameView;
        this.packetRenderer   = renderer;
        this.producers        = producers == null ? List.of() : producers;
    }

    /* ====================================================================== */
    /** Captures و pushes snapshot. */
    public void capture() { snapshotManager.recordSnapshot(buildSnapshot()); }

    public NetworkSnapshot buildSnapshot() {
        /* ---------- Boxes با بافر ---------- */
        List<NetworkSnapshot.SystemBoxState> bs = new ArrayList<>();
        for (SystemBoxModel b : boxes) {
            List<NetworkSnapshot.PacketState> buffer = new ArrayList<>();
            for (PacketModel p : b.getBuffer())
                buffer.add(new NetworkSnapshot.PacketState(0.0, p.getNoise(), p.getType()));
            bs.add(new NetworkSnapshot.SystemBoxState(b.getX(), b.getY(), b.getWidth(), b.getHeight(),
                    b.getInShapes(), b.getOutShapes(), buffer));
        }
        /* ---------- Wires ---------- */
        List<NetworkSnapshot.WireState> ws = new ArrayList<>();
        for (WireModel w : wires) {
            List<NetworkSnapshot.PacketState> ps = new ArrayList<>();
            for (PacketModel p : w.getPackets())
                ps.add(new NetworkSnapshot.PacketState(p.getProgress(), p.getNoise(), p.getType()));
            ws.add(new NetworkSnapshot.WireState(w.getSrcPort().getCenterX(), w.getSrcPort().getCenterY(),
                    w.getDstPort().getCenterX(), w.getDstPort().getCenterY(), ps));
        }
        return new NetworkSnapshot(scoreModel.getScore(), coinModel.getCoins(),
                lossModel.getLostCount(), bs, ws);
    }

    /* ====================================================================== */
    /** Restores snapshot، resets producers و تازه‌سازی UI. */
    public void restore(NetworkSnapshot snap) {
        if (snap == null) return;

        /* ---------- Reset scalar models ---------- */
        scoreModel.reset(); scoreModel.addPoints(snap.score());
        coinModel.reset();  coinModel.add(snap.coins());
        lossModel.reset();  for (int i = 0; i < snap.packetLoss(); i++) lossModel.increment();

        /* ---------- Reset & stop producers ---------- */
        for (PacketProducerController p : producers) {
            p.reset();
            p.stopProduction();
        }

        /* ---------- Restore boxes & buffers ---------- */
        var boxStates = snap.boxStates();
        for (int i = 0; i < boxes.size() && i < boxStates.size(); i++) {
            var st  = boxStates.get(i);
            var box = boxes.get(i);
            box.setX(st.x());
            box.setY(st.y());
            box.clearBuffer();
            for (var ps : st.bufferPackets()) {
                var pkt = new PacketModel(ps.type(), Config.DEFAULT_PACKET_SPEED);
                pkt.increaseNoise(ps.noise());
                box.enqueue(pkt);
            }
        }

        /* ---------- Restore wire packets ---------- */
        var wireStates = snap.wireStates();
        for (int i = 0; i < wires.size() && i < wireStates.size(); i++) {
            var wire  = wires.get(i);
            var state = wireStates.get(i);
            wire.clearPackets();
            for (var ps : state.packets()) {
                var pkt = new PacketModel(ps.type(), Config.DEFAULT_PACKET_SPEED);
                pkt.increaseNoise(ps.noise());
                wire.attachPacket(pkt, ps.progress());
            }
        }

        /* ---------- UI refresh ---------- */
        SwingUtilities.invokeLater(() -> {
            gameView.reset(boxes, wires);
            packetRenderer.refreshAll();

            hudView.setCoins(coinModel.getCoins());
            hudView.setPacketLoss(lossModel.getLostCount());
        });
    }

    /* ====================================================================== */
    public SnapshotManager getSnapshotManager() { return snapshotManager; }
}

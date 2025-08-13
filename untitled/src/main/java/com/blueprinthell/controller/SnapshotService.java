package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.model.large.MergedPacket;
import com.blueprinthell.motion.MotionStrategyFactory;
import com.blueprinthell.snapshot.NetworkSnapshot;
import com.blueprinthell.snapshot.NetworkSnapshot.*;
import com.blueprinthell.view.HudView;
import com.blueprinthell.view.screens.GameScreenView;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;


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
    private final List<PacketProducerController> producers;
    private final Map<WireModel, SystemBoxModel> destMap;
    public SnapshotService(Map<WireModel, SystemBoxModel> destMap,List<SystemBoxModel> boxes,
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
        this.destMap=destMap;
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


    public void capture() { snapshotManager.recordSnapshot(buildSnapshot()); }

    public NetworkSnapshot buildSnapshot() {
        // نگاشت Port→Box برای تشخیص اندپوینت‌ها و ایندکس‌ها
        Map<PortModel, SystemBoxModel> portToBox = buildPortToBoxMap(this.boxes);

        NetworkSnapshot snap = new NetworkSnapshot(scoreModel.getScore()); // DTO جدید
        // world counters
        snap.world.coins = coinModel.getCoins();
        snap.world.packetLoss = lossModel.getLostCount();
        snap.world.wireUsageTotal = usageModel.getTotalWireLength();
        snap.world.wireUsageUsed  = usageModel.getUsedWireLength();

        // Boxes
        for (SystemBoxModel b : boxes) {
            BoxState bs = new BoxState();
            bs.id = b.getId();
            bs.primaryKind = b.getPrimaryKind();
            bs.enabled = b.isEnabled();
            // اگر getter تایمر را طبق گام ۲ اضافه کرده‌ای:
            try { bs.disableTimer = b.isEnabled() ? 0.0 : b.getDisableTimer(); } catch (Throwable ignore) {}
            bs.inShapes.addAll(b.getInShapes());
            bs.outShapes.addAll(b.getOutShapes());
            // بافرهای اختصاصی
            for (PacketModel p : b.getBitBuffer())    bs.bitBuffer.add(toPacketState(p));
            for (LargePacket lp : b.getLargeBuffer()) bs.largeBuffer.add(toPacketState(lp));
            snap.world.boxes.add(bs);
        }

        // Wires
        for (WireModel w : wires) {
            WireState ws = new WireState();
            SystemBoxModel from = portToBox.get(w.getSrcPort());
            SystemBoxModel to   = portToBox.get(w.getDstPort());
            ws.fromBoxId   = from != null ? from.getId() : null;
            ws.toBoxId     = to   != null ? to.getId()   : null;
            ws.fromOutIndex = (from != null) ? indexOfPort(from.getOutPorts(), w.getSrcPort()) : -1;
            ws.toInIndex    = (to   != null) ? indexOfPort(to.getInPorts(),   w.getDstPort()) : -1;
            // مسیر
            for (Point p : w.getPath().getPoints()) ws.path.add(new IntPoint(p.x, p.y));
            // پکت‌های روی سیم
            for (PacketModel p : w.getPackets()) {
                PacketOnWire pow = new PacketOnWire();
                pow.base = toPacketState(p);
                double prog = p.getProgress();
                if (prog >= 0.999) prog = 0.999; if (prog < 0) prog = 0;
                pow.progress = prog;
                ws.packetsOnWire.add(pow);
            }
            ws.largePassCount = w.getLargePacketPassCount();
            snap.world.wires.add(ws);
        }

        return snap;
    }


    public void restore(NetworkSnapshot snap) {
        if (snap == null) return;

        // Counters
        scoreModel.reset();   scoreModel.addPoints(snap.world.score);
        coinModel.reset();    coinModel.add(snap.world.coins);
        lossModel.reset();    if (snap.world.packetLoss > 0) lossModel.incrementBy(snap.world.packetLoss);

        // ✅ بازگردانی WireUsage – چون روی HUD و محدودیت‌ها اثر دارد
        usageModel.reset(snap.world.wireUsageTotal);
        if (snap.world.wireUsageUsed > 0) {
            usageModel.useWire(snap.world.wireUsageUsed);
        }

        // توقف/ریست تولیدکننده‌ها
        for (PacketProducerController p : producers) { p.reset(); p.stopProduction(); }

        // نگاشت id→Box
        Map<String, SystemBoxModel> idToBox = new HashMap<>();
        for (SystemBoxModel b : boxes) idToBox.put(b.getId(), b);

        // نگاشت Port→Box (برای setPortToBoxMap و fallbackهای دیگر)
        Map<PortModel, SystemBoxModel> portToBox = buildPortToBoxMap(this.boxes);

        // Box buffers
        for (BoxState bs : snap.world.boxes) {
            SystemBoxModel box = idToBox.get(bs.id);
            if (box == null) continue;
            box.clearBuffer();
            if (!bs.enabled) {
                if (bs.disableTimer > 1e-6) box.disableFor(bs.disableTimer);
                else box.disable();
            }
            for (PacketState ps : bs.bitBuffer)   box.enqueueFront(fromPacketState(ps));
            for (PacketState ps : bs.largeBuffer) box.enqueueFront((LargePacket) fromPacketState(ps));
        }

        // ✅ سیم‌ها را کاملاً طبق اسنپ‌شات «بازسازی» کن
        List<WireModel> rebuilt = new ArrayList<>();
        for (WireState ws : snap.world.wires) {
            SystemBoxModel from = idToBox.get(ws.fromBoxId);
            SystemBoxModel to   = idToBox.get(ws.toBoxId);
            if (from == null || to == null) continue;
            if (ws.fromOutIndex < 0 || ws.fromOutIndex >= from.getOutPorts().size()) continue;
            if (ws.toInIndex   < 0 || ws.toInIndex   >= to.getInPorts().size())   continue;

            PortModel src = from.getOutPorts().get(ws.fromOutIndex);
            PortModel dst = to.getInPorts().get(ws.toInIndex);

            WireModel wire = findWireByEndpoints(wires, src, dst);
            if (wire == null) {
                wire = new WireModel(src, dst);
            }

            // مسیر
            List<Point> pts = new ArrayList<>();
            for (IntPoint ip : ws.path) pts.add(new Point(ip.x, ip.y));
            if (pts.size() >= 2) wire.setPath(new WirePath(pts));

            // ست کردن نگاشت Port→Box روی خود Wire (برای getCanonicalId/fallbackها)
            wire.setPortToBoxMap(portToBox);

            // پکت‌های روی سیم
            wire.clearPackets();
            for (PacketOnWire pow : ws.packetsOnWire) {
                PacketModel pkt = fromPacketState(pow.base);
                boolean compatible = src.isCompatible(pkt);
                pkt.setStartSpeedMul(1.0);
                pkt.setMotionStrategy(MotionStrategyFactory.create(pkt, compatible));
                wire.attachPacket(pkt, pow.progress);
            }
            // Restore large pass count (durability window)
            if (ws.largePassCount > 0) {
                wire.resetLargePacketCounter();
                for (int i = 0; i < ws.largePassCount; i++)
                    wire.incrementLargePacketPass();
            }
            rebuilt.add(wire);

             destMap.put(wire, to);
        }

        // جایگزینی لیست سیم‌های زنده با لیست بازسازی‌شده
        wires.clear();
        wires.addAll(rebuilt);

        SwingUtilities.invokeLater(() -> {
            gameView.reset(boxes, wires);
            packetRenderer.refreshAll();
            hudView.setCoins(coinModel.getCoins());
            hudView.setPacketLoss(lossModel.getLostCount());
        });
    }

    public SnapshotManager getSnapshotManager() { return snapshotManager; }

    // ----------------- helpers (new) -----------------
    private static Map<PortModel, SystemBoxModel> buildPortToBoxMap(List<SystemBoxModel> boxes) {
        Map<PortModel, SystemBoxModel> map = new HashMap<>();
        for (SystemBoxModel b : boxes) {
            for (PortModel p : b.getInPorts())  map.put(p, b);
            for (PortModel p : b.getOutPorts()) map.put(p, b);
        }
        return map;
    }

    private static int indexOfPort(List<PortModel> list, PortModel p) {
        for (int i = 0; i < list.size(); i++) if (list.get(i) == p) return i;
        return -1;
    }

    private static WireModel findWireByEndpoints(List<WireModel> wires, PortModel src, PortModel dst) {
        for (WireModel w : wires) if (w.getSrcPort() == src && w.getDstPort() == dst) return w;
        return null;
    }

    private static PacketState toPacketState(PacketModel p) {
        PacketState ps = new PacketState();
        ps.type = p.getType() != null ? p.getType().name() : null;
        ps.speed = p.getSpeed();
        ps.acceleration = p.getAcceleration();
        ps.progress = p.getProgress();
        ps.noise = p.getNoise();
        ps.returning = p.isReturning();
        ps.collisionCooldown = p.getCollisionCooldown();
        ps.holdWhileCooldown = p.isHoldWhileCooldown();

        // خانواده و دیتای تخصصی
        if (PacketOps.isBit(p)) {
            ps.family = "BIT";
            BitPacket bp = (BitPacket) p;
            ps.groupId = bp.getGroupId();
            ps.parentSizeUnits = bp.getParentSizeUnits();
            ps.indexInGroup = bp.getIndexInGroup();
            ps.colorId = bp.getColorId();
            return ps;
        }
        if (p instanceof MergedPacket mp) {
            ps.family = "MERGED";
            ps.groupId = mp.getGroupId();
            ps.expectedBits = mp.getExpectedBits();
            ps.colorId = mp.getColorId();
            ps.parentSizeUnits = mp.getSizeUnits();
            return ps;
        }
        if (PacketOps.isLarge(p)) {
            ps.family = "LARGE";
            LargePacket lp = (LargePacket) p;
            ps.parentSizeUnits = lp.getOriginalSizeUnits();
            if (lp.hasGroup()) {
                ps.groupId = lp.getGroupId();
                ps.expectedBits = lp.getExpectedBits();
                ps.colorId = lp.getColorId();
            }
            ps.rebuiltFromBits = lp.isRebuiltFromBits();
            return ps;
        }
        if (PacketOps.isProtected(p) || p instanceof ProtectedPacket) {
            ps.family = "PROTECTED";
            if (p instanceof ProtectedPacket pp) ps.protectedShield = pp.getShield();
            return ps;
        }
        if (PacketOps.isConfidentialVpn(p)) {
            ps.family = "CONFIDENTIAL";
            ps.confidential = true; ps.confidentialVpn = true;
            return ps;
        }
        if (PacketOps.isConfidential(p) || p instanceof ConfidentialPacket) {
            ps.family = "CONFIDENTIAL";
            ps.confidential = true; ps.confidentialVpn = false;
            return ps;
        }
        if (PacketOps.isTrojan(p) || p instanceof TrojanPacket) {
            ps.family = "TROJAN";
            TrojanPacket tp = (p instanceof TrojanPacket) ? (TrojanPacket) p : (TrojanPacket) PacketOps.unwrapTrojan(p);
            if (tp != null && tp.getOriginal() != null) {
                ps.trojanOriginalFamily = "MESSENGER";
                ps.trojanOriginalType = tp.getOriginal().getType().name();
            }
            return ps;
        }
        ps.family = "MESSENGER";
        return ps;
    }

    private static PacketModel fromPacketState(PacketState ps) {
        PacketType t = ps.type != null ? PacketType.valueOf(ps.type) : PacketType.CIRCLE;
        PacketModel base = new PacketModel(t, Config.DEFAULT_PACKET_SPEED);
        base.setSpeed(ps.speed);
        base.setAcceleration(ps.acceleration);
        base.setProgress(ps.progress);
        base.setNoise(ps.noise);
        base.setReturning(ps.returning);
        base.setCollisionCooldown(ps.collisionCooldown);
        base.setHoldWhileCooldown(ps.holdWhileCooldown);

// خانواده
        if ("BIT".equals(ps.family)) {
            int gid   = ps.groupId != null ? ps.groupId : 0;
            int psize = ps.parentSizeUnits != null ? ps.parentSizeUnits : 0;
            int idx   = ps.indexInGroup != null ? ps.indexInGroup : 0;
            int col   = ps.colorId != null ? ps.colorId : 0;
            return BitPacket.fromSample(base, gid, psize, idx, col);
        }

        if ("MERGED".equals(ps.family)) {
            int gid = ps.groupId != null ? ps.groupId : 0;
            int exp = ps.expectedBits != null ? ps.expectedBits : 0;
            int col = ps.colorId != null ? ps.colorId : 0;
            int psize = ps.parentSizeUnits != null ? ps.parentSizeUnits : 0;
            return new MergedPacket(base.getType(), base.getBaseSpeed(), psize, gid, exp, col);
        }

        if ("LARGE".equals(ps.family)) {
            int psize = ps.parentSizeUnits != null ? ps.parentSizeUnits : 0;
            LargePacket lp = new LargePacket(base.getType(), base.getBaseSpeed(), psize);

            Integer gid = ps.groupId;
            Integer exp = ps.expectedBits;
            Integer col = ps.colorId;
            if (gid != null && exp != null && col != null) {
                lp.setGroupInfo(gid, exp, col);
            }
            if (Boolean.TRUE.equals(ps.rebuiltFromBits)) {
                lp.markRebuilt();
            }
            return lp;
        }

        if ("PROTECTED".equals(ps.family)) {
            double shield = (ps.protectedShield != null && ps.protectedShield > 0) ? ps.protectedShield : 1.0;
            return PacketOps.toProtected(base, shield);
        }

        if ("CONFIDENTIAL".equals(ps.family)) {
            boolean vpn = Boolean.TRUE.equals(ps.confidentialVpn);
            return vpn ? PacketOps.toConfidentialVpn(base) : PacketOps.toConfidential(base);
        }

        if ("TROJAN".equals(ps.family)) {
            PacketType orig = (ps.trojanOriginalType != null) ? PacketType.valueOf(ps.trojanOriginalType) : base.getType();
            return PacketOps.toTrojan(new PacketModel(orig, base.getBaseSpeed()));
        }

        return base;
    }
}

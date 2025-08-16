package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.systems.BehaviorRegistry;
import com.blueprinthell.controller.systems.SystemBehavior;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.BitPacket;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.model.large.MergedPacket;
import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.motion.MotionStrategyFactory;
import com.blueprinthell.snapshot.NetworkSnapshot;
import com.blueprinthell.snapshot.NetworkSnapshot.*;
import com.blueprinthell.view.HudView;
import com.blueprinthell.view.screens.GameScreenView;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.IntSupplier;

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
    private final Runnable networkChangedCallback;
    private final LargeGroupRegistry            largeGroupRegistry; // اضافه شده
    private final IntSupplier currentLevelSupplier;
    public SnapshotService(Map<WireModel, SystemBoxModel> destMap,
                           List<SystemBoxModel> boxes,
                           List<WireModel> wires,
                           ScoreModel scoreModel,
                           CoinModel coinModel,
                           PacketLossModel lossModel,
                           WireUsageModel usageModel,
                           SnapshotManager snapshotManager,
                           HudView hudView,
                           GameScreenView gameView,
                           PacketRenderController renderer,
                           List<PacketProducerController> producers,
                           Runnable networkChangedCallback,IntSupplier currentLevelSupplier,
                           LargeGroupRegistry largeGroupRegistry) { // اضافه شده
        this.destMap         = destMap;
        this.boxes           = boxes;
        this.wires           = wires;
        this.scoreModel      = scoreModel;
        this.coinModel       = coinModel;
        this.lossModel       = lossModel;
        this.usageModel      = usageModel;
        this.snapshotManager = snapshotManager;
        this.hudView         = hudView;
        this.gameView        = gameView;
        this.packetRenderer  = renderer;
        this.producers       = (producers == null) ? List.of() : producers;
        this.networkChangedCallback = networkChangedCallback;
        this.currentLevelSupplier = currentLevelSupplier;
        this.largeGroupRegistry = largeGroupRegistry;
    }

    public void capture() { snapshotManager.recordSnapshot(buildSnapshot()); }

    public NetworkSnapshot buildSnapshot() {
        // نگاشت Port→Box برای تشخیص اندپوینت‌ها و ایندکس‌ها
        Map<PortModel, SystemBoxModel> portToBox = buildPortToBoxMap(this.boxes);

        NetworkSnapshot snap = new NetworkSnapshot(scoreModel.getScore());
        try {
            int lvl = (currentLevelSupplier != null) ? currentLevelSupplier.getAsInt() : 1;
            if (lvl <= 0) lvl = 1;
            snap.meta.levelNumber = lvl;
        } catch (Exception ignore) { snap.meta.levelNumber = 1; }

// مجموع برنامه‌ریزی تولید برای محاسبهٔ progress (برای metadata)
        if (producers != null && !producers.isEmpty()) {
            PacketProducerController p = producers.get(0);
            snap.meta.producedUnits = p.getTotalToProduce();     // مخرج درصد
            // اگر لازم داری:
            // int producedCount = p.getProducedCount();         // صورت درصد (در متادیتا استفاده می‌کنیم)
        }
        // world counters
        snap.world.coins          = coinModel.getCoins();
        snap.world.packetLoss     = lossModel.getLostCount(); // این شامل deferred loss هم می‌شود
        snap.world.wireUsageTotal = usageModel.getTotalWireLength();
        snap.world.wireUsageUsed  = usageModel.getUsedWireLength();

        // -------------------- LARGE GROUP REGISTRY STATE --------------------
        // ذخیره حالت registry برای بازسازی صحیح loss بعد از rewind
        if (largeGroupRegistry != null) {
            snap.largeGroups.clear();
            for (LargeGroupRegistry.GroupSnapshot gs : largeGroupRegistry.snapshot()) {
                LargeGroupState lgs = new LargeGroupState();
                lgs.id = gs.id;
                lgs.originalSizeUnits = gs.originalSizeUnits;
                lgs.expectedBits = gs.expectedBits;
                lgs.colorId = gs.colorId;
                lgs.receivedBits = gs.receivedBits;
                lgs.mergedBits = gs.mergedBits;
                lgs.lostBits = gs.lostBits;
                lgs.closed = gs.closed;
                lgs.partialMerges.addAll(gs.partialMerges);
                snap.largeGroups.add(lgs);
            }
        }

        // -------------------- PRODUCERS (CAPTURE) --------------------
        snap.world.producers.clear();
        for (PacketProducerController p : producers) {
            ProducerState ps = new ProducerState();
            ps.packetsPerPort = p.getPacketsPerPort();
            ps.totalToProduce = p.getTotalToProduce();
            ps.producedCount  = p.getProducedCount();
            ps.inFlight       = p.getInFlight();
            ps.running        = p.isRunning();
            ps.accumulatorSec = p.getAccumulatorSec();

            // per-port counters keyed by (boxId, outIndex)
            Map<PortModel,Integer> map = p.getProducedPerPortView();
            for (PortModel out : p.getOutPorts()) {
                SystemBoxModel box = portToBox.get(out);
                if (box == null) continue;
                int outIndex = box.getOutPorts().indexOf(out);
                if (outIndex < 0) continue;
                PortQuota q = new PortQuota();
                q.boxId = box.getId();
                q.outIndex = outIndex;
                q.producedForThisPort = map.getOrDefault(out, 0);
                ps.portQuotas.add(q);
            }
            snap.world.producers.add(ps);
        }

        // Boxes
        for (SystemBoxModel b : boxes) {
            BoxState bs = new BoxState();
            bs.id           = b.getId();
            bs.primaryKind  = b.getPrimaryKind();
            bs.enabled      = b.isEnabled();
            try { bs.disableTimer = b.isEnabled() ? 0.0 : b.getDisableTimer(); } catch (Throwable ignore) {}
            bs.x = b.getX();
            bs.y = b.getY();
            bs.inShapes.addAll(b.getInShapes());
            bs.outShapes.addAll(b.getOutShapes());
            for (PacketModel p : b.getBitBuffer())    bs.bitBuffer.add(toPacketState(p));
            for (LargePacket lp : b.getLargeBuffer()) bs.largeBuffer.add(toPacketState(lp));

            // ذخیره returnBuffer - مهم!
            Deque<PacketModel> returnBuf = b.getReturnBuffer();
            if (returnBuf != null) {
                for (PacketModel p : returnBuf) {
                    bs.returnBuffer.add(toPacketState(p));
                }
            }

            snap.world.boxes.add(bs);
        }

        // Wires
        for (WireModel w : wires) {
            WireState ws = new WireState();
            SystemBoxModel from = portToBox.get(w.getSrcPort());
            SystemBoxModel to   = portToBox.get(w.getDstPort());
            ws.fromBoxId    = (from != null) ? from.getId() : null;
            ws.toBoxId      = (to   != null) ? to.getId()   : null;
            ws.fromOutIndex = (from != null) ? indexOfPort(from.getOutPorts(), w.getSrcPort()) : -1;
            ws.toInIndex    = (to   != null) ? indexOfPort(to.getInPorts(),   w.getDstPort())  : -1;

            // مسیر
            for (Point p : w.getPath().getPoints()) ws.path.add(new IntPoint(p.x, p.y));

            // پکت‌های روی سیم
            for (PacketModel p : w.getPackets()) {
                PacketOnWire pow = new PacketOnWire();
                pow.base = toPacketState(p);
                double prog = p.getProgress();
                if (prog >= 0.999) prog = 0.999;
                if (prog < 0) prog = 0;
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

        // -------------------- RESTORE LARGE GROUP REGISTRY --------------------
        // این باید قبل از restore کردن loss انجام شود
        if (largeGroupRegistry != null && snap.largeGroups != null) {
            List<LargeGroupRegistry.GroupSnapshot> registrySnapshots = new ArrayList<>();
            for (LargeGroupState lgs : snap.largeGroups) {
                LargeGroupRegistry.GroupSnapshot gs = new LargeGroupRegistry.GroupSnapshot(
                        lgs.id,
                        lgs.originalSizeUnits,
                        lgs.expectedBits,
                        lgs.colorId,
                        lgs.receivedBits,
                        lgs.mergedBits,
                        lgs.lostBits,
                        lgs.closed,
                        lgs.partialMerges
                );
                registrySnapshots.add(gs);
            }
            largeGroupRegistry.restore(registrySnapshots);
        }

        // Counters - حالا loss به درستی محاسبه می‌شود
        scoreModel.reset();
        scoreModel.addPoints(snap.world.score);
        coinModel.reset();
        coinModel.add(snap.world.coins);

        // مهم: immediateLoss را از snapshot restore می‌کنیم
        // deferred loss از registry محاسبه می‌شود
        lossModel.reset();

        // محاسبه immediate loss از snapshot
        // نکته: snap.world.packetLoss شامل total loss (immediate + deferred) است
        // باید immediate را جدا کنیم
        int totalLossInSnapshot = snap.world.packetLoss;
        int deferredLossInSnapshot = 0;

        // محاسبه deferred loss در زمان snapshot
        if (snap.largeGroups != null) {
            for (LargeGroupState lgs : snap.largeGroups) {
                if (lgs.closed) {
                    // محاسبه loss برای گروه بسته شده
                    int i = lgs.partialMerges.size();
                    if (i == 0) {
                        deferredLossInSnapshot += lgs.originalSizeUnits;
                    } else {
                        double product = 1.0;
                        for (int a : lgs.partialMerges) product *= a;
                        int recovered = (int) Math.floor(i * Math.sqrt(product));
                        deferredLossInSnapshot += Math.max(0, lgs.originalSizeUnits - recovered);
                    }
                }
            }
        }

        // immediate loss = total - deferred  (SET, don't add)
        int immediateLossToRestore = Math.max(0, totalLossInSnapshot - deferredLossInSnapshot);
        lossModel.restoreImmediateLoss(immediateLossToRestore);
// WireUsage روی HUD/محدودیت‌ها اثر دارد
        usageModel.reset(snap.world.wireUsageTotal);
        if (snap.world.wireUsageUsed > 0) usageModel.useWire(snap.world.wireUsageUsed);

        // تولیدکننده‌ها را متوقف کن (بدون reset)
        for (PacketProducerController p : producers) p.stopProduction();

        // نگاشت‌ها
        Map<String, SystemBoxModel> idToBox = new HashMap<>();
        for (SystemBoxModel b : boxes) idToBox.put(b.getId(), b);
        Map<PortModel, SystemBoxModel> portToBox = buildPortToBoxMap(this.boxes);

        // -------------------- PRODUCERS (RESTORE) --------------------
        if (snap.world != null && snap.world.producers != null) {
            int n = Math.min(producers.size(), snap.world.producers.size());
            for (int i = 0; i < n; i++) {
                PacketProducerController p = producers.get(i);
                ProducerState st = snap.world.producers.get(i);

                // بازسازی map پر-پورت از روی (boxId,outIndex)
                Map<PortModel,Integer> perPort = new HashMap<>();
                if (st.portQuotas != null) {
                    for (PortQuota q : st.portQuotas) {
                        SystemBoxModel box = idToBox.get(q.boxId);
                        if (box == null) continue;
                        List<PortModel> outs = box.getOutPorts();
                        if (q.outIndex < 0 || q.outIndex >= outs.size()) continue;
                        PortModel out = outs.get(q.outIndex);
                        perPort.put(out, Math.max(0, q.producedForThisPort));
                    }
                }

                p.restoreFrom(
                        st.producedCount,
                        st.inFlight,
                        st.accumulatorSec,
                        false, // همیشه false - بعداً در countdown start می‌شود
                        perPort
                );
            }
        }

        // Box buffers
        boolean hasBoxXY = (snap.meta != null && "model-v3".equals(snap.meta.schemaVersion));
        for (BoxState bs : snap.world.boxes) {
            SystemBoxModel box = idToBox.get(bs.id);
            if (box == null) continue;
            if (hasBoxXY) {
                box.setX(bs.x);
                box.setY(bs.y);
            }
            box.clearBuffer();
            if (!bs.enabled) {
                if (bs.disableTimer > 1e-6) box.disableFor(bs.disableTimer);
                else box.disable();
            }
            for (PacketState ps : bs.bitBuffer)   box.enqueueBitSilently(fromPacketState(ps));
            for (PacketState ps : bs.largeBuffer) box.enqueueLargeSilently((LargePacket) fromPacketState(ps));
        }

        // بازسازی کامل سیم‌ها طبق snapshot
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
            if (wire == null) wire = new WireModel(src, dst);

            // مسیر
            List<Point> pts = new ArrayList<>();
            for (IntPoint ip : ws.path) pts.add(new Point(ip.x, ip.y));
            if (pts.size() >= 2) wire.setPath(new WirePath(pts));

            // نگاشت Port→Box برای wire
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

            // large pass count
            if (ws.largePassCount > 0) {
                wire.resetLargePacketCounter();
                for (int i = 0; i < ws.largePassCount; i++) wire.incrementLargePacketPass();
            }

            rebuilt.add(wire);
            destMap.put(wire, to);
        }

        // جایگزینی لیست سیم‌های زنده با لیست بازسازی‌شده
        wires.clear();
        wires.addAll(rebuilt);

        // --- Fixup: rebind orphan LargePackets to existing open groups (by color & size) ---
        if (largeGroupRegistry != null) {
            for (WireModel w : wires) {
                for (PacketModel p : w.getPackets()) {
                    if (p instanceof LargePacket lp) {
                        int gid = lp.getGroupId();
                        LargeGroupRegistry.GroupState st = (gid > 0) ? largeGroupRegistry.get(gid) : null;
                        if (st == null || st.isClosed()) {
                            Integer adopt = largeGroupRegistry.findOpenGroupByColorAndSize(lp.getColorId(), lp.getOriginalSizeUnits());
                            if (adopt != null) {
                                lp.setGroupInfo(adopt, lp.getExpectedBits(), lp.getColorId());
                            }
                        }
                    }
                }
            }
        }
        SwingUtilities.invokeLater(() -> {
            gameView.reset(boxes, wires);
            packetRenderer.refreshAll();
            gameView.rebuildControllers(wires, usageModel, coinModel, networkChangedCallback);
            hudView.setCoins(coinModel.getCoins());
            hudView.setPacketLoss(lossModel.getLostCount()); // حالا این مقدار صحیح خواهد بود
        });
    }

    // ... بقیه متدها بدون تغییر ...

    public SnapshotManager getSnapshotManager() { return snapshotManager; }

    // ----------------- helpers -----------------
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
        ps.type = (p.getType() != null) ? p.getType().name() : null;
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

            // ⭐ همیشه groupId را ذخیره کن، حتی اگر -1 باشد
            ps.groupId = lp.getGroupId();
            ps.expectedBits = lp.getExpectedBits();
            ps.colorId = lp.getColorId();

            // رنگ دقیق را هم ذخیره کن
            Color c = lp.getCustomColor();
            if (c != null) ps.customRgb = c.getRGB();

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
        PacketType t = (ps.type != null) ? PacketType.valueOf(ps.type) : PacketType.CIRCLE;
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
            int gid   = (ps.groupId != null) ? ps.groupId : 0;
            int psize = (ps.parentSizeUnits != null) ? ps.parentSizeUnits : 0;
            int idx   = (ps.indexInGroup != null) ? ps.indexInGroup : 0;
            int col   = (ps.colorId != null) ? ps.colorId : 0;
            return BitPacket.fromSample(base, gid, psize, idx, col);
        }
        if ("MERGED".equals(ps.family)) {
            int gid   = (ps.groupId != null) ? ps.groupId : 0;
            int exp   = (ps.expectedBits != null) ? ps.expectedBits : 0;
            int col   = (ps.colorId != null) ? ps.colorId : 0;
            int psize = (ps.parentSizeUnits != null) ? ps.parentSizeUnits : 0;
            return new MergedPacket(base.getType(), base.getBaseSpeed(), psize, gid, exp, col);
        }





        if ("LARGE".equals(ps.family)) {
            int psize = (ps.parentSizeUnits != null) ? ps.parentSizeUnits : 0;
            LargePacket lp = new LargePacket(base.getType(), base.getBaseSpeed(), psize);

            // ⭐ همیشه groupId را بازیابی کن
            Integer gid = ps.groupId != null ? ps.groupId : -1;
            Integer exp = ps.expectedBits != null ? ps.expectedBits : psize;
            Integer col = ps.colorId != null ? ps.colorId : 0;

            lp.setGroupInfo(gid, exp, col);

            if (ps.customRgb != null) {
                lp.setCustomColor(new Color(ps.customRgb, false));
            }

            if (Boolean.TRUE.equals(ps.rebuiltFromBits)) lp.markRebuilt();
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

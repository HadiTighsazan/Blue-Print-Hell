package com.blueprinthell.controller.pvp;

import com.blueprinthell.shared.protocol.NetworkProtocol.*;
import com.blueprinthell.view.PacketView;
import com.blueprinthell.view.pvp.PvPMatchView;
import com.blueprinthell.view.screens.GameScreenView;

import javax.swing.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * کنترلر سمت کلاینت برای مدیریت UI و رندر کردن وضعیت بازی PvP.
 * این کلاس هیچ منطق شبیه‌سازی ندارد و فقط داده‌های دریافتی از سرور را نمایش می‌دهد.
 */
public class PvPGameController {

    private final GameScreenView gameView;
    private final PvPMatchView matchView;
    private final Map<String, PacketView> renderedPackets = new HashMap<>();
    // ### START OF PATCH 15 ###
    private final Map<String, WireLayout> allWiresById = new HashMap<>();
    private final int playerSide;
    // ### END OF PATCH 15 ###

    // ### START OF PATCH 16 ###
    public PvPGameController(GameScreenView gameView, PvPMatchView matchView,
                             List<WireLayout> ownWires, List<WireLayout> opponentWires, int playerSide) {
        this.gameView = gameView;
        this.matchView = matchView;
        this.playerSide = playerSide;

        // تمام سیم‌های بازی را برای دسترسی سریع در یک Map ذخیره می‌کنیم
        if (ownWires != null) {
            for (WireLayout wire : ownWires) {
                allWiresById.put(wire.id, wire);
            }
        }
        if (opponentWires != null) {
            for (WireLayout wire : opponentWires) {
                allWiresById.put(wire.id, wire);
            }
        }
    }
    // ### END OF PATCH 16 ###

    /**
     * این متد توسط PvPClientController فراخوانی می‌شود تا UI را بر اساس آخرین وضعیت دریافتی از سرور به‌روز کند.
     */
    public void updateState(PvPStateSnapshot snapshot) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> render(snapshot));
        } else {
            render(snapshot);
        }
    }

    private void render(PvPStateSnapshot snapshot) {
        // ۱. به‌روزرسانی UI اصلی مسابقه (امتیازات، مهمات و...)
        matchView.updateScores(snapshot.scoreP1.delivered, snapshot.scoreP1.lost, snapshot.scoreP2.delivered, snapshot.scoreP2.lost);
        int myAmmo = (playerSide == 1) ? snapshot.scoreP1.ammo : snapshot.scoreP2.ammo;
        matchView.updateAmmo(myAmmo);
        matchView.updateSpeedMultiplier(snapshot.globalSpeedMultiplier);
        // ### START OF PATCH 17 ###
        // ارسال وضعیت cooldownها به ویو
        if (snapshot.systems != null) {
            matchView.updateSystemCooldowns(snapshot.systems, playerSide);
        }
        // ### END OF PATCH 17 ###

        // ۲. رندر کردن پکت‌ها
        JComponent gameArea = gameView.getGameArea();
        Set<String> activePacketIds = new HashSet<>();

        for (PvPStateSnapshot.PacketState packetState : snapshot.packets) {
            activePacketIds.add(packetState.id);
            PacketView packetView = renderedPackets.get(packetState.id);

            // اگر پکت جدید است، آن را ایجاد کن
            if (packetView == null) {
                com.blueprinthell.model.PacketModel renderModel = new com.blueprinthell.model.PacketModel(
                        com.blueprinthell.model.PacketType.valueOf(packetState.type), 100);

                packetView = new PacketView(renderModel);
                renderedPackets.put(packetState.id, packetView);
                gameArea.add(packetView);
            }

            // ### START OF PATCH 18 ###
            // به‌روزرسانی موقعیت دقیق پکت
            WireLayout wire = allWiresById.get(packetState.wireId);
            if (wire != null) {
                // محاسبه موقعیت دقیق با استفاده از کلاس کمکی که منطق WirePhysics را شبیه‌سازی می‌کند
                WireLayout.Point2D pos = ClientWirePathHelper.pointAt(wire, packetState.progress);
                packetView.getModel().setX(pos.x - packetView.getWidth() / 2);
                packetView.getModel().setY(pos.y - packetView.getHeight() / 2);
            } else {
                // Fallback: اگر سیم پیدا نشد، از مختصات سرور استفاده کن
                packetView.getModel().setX(packetState.x - packetView.getWidth() / 2);
                packetView.getModel().setY(packetState.y - packetView.getHeight() / 2);
            }
            // ### END OF PATCH 18 ###
            packetView.refreshView();
        }

        // ۳. حذف پکت‌هایی که دیگر در بازی نیستند
        Set<String> toRemove = new HashSet<>(renderedPackets.keySet());
        toRemove.removeAll(activePacketIds);

        for (String packetId : toRemove) {
            PacketView packetView = renderedPackets.remove(packetId);
            if (packetView != null) {
                gameArea.remove(packetView);
            }
        }

        gameArea.revalidate();
        gameArea.repaint();
    }

    /**
     * پاکسازی تمام عناصر بصری مربوط به بازی PvP از صفحه.
     */
    public void cleanup() {
        JComponent gameArea = gameView.getGameArea();
        for (PacketView packetView : renderedPackets.values()) {
            gameArea.remove(packetView);
        }
        renderedPackets.clear();
        allWiresById.clear();
        gameArea.revalidate();
        gameArea.repaint();
    }

    // ### START OF PATCH 19 ###
    /**
     * کلاس کمکی استاتیک برای محاسبات هندسی مسیر سیم در کلاینت.
     * این کلاس منطق WirePhysics و WirePathHelper سرور را بازسازی می‌کند.
     */
    private static final class ClientWirePathHelper {
        public static double length(WireLayout wire) {
            if (wire == null || wire.path == null || wire.path.size() < 2) return 0.0;
            double len = 0.0;
            List<WireLayout.Point2D> pts = wire.path;
            for (int i = 1; i < pts.size(); i++) {
                len += distance(pts.get(i - 1), pts.get(i));
            }
            return len;
        }

        public static WireLayout.Point2D pointAt(WireLayout wire, double progress) {
            List<WireLayout.Point2D> pts = wire.path;
            if (progress <= 0) return pts.get(0);
            if (progress >= 1) return pts.get(pts.size() - 1);

            double totalLength = length(wire);
            if (totalLength == 0) return pts.get(0);

            double targetDist = progress * totalLength;
            double traversed = 0.0;

            for (int i = 1; i < pts.size(); i++) {
                WireLayout.Point2D p1 = pts.get(i - 1);
                WireLayout.Point2D p2 = pts.get(i);
                double segmentLength = distance(p1, p2);

                if (traversed + segmentLength >= targetDist) {
                    double localProgress = (segmentLength == 0) ? 0 : (targetDist - traversed) / segmentLength;
                    int x = (int) Math.round(p1.x + localProgress * (p2.x - p1.x));
                    int y = (int) Math.round(p1.y + localProgress * (p2.y - p1.y));
                    return new WireLayout.Point2D(x, y);
                }
                traversed += segmentLength;
            }
            return pts.get(pts.size() - 1);
        }

        private static double distance(WireLayout.Point2D p1, WireLayout.Point2D p2) {
            double dx = p1.x - p2.x;
            double dy = p1.y - p2.y;
            return Math.sqrt(dx * dx + dy * dy);
        }
    }
    // ### END OF PATCH 19 ###
}
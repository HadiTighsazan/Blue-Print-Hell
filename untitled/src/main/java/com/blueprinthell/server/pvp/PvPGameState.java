package com.blueprinthell.server.pvp;

import com.blueprinthell.shared.protocol.NetworkProtocol.PlayerScore;
import com.blueprinthell.shared.protocol.NetworkProtocol.SystemLayout;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * کلاس مرکزی برای نگهداری تمام وضعیت‌های یک بازی PvP.
 * این کلاس در سرور مدیریت شده و در هر تیک شبیه‌سازی به‌روز می‌شود.
 */
public class PvPGameState {

    // وضعیت کلی بازی
    public String matchId;
    public MessengerGameSimulation.NetworkLayout layoutP1;
    public MessengerGameSimulation.NetworkLayout layoutP2;

    // وضعیت بازیکنان
    public final PlayerScore scoreP1 = new PlayerScore();
    public final PlayerScore scoreP2 = new PlayerScore();

    // وضعیت پکت‌های فعال در بازی
    public final Map<String, MessengerGameSimulation.SimPacket> activePackets = new ConcurrentHashMap<>();

    // وضعیت سیستم‌های کنترل‌پذیر (cooldowns, ammo)
    public final Map<String, SystemState> systemStates = new ConcurrentHashMap<>();

    // وضعیت جریمه‌ها
    public double globalSpeedMultiplier = 1.0;
    public double cooldownMultiplierP1 = 1.0;
    public double cooldownMultiplierP2 = 1.0;

    public PvPGameState(String matchId, MessengerGameSimulation.NetworkLayout p1, MessengerGameSimulation.NetworkLayout p2) {
        this.matchId = matchId;
        this.layoutP1 = p1;
        this.layoutP2 = p2;
        initializeSystemStates();
    }

    private void initializeSystemStates() {
        // افزودن سیستم‌های بازیکن اول
        if (layoutP1 != null && layoutP1.layout != null && layoutP1.layout.boxes != null) {
            for (SystemLayout box : layoutP1.layout.boxes) {
                if (!box.isSource && !box.isSink) {
                    systemStates.put(box.id, new SystemState(box.id));
                }
            }
        }
        // افزودن سیستم‌های بازیکن دوم
        if (layoutP2 != null && layoutP2.layout != null && layoutP2.layout.boxes != null) {
            for (SystemLayout box : layoutP2.layout.boxes) {
                if (!box.isSource && !box.isSink) {
                    // ممکن است سیستم مشترک باشد، پس از computeIfAbsent استفاده می‌کنیم
                    systemStates.computeIfAbsent(box.id, id -> new SystemState(id));
                }
            }
        }
    }

    // کلاس داخلی برای نگهداری وضعیت هر سیستم
    public static class SystemState {
        public final String id;
        public int ammoP1 = 3; // مهمات اولیه
        public int ammoP2 = 3; // مهمات اولیه
        public long systemCooldownUntil = 0; // زمان پایان cooldown به میلی‌ثانیه
        public long packetCooldownUntilP1 = 0;
        public long packetCooldownUntilP2 = 0;

        public SystemState(String id) {
            this.id = id;
        }
    }

    // ### START OF PATCH 5 ###
    /**
     * نمایش ساده شده یک پکت در شبیه‌سازی سرور.
     * مختصات x و y اضافه شده است.
     */
    static class SimPacket {
        String id;
        String wireId;
        int playerSide;
        double progress; // 0.0 to 1.0
        double wireLength;
        double noise;
        String type;
        // مختصات دقیق پکت برای تشخیص برخورد
        int x;
        int y;
    }
    // ### END OF PATCH 5 ###
}
package com.blueprinthell.ui;

import com.blueprinthell.engine.NetworkController;
import com.blueprinthell.engine.TimelineController;
import com.blueprinthell.model.Packet;
import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.Port;
import com.blueprinthell.model.SystemBox;
import com.blueprinthell.model.Wire;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * GameController — منطق بازی و حلقهٔ اصلی را از GameScreen جدا می‌کند.
 * <p>
 * این کلاس هیچ عنصر گرافیکی ندارد؛ تمام آپدیت‌های نمایشی از طریق
 * callbackهای تزریق‌شده انجام می‌شود.
 */
public class GameController {

    /* ======================= وابستگی‌ها ======================= */
    private final NetworkController netCtrl;
    private final TimelineController timeline;
    private final List<Port> originPorts;
    private final List<SystemBox> systems;

    /* ======================= پیکربندی لِول ==================== */
    private final int totalPackets;
    private boolean generationDone = false;

    /* ======================= callbackها ======================= */
    private final Runnable onUpdate;        // هر فریم برای آپدیت View/HUD
    private final Runnable onImpactSound;   // وقتی پکت در مبدا بدون Wire ازدست‌رفت
    private final Runnable onGameOver;      // وقتی باخت
    private final Runnable onMissionPassed; // وقتی برد

    /* ======================= تایمرها ========================= */
    private Timer releaseTimer; // پخش پکت‌ها در ابتدای بازی
    private Timer gameTimer;    // حلقهٔ اصلی ~60fps

    public GameController(NetworkController netCtrl,
                          TimelineController timeline,
                          List<Port> originPorts,
                          List<SystemBox> systems,
                          int totalPackets,
                          Runnable onUpdate,
                          Runnable onImpactSound,
                          Runnable onGameOver,
                          Runnable onMissionPassed) {
        this.netCtrl = netCtrl;
        this.timeline = timeline;
        this.originPorts = originPorts;
        this.systems = systems;
        this.totalPackets = totalPackets;
        this.onUpdate = onUpdate;
        this.onImpactSound = onImpactSound;
        this.onGameOver = onGameOver;
        this.onMissionPassed = onMissionPassed;
    }

    public void resetMissionFlag() {
        // اگر هنوز بسته‌ها کامل رها نشده‌اند تغییری نده، وگرنه دوباره اجازه می‌دهیم حلقه مأموریت را ارزیابی کند.
        generationDone = false;
    }

    /* ======================= Start/Pause/Resume ============== */
    public void start() {
        generationDone = false;
        buildReleaseTimer();
        buildGameTimer();
        releaseTimer.start();
        gameTimer.start();
        timeline.resume();
    }

    public void pause() {
        if (releaseTimer != null) releaseTimer.stop();
        if (gameTimer != null) gameTimer.stop();
        timeline.pause();
    }
    public void resetGenerationFlag() {
        if (releaseTimer != null) releaseTimer.stop();
        restartReleaseTimerFromState();    // ✨ چرخه از جای درست ادامه مى‌دهد
    }



    public void resume() {
        timeline.resume();
        if (releaseTimer != null) releaseTimer.start();
        if (gameTimer    != null) gameTimer.start();
    }




    private void buildReleaseTimer() {
        final int cycles = 3; // تعداد دفعات رهاسازی
        final Random rand = new Random();
        releaseTimer = new Timer(2000, null);
        releaseTimer.setInitialDelay(0);
        releaseTimer.addActionListener(new ActionListener() {
            int cycleCount = 0;
            @Override public void actionPerformed(ActionEvent e) {
                if (cycleCount >= cycles) {
                    releaseTimer.stop();
                    generationDone = true;
                    return;
                }
                for (Port out : originPorts) {
                    Wire w = netCtrl.getWires().stream()
                            .filter(x -> x.getSrcPort() == out)
                            .findFirst().orElse(null);
                    PacketType type = rand.nextBoolean() ? PacketType.SQUARE : PacketType.TRIANGLE;
                    if (w != null) {
                        Packet p = new Packet(type, 100);
                        w.attachPacket(p, 0.0);
                    } else {
                        netCtrl.incrementPacketLoss();
                        if (onImpactSound != null) onImpactSound.run();
                    }
                }
                cycleCount++;
            }
        });
    }

    /* ======================= حلقه اصلی بازی =================== */
    private void buildGameTimer() {
        gameTimer = new Timer(16, e -> {
            if (!timeline.isPlaying()) return; // در حالت scrub یا pause کاری نکن

            // فیزیک شبکه
            netCtrl.tick(0.016);
            timeline.recordFrame();

            // آپدیت نمای گرافیکی و HUD
            if (onUpdate != null) onUpdate.run();

            // بررسی شرایط باخت
            if (netCtrl.getPacketLoss() * 2 >= totalPackets) {
                stopAll();
                if (onGameOver != null) onGameOver.run();
                return;
            }

            // بررسی شرایط برد
            if (generationDone && allPacketsProcessed()) {
                stopAll();
                if (onMissionPassed != null) onMissionPassed.run();
            }
        });
    }

    /* ======================= وضعیت پکت‌ها ====================== */
    private boolean allPacketsProcessed() {
        boolean noInFlight = netCtrl.getPackets().isEmpty();
        boolean noInBuffer = systems.stream()
                .filter(s -> !s.getOutPorts().isEmpty())
                .flatMap(s -> s.getBuffer().stream())
                .findAny().isEmpty();
        return noInFlight && noInBuffer;
    }

    /* ======================= توقف همه چیز ===================== */
    void stopAll() {
        if (releaseTimer != null) releaseTimer.stop();
        if (gameTimer != null) gameTimer.stop();
        timeline.pause();
    }
    private int releasedCycles() {
        int released = netCtrl.getPackets().size()                     // داخل شبکه
                + systems.stream()                               // در بافر سیستم‌ها
                .flatMap(s -> s.getBuffer().stream())
                .collect(Collectors.toList()).size()
                + netCtrl.getPacketLoss();                       // گمشده‌ها
        return released / originPorts.size();   // عدد صحیحِ چرخه‌های انجام‌شده
    }
    private void restartReleaseTimerFromState() {
        int startCycle = releasedCycles();      // چند چرخه قبلاً انجام شده؟
        final int cycles = 3;
        final Random rnd = new Random();

        if (startCycle >= cycles) {     // کار انتشار تمام شده
            generationDone = true;
            releaseTimer = null;
            return;
        }

        generationDone = false;
        releaseTimer = new Timer(2000, null);
        releaseTimer.setInitialDelay(0);
        releaseTimer.addActionListener(new ActionListener() {
            int cycleCount = startCycle;            // ✨ از چرخهٔ درست شروع کن
            @Override public void actionPerformed(ActionEvent e) {
                if (cycleCount >= cycles) { releaseTimer.stop(); generationDone = true; return; }
                for (Port out : originPorts) {
                    Wire w = netCtrl.getWires().stream().filter(x -> x.getSrcPort()==out).findFirst().orElse(null);
                    PacketType t = rnd.nextBoolean()? PacketType.SQUARE : PacketType.TRIANGLE;
                    if (w!=null) w.attachPacket(new Packet(t,100),0.0);
                    else { netCtrl.incrementPacketLoss(); onImpactSound.run(); }
                }
                cycleCount++;
            }
        });
        releaseTimer.start();
    }


}

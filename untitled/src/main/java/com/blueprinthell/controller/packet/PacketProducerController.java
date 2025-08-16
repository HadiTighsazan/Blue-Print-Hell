package com.blueprinthell.controller.packet;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.motion.KinematicsProfile;
import com.blueprinthell.motion.KinematicsRegistry;
import com.blueprinthell.motion.MotionStrategy;
import com.blueprinthell.motion.MotionStrategyFactory;

import java.awt.*;
import java.util.*;
import java.util.List;

public class PacketProducerController implements Updatable {

    private static final Random RND = new Random();
    private static final double INTERVAL_SEC = 0.4;

    private final List<SystemBoxModel> sourceBoxes;
    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destMap;
    private final double baseSpeed;
    private final int packetsPerPort;
    private final int totalToProduce;

    private double acc = 0.0;
    private boolean running = false;
    private int producedCount = 0;

    // --- تغییر: از inFlight برای شمارش پکت‌های درحال حرکت استفاده می‌کنیم
    private int inFlight = 0;
    private int producedUnits = 0;

    public int getProducedUnits() {
        return producedUnits;
    }
    // --- توجه: فیلد قبلی returnedCredits حذف نشده، اما دیگر استفاده نمی‌شود
    @SuppressWarnings("unused")
    private int returnedCredits = 0;

    // --- تغییر: شمارش تولید به‌ازای هر پورت برای enforce کردن packetsPerPort
    private final Map<PortModel, Integer> producedPerPort = new HashMap<>();

    public PacketProducerController(List<SystemBoxModel> sourceBoxes,
                                    List<WireModel> wires,
                                    Map<WireModel, SystemBoxModel> destMap,
                                    double baseSpeed,
                                    int packetsPerPort) {
        this.sourceBoxes = sourceBoxes;
        this.wires = wires;
        this.destMap = destMap;
        this.baseSpeed = baseSpeed;
        this.packetsPerPort = packetsPerPort;
        int outs = sourceBoxes.stream().mapToInt(b -> b.getOutPorts().size()).sum();
        this.totalToProduce = outs * packetsPerPort;
    }

    public void startProduction() { running = true; }
    public void stopProduction()  { running = false; }

    public void reset() {
        running = false;
        acc = 0.0;

        // --- تغییر: ریست شمارنده‌ها برای راند جدید
        producedCount = 0;
        inFlight = 0;
        producedPerPort.clear();
        // returnedCredits را دست‌نخورده می‌گذاریم تا حذف فیلد نداشته باشیم
    }

    // --- تغییر: این متد حالا وقتی پکتی برمی‌گرده، inFlight را کم می‌کند
    public void onPacketReturned() {
        if (inFlight > 0) inFlight--;
    }




    @Override
    public void update(double dt) {
        if (!running || isFinished()) return;
        acc += dt;
        while (acc >= INTERVAL_SEC && !isFinished()) {
            acc -= INTERVAL_SEC;
            emitOnce();
        }
        if (isFinished()) running = false;
    }

    private void emitOnce() {
        for (SystemBoxModel box : sourceBoxes) {
            if (!box.getInPorts().isEmpty()) {
                continue;
            }
            for (PortModel out : box.getOutPorts()) {
                int producedForThisPort = producedPerPort.getOrDefault(out, 0);
                if (producedForThisPort >= packetsPerPort) {
                    continue;
                }
                if (producedCount >= totalToProduce) {
                    break;
                }

                wires.stream()
                        .filter(w -> w.getSrcPort() == out)
                        .findFirst()
                        .ifPresent(wire -> {
                            PacketModel packet;

                            // ابتدا پکت پایه را بسازید
                            if (out.getShape() == PortShape.CIRCLE) {
                                if (RND.nextInt(10) < 10) {
                                    packet = createLargePacketForPort(out.getType(), baseSpeed);
                                } else {
                                    packet = new PacketModel(PacketType.CIRCLE, baseSpeed);
                                }
                            } else {
                                if (RND.nextInt(10) < 0) {
                                    packet = createLargePacketForPort(out.getType(), baseSpeed);
                                } else {
                                    packet = new PacketModel(randomType(), baseSpeed);
                                }
                            }

                            // حالا اگر می‌خواهید، آن را به محرمانه تبدیل کنید
                            // این کار باید بعد از ساخت پکت پایه انجام شود
                            if (RND.nextInt(10) < 0) { // برای تست، همیشه محرمانه
                                packet = PacketOps.toConfidential(packet);
                            }

                            // تنظیم سرعت اولیه و پیکربندی استراتژی حرکت
                            packet.setStartSpeedMul(1.0);
                            boolean compatible = wire.getSrcPort().isCompatible(packet);
                            MotionStrategy ms = MotionStrategyFactory.create(packet, compatible);
                            packet.setMotionStrategy(ms);

                            // چسباندن پکت به سیم خروجی
                            wire.attachPacket(packet, 0);

                            // به‌روزرسانی شمارنده‌ها
                            producedCount++;
                            inFlight++;
                            producedPerPort.put(out, producedForThisPort + 1);

                            if (packet instanceof LargePacket lp) {
                                producedUnits += lp.getOriginalSizeUnits();
                            } else {
                                producedUnits++;
                            }
                        });
            }
        }
    }
    private LargePacket createLargePacketForPort(PacketType portType, double baseSpeed) {
        int units = (RND.nextBoolean() ? Config.LARGE_PACKET_SIZE_8 : Config.LARGE_PACKET_SIZE_10);

        // تولید colorId تصادفی
        int colorId = RND.nextInt(360);
        Color color = Color.getHSBColor(colorId / 360.0f, 0.8f, 0.9f);

        LargePacket lp = new LargePacket(portType, baseSpeed, units);

        // تنظیم سایز ویژوال
        int visualSize = units * Config.PACKET_SIZE_MULTIPLIER;
        lp.setWidth(visualSize);
        lp.setHeight(visualSize);

        // تنظیم رنگ و colorId
        lp.setCustomColor(color);
        lp.setGroupInfo(-1, units, colorId);  // اضافه کردن این خط مهم است

        KinematicsRegistry.setProfile(
                lp,
                (units == Config.LARGE_PACKET_SIZE_8) ? KinematicsProfile.LARGE_8 : KinematicsProfile.LARGE_10
        );

        return lp;
    }

    private PacketType randomType() {
        int r = RND.nextInt(3);
        return (r == 0) ? PacketType.SQUARE
                : (r == 1) ? PacketType.TRIANGLE
                : PacketType.CIRCLE;
    }
    // فایل: untitled/src/main/java/com/blueprinthell/controller/PacketProducerController.java
// اضافه کردن متدهای جدید:

    public void onPacketConsumed() {
        if (inFlight > 0) inFlight--;
    }

    public void onPacketLost() {
        if (inFlight > 0) inFlight--;
    }

    // همچنین در متد isFinished() اضافه کنید یک لاگ برای دیباگ:
    public boolean isFinished() {
        boolean finished = producedCount >= totalToProduce && inFlight == 0;
        // برای دیباگ:
        if (!finished && producedCount >= totalToProduce) {
        }
        return finished;
    }
    public int getPacketsPerPort()      { return packetsPerPort; }
    public int getTotalToProduce()      { return totalToProduce; }
    public int getProducedCount()       { return producedCount; }
    public int getInFlight()            { return inFlight; }
    public boolean isRunning()          { return running; }


    /** Emission accumulator (seconds) to preserve emission cadence. */
    public double getAccumulatorSec()   { return acc; }
    /** Unmodifiable view of per-port produced counters. */
    public Map<PortModel,Integer> getProducedPerPortView() {
        return Collections.unmodifiableMap(new HashMap<>(producedPerPort));
    }

        /** Aggregate list of all out-ports of all source boxes. */
        public List<PortModel> getOutPorts() {
                List<PortModel> outs = new ArrayList<>();
                for (SystemBoxModel b : sourceBoxes) outs.addAll(b.getOutPorts());
                return outs;
            }
    // -------------------- SNAPSHOT RESTORE --------------------
      public void restoreFrom(int producedCount,
                            int inFlight,
                            double accumulatorSec,
                            boolean running,
                            Map<PortModel,Integer> perPortProduced) {
                // variable counters
                        this.producedCount  = Math.min(Math.max(0, producedCount), this.totalToProduce);
                this.inFlight       = Math.max(0, inFlight);
                this.acc            = Math.max(0.0, accumulatorSec);

                        // rebuild per-port counters
                                this.producedPerPort.clear();
                if (perPortProduced != null) {
                        for (Map.Entry<PortModel,Integer> e : perPortProduced.entrySet()) {
                                int v = (e.getValue() == null) ? 0 : e.getValue();
                                v = Math.max(0, Math.min(this.packetsPerPort, v)); // clamp by current final packetsPerPort
                                if (e.getKey() != null) this.producedPerPort.put(e.getKey(), v);
                            }
                    }

                        // only allow running if there's still budget left
                                this.running = running && (this.producedCount < this.totalToProduce);
            }

}


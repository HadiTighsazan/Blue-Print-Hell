package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.motion.KinematicsProfile;
import com.blueprinthell.motion.KinematicsRegistry;
import com.blueprinthell.motion.MotionStrategy;
import com.blueprinthell.motion.MotionStrategyFactory;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.HashMap;

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

    public boolean isFinished() {
        // --- تغییر: پایان وقتی همه تولید شده و هیچ پکتی در پرواز نیست
        return producedCount >= totalToProduce && inFlight == 0;
    }

    public int getProducedCount() { return producedCount; }
    public int getPacketsPerPort() { return packetsPerPort; }

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

                // --- تغییر: محدودیت per-port
                int producedForThisPort = producedPerPort.getOrDefault(out, 0);
                if (producedForThisPort >= packetsPerPort) {
                    continue; // این پورت سهمش را کامل تولید کرده
                }

                // --- محافظ اضافه: اگر سقف کلی پر شده دیگر تولید نکن
                if (producedCount >= totalToProduce) {
                    break;
                }

                wires.stream()
                        .filter(w -> w.getSrcPort() == out)
                        .findFirst()
                        .ifPresent(wire -> {
                            // تولید پکت بر اساس شکل پورت
                            PacketModel packet;
                            if (out.getShape() == PortShape.CIRCLE) {
                                // پورت دایره‌ای = 30% شانس پکت حجیم
                                // توجه: عمداً 100% گذاشتی؛ دست نمی‌زنیم
                                if (RND.nextInt(10) < 10) {
                                    packet = createLargePacketForPort(out.getType(), baseSpeed);
                                } else {
                                    packet = new PacketModel(PacketType.CIRCLE, baseSpeed);
                                }
                            } else {
                                // پورت‌های دیگر = 10% شانس پکت حجیم
                                // توجه: عمداً 100% گذاشتی؛ دست نمی‌زنیم
                                if (RND.nextInt(10) < 0) {
                                    packet = createLargePacketForPort(out.getType(), baseSpeed);
                                } else {
                                    packet = new PacketModel(randomType(), baseSpeed);
                                }
                            }

                            // تنظیم سرعت اولیه و پیکربندی استراتژی حرکت
                            packet.setStartSpeedMul(1.0);
                            boolean compatible = wire.getSrcPort().isCompatible(packet);
                            MotionStrategy ms = MotionStrategyFactory.create(packet, compatible);
                            packet.setMotionStrategy(ms);

                            // چسباندن پکت به سیم خروجی
                            wire.attachPacket(packet, 0);

                            // --- تغییر اصلی: شمارنده‌ها را بعد از attach به‌روز کن
                            producedCount++;
                            inFlight++;
                            producedPerPort.put(out, producedForThisPort + 1);
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
}

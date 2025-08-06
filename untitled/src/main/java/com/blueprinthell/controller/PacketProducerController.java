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
    private int returnedCredits = 0;

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
    }
    private PacketModel createLargePacketRandomly() {
        int size = RND.nextBoolean() ? Config.LARGE_PACKET_SIZE_8 : Config.LARGE_PACKET_SIZE_10;
        int colorId = RND.nextInt(360);
        Color color = Color.getHSBColor(colorId / 360.0f, 0.8f, 0.9f);

        LargePacket large = new LargePacket(PacketType.CIRCLE, Config.DEFAULT_PACKET_SPEED, size, color);
        large.setGroupInfo(-1, size, colorId); // گروه بعداً در Distributor تنظیم می‌شود

        // تنظیم پروفایل حرکتی
        if (size == 8) {
            KinematicsRegistry.setProfile(large, KinematicsProfile.LARGE_8);
        } else {
            KinematicsRegistry.setProfile(large, KinematicsProfile.LARGE_10);
        }

        return large;
    }

    public void onPacketReturned() {
        returnedCredits++;
    }


    public boolean isFinished() {
        return producedCount >= totalToProduce && returnedCredits == 0;
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
                wires.stream()
                        .filter(w -> w.getSrcPort() == out)
                        .findFirst()
                        .ifPresent(wire -> {
                            PacketModel packet;
                            if (returnedCredits > 0) {
                                // 10% احتمال تولید پکت حجیم
                                if (RND.nextInt(10) <= 10) {
                                    packet = createLargePacketForPort(out.getType(), baseSpeed);
                                } else {
                                    packet = new PacketModel(randomType(), baseSpeed);
                                }
                                returnedCredits--;
                            } else if (producedCount < totalToProduce) {
                                // 10% احتمال تولید پکت حجیم
                                if (RND.nextInt(10) <= 10) {
                                    packet = createLargePacketForPort(out.getType(), baseSpeed);
                                } else {
                                    packet = new PacketModel(randomType(), baseSpeed);
                                }
                                producedCount++;
                            } else {
                                return;
                            }

                            boolean compatible = wire.getSrcPort().isCompatible(packet);

                            packet.setStartSpeedMul(1.0);
                            MotionStrategy ms = MotionStrategyFactory.create(packet, compatible);
                            packet.setMotionStrategy(ms);

                            wire.attachPacket(packet, 0);
                        });
            }
        }
    }

    // imports بالاست: java.awt.Color و ... را داری
    private LargePacket createLargePacketForPort(PacketType portType, double baseSpeed) {
        int units = (RND.nextBoolean() ? Config.LARGE_PACKET_SIZE_8 : Config.LARGE_PACKET_SIZE_10);

        // نوع پکت را مطابق پورت می‌دهیم
        LargePacket lp = new LargePacket(portType, baseSpeed, units);

        // رنگ داینامیک (تا واقعا رنگ‌های متنوع ببینی)
        lp.setCustomColor(Color.getHSBColor(RND.nextFloat(), 0.8f, 0.9f));

        // پروفایل کینماتیکی مناسب سایز
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

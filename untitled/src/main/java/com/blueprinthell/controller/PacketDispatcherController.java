package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.*;
import com.blueprinthell.model.large.LargePacket;
import com.blueprinthell.model.large.MergedPacket;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class PacketDispatcherController implements Updatable {

    private final List<WireModel> wires;
    private final Map<WireModel, SystemBoxModel> destinationMap;
    private final CoinModel coinModel;
    private final PacketLossModel lossModel;
    private WireDurabilityController durability;
    private WireRemovalController wireRemover;
    private final List<WireModel> wiresForRemoval = new ArrayList<>();
    private Map<WireModel, SystemBoxModel> sourceMap;
    public PacketDispatcherController(List<WireModel> wires,
                                      Map<WireModel, SystemBoxModel> destinationMap,
                                      CoinModel coinModel,
                                      PacketLossModel lossModel) {
        this.wires = wires;
        this.destinationMap = destinationMap;
        this.coinModel = coinModel;
        this.lossModel = lossModel;
    }

    public void setDurabilityController(WireDurabilityController durability) {
        this.durability = durability;
    }

    public void setWireRemover(WireRemovalController remover) {
        this.wireRemover = remover;
    }
    public void setSourceMap(Map<WireModel, SystemBoxModel> sourceMap) {
                this.sourceMap = sourceMap;
            }
    @Override
    public void update(double dt) {
        for (WireModel wire : wires) {
            List<PacketModel> arrived = wire.update(dt);
            SystemBoxModel dest = destinationMap.get(wire);
            PortModel dstPort = wire.getDstPort();

            for (PacketModel packet : arrived) {

                // [PATCH] تحویل پکتِ برگشتی به باکسِ منبع (نه مقصد)
                if (packet.isReturning()) {
                    SystemBoxModel srcBox = (sourceMap != null) ? sourceMap.get(wire) : null;
                    PortModel srcPort = wire.getSrcPort();
                    if (srcBox != null && srcPort != null) {
                        boolean ok = srcBox.enqueue(packet, srcPort); // ورود از خروجی ⇒ می‌رود داخل returnBuffer
                        if (ok) {
                            packet.setReturning(false);
                            continue; // به منطق مقصد نرو
                        } else {
                            // بافر منبع پر بود ⇒ Loss طبق قانون نوع‌محور
                            lossModel.incrementPacket(packet);
                            SimulationController sim = WireModel.getSimulationController();
                            if (sim != null && sim.getPacketProducerController() != null) {
                                sim.getPacketProducerController().onPacketLost();
                            }
                            continue;
                        }
                    }
                    // اگر srcBox/srcPort نبود، اجازه بده منطق مقصد ادامه یابد (fallback)
                }

                if (packet instanceof LargePacket lp && !lp.isRebuiltFromBits() && !(packet instanceof MergedPacket)) {
                    wire.incrementLargePacketPass();

                    if (wire.shouldBeDestroyed()) {
                        if (wireRemover != null) {
                            wireRemover.scheduleRemoval(wire);
                        } else {
                            wiresForRemoval.add(wire);
                        }
                    }
                }
                if (durability != null) {
                    durability.onPacketArrived(packet, wire);
                }

                // اگر مقصدی برای این سیم ثبت نشده، ادامه نده (از NPE هم جلوگیری می‌کند)
                if (dest == null) {
                    continue;
                }

                // --- خاموشی مقصد بر اثر سرعت بالای ورود (برای همهٔ انواع پکت) ---
                // این چک باید قبل از هر منطق دیگری (سازگاری پورت/بوست/صف) انجام شود.
                double entrySpeed = packet.getSpeed();
                double maxAllowed = getMaxAllowedSpeed(packet); // فعلاً مقدار ثابت از Config
                if (entrySpeed > maxAllowed + 1e-6 && dest.isEnabled()) {
                    dest.disable();                 // یا dest.disableFor(Config.DEST_DISABLE_MS) اگر دارید
                    packet.setReturning(true);      // پکت برگردد از سمت مقصد
                    wire.attachPacket(packet, 1.0); // progress=1.0 یعنی از انتهای سیم برگردد
                    continue;
                }
                // -------------------------------------------------------------------

                // ناسازگاری پورت → فقط بوست خروجی برای مسنجرها (رفتار قبلی حفظ می‌شود)
                if (dstPort != null && !dstPort.isCompatible(packet) && PacketOps.isMessenger(packet)) {
                    packet.setExitBoostMultiplier(2.0);
                }

                boolean accepted = dest.enqueue(packet, dstPort);

                if (accepted) {
                    int coins = 0;

                    // برای پکت‌های حجیم
                    if (packet instanceof LargePacket lp2) {
                        coins = lp2.getOriginalSizeUnits();
                    }
                    // برای پکت‌های محرمانه
                    else if (PacketOps.isConfidential(packet)) {
                        if (PacketOps.isConfidentialVpn(packet)) {
                            coins = 4; // پکت محرمانه VPN
                        } else {
                            coins = 3; // پکت محرمانه عادی
                        }
                    }
                    // برای پکت‌های پیام‌رسان
                    else if (PacketOps.isMessenger(packet)) {
                        coins = PacketOps.coinValueOnEntry(packet);
                    }
                    // برای سیستم VPN با پکت‌های خاص
                    else if (dest.getPrimaryKind() == SystemKind.VPN) {
                        if (PacketOps.isMessenger(packet)) {
                            coins = 5;
                        }
                    }

                    if (coins > 0) {
                        coinModel.add(coins);
                    }
                } else {
                    if (!dest.isEnabled()) {
                        packet.setReturning(true);
                        wire.attachPacket(packet, 1.0);
                        continue;
                    } else {
                        lossModel.incrementPacket(packet);

                        // اطلاع به producer که پکت از بین رفت
                        SimulationController sim = WireModel.getSimulationController();
                        if (sim != null && sim.getPacketProducerController() != null) {
                            sim.getPacketProducerController().onPacketLost();
                        }
                    }
                }
            }
        }

        // حذف سیم‌های نشانه‌گذاری شده
        for (WireModel wire : wiresForRemoval) {
            wires.remove(wire);
            destinationMap.remove(wire);
            // اطلاع رسانی به UI برای حذف نمای سیم
            // این کار باید در WireRemovalController انجام شود
        }
        wiresForRemoval.clear();
    }

    // در صورت نیاز به آستانه‌های متفاوت برای انواع پکت،
    // این متد را به خواندن از یک Map در Config گسترش دهید.
    private double getMaxAllowedSpeed(PacketModel p) {
        return Config.MAX_ALLOWED_SPEED;
    }
}

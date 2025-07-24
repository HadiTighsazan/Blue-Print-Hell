package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.systems.*;
import com.blueprinthell.level.LevelDefinition;
import com.blueprinthell.model.PacketLossModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.WireUsageModel;
import com.blueprinthell.model.large.LargeGroupRegistry;
import com.blueprinthell.view.SystemBoxView;
import com.blueprinthell.view.screens.GameScreenView;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * <h2>LevelBuilder</h2>
 * <ul>
 *   <li>ساخت {@link SystemBoxModel} ها از روی {@link LevelDefinition}.</li>
 *   <li>ثبت رفتارها در {@link BehaviorRegistry} بر اساس {@link SystemKind} هر باکس.</li>
 *   <li>اتصال کنترلر Drag برای جابجایی باکس‌ها.</li>
 * </ul>
 */
public final class LevelBuilder {

    private final GameScreenView     gameView;
    private final List<WireModel>    wires;
    private final WireUsageModel     usageModel;
    private final BehaviorRegistry   behaviorRegistry;   // nullable
    private final LargeGroupRegistry largeRegistry;      // nullable
    private final PacketLossModel    lossModel;          // nullable

    /* ------------------------------------------------------ */
    /*                     Constructors                       */
    /* ------------------------------------------------------ */

    /** نسخهٔ قدیمی بدون رجیستری‌ها */
    public LevelBuilder(GameScreenView gameView,
                        List<WireModel> wires,
                        WireUsageModel usageModel) {
        this(gameView, wires, usageModel, null, null, null);
    }

    /** نسخهٔ میانی فقط با BehaviorRegistry */
    public LevelBuilder(GameScreenView gameView,
                        List<WireModel> wires,
                        WireUsageModel usageModel,
                        BehaviorRegistry behaviorRegistry) {
        this(gameView, wires, usageModel, behaviorRegistry, null, null);
    }

    /** نسخهٔ کامل با تمام وابستگی‌ها */
    public LevelBuilder(GameScreenView gameView,
                        List<WireModel> wires,
                        WireUsageModel usageModel,
                        BehaviorRegistry behaviorRegistry,
                        LargeGroupRegistry largeRegistry,
                        PacketLossModel lossModel) {
        this.gameView       = gameView;
        this.wires          = wires;
        this.usageModel     = usageModel;
        this.behaviorRegistry = behaviorRegistry;
        this.largeRegistry  = largeRegistry;
        this.lossModel      = lossModel;
    }

    /* ------------------------------------------------------ */
    /*                        Build API                       */
    /* ------------------------------------------------------ */

    public List<SystemBoxModel> build(LevelDefinition def) {
        List<SystemBoxModel> boxes = new ArrayList<>();
        for (LevelDefinition.BoxSpec spec : def.boxes()) {
            SystemBoxModel box = new SystemBoxModel(
                    spec.x(), spec.y(), spec.width(), spec.height(),
                    spec.inShapes(), spec.outShapes());
            boxes.add(box);

            if (behaviorRegistry != null) {
                registerBehaviors(box, spec);
            }
        }

        gameView.reset(boxes, wires);
        attachDragControllers();
        return boxes;
    }

    public List<SystemBoxModel> extend(List<SystemBoxModel> existingBoxes,
                                       List<LevelDefinition.BoxSpec> newSpecs) {
        int neededInputs = newSpecs.stream().mapToInt(s -> s.inShapes().size()).sum();
        int freeOutputs = 0;
        for (SystemBoxModel box : existingBoxes) {
            freeOutputs += Math.max(0, Config.MAX_OUTPUT_PORTS - box.getOutPorts().size());
        }
        if (neededInputs > freeOutputs) {
            throw new IllegalStateException("Insufficient port capacity in existing systems for new stage");
        }

        List<SystemBoxModel> newBoxes = new ArrayList<>();
        for (LevelDefinition.BoxSpec spec : newSpecs) {
            SystemBoxModel box = new SystemBoxModel(
                    spec.x(), spec.y(), spec.width(), spec.height(),
                    spec.inShapes(), spec.outShapes());
            newBoxes.add(box);
            if (behaviorRegistry != null) {
                registerBehaviors(box, spec);
            }
        }

        List<SystemBoxModel> all = new ArrayList<>(existingBoxes);
        all.addAll(newBoxes);

        // تضمین حداقل یک مقصد بدون خروجی
        boolean hasDestination = all.stream().anyMatch(b -> b.getOutPorts().isEmpty());
        if (!hasDestination && !all.isEmpty()) {
            all.get(all.size() - 1).removeOutputPort();
        }

        gameView.reset(all, wires);
        attachDragControllers();
        return all;
    }

    /* ------------------------------------------------------ */
    /*                          Helpers                       */
    /* ------------------------------------------------------ */

    private void attachDragControllers() {
        for (Component c : gameView.getGameArea().getComponents()) {
            if (c instanceof SystemBoxView sbv) {
                new SystemBoxDragController(sbv.getModel(), sbv, wires, usageModel);
            }
        }
    }

    /**
     * ثبت رفتار مناسب بر اساس kind.
     */
    private void registerBehaviors(SystemBoxModel box, LevelDefinition.BoxSpec spec) {
        SystemKind kind = spec.kind();
        if (kind == null) kind = SystemKind.NORMAL;

        switch (kind) {
            case DISTRIBUTOR -> {
                behaviorRegistry.register(box, new NormalBehavior(box));
                if (largeRegistry != null && lossModel != null) {
                    behaviorRegistry.register(box, new DistributorBehavior(box, largeRegistry, lossModel));
                }
                behaviorRegistry.register(box, new PortRandomizerBehavior(box));
            }
            case MERGER -> {
                behaviorRegistry.register(box, new NormalBehavior(box));
                if (largeRegistry != null && lossModel != null) {
                    behaviorRegistry.register(box, new MergerBehavior(box, largeRegistry, lossModel));
                }
                behaviorRegistry.register(box, new PortRandomizerBehavior(box));
            }
            case SPY -> {
                behaviorRegistry.register(box, new NormalBehavior(box));
                if (lossModel != null) {
                    behaviorRegistry.register(box, new SpyBehavior(box, behaviorRegistry, lossModel));
                }
            }
            case MALICIOUS -> {
                behaviorRegistry.register(box, new NormalBehavior(box));
                behaviorRegistry.register(box, new MaliciousBehavior(box, Config.TROJAN_PROBABILITY));
            }
            case VPN -> {
                behaviorRegistry.register(box, new NormalBehavior(box));
                behaviorRegistry.register(box, new VpnBehavior(box, Config.DEFAULT_SHIELD_CAPACITY));
            }
            case ANTI_TROJAN -> {
                behaviorRegistry.register(box, new NormalBehavior(box));
                // AntiTrojan به لیست wires نیاز دارد
                behaviorRegistry.register(box, new AntiTrojanBehavior(box, wires, Config.ANTI_TROJAN_RADIUS_PX, Config.ANTI_TROJAN_COOLDOWN_S));
            }
            default -> {
                behaviorRegistry.register(box, new NormalBehavior(box));
                // PortRandomizer برای همه مفید است چون ممکن است LargePacket وارد شود
                behaviorRegistry.register(box, new PortRandomizerBehavior(box));
            }
        }
    }
}

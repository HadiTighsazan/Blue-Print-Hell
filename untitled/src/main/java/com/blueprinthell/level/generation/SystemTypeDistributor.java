package com.blueprinthell.level.generation;

import com.blueprinthell.controller.systems.SystemKind;
import com.blueprinthell.model.PortShape;

import java.util.*;


public class SystemTypeDistributor {

    private final Random random = new Random();


    public List<SystemKind> getSystemsForLevel(int levelNum) {
        List<SystemKind> systems = new ArrayList<>();

        switch (levelNum) {
            case 0:
                // Level 1: Just normal systems
                systems.add(SystemKind.NORMAL);
                break;

            case 1:
                // Level 2: Introduce VPN
                systems.add(SystemKind.VPN);
                systems.add(SystemKind.NORMAL);
                break;

            case 2:
                // Level 3: Add Malicious
                systems.add(SystemKind.MALICIOUS);
                systems.add(SystemKind.VPN);
                break;

            case 3:
                // Level 4: Add Anti-Trojan
                systems.add(SystemKind.ANTI_TROJAN);
                systems.add(SystemKind.MALICIOUS);
                systems.add(SystemKind.NORMAL);
                break;

            case 4:
                // Level 5: Add Spy
                systems.add(SystemKind.SPY);
                systems.add(SystemKind.VPN);
                systems.add(SystemKind.ANTI_TROJAN);
                break;

            case 5:
                // Level 6: Add Distributor/Merger
                systems.add(SystemKind.DISTRIBUTOR);
                systems.add(SystemKind.SPY);
                systems.add(SystemKind.MERGER);
                break;

            default:
                // Level 7+: Random mix of special systems
                systems.addAll(getRandomSystemMix(2 + random.nextInt(3)));

                // Always add some normal systems for balance
                if (random.nextBoolean()) {
                    systems.add(SystemKind.NORMAL);
                }
                break;
        }

        return systems;
    }


    public PortConfiguration getPortConfigForSystemKind(SystemKind kind) {
        switch (kind) {
            case SPY:
                // Spies need multiple outputs for teleportation
                return new PortConfiguration(
                        randomShapes(1 + random.nextInt(2)),
                        randomShapes(2 + random.nextInt(2))
                );

            case MALICIOUS:
                // Malicious systems work with any configuration
                return new PortConfiguration(
                        randomShapes(1 + random.nextInt(3)),
                        randomShapes(1 + random.nextInt(3))
                );

            case VPN:
                // VPN systems need balanced I/O
                int count = 1 + random.nextInt(3);
                return new PortConfiguration(
                        randomShapes(count),
                        randomShapes(count)
                );

            case ANTI_TROJAN:
                // Anti-Trojan works as a passthrough
                return new PortConfiguration(
                        randomShapes(1 + random.nextInt(2)),
                        randomShapes(1 + random.nextInt(2))
                );

            case DISTRIBUTOR:
                // Distributor: few inputs, many outputs
                return new PortConfiguration(
                        randomShapes(1),
                        randomShapes(3)
                );

            case MERGER:
                // Merger: many inputs, few outputs
                return new PortConfiguration(
                        randomShapes(3),
                        randomShapes(1)
                );

            case PORT_RANDOMIZER:
                // Port randomizer needs multiple ports to randomize
                return new PortConfiguration(
                        randomShapes(2 + random.nextInt(2)),
                        randomShapes(2 + random.nextInt(2))
                );

            case NORMAL:
            default:
                return new PortConfiguration(
                        randomShapes(1 + random.nextInt(3)),
                        randomShapes(1 + random.nextInt(3))
                );
        }
    }


    private List<SystemKind> getRandomSystemMix(int count) {
        List<SystemKind> pool = Arrays.asList(
                SystemKind.SPY,
                SystemKind.MALICIOUS,
                SystemKind.VPN,
                SystemKind.ANTI_TROJAN,
                SystemKind.DISTRIBUTOR,
                SystemKind.MERGER,
                SystemKind.PORT_RANDOMIZER
        );

        Collections.shuffle(pool);
        return new ArrayList<>(pool.subList(0, Math.min(count, pool.size())));
    }

    private PortShape randomShape() {
        int r = random.nextInt(3);
        return (r == 0) ? PortShape.SQUARE
                : (r == 1) ? PortShape.TRIANGLE
                : PortShape.CIRCLE;
    }

    private List<PortShape> randomShapes(int count) {
        List<PortShape> shapes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            shapes.add(randomShape());
        }
        return shapes;
    }


    public static class PortConfiguration {
        public final List<PortShape> inShapes;
        public final List<PortShape> outShapes;

        public PortConfiguration(List<PortShape> inShapes, List<PortShape> outShapes) {
            this.inShapes = inShapes;
            this.outShapes = outShapes;
        }
    }
}
package cn.academy.port.fusion.phase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import cn.academy.port.fusion.phase.PhaseLakeAlgorithm.Output;

/** Pure source differential checks, no Minecraft bootstrap or native server. */
public final class PhaseLiquidWorldgenRegressionTest {
    private static int assertions;
    private static void check(boolean condition, String label) {
        ++assertions;
        if (!condition) throw new AssertionError(label);
    }
    private static final class Draws implements PhaseLiquidRandom {
        private final Random random;
        private final List<String> calls = new ArrayList<>();
        Draws(long seed) { random = new Random(seed); }
        @Override public int nextInt(int bound) { calls.add("int:" + bound); return random.nextInt(bound); }
        @Override public double nextDouble() { calls.add("double"); return random.nextDouble(); }
    }
    private static final class FixedDraws implements PhaseLiquidRandom {
        private final double chance;
        private final int[] values;
        private int next;
        private final List<String> calls = new ArrayList<>();
        FixedDraws(double chance, int... values) { this.chance = chance; this.values = values; }
        @Override public int nextInt(int bound) {
            calls.add("int:" + bound);
            int value = values[next++];
            check(value >= 0 && value < bound, "fixed draw within bound");
            return value;
        }
        @Override public double nextDouble() { calls.add("double"); return chance; }
    }
    private enum Cell { STONE, AIR, WATER, PHASE, DIRT, OTHER, GRASS, MYCELIUM, ICE }
    private record Pos(int x, int y, int z) {}
    private record Write(Pos pos, Output output) {}
    private static final class TestWorld implements PhaseLakeAlgorithm.World {
        private final Cell fallback;
        private final Map<Pos, Cell> cells = new HashMap<>();
        private final List<Write> writes = new ArrayList<>();
        private final List<Pos> airProbes = new ArrayList<>();
        private final List<Pos> freezeProbes = new ArrayList<>();
        private boolean sky;
        private boolean mushroom;
        private boolean freezeWater;
        TestWorld(Cell fallback) { this.fallback = fallback; }
        private Cell cell(int x, int y, int z) { return cells.getOrDefault(new Pos(x, y, z), fallback); }
        private void put(int x, int y, int z, Cell cell) { cells.put(new Pos(x, y, z), cell); }
        @Override public boolean isAir(int x, int y, int z) {
            airProbes.add(new Pos(x, y, z)); return cell(x, y, z) == Cell.AIR;
        }
        @Override public boolean isLiquid(int x, int y, int z) {
            return cell(x, y, z) == Cell.WATER || cell(x, y, z) == Cell.PHASE;
        }
        @Override public boolean isSolid(int x, int y, int z) {
            return switch (cell(x, y, z)) {
                case STONE, DIRT, GRASS, MYCELIUM, ICE -> true;
                default -> false;
            };
        }
        @Override public boolean isPhase(int x, int y, int z) { return cell(x, y, z) == Cell.PHASE; }
        @Override public boolean isDirt(int x, int y, int z) { return cell(x, y, z) == Cell.DIRT; }
        @Override public boolean hasSkyLight(int x, int y, int z) { return sky; }
        @Override public boolean hasMyceliumTop(int x, int y, int z) { return mushroom; }
        @Override public boolean isFreezable(int x, int y, int z) {
            freezeProbes.add(new Pos(x, y, z)); return freezeWater && cell(x, y, z) == Cell.WATER;
        }
        @Override public void set(int x, int y, int z, Output output) {
            Pos pos = new Pos(x, y, z);
            writes.add(new Write(pos, output));
            cells.put(pos, switch (output) {
                case AIR -> Cell.AIR;
                case PHASE_LIQUID -> Cell.PHASE;
                case GRASS -> Cell.GRASS;
                case MYCELIUM -> Cell.MYCELIUM;
                case ICE -> Cell.ICE;
            });
        }
    }

    // Literal original variable/index expressions form an independent mask/boundary oracle.
    private static boolean[] originalMask(PhaseLiquidRandom random) {
        boolean[] buffer = new boolean[2048];
        for (int i = 0, loops = random.nextInt(4) + 4; i < loops; ++i) {
            double d0 = random.nextDouble() * 6.0D + 3.0D;
            double d1 = random.nextDouble() * 4.0D + 2.0D;
            double d2 = random.nextDouble() * 6.0D + 3.0D;
            double d3 = random.nextDouble() * (14.0D - d0) + 1.0D + d0 / 2.0D;
            double d4 = random.nextDouble() * (4.0D - d1) + 2.0D + d1 / 2.0D;
            double d5 = random.nextDouble() * (14.0D - d2) + 1.0D + d2 / 2.0D;
            for (int j = 1; j < 15; ++j) {
                for (int k = 1; k < 15; ++k) {
                    for (int l = 1; l < 7; ++l) {
                        double d6 = (j - d3) / (d0 / 2.0D);
                        double d7 = (l - d4) / (d1 / 2.0D);
                        double d8 = (k - d5) / (d2 / 2.0D);
                        double d9 = d6 * d6 + d7 * d7 + d8 * d8;
                        if (d9 < 0.6D) buffer[(j * 16 + k) * 8 + l] = true;
                    }
                }
            }
        }
        return buffer;
    }
    private static boolean originalBoundary(boolean[] buffer, int i, int j1, int j2) {
        return !buffer[(i * 16 + j2) * 8 + j1] && (i < 15 && buffer[((i + 1) * 16 + j2) * 8 + j1]
                || i > 0 && buffer[((i - 1) * 16 + j2) * 8 + j1]
                || j2 < 15 && buffer[(i * 16 + j2 + 1) * 8 + j1]
                || j2 > 0 && buffer[(i * 16 + (j2 - 1)) * 8 + j1]
                || j1 < 7 && buffer[(i * 16 + j2) * 8 + j1 + 1]
                || j1 > 0 && buffer[(i * 16 + j2) * 8 + (j1 - 1)]);
    }
    private static boolean originalGenerate(TestWorld world, PhaseLiquidRandom random, int x, int y, int z) {
        for (x -= 8, z -= 8; y > 5 && world.isAir(x, y, z); --y) {}
        if (y <= 4) return false;
        y -= 4;
        boolean[] buffer = originalMask(random);
        for (int i = 0; i < 16; ++i) {
            for (int j2 = 0; j2 < 16; ++j2) {
                for (int j1 = 0; j1 < 8; ++j1) {
                    if (originalBoundary(buffer, i, j1, j2)) {
                        if (j1 >= 4 && world.isLiquid(x + i, y + j1, z + j2)) return false;
                        if (j1 < 4 && !world.isSolid(x + i, y + j1, z + j2)
                                && !world.isPhase(x + i, y + j1, z + j2)) return false;
                    }
                }
            }
        }
        for (int i1 = 0; i1 < 16; ++i1) {
            for (int j2 = 0; j2 < 16; ++j2) {
                for (int j1 = 0; j1 < 8; ++j1) {
                    if (buffer[(i1 * 16 + j2) * 8 + j1])
                        world.set(x + i1, y + j1, z + j2, j1 >= 4 ? Output.AIR : Output.PHASE_LIQUID);
                }
            }
        }
        for (int i1 = 0; i1 < 16; ++i1) {
            for (int j2 = 0; j2 < 16; ++j2) {
                for (int j1 = 4; j1 < 8; ++j1) {
                    if (buffer[(i1 * 16 + j2) * 8 + j1] && world.isDirt(x + i1, y + j1 - 1, z + j2)
                            && world.hasSkyLight(x + i1, y + j1, z + j2))
                        world.set(x + i1, y + j1 - 1, z + j2,
                                world.hasMyceliumTop(x + i1, y + j1, z + j2) ? Output.MYCELIUM : Output.GRASS);
                }
            }
        }
        for (int i1 = 0; i1 < 16; ++i1)
            for (int j2 = 0; j2 < 16; ++j2)
                if (world.isFreezable(x + i1, y + 4, z + j2))
                    world.set(x + i1, y + 4, z + j2, Output.ICE);
        return true;
    }
    private static Pos boundary(boolean[] mask, boolean upper) {
        for (int x = 0; x < 16; ++x)
            for (int z = 0; z < 16; ++z)
                for (int y = upper ? 4 : 0; y < (upper ? 8 : 4); ++y)
                    if (originalBoundary(mask, x, y, z)) return new Pos(x, y, z);
        throw new AssertionError("seed must have " + (upper ? "upper" : "lower") + " shell");
    }
    private static void starts() {
        for (boolean enabled : new boolean[] {false, true}) {
            for (boolean overworld : new boolean[] {false, true}) {
                if (enabled && overworld) continue;
                FixedDraws random = new FixedDraws(0);
                check(PhaseLiquidRules.drawStart(enabled, overworld, random, 0, 0) == null, "config/dimension reject");
                check(random.calls.isEmpty(), "gates consume no random draws");
            }
        }
        for (double chance : new double[] {.3D, .30000000000000004D, .999999D}) {
            FixedDraws random = new FixedDraws(chance);
            check(PhaseLiquidRules.drawStart(true, true, random, 0, 0) == null, "strict 0.3 chance boundary");
            check(random.calls.equals(List.of("double")), "chance rejection no coordinates");
        }
        for (int[] xyz : new int[][] {{0, 0, 0}, {15, 29, 15}, {3, 17, 7}}) {
            FixedDraws random = new FixedDraws(Math.nextDown(.3D), xyz);
            var start = PhaseLiquidRules.drawStart(true, true, random, -2, 3);
            check(start.equals(new PhaseLiquidRules.Start(-32 + xyz[0], 5 + xyz[1], 48 + xyz[2])), "exact negative-chunk coordinates");
            check(random.calls.equals(List.of("double", "int:16", "int:30", "int:16")), "exact x/y/z draw order");
        }
        for (int seed = 0; seed < 10000; ++seed) {
            Random source = new Random(seed);
            PhaseLiquidRules.Start expected = null;
            if (source.nextDouble() < .3D)
                expected = new PhaseLiquidRules.Start(-48 + source.nextInt(16), 5 + source.nextInt(30), 112 + source.nextInt(16));
            Draws actual = new Draws(seed);
            check(java.util.Objects.equals(expected, PhaseLiquidRules.drawStart(true, true, actual, -3, 7)), "source start seed " + seed);
            check(source.nextLong() == actual.random.nextLong(), "source start random tail " + seed);
        }
    }
    private static void masks() {
        for (long seed = 0; seed < 256; ++seed) {
            Draws source = new Draws(seed), actual = new Draws(seed);
            boolean[] expected = originalMask(source), mask = PhaseLakeAlgorithm.createMask(actual);
            check(mask.length == 2048, "source mask capacity");
            check(source.calls.equals(actual.calls), "source ellipsoid draws");
            check(source.random.nextLong() == actual.random.nextLong(), "source ellipsoid random tail");
            int cells = 0;
            for (int x = 0; x < 16; ++x) {
                for (int z = 0; z < 16; ++z) {
                    for (int y = 0; y < 8; ++y) {
                        int index = (x * 16 + z) * 8 + y;
                        check(mask[index] == expected[index], "source .6 ellipsoid cell");
                        check(PhaseLakeAlgorithm.isBoundary(mask, x, y, z) == originalBoundary(expected, x, y, z), "source shell adjacency");
                        if (mask[index]) {
                            ++cells;
                            check(x > 0 && x < 15 && z > 0 && z < 15 && y > 0 && y < 7, "source margins remain intact");
                        }
                    }
                }
            }
            check(cells > 0, "source shape is nonempty");
        }
    }
    private static TestWorld scenario(long seed, int scenario) {
        TestWorld world = new TestWorld(scenario == 6 ? Cell.AIR : Cell.STONE);
        boolean[] mask = originalMask(new Draws(seed));
        if (scenario >= 1 && scenario <= 4) {
            Pos border = boundary(mask, scenario == 1);
            world.put(92 + border.x(), 16 + border.y(), -28 + border.z(), switch (scenario) {
                case 1, 2 -> Cell.WATER;
                case 3 -> Cell.OTHER;
                default -> Cell.PHASE;
            });
        }
        if (scenario == 5) {
            // Probe descent at the shifted corner: start20 -> stop8 -> base4.
            for (int y = 9; y <= 20; ++y) world.put(92, y, -28, Cell.AIR);
        }
        if (scenario >= 7) {
            world.sky = scenario != 7;
            world.mushroom = scenario == 9;
            world.freezeWater = true;
            for (int x = 0; x < 16; ++x) {
                for (int z = 0; z < 16; ++z) {
                    for (int y = 4; y < 8; ++y)
                        if (mask[(x * 16 + z) * 8 + y] && !mask[(x * 16 + z) * 8 + y - 1])
                            world.put(92 + x, 16 + y - 1, -28 + z, Cell.DIRT);
                    if (!mask[(x * 16 + z) * 8 + 4] && !originalBoundary(mask, x, 4, z))
                        world.put(92 + x, 20, -28 + z, Cell.WATER);
                }
            }
        }
        return world;
    }
    private static void worlds() {
        for (long seed = 0; seed < 64; ++seed) {
            for (int scenario = 0; scenario < 10; ++scenario) {
                TestWorld source = scenario(seed, scenario), actual = scenario(seed, scenario);
                Draws sourceRandom = new Draws(seed), random = new Draws(seed);
                boolean expected = originalGenerate(source, sourceRandom, 100, 20, -20);
                boolean placed = PhaseLakeAlgorithm.generate(actual, random, 100, 20, -20);
                check(placed == expected, "source world result");
                check(actual.writes.equals(source.writes), "source exact writes including order");
                check(actual.cells.equals(source.cells), "source exact resulting world");
                check(actual.airProbes.equals(source.airProbes), "source shifted-corner descent");
                check(actual.freezeProbes.equals(source.freezeProbes), "source complete freeze pass");
                check(sourceRandom.calls.equals(random.calls), "source world random operations");
                check(sourceRandom.random.nextLong() == random.random.nextLong(), "source world random tail");
                if (scenario == 0 || scenario == 4 || scenario == 5 || scenario >= 7) {
                    check(placed, "stone or existing phase shell accepted");
                    check(actual.freezeProbes.size() == 256, "every freeze position checked");
                } else {
                    check(!placed && actual.writes.isEmpty(), "liquid/unsupported shell rejection is atomic");
                    check(actual.freezeProbes.isEmpty(), "no freeze writes on rejection");
                }
                if (scenario == 0) {
                    boolean lower = false, upper = false;
                    for (Write write : actual.writes) {
                        int relativeY = write.pos().y() - 16;
                        check(write.output() == (relativeY >= 4 ? Output.AIR : Output.PHASE_LIQUID), "lower4 phase source upper4 air");
                        lower |= relativeY < 4;
                        upper |= relativeY >= 4;
                    }
                    check(lower && upper, "both source liquid and air halves written");
                }
                if (scenario == 7)
                    check(actual.writes.stream().noneMatch(w -> w.output() == Output.GRASS || w.output() == Output.MYCELIUM), "no grass without skylight");
                if (scenario == 8 || scenario == 9) {
                    Output expectedSoil = scenario == 9 ? Output.MYCELIUM : Output.GRASS;
                    check(actual.writes.stream().anyMatch(w -> w.output() == expectedSoil), "source biome/skylight soil recovery");
                }
                if (scenario >= 7)
                    check(actual.writes.stream().anyMatch(w -> w.output() == Output.ICE), "outside-shape freezable water still checked");
            }
        }
        for (int y : new int[] {-64, 0, 4}) {
            TestWorld world = new TestWorld(Cell.STONE);
            Draws random = new Draws(1);
            check(!PhaseLakeAlgorithm.generate(world, random, 0, y, 0), "source low-height reject");
            check(world.writes.isEmpty() && random.calls.isEmpty(), "low-height reject before mask/random/writes");
        }
        TestWorld y5 = new TestWorld(Cell.STONE);
        check(PhaseLakeAlgorithm.generate(y5, new Draws(1), 0, 5, 0), "absolute Y5 is accepted");
        check(y5.airProbes.isEmpty(), "Y5 does not descend below source minimum");
        check(y5.writes.stream().filter(w -> w.output() == Output.PHASE_LIQUID).allMatch(w -> w.pos().y() >= 1 && w.pos().y() <= 4), "Y5 anchor lower-four range");
    }
    public static void main(String[] args) {
        starts(); masks(); worlds();
        System.out.println("PASS " + assertions + " source-exact phase-liquid worldgen assertions");
    }
}

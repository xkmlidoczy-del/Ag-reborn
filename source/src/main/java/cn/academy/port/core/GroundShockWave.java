/* AcademyCraft 1.0.7 Groundshock and classic Plotter adaptation. See NOTICE. */
package cn.academy.port.core;

import cn.academy.port.skill.Plotter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Dependency-free source-order wave simulation; adapters own actual world mutations. */
public final class GroundShockWave {
    public static final String ID = "ground_shock";
    public static final int MIN_HOLD_TICKS = 5;
    public static final double ENTITY_EXPERIENCE = .002F;
    public static final double PERFORM_EXPERIENCE = .001F;
    public static final double GROUND_BREAK_PROBABILITY = .3;

    public record Cell(int x, int y, int z) {}
    public enum Kind { AIR, STONE, GRASS, FARMLAND, COBBLESTONE, DIRT, OTHER }
    public record Block(Kind kind, double hardness, boolean liquid) {}
    public record Parameters(float mastery, double energy, float damage, float cp, float overload,
                             int iterations, int cooldown, float dropRate, float verticalSpeed) {}
    public record Result(List<Cell> affected, int entities, double energy, boolean masteryClear) {}
    public interface Random {
        double nextDouble();
        float nextFloat();
    }
    public interface World {
        Block block(Cell cell);
        /** Conversion protection is a modern adaptation; the source skipped its event here. */
        void convert(Cell cell, Kind replacement);
        boolean canBreak(Cell cell);
        void destroy(Cell cell, boolean drop);
        /** Living entities and boss parts overlapping pt[-.2,+1.4] × y[-.2,+2.2], excluding caster. */
        Iterable<?> targets(Cell cell);
        void attackAndLaunch(Object target, float damage, float verticalSpeed);
    }

    private GroundShockWave() {}
    public static boolean acceptsRelease(long ticks) { return ticks >= MIN_HOLD_TICKS; }
    public static boolean mayStart(AbilityProgress state) {
        return state != null && "vecmanip".equals(state.category) && state.level >= 1 && state.canUse(ID);
    }
    private static float lerp(float a, float b, double mastery) {
        float e = (float) ClassicRules.clamp(mastery, 0, 1);
        return a + e * (b - a);
    }
    /** Source values and one random y speed are captured when the context is constructed. */
    public static Parameters parameters(double mastery, float launchRandom) {
        float e = (float) ClassicRules.clamp(mastery, 0, 1);
        float launch = .6F + (float) ClassicRules.clamp(launchRandom, 0, 1) * (.9F - .6F);
        return new Parameters(e, lerp(60, 120, e), lerp(4, 6, e), lerp(80, 150, e),
                lerp(15, 10, e), (int) lerp((float) SkillTuning.number("ground_shock", "max_steps_novice"), (float) SkillTuning.number("ground_shock", "max_steps_mastered"), e), (int) lerp(80, 40, e),
                lerp(.3F, 1, e), launch * lerp(.8F, 1.3F, e));
    }

    /**
     * Consume outside this routine only after grounded/hold validation. Coordinates deliberately
     * truncate toward zero. The three-dimensional look is normalized by the caller, but its y
     * component survives in lateral offsets even though the Plotter's vertical slope is zero.
     */
    public static Result perform(AbilityProgress state, Parameters p, World world, Random random,
                                 double playerX, double playerY, double playerZ,
                                 double lookX, double lookY, double lookZ) {
        Objects.requireNonNull(state); Objects.requireNonNull(p); Objects.requireNonNull(world);
        Objects.requireNonNull(random);
        var plotter = new Plotter((int) playerX, (int) playerY - 1, (int) playerZ, lookX, 0, lookZ);
        // Minecraft 1.7 Vec3.rotateAroundY takes radians. Passing 90 was source behavior.
        float sine = classicSin(90F), cosine = classicCos(90F);
        double rx = lookX * cosine + lookZ * sine;
        double rz = lookZ * cosine - lookX * sine;
        double[][] offsets = {{0, 0, 0, 1}, {rx, lookY, rz, .7}, {-rx, -lookY, -rz, .7},
                {2 * rx, 2 * lookY, 2 * rz, .3}, {-2 * rx, -2 * lookY, -2 * rz, .3}};
        Set<Cell> seenBlocks = new LinkedHashSet<>(); Set<Object> seenEntities = new LinkedHashSet<>();
        Energy energy = new Energy(p.energy());
        for (int iter = 0; energy.value > 0 && iter < p.iterations(); iter++) {
            int[] next = plotter.next(); var center = new Cell(next[0], next[1], next[2]);
            for (var delta : offsets) {
                var pt = new Cell((int) (center.x() + delta[0]), (int) (center.y() + delta[1]),
                        (int) (center.z() + delta[2]));
                Block block = world.block(pt);
                if (random.nextDouble() < delta[3] && block.kind() != Kind.AIR && seenBlocks.add(pt)) {
                    switch (block.kind()) {
                        case STONE -> { world.convert(pt, Kind.COBBLESTONE); energy.value -= .4; }
                        case GRASS -> { world.convert(pt, Kind.DIRT); energy.value -= .2; }
                        case FARMLAND -> energy.value -= .1;
                        default -> energy.value -= .5;
                    }
                    // Source uses CENTER here, even when pt is lateral, and queries converted hardness.
                    if (random.nextDouble() < GROUND_BREAK_PROBABILITY)
                        breakWithForce(world, random, center, energy, p.dropRate(), false);
                    for (Object target : world.targets(pt)) if (seenEntities.add(target)) {
                        energy.value -= 1;
                        world.attackAndLaunch(target, p.damage(), p.verticalSpeed());
                        state.addExperience(ID, ENTITY_EXPERIENCE);
                    }
                }
                // All five offsets repeat the central above-column check, even on air/probability miss.
                for (int d = 1; d <= 3; d++)
                    breakWithForce(world, random, new Cell(center.x(), center.y() + d, center.z()),
                            energy, p.dropRate(), false);
            }
        }
        double remaining = energy.value;
        energy.value = Double.MAX_VALUE;
        // Source checks LIVE mastery after per-entity EXP, before its final .001 EXP award.
        boolean mastered = (float) state.exp(ID) == 1F;
        if (mastered) {
            int x0 = (int) playerX, y0 = (int) playerY, z0 = (int) playerZ;
            for (int x = x0 - 5; x < x0 + 5; x++)
                for (int y = y0 - 1; y < y0 + 1; y++)
                    for (int z = z0 - 5; z < z0 + 5; z++) {
                        var cell = new Cell(x, y, z);
                        if (world.block(cell).hardness() <= .6)
                            breakWithForce(world, random, cell, energy, p.dropRate(), true);
                    }
        }
        state.addExperience(ID, PERFORM_EXPERIENCE);
        state.setCooldown(ID, p.cooldown());
        return new Result(List.copyOf(seenBlocks), seenEntities.size(), remaining, mastered);
    }

    private static void breakWithForce(World world, Random random, Cell cell, Energy energy,
                                       float dropRate, boolean drop) {
        Block block = world.block(cell);
        // Keep event ordering, nonnegative hardness, farmland/liquid exception and energy threshold.
        if (!world.canBreak(cell)) return;
        double hardness = block.hardness();
        if (hardness >= 0 && energy.value >= hardness && block.kind() != Kind.FARMLAND && !block.liquid()) {
            energy.value -= hardness;
            world.destroy(cell, drop && random.nextFloat() < dropRate);
        }
    }
    private static final class Energy { double value; Energy(double value) { this.value = value; } }
    /** Legacy MathHelper LUT, including float multiply/index truncation, rather than double trig. */
    public static float classicSin(float radians) { return sinIndex((int) (radians * 10430.378F) & 65535); }
    public static float classicCos(float radians) { return sinIndex((int) (radians * 10430.378F + 16384F) & 65535); }
    private static float sinIndex(int index) { return (float) Math.sin(index * Math.PI * 2 / 65536); }
}

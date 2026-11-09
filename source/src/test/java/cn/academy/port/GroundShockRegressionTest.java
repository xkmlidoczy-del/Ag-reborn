package cn.academy.port;

import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.GroundShockWave;
import cn.academy.port.core.GroundShockWave.*;
import static cn.academy.port.core.GroundShockWave.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Deterministic source-order checks without booting Minecraft. Native behavior has separate GameTests. */
public final class GroundShockRegressionTest {
    private static int checks;
    private static void check(boolean test) { checks++; if (!test) throw new AssertionError("Check " + checks); }
    private static void equal(double expected, double actual) {
        checks++; if (!Double.isFinite(actual) || Math.abs(expected - actual) > .000001)
            throw new AssertionError("Check " + checks + ": " + expected + " != " + actual);
    }
    private static AbilityProgress ready(double mastery) {
        var state = new AbilityProgress(); state.selectCategory("vecmanip"); state.setLevel(1);
        state.activated = true; state.experience.put(GroundShockWave.ID, mastery); return state;
    }
    private static GroundShockWave.Random random(double d, float f) {
        return new GroundShockWave.Random() { public double nextDouble() { return d; } public float nextFloat() { return f; } };
    }
    private static Result wave(AbilityProgress state, Parameters p, FakeWorld world, double random) {
        return GroundShockWave.perform(state, p, world, random(random, .5F), 3.5, 1, 2.5, 0, 0, 1);
    }
    private static final class FakeWorld implements GroundShockWave.World {
        final Map<Cell, Block> blocks = new HashMap<>();
        final Map<Cell, List<Object>> targets = new HashMap<>();
        final List<Cell> queried = new ArrayList<>(), attempted = new ArrayList<>(), destroyed = new ArrayList<>(), drops = new ArrayList<>();
        final List<Object> attacked = new ArrayList<>();
        boolean allowed = true; float damage, vertical;
        void put(int x, int y, int z, Kind kind, double hardness) { blocks.put(new Cell(x, y, z), new Block(kind, hardness, false)); }
        public Block block(Cell cell) { queried.add(cell); return blocks.getOrDefault(cell, new Block(Kind.AIR, 0, false)); }
        public void convert(Cell cell, Kind replacement) {
            if (allowed) blocks.put(cell, new Block(replacement, replacement == Kind.COBBLESTONE ? 2 : .5, false));
        }
        public boolean canBreak(Cell cell) { attempted.add(cell); return allowed; }
        public void destroy(Cell cell, boolean drop) { if (blocks.remove(cell) != null) { destroyed.add(cell); if (drop) drops.add(cell); } }
        public Iterable<?> targets(Cell cell) { return targets.getOrDefault(cell, List.of()); }
        public void attackAndLaunch(Object target, float amount, float speed) { attacked.add(target); damage = amount; vertical = speed; }
    }

    public static void main(String[] args) {
        for (double mastery : new double[]{0, .25, .5, .75, 1}) {
            var p = parameters(mastery, .5F);
            equal(60 + 60 * mastery, p.energy()); equal(4 + 2 * mastery, p.damage());
            equal(80 + 70 * mastery, p.cp()); equal(15 - 5 * mastery, p.overload());
            equal((int) (cn.academy.port.core.SkillTuning.number("ground_shock", "max_steps_novice") + (cn.academy.port.core.SkillTuning.number("ground_shock", "max_steps_mastered") - cn.academy.port.core.SkillTuning.number("ground_shock", "max_steps_novice")) * mastery), p.iterations()); equal((int) (80 - 40 * mastery), p.cooldown());
            equal(.3F + (float) mastery * (1F - .3F), p.dropRate());
            equal((.6F + .5F * (.9F - .6F)) * (.8F + (float) mastery * (1.3F - .8F)), p.verticalSpeed());
        }
        check(!acceptsRelease(-1)); check(!acceptsRelease(0)); check(!acceptsRelease(4));
        check(acceptsRelease(5)); check(acceptsRelease(50)); check(acceptsRelease(200)); check(acceptsRelease(Long.MAX_VALUE));
        check(!mayStart(null)); var state = ready(0); check(mayStart(state)); state.level = 0; check(!mayStart(state));
        state.level = 1; state.interfering = true; check(!mayStart(state)); state.interfering = false;
        state.cooldowns.put(ID, 1); check(!mayStart(state)); state.cooldowns.clear(); state.overloadFine = false; check(!mayStart(state));
        state.overloadFine = true; state.activated = false; check(!mayStart(state)); state.activated = true;
        state.category = "teleporter"; check(!mayStart(state));
        equal(80, parameters(Double.NaN, 0).cp()); equal(150, parameters(2, 1).cp()); equal(15, parameters(-1, 0).overload());
        equal(.48F, parameters(0, 0).verticalSpeed()); equal(1.17F, parameters(1, 1).verticalSpeed());
        equal(.89399886F, classicSin(90F)); equal(-.4480692148F, classicCos(90F));

        // Preparation is free; consume is one shot, with captured values even if mastery changes.
        state = ready(0); var p = parameters(state.exp(ID), .5F); equal(1800, state.cp); equal(0, state.overload);
        state.experience.put(ID, 1.0); check(state.consume(p.cp(), p.overload(), false)); equal(1720, state.cp); equal(15, state.overload);
        var world = new FakeWorld(); var result = wave(state, p, world, .5);
        equal(80, state.cooldowns.get(ID)); equal(80, state.cooldownMaximum(ID)); equal(.001F, state.levelExperience); check(result.masteryClear());
        state = ready(0); state.cp = 79; check(!state.consume(p.cp(), p.overload(), false)); equal(79, state.cp); equal(0, state.overload);
        check(state.consume(p.cp(), p.overload(), true)); equal(79, state.cp); equal(0, state.overload);

        // All five central above-column attempts run even if the sampled ground is air/probability misses.
        state = ready(0); world = new FakeWorld(); world.put(3, 1, 3, Kind.OTHER, 2);
        world.put(2, 1, 3, Kind.OTHER, 1); result = wave(state, p, world, .99);
        check(world.destroyed.contains(new Cell(3, 1, 3))); check(world.blocks.containsKey(new Cell(2, 1, 3)));
        equal(58, result.energy()); equal(15 * p.iterations(), world.attempted.size()); equal(0, result.affected().size()); equal(.001F, state.exp(ID));

        // Source stone/grass conversion rates, untouched farmland, unique entity across overlapping cells.
        state = ready(0); world = new FakeWorld(); world.put(3, 0, 3, Kind.STONE, 1.5);
        world.put(2, 0, 3, Kind.GRASS, .6); world.put(3, 0, 4, Kind.FARMLAND, .6);
        Object entity = new Object(); world.targets.put(new Cell(3, 0, 3), List.of(entity)); world.targets.put(new Cell(2, 0, 3), List.of(entity));
        result = wave(state, p, world, .5);
        check(world.blocks.get(new Cell(3, 0, 3)).kind() == Kind.COBBLESTONE);
        check(world.blocks.get(new Cell(2, 0, 3)).kind() == Kind.DIRT);
        check(world.blocks.get(new Cell(3, 0, 4)).kind() == Kind.FARMLAND);
        equal(1, world.attacked.size()); equal(1, result.entities()); equal(.003F, state.exp(ID)); equal(4, world.damage);
        equal(p.verticalSpeed(), world.vertical); equal(58.3, result.energy());

        // A lateral ground cell still breaks center ground, not itself. Air after conversion does not erase affected list.
        state = ready(0); world = new FakeWorld(); world.put(3, 0, 3, Kind.STONE, 1.5);
        world.put(2, 0, 3, Kind.GRASS, .6); result = wave(state, p, world, 0);
        check(world.destroyed.contains(new Cell(3, 0, 3))); check(world.blocks.get(new Cell(2, 0, 3)).kind() == Kind.DIRT);
        check(result.affected().contains(new Cell(3, 0, 3))); equal(57.4, result.energy());

        // Farmland, liquids and negative hardness are never broken, including mastery clearing.
        state = ready(1); world = new FakeWorld(); world.put(3, 0, 3, Kind.FARMLAND, .6);
        world.put(3, 1, 3, Kind.OTHER, -1); world.blocks.put(new Cell(3, 2, 3), new Block(Kind.OTHER, 0, true));
        world.put(3, 3, 3, Kind.OTHER, 1000); result = wave(state, parameters(1, .5F), world, 0);
        check(world.blocks.containsKey(new Cell(3, 0, 3))); check(world.blocks.containsKey(new Cell(3, 1, 3)));
        check(world.blocks.containsKey(new Cell(3, 2, 3))); check(world.blocks.containsKey(new Cell(3, 3, 3)));

        // Target EXP can unlock the area-clear in this perform; its exact bounds use source until (exclusive upper).
        state = ready(.999); world = new FakeWorld(); world.put(3, 0, 3, Kind.OTHER, 1000);
        world.targets.put(new Cell(3, 0, 3), List.of(entity)); world.put(-2, 0, -3, Kind.OTHER, .5);
        world.put(7, 1, 6, Kind.OTHER, .5); world.put(8, 0, 2, Kind.OTHER, .5); world.put(3, 2, 2, Kind.OTHER, .5);
        result = wave(state, parameters(.999, .5F), world, .5); check(result.masteryClear()); equal(1, state.exp(ID));
        check(world.drops.contains(new Cell(-2, 0, -3))); check(world.drops.contains(new Cell(7, 1, 6)));
        check(world.blocks.containsKey(new Cell(8, 0, 2))); check(world.blocks.containsKey(new Cell(3, 2, 2)));
        equal(40, state.cooldowns.get(ID)); equal(40, state.cooldownMaximum(ID)); // .999 context truncates cooldown to 40 before EXP.
        state = ready(.9995); world = new FakeWorld(); world.put(-2, 0, -3, Kind.OTHER, .5);
        result = wave(state, parameters(.9995, .5F), world, .5); check(!result.masteryClear()); equal(1, state.exp(ID));
        check(world.blocks.containsKey(new Cell(-2, 0, -3)));
        state = ready(.99999999); world = new FakeWorld(); world.put(-2, 0, -3, Kind.OTHER, .5);
        result = wave(state, parameters(state.exp(ID), .5F), world, .5);
        check(result.masteryClear()); check(!world.blocks.containsKey(new Cell(-2, 0, -3)));

        // Energy depletion only stops the next outer iteration; remaining offsets in this one still process.
        state = ready(0); world = new FakeWorld(); world.put(3, 0, 3, Kind.FARMLAND, .6); world.put(2, 0, 3, Kind.STONE, 1.5);
        var many = new ArrayList<Object>(); for (int i = 0; i < 61; i++) many.add(new Object());
        world.targets.put(new Cell(3, 0, 3), many); result = wave(state, p, world, .5);
        equal(61, result.entities()); equal(-1.5, result.energy()); equal(2, result.affected().size());
        check(world.blocks.get(new Cell(2, 0, 3)).kind() == Kind.COBBLESTONE); equal(15, world.attempted.size());

        // Modern mutation protection is adapter-owned; blocked changes retain classic energy/EXP classification.
        state = ready(0); world = new FakeWorld(); world.allowed = false; world.put(3, 0, 3, Kind.STONE, 1.5);
        world.targets.put(new Cell(3, 0, 3), List.of(entity)); result = wave(state, p, world, 0);
        check(world.blocks.get(new Cell(3, 0, 3)).kind() == Kind.STONE); equal(58.6, result.energy()); equal(.003F, state.exp(ID));

        // Truncation at negative coordinates is deliberately not floor, including lateral vertical offsets.
        state = ready(0); world = new FakeWorld(); result = GroundShockWave.perform(state, p, world, random(.5, .5F),
                -3.9, 2.9, -2.9, 0, -.8, .6);
        check(world.queried.contains(new Cell(-3, 1, -1))); check(world.queried.contains(new Cell(-2, 0, -1)));
        // Native GameTests may run at large negative coordinates. Arrange around the source
        // integer anchor, using a signed fractional position, rather than assuming floor(x+.5).
        for (int[] anchor : new int[][]{{1003, 2, 1002}, {-2484101, -59, -6098332}, {0, 1, 0}, {-5, 1, 7}}) {
            int ax = anchor[0], ay = anchor[1], az = anchor[2];
            double px = ax + (ax < 0 ? -.5 : .5), pz = az + (az < 0 ? -.5 : .5);
            equal(ax, (int) px); equal(az, (int) pz);
            var firstCell = new Cell(ax, ay - 1, az + 1);
            state = ready(1); world = new FakeWorld(); world.blocks.put(firstCell, new Block(Kind.STONE, 1.5, false));
            world.targets.put(firstCell, List.of(entity));
            var inside = new Cell(ax - 5, ay - 1, az - 5);
            var outside = new Cell(ax + 5, ay - 1, az - 5);
            world.blocks.put(inside, new Block(Kind.DIRT, .5, false)); world.blocks.put(outside, new Block(Kind.DIRT, .5, false));
            result = GroundShockWave.perform(state, parameters(1, .5F), world, random(.5, .5F), px, ay, pz, 0, 0, 1);
            check(result.affected().contains(firstCell)); equal(1, result.entities()); equal(1, world.attacked.size());
            check(world.blocks.get(firstCell).kind() == Kind.COBBLESTONE);
            check(result.masteryClear()); check(!world.blocks.containsKey(inside)); check(world.blocks.containsKey(outside));
        }
        // A25-center mastered wave's lateral int-truncation may touch ground row26 at negative
        // absolute origins. It must never execute row26's CENTRAL above-column destruction.
        for(int sign:new int[]{1,-1}){
            int ax=sign*100,az=sign*100;state=ready(1);world=new FakeWorld();
            for(int n=1;n<=26;n++)world.put(ax,0,az+n,Kind.STONE,1.5);
            var lastAbove=new Cell(ax,1,az+25);var outsideAbove=new Cell(ax,1,az+26);
            world.blocks.put(lastAbove,new Block(Kind.STONE,1.5,false));world.blocks.put(outsideAbove,new Block(Kind.STONE,1.5,false));
            GroundShockWave.perform(state,parameters(1,.5F),world,random(.5,.5F),ax+sign*.5,1,az+sign*.5,0,0,1);
            check(world.blocks.get(new Cell(ax,0,az+25)).kind()==Kind.COBBLESTONE);
            check(world.blocks.get(new Cell(ax,0,az+26)).kind()==(sign<0?Kind.COBBLESTONE:Kind.STONE));
            check(!world.blocks.containsKey(lastAbove));check(world.blocks.get(outsideAbove).kind()==Kind.STONE);
            equal(25,parameters(1,.5F).iterations());
        }
        System.out.println("PASS " + checks + " GroundShock source-order/formula/terrain/target regression checks");
    }
}

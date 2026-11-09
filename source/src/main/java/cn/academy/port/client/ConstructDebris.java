package cn.academy.port.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * V-ADV-01 debris, presentation only: chips of the locked structure's own blocks and of the ground around it crack loose, fly and
 * bounce. Particles are never blocks and never touch the world; the real blocks are moved by the server and the physics backend.
 */
final class ConstructDebris {
    static final int SAMPLES = 48;
    private ConstructDebris() {}

    /** A block chip that bounces off the ground a few times before it settles. */
    static final class Chip extends TerrainParticle {
        private int bounces;
        Chip(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, BlockState state, BlockPos pos, float size) {
            super(level, x, y, z, xd, yd, zd, state, pos);
            this.xd = xd; this.yd = yd; this.zd = zd;
            this.scale(size);
            this.lifetime = 34 + level.random.nextInt(26);
        }
        @Override public void tick() {
            double before = this.yd;
            super.tick();
            if (this.onGround && before < -.1 && bounces < 3) {
                this.yd = -before * (.62 - .1 * bounces); this.xd *= 1.1; this.zd *= 1.1; this.onGround = false; bounces++;
            }
        }
    }

    /** A chip that flies a fixed number of ticks along an arc from where it broke off to a point on the target, then vanishes into the block that appears there. */
    static final class GatherChip extends TerrainParticle {
        private final Vec3 from, to; private final int flight; private final double arc;
        GatherChip(ClientLevel level, Vec3 from, Vec3 to, int flight, double arc, BlockState state, BlockPos pos, float size) {
            super(level, from.x, from.y, from.z, 0, 0, 0, state, pos);
            this.from = from; this.to = to; this.flight = Math.max(1, flight); this.arc = arc;
            this.gravity = 0; this.hasPhysics = false; this.lifetime = this.flight + 1;
            this.scale(size);
        }
        @Override public void tick() {
            this.xo = this.x; this.yo = this.y; this.zo = this.z;
            if (this.age++ >= this.flight) { this.remove(); return; }
            double u = (double) this.age / this.flight, e = u * u * (3 - 2 * u);
            this.setPos(from.x + (to.x - from.x) * e, from.y + (to.y - from.y) * e + arc * Math.sin(Math.PI * e), from.z + (to.z - from.z) * e);
        }
    }

    /** The thrown structure bursts: a shower of its own blocks from everywhere in its bounds, thrown on along the flight, plus dust and flashes. */
    static void shatter(ClientLevel level, List<Sample> samples, Vec3 min, Vec3 max, Vec3 dir, int count) {
        RandomSource r = level.random; Vec3 c = min.add(max).scale(.5);
        for (int i = 0; i < count; i++) {
            Vec3 at = new Vec3(min.x + r.nextDouble() * (max.x - min.x), min.y + r.nextDouble() * (max.y - min.y), min.z + r.nextDouble() * (max.z - min.z));
            Vec3 out = at.subtract(c); double len = Math.max(1e-6, out.length()); out = out.scale(1 / len);
            double v = .25 + r.nextDouble() * .5;
            BlockState state; BlockPos pos;
            if (samples != null && !samples.isEmpty()) { var s = samples.get(r.nextInt(samples.size())); state = s.state(); pos = s.pos(); }
            else { pos = BlockPos.containing(at); state = level.getBlockState(pos); }
            chip(level, state, pos, at, new Vec3(out.x * v + dir.x * .5, out.y * v + dir.y * .3 + .15 + r.nextDouble() * .3, out.z * v + dir.z * .5), 1.0f + r.nextFloat() * 1.3f);
        }
        for (int i = 0; i < 24; i++)
            level.addParticle(net.minecraft.core.particles.ParticleTypes.POOF, c.x + (r.nextDouble() - .5) * (max.x - min.x), c.y + (r.nextDouble() - .5) * (max.y - min.y), c.z + (r.nextDouble() - .5) * (max.z - min.z),
                    (r.nextDouble() - .5) * .3, r.nextDouble() * .2, (r.nextDouble() - .5) * .3);
        for (int i = 0; i < 3; i++) level.addParticle(net.minecraft.core.particles.ParticleTypes.EXPLOSION, c.x + (r.nextDouble() - .5) * 3, c.y + (r.nextDouble() - .5) * 3, c.z + (r.nextDouble() - .5) * 3, 0, 0, 0);
    }

    /** Pieces that broke off the structure fly to points on the shell around the target and are gone the tick the real blocks appear there. */
    static void gather(ClientLevel level, List<Sample> samples, Vec3 from, Vec3 tmin, Vec3 tmax, int flight, int count) {
        RandomSource r = level.random;
        double x0 = Math.floor(tmin.x) - 1, y0 = Math.floor(tmin.y) - 1, z0 = Math.floor(tmin.z) - 1, x1 = Math.floor(tmax.x) + 2, y1 = Math.floor(tmax.y) + 2, z1 = Math.floor(tmax.z) + 2;
        for (int i = 0; i < count; i++) {
            // A random point on the surface of the shell's bounding box.
            int face = r.nextInt(6); double px = x0 + r.nextDouble() * (x1 - x0), py = y0 + r.nextDouble() * (y1 - y0), pz = z0 + r.nextDouble() * (z1 - z0);
            switch (face) { case 0 -> px = x0 + .5; case 1 -> px = x1 - .5; case 2 -> py = y0 + .5; case 3 -> py = y1 - .5; case 4 -> pz = z0 + .5; default -> pz = z1 - .5; }
            Vec3 start = from.add((r.nextDouble() - .5) * 4, (r.nextDouble() - .5) * 4, (r.nextDouble() - .5) * 4);
            BlockState state; BlockPos pos;
            if (samples != null && !samples.isEmpty()) { var s = samples.get(r.nextInt(samples.size())); state = s.state(); pos = s.pos(); }
            else { state = Blocks.STONE.defaultBlockState(); pos = BlockPos.containing(start); }
            Minecraft.getInstance().particleEngine.add(new GatherChip(level, start, new Vec3(px, py, pz), flight + r.nextInt(3) - 1, 1.5 + r.nextDouble() * 2, state, pos, 2.2f + r.nextFloat() * 1.2f));
        }
    }

    /** Remembers up to {@link #SAMPLES} (position, state) pairs of the structure's solid blocks, taken while they are still in the world. */
    record Sample(BlockPos pos, BlockState state) {}

    static List<Sample> sample(ClientLevel level, Vec3 min, Vec3 max) {
        List<Sample> out = new ArrayList<>(); RandomSource r = level.random;
        int x0 = (int) Math.floor(min.x), y0 = (int) Math.floor(min.y), z0 = (int) Math.floor(min.z);
        int x1 = (int) Math.ceil(max.x) - 1, y1 = (int) Math.ceil(max.y) - 1, z1 = (int) Math.ceil(max.z) - 1;
        if (x1 < x0 || y1 < y0 || z1 < z0 || (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 1_000_000) return out;
        for (int i = 0; i < SAMPLES * 6 && out.size() < SAMPLES; i++) {
            var pos = new BlockPos(x0 + r.nextInt(x1 - x0 + 1), y0 + r.nextInt(y1 - y0 + 1), z0 + r.nextInt(z1 - z0 + 1));
            var state = level.getBlockState(pos);
            if (!state.isAir() && state.getRenderShape() != net.minecraft.world.level.block.RenderShape.INVISIBLE) out.add(new Sample(pos, state));
        }
        return out;
    }

    private static void chip(ClientLevel level, BlockState state, BlockPos pos, Vec3 at, Vec3 vel, float size) {
        if (state == null || state.isAir()) state = Blocks.STONE.defaultBlockState();
        Minecraft.getInstance().particleEngine.add(new Chip(level, at.x, at.y, at.z, vel.x, vel.y, vel.z, state, pos, size));
    }

    /** Chips peel off the structure's own blocks, outward from its axis. (Vanilla break bursts are avoided: with the block still in place they stick inside it.) */
    static void crack(ClientLevel level, List<Sample> samples, Vec3 min, Vec3 max, int count, double power) {
        if (samples == null || samples.isEmpty() || count <= 0) return;
        RandomSource r = level.random; Vec3 c = min.add(max).scale(.5);
        for (int i = 0; i < count; i++) {
            var s = samples.get(r.nextInt(samples.size()));
            Vec3 at = new Vec3(s.pos().getX() + .2 + r.nextDouble() * .6, s.pos().getY() + .2 + r.nextDouble() * .6, s.pos().getZ() + .2 + r.nextDouble() * .6);
            Vec3 out = new Vec3(at.x - c.x, 0, at.z - c.z); double len = Math.max(1e-6, out.length()); out = out.scale(1 / len);
            double v = power * (.12 + r.nextDouble() * .22);
            chip(level, s.state(), s.pos(), at, new Vec3(out.x * v + (r.nextDouble() - .5) * .08, power * (.12 + r.nextDouble() * .3), out.z * v + (r.nextDouble() - .5) * .08), 1.4f + r.nextFloat() * 1.6f);
        }
    }

    /** Ground around the footprint heaves: chips of the surface blocks fly up and out of a ring around the structure and bounce. */
    static void heave(ClientLevel level, Vec3 min, Vec3 max, int count, double power) {
        RandomSource r = level.random; Vec3 c = min.add(max).scale(.5);
        double hx = (max.x - min.x) / 2 + 1, hz = (max.z - min.z) / 2 + 1;
        int floorY = (int) Math.floor(min.y) - 1;
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Math.PI * 2, reach = 1 + r.nextDouble() * 3.5;
            double dx = Math.cos(a), dz = Math.sin(a);
            // Point on a rectangle ring a little outside the footprint.
            double t = Math.min(hx / Math.max(1e-6, Math.abs(dx)), hz / Math.max(1e-6, Math.abs(dz)));
            double px = c.x + dx * (t + reach), pz = c.z + dz * (t + reach);
            var pos = new BlockPos((int) Math.floor(px), floorY, (int) Math.floor(pz));
            var state = level.getBlockState(pos);
            if (state.isAir()) { pos = pos.below(); state = level.getBlockState(pos); }
            if (state.isAir()) continue;
            double v = power * (.1 + r.nextDouble() * .2);
            chip(level, state, pos, new Vec3(px, pos.getY() + 1.05, pz), new Vec3(dx * v, power * (.25 + r.nextDouble() * .4), dz * v), 1.0f + r.nextFloat() * .9f);
        }
    }

    /** Kick-off burst under the caster's feet: the stomp of the crouch and the launch of the dash. */
    static void feet(ClientLevel level, Vec3 feet, Vec3 dir, int count, double power) {
        RandomSource r = level.random;
        var pos = BlockPos.containing(feet.x, feet.y - .5, feet.z);
        var state = level.getBlockState(pos);
        if (state.isAir()) state = level.getBlockState(pos = pos.below());
        if (state.isAir()) return;
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Math.PI * 2, v = power * (.06 + r.nextDouble() * .16);
            Vec3 vel = new Vec3(Math.cos(a) * v - dir.x * power * .1, power * (.15 + r.nextDouble() * .3), Math.sin(a) * v - dir.z * power * .1);
            chip(level, state, pos, new Vec3(feet.x + (r.nextDouble() - .5) * .8, feet.y + .05, feet.z + (r.nextDouble() - .5) * .8), vel, .8f + r.nextFloat() * .7f);
        }
    }
}

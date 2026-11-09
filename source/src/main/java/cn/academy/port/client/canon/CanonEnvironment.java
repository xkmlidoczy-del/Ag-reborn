package cn.academy.port.client.canon;

import static cn.academy.port.client.canon.CanonFxScripts.*;

import cn.academy.port.canon.CanonVfx;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.Vec3;

/**
 * How the world answers a skill: debris and dust take the colour of the block that was actually hit, water surfaces splash instead
 * of cracking, grass and flowers shed green bits, and each system leaves its own mark (scorch, molten glass, frost, ink, cracks,
 * folds, paper scraps). Area skills raise their reaction at their real radius, wind sweeps dust along the ground in its direction,
 * dashes kick up a wake only where they skim the ground. Fired once per hit/release; strength follows the skill level.
 * Presentation only: no block or entity is changed.
 */
final class CanonEnvironment {
    enum Kind { SHARD, FROST, WIND, MIND, FLOAT, PAPER, INK, SCORCH, MOLTEN, FOLD, CRACK }
    private static final Set<String> WAKES = Set.of("dark_step", "dark_wing_charge", "nitrogen_dash", "nitrogen_ram", "nitrogen_rocket", "nitrogen_brake",
            "aero_jet", "aero_side_step", "aero_backstep", "aero_double_jet", "aero_vertical_jet", "aero_updraft",
            "jet_engine", "vec_accel", "magnetic_ground_step", "mag_movement", "reverse_jet_brake", "phase_sidestep", "flashing", "phase_corridor");
    /** Strikes whose impact really lands on the ground or a body (classic): the impact reaction fires at the end point. */
    private static final Set<String> CLASSIC_IMPACT = Set.of("arc_gen", "forked_arc", "thunder_bolt", "thunder_net", "iron_sand_lance", "conductive_circuit",
            "electron_bomb", "meltdowner", "ray_barrage", "electron_missile", "dual_point", "point_meltdown", "beam_etching",
            "threatening_teleport", "flesh_ripping", "shift_tp", "dir_shock", "dir_blast", "blood_retro", "deflection_strike", "yaw_palm");
    private static final Set<String> CLASSIC_AREA = Set.of("thunder_bolt", "ground_shock", "beam_step", "location_teleport", "inertial_anchor");
    private static final float[] WATER = {.55f, .75f, 1f}, LEAF = {.36f, .62f, .28f};
    private static int serial;
    private CanonEnvironment() {}

    static Kind kind(String category) {
        return switch (category) {
            case "dark_matter" -> Kind.SHARD; case "nitrogen_armor" -> Kind.FROST; case "aero_hand" -> Kind.WIND; case "mental_out" -> Kind.MIND;
            case "float_dial" -> Kind.FLOAT; case "paper_psychokinesis" -> Kind.PAPER; case "liquid_shadow" -> Kind.INK;
            case "electromaster" -> Kind.SCORCH; case "meltdowner" -> Kind.MOLTEN; case "teleporter" -> Kind.FOLD; default -> Kind.CRACK;
        };
    }

    // ------------------------------------------------------------------ feeds
    /** Canon visuals: HIT lands an impact at the end point; START of an area raises its ring; dashes leave a wake. */
    static void canon(CanonVfx.Visual v, String category, int tier, Vec3 feet) {
        if (category.equals("hydro_hand") || CanonFxLiquid.FIELD_SKILL.equals(v.skill())) return; // Dedicated splash and traces.
        if ("aero_ground_blast".equals(v.skill())
                && !cn.academy.port.highpower.environment.SkillEnvironment.showsDebris()) return;
        Kind k = kind(category); double power = .55 + .18 * Math.max(1, Math.min(5, tier));
        if (v.phase() == CanonVfx.Phase.HIT) {
            if (k == Kind.WIND) gust(v.endpoint(), v.endpoint().subtract(v.origin()), 1.5 + tier * .4, power);
            else impact(v.endpoint(), power, k, 0);
        } else if (v.phase() == CanonVfx.Phase.START) {
            if (v.radius() > 2 && feet != null && k != Kind.MIND) ring(feet, Math.min(16, v.radius()), power * .8, k);
            if (WAKES.contains(v.skill())) wake(v.origin(), v.endpoint(), power, k);
            if (k == Kind.WIND && v.radius() <= 2 && !WAKES.contains(v.skill())) gust(v.origin().add(v.endpoint().subtract(v.origin()).scale(.5)), v.endpoint().subtract(v.origin()), 2, power * .7);
        }
    }

    /** Classic releases (the system's own decals and bursts stay; this adds the block-true reaction). */
    static void classic(String skill, String category, int tier, Vec3 hand, Vec3 end, Vec3 ground, Vec3 feet) {
        Kind k = kind(category); double power = .55 + .18 * tier;
        int arrive = (int) Math.min(6, end.distanceTo(hand) / 6);
        if (CLASSIC_IMPACT.contains(skill)) impact(end, power * (skill.equals("dir_blast") || skill.equals("thunder_bolt") ? 1.4 : 1), k, arrive);
        if (CLASSIC_AREA.contains(skill)) ring(skill.equals("thunder_bolt") ? ground : feet, switch (skill) { case "thunder_bolt" -> 8;
            case "ground_shock" -> 3; default -> 1.6; }, power, k);
        if (WAKES.contains(skill)) wake(feet, end, power, k);
        switch (skill) {
            case "ground_shock" -> { Vec3 f = flat(end.subtract(hand)); for (int i = 1; i <= 5; i++) impact(feet.add(f.scale(i * 1.8)), power * (1.1 - i * .12), Kind.CRACK, i * 2); }
            case "dir_shock", "dir_blast" -> gust(feet.add(flat(end.subtract(hand)).scale(1.5)), end.subtract(hand), 2.5, power);
            case "vec_accel" -> gust(feet, hand.subtract(end), 1.5, power * .8);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ reactions
    /** One hit at {@code at}: block-coloured chunks and dust, a splash on water, plant bits on grass, and the system's own mark. */
    static void impact(Vec3 at, double power, Kind k, int delay) {
        if (k == Kind.MIND) return;
        var mc = Minecraft.getInstance(); if (mc.level == null) return;
        Double y = CanonDecals.surfaceAt(at.add(0, .4, 0)); if (y == null) { air(at, power, k, delay); return; }
        boolean nearGround = at.y - y < 2.2; if (!nearGround) { air(at, power, k, delay); return; }
        Vec3 g = new Vec3(at.x, y + .03, at.z); BlockPos pos = BlockPos.containing(g.x, y - .01, g.z), above = pos.above();
        var state = mc.level.getBlockState(pos); boolean water = !mc.level.getFluidState(pos).isEmpty() || !mc.level.getFluidState(above).isEmpty();
        float[] tint = lit(water ? WATER : dust(state, pos), above);
        if (k == Kind.FLOAT) {
            for (int i = 0; i < 5; i++) CanonParticles.spawn("ah_dust_sheet", i, g.add((rnd(i + serial) - .5) * .8, .15, (rnd(i + serial + 9) - .5) * .8),
                    new Vec3(0, .025, 0), -.001, .05, .35, .01, 28, tint, .25f, CanonFx4.Mode.ALPHA, CanonParticles.Land.NONE, false, delay);
            serial++; return;
        }
        int n = (int) (6 + 10 * power); serial++;
        if (water) {
            for (int i = 0; i < n; i++) { double a = rnd(serial * 41 + i) * 6.28, s = .08 + .14 * power * rnd(serial * 7 + i);
                CanonParticles.spawn("hh_drop_atlas", i % 4, g.add(0, .1, 0), new Vec3(Math.cos(a) * s * .6, .18 + .25 * power * rnd(i + serial), Math.sin(a) * s * .6), .045, .02, .09, 0, 22,
                        tint, .9f, CanonFx4.Mode.ALPHA, CanonParticles.Land.SPLASH, true, delay); }
            CanonParticles.spawn("hh_splash_sheet", 0, g.add(0, .5 * power, 0), Vec3.ZERO, 0, 0, 1.2 * power, 0, 14, tint, .85f, CanonFx4.Mode.ALPHA, CanonParticles.Land.NONE, false, delay);
            CanonDecals.add("hh_wet_decal", g, .8 + .6 * power, WATER, .55f, 60);
            return;
        }
        // chunks of the real block, a low dust cloud in its colour, plant bits when there is foliage
        for (int i = 0; i < n; i++) { double a = rnd(serial * 31 + i) * 6.28, s = .05 + .11 * power * rnd(serial * 13 + i);
            CanonParticles.spawn("vm_debris_atlas", i % 16, g.add(0, .08, 0), new Vec3(Math.cos(a) * s, .12 + .22 * power * rnd(serial * 3 + i), Math.sin(a) * s),
                    .04, .015, .09 + .08 * rnd(i) * power, .3, 34 + (int) (24 * rnd(i + 4)), shade(tint, 1.15 + .3 * rnd(i)), 1f, CanonFx4.Mode.ALPHA, CanonParticles.Land.STOP, false, delay); }
        for (int i = 0; i < 4 + (int) (3 * power); i++) { double a = i * 1.3 + rnd(serial + i) * .6;
            CanonParticles.spawn("ah_dust_sheet", i % 16, g.add(Math.cos(a) * .3, .2, Math.sin(a) * .3), new Vec3(Math.cos(a) * .035 * power, .012, Math.sin(a) * .035 * power),
                    -.0015, .05, .8 + .7 * power, .02, 40, shade(tint, 1.35), .72f, CanonFx4.Mode.ALPHA, CanonParticles.Land.NONE, false, delay + 1); }
        if (state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) || mc.level.getBlockState(above).is(BlockTags.REPLACEABLE_BY_TREES) || mc.level.getBlockState(above).is(BlockTags.FLOWERS) || state.is(BlockTags.LEAVES))
            for (int i = 0; i < 6; i++) { double a = rnd(serial * 5 + i) * 6.28;
                CanonParticles.spawn("dm_feather_atlas", i % 8, g.add(0, .3, 0), new Vec3(Math.cos(a) * .06, .1 + .08 * rnd(i), Math.sin(a) * .06), .012, .05, .07, .4, 40,
                        lit(LEAF, above), .95f, CanonFx4.Mode.ALPHA, CanonParticles.Land.STOP, false, delay); }
        mark(g, power, k);
    }

    /** Area reaction along a ring of radius {@code r}: a ring of small impacts and one low dust wave in the ground colour. */
    static void ring(Vec3 centre, double r, double power, Kind k) {
        int n = (int) Math.min(14, 5 + r); for (int i = 0; i < n; i++) { double a = i * 6.283 / n;
            Vec3 p = centre.add(Math.cos(a) * r, .6, Math.sin(a) * r); if (k == Kind.FLOAT || k == Kind.MIND) continue;
            if (i % 2 == 0) impact(p, power * .55, k == Kind.WIND ? Kind.CRACK : k, (int) (r * .6)); }
        if (k == Kind.FLOAT) for (int i = 0; i < n; i++) { double a = i * 6.283 / n;
            impact(centre.add(Math.cos(a) * r, .2, Math.sin(a) * r), power * .35, k, (int) (r * .4)); }
    }

    /** Wind: dust and plant bits swept along the ground in {@code dir}, in the colour of the ground under them. */
    static void gust(Vec3 at, Vec3 dir, double width, double power) {
        var mc = Minecraft.getInstance(); if (mc.level == null) return; Vec3 f = flat(dir), r = rightOf(f); serial++;
        for (int i = 0; i < 10; i++) { Vec3 p = at.add(r.scale((rnd(serial * 9 + i) - .5) * 2 * width)).add(f.scale((rnd(serial * 4 + i) - .5) * width));
            Double y = CanonDecals.surfaceAt(p.add(0, 1, 0)); if (y == null || p.y - y > 3) continue;
            var pos = BlockPos.containing(p.x, y - .01, p.z); float[] tint = lit(dust(mc.level.getBlockState(pos), pos), pos.above());
            CanonParticles.spawn("ah_dust_sheet", i % 16, new Vec3(p.x, y + .25, p.z), f.scale(.12 + .1 * power).add(0, .02, 0), -.001, .04, .7 + .4 * power, .05, 30, shade(tint, 1.35), .65f,
                    CanonFx4.Mode.ALPHA, CanonParticles.Land.NONE, true, i / 3); }
    }

    /** A dash skimming the ground stirs dust at its start and along its path; airborne stretches leave nothing. */
    static void wake(Vec3 from, Vec3 to, double power, Kind k) {
        var mc = Minecraft.getInstance(); if (mc.level == null) return; Vec3 d = to.subtract(from); int n = (int) Math.min(10, 2 + d.length());
        for (int i = 0; i <= n; i++) { Vec3 p = from.add(d.scale(i / (double) n)); Double y = CanonDecals.surfaceAt(p.add(0, .3, 0)); if (y == null || p.y - y > 1.4) continue;
            var pos = BlockPos.containing(p.x, y - .01, p.z); float[] tint = lit(k == Kind.FROST ? new float[]{.9f, .97f, 1f} : dust(mc.level.getBlockState(pos), pos), pos.above());
            CanonParticles.spawn(k == Kind.FROST ? "na_vapor_sheet" : "ah_dust_sheet", i % 16, new Vec3(p.x, y + .15, p.z), d.normalize().scale(-.03).add(0, .015, 0), -.001, .05,
                    .6 + .35 * power * (i == 0 ? 1.6 : 1), .02, 28, shade(tint, 1.35), .65f, CanonFx4.Mode.ALPHA, CanonParticles.Land.NONE, false, i);
            if (k == Kind.FROST && i % 2 == 0) CanonDecals.add("na_frost_decal", new Vec3(p.x, y, p.z), .5, new float[]{1, 1, 1}, .6f, 80); }
    }

    // ------------------------------------------------------------------ marks and mid-air hits
    private static void mark(Vec3 g, double power, Kind k) {
        float[] w = {1, 1, 1}; double rad = .5 + .45 * power;
        switch (k) {
            case SCORCH -> CanonDecals.add("em_scorch_decal", g, rad, w, .75f, 140);
            case MOLTEN -> { CanonDecals.add("md_molten_decal", g, rad * .9, w, .8f, 150); CanonDecals.light("common_ground_mask", g, rad * 1.3, new float[]{.43f, 1f, .59f}, .3f, 30); }
            case FROST -> CanonDecals.add("na_frost_decal", g, rad * 1.3, w, .8f, 160);
            case INK -> CanonDecals.add("ls_pool_decal", g, rad, w, .8f, 120);
            case FOLD -> CanonDecals.add("tp_fold_decal", g, rad * .8, w, .6f, 60);
            case CRACK -> CanonDecals.add("vm_crack_decal", g, rad * 1.2, w, .8f, 150);
            case PAPER -> { for (int i = 0; i < 5; i++) { double a = rnd(serial + i * 3) * 6.28;
                CanonParticles.spawn("pp_scrap_sheet", i * 9, g.add(0, .4, 0), new Vec3(Math.cos(a) * .05, .12, Math.sin(a) * .05), .01, .06, .12, .3, 50, new float[]{1f, .95f, .85f}, 1f,
                        CanonFx4.Mode.ALPHA, CanonParticles.Land.STOP, false, 0); } }
            case SHARD -> { for (int i = 0; i < 4; i++) { double a = rnd(serial + i * 5) * 6.28;
                CanonParticles.spawn("dm_shard_atlas", i, g.add(0, .2, 0), new Vec3(Math.cos(a) * .04, .1, Math.sin(a) * .04), .03, .02, .1, .5, 60, new float[]{.97f, .97f, 1f}, 1f,
                        CanonFx4.Mode.ALPHA, CanonParticles.Land.STOP, false, 0); } }
            default -> { }
        }
    }
    /** A hit with no ground under it: only what the system itself sheds (sparks, embers, frost flakes, feathers, ink drips). */
    private static void air(Vec3 at, double power, Kind k, int delay) {
        String tex; float[] c; CanonFx4.Mode mode = CanonFx4.Mode.ALPHA; double g = .02;
        switch (k) {
            case SCORCH -> { tex = "em_spark_sheet"; c = new float[]{.9f, .97f, 1f}; mode = CanonFx4.Mode.ADD; g = .01; }
            case MOLTEN -> { tex = "md_ember_sheet"; c = new float[]{.6f, 1f, .7f}; mode = CanonFx4.Mode.ADD; g = -.002; }
            case FROST -> { tex = "na_frost_atlas"; c = new float[]{.92f, .98f, 1f}; }
            case SHARD -> { tex = "dm_feather_atlas"; c = new float[]{.97f, .97f, 1f}; g = .008; }
            case INK -> { tex = "ls_drip_sheet"; c = new float[]{.16f, .16f, .22f}; g = .04; }
            case PAPER -> { tex = "pp_scrap_sheet"; c = new float[]{1f, .95f, .85f}; g = .01; }
            default -> { return; }
        }
        serial++; for (int i = 0; i < 6 + (int) (4 * power); i++) { double a = rnd(serial * 11 + i) * 6.28, b = (rnd(serial * 3 + i) - .5) * 2;
            CanonParticles.spawn(tex, i, at, new Vec3(Math.cos(a), b, Math.sin(a)).scale(.06 + .06 * power), g, .04, .09, .3, 26, c, .95f, mode, CanonParticles.Land.STOP, false, delay); }
    }
    /** Debris and dust are not emissive: dim them with the real light at the spot (sky light follows the time of day). */
    private static float[] lit(float[] c, BlockPos at) {
        var mc = Minecraft.getInstance(); if (mc.level == null) return c;
        // ClientLevel's no-argument accessor is a server-maintained cache; its float overload follows client time/weather.
        double sky = mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, at) / 15.0 * mc.level.getSkyDarken(1f),
                block = mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, at) / 15.0;
        return shade(c, .18 + .82 * Math.max(sky, block));
    }
    /** What a hit actually throws up: soil from grass, mycelium and podzol (their map colour is the top), the block's own colour otherwise. */
    private static float[] dust(net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
        if (state.is(BlockTags.DIRT)) return rgb(net.minecraft.world.level.material.MapColor.DIRT.col);
        return rgb(state.getMapColor(Minecraft.getInstance().level, pos).col);
    }
    private static float[] rgb(int col) { if (col == 0) return new float[]{.55f, .52f, .48f}; return new float[]{(col >> 16 & 255) / 255f, (col >> 8 & 255) / 255f, (col & 255) / 255f}; }
    private static float[] shade(float[] c, double s) { return new float[]{(float) Math.min(1, c[0] * s), (float) Math.min(1, c[1] * s), (float) Math.min(1, c[2] * s)}; }
}

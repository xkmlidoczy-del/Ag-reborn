package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Billboard;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Stroke;
import cn.academy.port.highpower.core.CastPose;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Level-tiered skill cues for skills whose own effect is faint, hidden or instantaneous (found by reading the real peak frames of all
 * 96 release shots, the way the world boss effects were reviewed). Three cues, each gaining one layer per skill level so LV1 is a clean
 * base and LV5 is the full grand version, in the tech style of {@link CastSignatureDecor} (reticles, rings, grids, data marks):
 * <ul>
 * <li>{@link Kind#BEAM}: a visible shot. While held a thin guide core with travelling beads; on release a muzzle flash, a bright core
 *     line to the aim point and an impact mark. LV2 adds colour fringing, LV3 a travelling pulse, LV4 impact sparks and a second ring,
 *     LV5 a spoke burst and a doubled core;</li>
 * <li>{@link Kind#AURA}: a persistent state cue for buff, shield and movement skills. Rings rise through the body; LV3 adds orbiting
 *     motes and the category motif, LV4 a ground ring and corner pillars, LV5 a halo above the head;</li>
 * <li>{@link Kind#MARK}: a beacon where a mark, placement or anchor lands. LV1 ground ring, then pillar, brackets, rising motes and a
 *     collapsing frame for LV5.</li>
 * </ul>
 * Teleport departure and arrival are handled by {@link TeleportTrace}. Pure functions of their arguments; drawing only.
 */
public final class SkillFxKit {
    public enum Kind { NONE, BEAM, AURA, MARK }
    public static final int MAX_QUADS = 900;

    private static final Set<String> BEAM = Set.of("arc_gen", "forked_arc", "electron_bomb", "dual_point", "beam_etching", "mine_ray_basic", "mine_ray_expert",
            "mine_ray_luck", "point_meltdown", "meltdowner", "ray_barrage", "deflection_strike", "dir_shock", "iron_sand_lance", "particle_laminate",
            "folded_mirror_array", "polarity_tether", "yaw_palm");
    private static final Set<String> AURA = Set.of("charging", "mag_movement", "body_intensify", "magnetic_bracer", "grounding_return", "potential_buffer",
            "pulse_shield", "magnetic_ground_step", "magnetic_domain", "magnetic_item_pull", "mine_detect", "rail_relay", "light_shield", "thermal_shell",
            "reverse_jet_brake", "beam_step", "jet_engine", "zero_gap_armor", "scatter_plates", "directional_dissipation_curtain", "multicore_canopy",
            "vec_accel", "vec_deviation", "vec_reflection", "storm_wing", "circulation_listening", "circulation_relief", "wind_pressure_lift", "impulse_split",
            "inertial_anchor", "vector_parry", "braking_touch", "multi_axis_reflection", "autonomous_vector_circle");
    private static final Set<String> MARK = Set.of("mark_teleport", "return_mark", "recovery_mark", "location_teleport", "scatter_locator_plate",
            "charge_decoy", "conductive_circuit");

    private SkillFxKit() {}

    /** Cue of a skill; other players' casts carry no skill id, so the gesture decides. */
    public static Kind kind(String skill, CastPose.Archetype action) {
        if (skill != null && !skill.isEmpty()) {
            if (BEAM.contains(skill)) return Kind.BEAM;
            if (AURA.contains(skill)) return Kind.AURA;
            if (MARK.contains(skill)) return Kind.MARK;
            return Kind.NONE;
        }
        if (action == null) return Kind.NONE;
        return switch (action) {
            case AIM, LANCE -> Kind.BEAM;
            case GUARD, BRAKE, FIELD_TUNE, VECTOR_DOMAIN, CANOPY, ACCELERATOR, FLIGHT, LISTEN, LIFT -> Kind.AURA;
            case PLACE, GRID_DEPLOY, PHASE_GATE -> Kind.MARK;
            default -> Kind.NONE;
        };
    }

    /** How far from the hand the aim point is searched, in blocks. */
    public static double range(String skill) { return skill != null && skill.startsWith("mine_ray") ? 24 : 28; }

    private static int rgb(String category) {
        return switch (category) {
            case "electromaster" -> CastSignatureDecor.EM; case "meltdowner" -> CastSignatureDecor.MD;
            case "teleporter" -> CastSignatureDecor.TP; case "vecmanip" -> CastSignatureDecor.VM; default -> CastSignatureDecor.NEUTRAL;
        };
    }

    /**
     * @param end  aim point (block hit or maximum range) for BEAM and MARK
     * @param age  ticks since the cast began; {@code since} ticks since release, or -1 while held
     */
    public static FxLib.Parts fx(Kind kind, String category, int level, Point feet, Point hand, Point look, Point end, double age, double since, long seed, double time, VisualTier tier) {
        if (kind == Kind.NONE || tier == null || !tier.enhanced() || feet == null || hand == null || look == null || end == null
                || !feet.finite() || !hand.finite() || !look.finite() || !end.finite() || !Double.isFinite(age) || !Double.isFinite(since) || !Double.isFinite(time)) return FxLib.Parts.EMPTY;
        int lv = Math.max(1, Math.min(5, level));
        double fade = since < 0 ? 1 : Math.max(0, 1 - since / (kind == Kind.AURA ? 24.0 : kind == Kind.MARK ? 22.0 : 12.0));
        if (fade <= 0) return FxLib.Parts.EMPTY;
        int color = rgb(category);
        var out = new FxLib.Builder();
        switch (kind) {
            case BEAM -> beam(out, category, lv, color, hand, look, end, age, since, seed, time, fade, tier);
            case AURA -> aura(out, category, lv, color, feet, age, since, seed, time, fade, tier);
            case MARK -> mark(out, category, lv, color, feet, end, age, since, seed, time, fade, tier);
            default -> { }
        }
        return out.build();
    }

    // ---- BEAM --------------------------------------------------------------------------------------------------------------

    private static void beam(FxLib.Builder out, String category, int lv, int color, Point hand, Point look, Point end, double age, double since, long seed, double time, double fade, VisualTier tier) {
        Point d = end.minus(hand);
        double length = d.length();
        if (length < .5) return;
        Point dir = d.scale(1 / length);
        double w = tier.widthScale, held = since < 0 ? 1 : 0;
        double build = Math.min(1, age / 4.0);
        var strokes = new ArrayList<Stroke>();
        // Core line: thin and dim while held (a guide), bright and thick right after release.
        double coreAlpha = held > 0 ? 120 * build : 255 * fade, glowAlpha = held > 0 ? 55 * build : 150 * fade;
        double coreWidth = (held > 0 ? .008 : .016 + .006 * lv) * w;
        CastSignatureDecor.line(strokes, hand, end, coreWidth * 3.4, color, (int) glowAlpha);
        CastSignatureDecor.line(strokes, hand, end, coreWidth, 0xFFFFFF, (int) coreAlpha);
        if (lv >= 5 && held == 0) CastSignatureDecor.line(strokes, hand, end, coreWidth * 6, color, (int) (60 * fade));
        // Travelling beads: a data packet running to the target, faster at higher level.
        int beads = held > 0 ? 3 : 2 + lv;
        var sprites = new ArrayList<Billboard>();
        double speed = (.035 + .012 * lv);
        for (int i = 0; i < beads; i++) {
            double p = ((time * speed + i / (double) beads) % 1 + 1) % 1;
            Point at = hand.plus(dir.scale(length * p));
            sprites.add(new Billboard(at, (.05 + .02 * lv) * w, color, FxLib.alpha(220 * tier.glow, fade * (held > 0 ? .6 : 1)), RailThunderArrayGrandGeometry.RADIAL, 0));
        }
        if (lv >= 3 && held == 0) {   // one big pulse that races the length of the line
            double p = Math.min(1, since / 5.0);
            sprites.add(new Billboard(hand.plus(dir.scale(length * p)), .22 * w, 0xFFFFFF, FxLib.alpha(240, fade), RailThunderArrayGrandGeometry.RADIAL, 0));
        }
        out.add(new FxLib.Parts(List.copyOf(strokes), List.copyOf(sprites), List.of()));
        if (lv >= 2) out.add(FxLib.dispersion(hand, end, .018 + .004 * lv, (held > 0 ? 50 : 120) * fade, tier));
        if (held > 0) {   // charge reticle at the muzzle
            out.add(CastSignatureDecor.reticle(hand.plus(dir.scale(.25)), .1 + .02 * lv, color, 200 * build, time * .12, tier));
            return;
        }
        // Release: muzzle flash and impact.
        out.add(FxLib.lensStreak(hand, .9 + .4 * lv, color, 200, fade, tier));
        Point nrm = new Point(0, 1, 0);
        out.add(FxLib.groundGlow(end, .15 + .05 * lv, color, 110 * fade, .35 + since * .12 * lv, 160 * fade, tier));
        out.add(CastSignatureDecor.ring(end, dir, .18 + .05 * lv + since * .03, color, 200 * fade, time * .3, 8 + 2 * lv, tier));
        if (lv >= 4) {
            out.add(FxLib.sparkShower(end, dir.scale(-1), seed, since, 10 + 4 * lv, 4, 1.1, 9, 12, 0xFFFFFF, color, tier));
            out.add(CastSignatureDecor.ring(end, nrm, .3 + .07 * lv + since * .05, 0xFFFFFF, 140 * fade, -time * .2, 14, tier));
        }
        if (lv >= 5) out.add(FxLib.spokes(end, seed, since, 12, 12, 1.0, color, 190, tier));
    }

    // ---- AURA --------------------------------------------------------------------------------------------------------------

    private static void aura(FxLib.Builder out, String category, int lv, int color, Point feet, double age, double since, long seed, double time, double fade, VisualTier tier) {
        double build = Math.min(1, age / 6.0), env = build * fade, w = tier.widthScale;
        Point up = new Point(0, 1, 0);
        // Rings rise through the body in a loop; one ring per level up to four.
        int rings = Math.min(4, lv);
        for (int i = 0; i < rings; i++) {
            double phase = ((time / 36.0 + i / (double) rings) % 1 + 1) % 1;
            double y = .1 + 1.9 * phase, r = .52 + .12 * Math.sin(phase * Math.PI) + .03 * lv;
            double a = Math.sin(phase * Math.PI);
            out.add(CastSignatureDecor.ring(feet.plus(new Point(0, y, 0)), up, r + .06, color, 235 * env * Math.max(.35, a), time * (.05 + .02 * i), 16 + 2 * lv, tier));
        }
        // LV3: orbiting motes plus the category motif.
        if (lv >= 3) {
            var sprites = new ArrayList<Billboard>();
            int motes = 3 * lv;
            for (int i = 0; i < motes; i++) {
                double t = time * .07 + i * Math.PI * 2 / motes, h = ((time / 50.0 + i * .37) % 1 + 1) % 1;
                Point at = feet.plus(new Point(Math.cos(t) * .7, .15 + h * 1.9, Math.sin(t) * .7));
                sprites.add(new Billboard(at, .045 * w, i % 2 == 0 ? color : 0xFFFFFF, FxLib.alpha(210 * tier.glow, env * Math.sin(h * Math.PI)), RailThunderArrayGrandGeometry.RADIAL, 0));
            }
            out.add(new FxLib.Parts(List.of(), List.copyOf(sprites), List.of()));
            motif(out, category, lv, color, feet, seed, time, env, tier);
        }
        // LV4: lit ground ring and four corner pillars of light.
        if (lv >= 4) {
            out.add(FxLib.groundGlow(feet.plus(new Point(0, .03, 0)), .7, color, 80 * env, 1.1 + .1 * Math.sin(time * .2), 150 * env, tier));
            var strokes = new ArrayList<Stroke>();
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + time * .015;
                Point b = feet.plus(new Point(Math.cos(a) * .95, 0, Math.sin(a) * .95));
                CastSignatureDecor.line(strokes, b, b.plus(new Point(0, 2.2, 0)), .012 * w, color, (int) (90 * env));
            }
            out.add(new FxLib.Parts(List.copyOf(strokes), List.of(), List.of()));
        }
        // LV5: a halo above the head and a shock ring on release.
        if (lv >= 5) {
            out.add(CastSignatureDecor.ring(feet.plus(new Point(0, 2.35, 0)), up, .38, 0xFFFFFF, 210 * env, -time * .06, 18, tier));
            if (since >= 0) out.add(FxLib.groundGlow(feet.plus(new Point(0, .04, 0)), .1, color, 1, 1 + since * .5, 220 * fade, tier));
        }
    }

    private static void motif(FxLib.Builder out, String category, int lv, int color, Point feet, long seed, double time, double env, VisualTier tier) {
        Point chest = feet.plus(new Point(0, 1.1, 0));
        switch (category) {
            case "electromaster" -> {
                for (int i = 0; i < Math.min(3, lv - 1); i++) {
                    double a = time * .13 + i * 2.1;
                    out.add(FxLib.lightning(chest.plus(new Point(Math.cos(a) * .6, .5, Math.sin(a) * .6)), chest.plus(new Point(Math.cos(a + 1.7) * .6, -.5, Math.sin(a + 1.7) * .6)),
                            seed + i * 31L, time, 6, .12, .014, 0xFFFFFF, color, env * .8, tier));
                }
            }
            case "meltdowner" -> out.add(FxLib.embers(feet.plus(new Point(0, .1, 0)), .6, 1.9, seed, time, 6 + 2 * lv, 0xF0FFF2, color, tier));
            case "teleporter" -> out.add(CastSignatureDecor.cube(chest, .62 + .04 * lv, color, 150 * env, time * .03, tier));
            case "vecmanip" -> out.add(CastSignatureDecor.arrows(chest, .85 + .05 * lv, 6 + lv, time * .09, .25, color, 170 * env, tier));
            default -> { }
        }
    }

    // ---- MARK --------------------------------------------------------------------------------------------------------------

    private static void mark(FxLib.Builder out, String category, int lv, int color, Point feet, Point end, double age, double since, long seed, double time, double fade, VisualTier tier) {
        double build = Math.min(1, age / 5.0), env = (since < 0 ? build * .7 : 1) * fade, w = tier.widthScale;
        Point at = end.plus(new Point(0, .04, 0));
        out.add(FxLib.groundGlow(at, .35 + .08 * lv, color, 110 * env, .7 + .12 * lv + (since >= 0 ? since * .08 : 0), 170 * env, tier));
        var strokes = new ArrayList<Stroke>();
        double height = 1.2 + .5 * lv;
        if (lv >= 2) CastSignatureDecor.line(strokes, at, at.plus(new Point(0, height, 0)), .02 * w, 0xFFFFFF, (int) (200 * env));
        if (lv >= 2) CastSignatureDecor.line(strokes, at, at.plus(new Point(0, height, 0)), .07 * w, color, (int) (90 * env));
        out.add(new FxLib.Parts(List.copyOf(strokes), List.of(), List.of()));
        if (lv >= 3) out.add(CastSignatureDecor.brackets(at.plus(new Point(0, .5, 0)), .3 + .04 * lv, color, 200 * env, tier));
        if (lv >= 4) out.add(FxLib.embers(at, .5, height, seed, time, 10, 0xFFFFFF, color, tier));
        if (lv >= 5) out.add(CastSignatureDecor.cube(at.plus(new Point(0, 1, 0)), .9 - (since >= 0 ? Math.min(.5, since * .03) : 0), color, 150 * env, time * .04, tier));
        // Marks always get a small ring so even LV1 reads as a placement.
        out.add(CastSignatureDecor.ring(at.plus(new Point(0, .05, 0)), new Point(0, 1, 0), .4 + .06 * lv, color, 200 * env, time * .08, 12, tier));
    }
}

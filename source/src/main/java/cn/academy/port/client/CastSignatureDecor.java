package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGeometry.Segment;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Billboard;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Decal;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Stroke;
import cn.academy.port.highpower.core.CastPose;
import java.util.ArrayList;
import java.util.List;

/**
 * The category signature every cast carries on top of its own skill effect, so every skill — original or high-power — reads at
 * a glance as its ability category, in the tech style (reticles, grids, data marks, scan rings; no magic circles):
 * <ul>
 * <li>electromaster: cyan arcs crawling from the hand to the ground and around the arm, a charge reticle, sparks on release;</li>
 * <li>meltdowner: a green-white particle core gathering in the hand inside an orbiting ring, a lens streak and ray burst on release;</li>
 * <li>teleporter: a violet coordinate lattice under the feet and corner brackets at the hand, a wireframe cell around the caster for
 * advanced skills;</li>
 * <li>vector: white force arrows orbiting the chest, a pressure ripple on release, a second tilted orbit for advanced skills.</li>
 * </ul>
 * Strength grows with the skill level; LV4/LV5 skills add a ground ring, a release shock ring and an extra motif layer.
 * Pure function of a {@link CastPoseClient.SignatureCast} snapshot; drawing only.
 */
public final class CastSignatureDecor {
    public static final int MAX_QUADS = 700, MAX_CASTS = 6;
    public static final int EM = 0x7FDFFF, MD = 0x8CFF9E, TP = 0xC59CFF, VM = 0xEEF3FF, NEUTRAL = 0xBFEFFF;

    private CastSignatureDecor() {}

    /** Category of a cast: the local caster's own category, or a hint from the gesture for other players. */
    public static String category(CastPoseClient.SignatureCast cast, String localCategory) {
        if (cast.category() != null && !cast.category().isEmpty()) return cast.category();
        if (cast.local() && localCategory != null && !localCategory.isEmpty()) return localCategory;
        if (cast.skill() != null && !cast.skill().isEmpty())
            for (var s : SkillCatalog.REGISTERED) if (s.id().equals(cast.skill())) return s.category();
        return switch (cast.archetype()) {
            case LANCE, RAIL_DEPLOY, DISCHARGE, FIELD_TUNE -> "electromaster";
            case CANOPY, AIM -> "meltdowner";
            case PHASE_ROUTE, PHASE_GATE, GRID_DEPLOY, PLACE -> "teleporter";
            case VECTOR_CAGE, VECTOR_DOMAIN, ACCELERATOR, FLIGHT, BRAKE, LISTEN, SWEEP, CONSTRUCT_LOCK, CONSTRUCT_PULL, CONSTRUCT_STEER,
                 CONSTRUCT_THROW, CONSTRUCT_LAND -> "vecmanip";
            default -> "";
        };
    }

    /** Skill level of a local cast (1..5), or 3 when unknown (other players). */
    public static int level(CastPoseClient.SignatureCast cast, String category) {
        if (cast.category() != null && !cast.category().isEmpty()) return cast.level();
        if (cast.skill() == null || cast.skill().isEmpty()) return 3;
        return SkillCatalog.find(category, cast.skill()).map(SkillCatalog.Skill::level).orElse(3);
    }

    /**
     * @param feet caster position, {@code hand} the main wrist, {@code look} unit view direction
     * @param age ticks since the cast began, {@code since} ticks since release or -1 while held
     */
    public static FxLib.Parts fx(String category, int level, Point feet, Point hand, Point look, double age, double since, long seed, double time, VisualTier tier) {
        if (tier == null || !tier.enhanced() || feet == null || hand == null || !feet.finite() || !hand.finite()
                || category == null || !Double.isFinite(age) || !Double.isFinite(since) || !Double.isFinite(time)) return FxLib.Parts.EMPTY;
        if (look == null || !look.finite()) look = new Point(0, 0, 1);
        double power = Math.max(.35, Math.min(1, level / 5.0));
        boolean advanced = level >= 4;
        double charge = Math.min(1, age / 12.0), fade = since < 0 ? 1 : Math.max(0, 1 - since / 14.0);
        if (fade <= 0) return FxLib.Parts.EMPTY;
        var parts = new FxLib.Builder();
        int rgb = switch (category) { case "electromaster" -> EM; case "meltdowner" -> MD; case "teleporter" -> TP; case "vecmanip" -> VM; default -> NEUTRAL; };
        double env = charge * fade;
        // Shared: a lit ground ring; advanced skills add a widening scan ring after release.
        if (advanced || since >= 0)
            parts.add(FxLib.groundGlow(feet.plus(new Point(0, .03, 0)), .55 + .3 * power, rgb, 70 * env * power, .9 + .4 * power, 140 * env, tier));
        if (advanced && since >= 0)
            parts.add(FxLib.groundGlow(feet.plus(new Point(0, .04, 0)), .1, rgb, 1, 1 + since * .35 * (1 + power), 200 * fade, tier));
        switch (category) {
            case "electromaster" -> electro(parts, power, advanced, feet, hand, age, since, seed, env, tier);
            case "meltdowner" -> melt(parts, power, advanced, hand, look, age, since, seed, time, env, tier);
            case "teleporter" -> teleport(parts, power, advanced, feet, hand, since, time, env, tier);
            case "vecmanip" -> vector(parts, power, advanced, feet, since, time, env, tier);
            default -> parts.add(FxLib.spokes(hand, seed, Math.max(0, since), 12, 6, .5, rgb, 150 * fade, tier));
        }
        return parts.build();
    }

    /** Semantic action layer; the skill's own hit/path/projectile effect remains the primary visual. */
    public static FxLib.Parts fx(String skill, CastPose.Archetype action, String category, int level, Point feet, Point hand, Point look,
                                double age, double since, long seed, double time, VisualTier tier) {
        if (action == null || tier == null || !tier.enhanced() || feet == null || hand == null || look == null
                || !feet.finite() || !hand.finite() || !look.finite() || !Double.isFinite(age) || !Double.isFinite(since)) return FxLib.Parts.EMPTY;
        var out = new FxLib.Builder();
        out.add(fx(category, level, feet, hand, look, age, since, seed, time, tier));
        double fade = since < 0 ? 1 : Math.max(0, 1 - since / 14.0);
        if (fade <= 0) return FxLib.Parts.EMPTY;
        double build = Math.min(1, Math.max(0, age / 5)), alpha = 180 * fade * build;
        boolean advanced = level >= 4;
        int rgb = switch (category) { case "electromaster" -> EM; case "meltdowner" -> MD; case "teleporter" -> TP; case "vecmanip" -> VM; default -> NEUTRAL; };
        Point forward = look.length() > 1e-6 ? look.scale(1 / look.length()) : new Point(0, 0, 1);
        Point side = forward.cross(new Point(0, 1, 0));
        if (side.length() < 1e-6) side = new Point(1, 0, 0); else side = side.scale(1 / side.length());
        Point up = side.cross(forward);
        // A stable offset gives variants their own cadence without random per-frame flicker.
        double variant = skill == null ? 0 : (skill.hashCode() & 15) * Math.PI / 8;
        double reach = .5 + .08 * Math.max(1, Math.min(5, level));
        var strokes = new ArrayList<Stroke>();
        switch (action) {
            case AIM, LANCE, ACCELERATOR -> {
                Point tip = hand.plus(forward.scale(reach));
                for (int i = -1; i <= 1; i += 2) {
                    Point o = side.scale(.10 * i);
                    line(strokes, hand.plus(o), tip.plus(o.scale(.35)), .009 * tier.widthScale, rgb, (int) alpha);
                }
                out.add(ring(tip, forward, advanced ? .22 : .14, rgb, alpha, variant + time * .08, advanced ? 12 : 8, tier));
            }
            case STRIKE, FLICK, CONSTRUCT_THROW -> {
                double extension = since < 0 ? .25 : Math.min(1, .25 + since * .18);
                Point tip = hand.plus(forward.scale(reach * extension));
                line(strokes, hand, tip, .014 * tier.widthScale, rgb, (int) alpha);
                line(strokes, tip, tip.minus(forward.scale(.16)).plus(side.scale(.10)), .012 * tier.widthScale, rgb, (int) alpha);
                line(strokes, tip, tip.minus(forward.scale(.16)).minus(side.scale(.10)), .012 * tier.widthScale, rgb, (int) alpha);
            }
            case GUARD, BRAKE -> {
                Point center = hand.plus(forward.scale(.16));
                // A segmented forward plate follows the hand, rather than suggesting an omnidirectional hitbox.
                out.add(ring(center, forward, .36 + level * .025, rgb, alpha, variant, advanced ? 16 : 10, tier));
                line(strokes, center.minus(side.scale(.22)), center.plus(side.scale(.22)), .010, rgb, (int) (alpha * .6));
            }
            case SLAM, PLACE, CONSTRUCT_LAND -> {
                Point p = feet.plus(forward.scale(.7)).plus(new Point(0, .06, 0));
                out.add(brackets(p, .2 + .03 * level, rgb, alpha, tier));
                line(strokes, hand, p, .009, rgb, (int) (alpha * .5));
            }
            case SWEEP, RAIL_DEPLOY, PHASE_ROUTE -> {
                Point prev = null;
                for (int i = 0; i <= 9; i++) {
                    double a = -.85 + i * 1.7 / 9;
                    Point p = hand.plus(forward.scale(Math.cos(a) * reach)).plus(side.scale(Math.sin(a) * reach));
                    if (prev != null) line(strokes, prev, p, .014 * tier.widthScale, rgb, (int) alpha);
                    prev = p;
                }
            }
            case FLIGHT, LIFT -> {
                Point chest = feet.plus(new Point(0, .9, 0));
                for (int i = -1; i <= 1; i += 2) {
                    Point tip = chest.plus(side.scale(i * (.65 + .03 * level))).minus(forward.scale(.3));
                    line(strokes, chest, tip, .016, rgb, (int) alpha);
                    line(strokes, tip, tip.minus(forward.scale(.35)).minus(up.scale(.2)), .011, rgb, (int) alpha);
                }
            }
            case GRID_DEPLOY, PHASE_GATE, VECTOR_CAGE -> out.add(cube(hand.plus(forward.scale(.5)), .25 + level * .025, rgb, alpha, variant + time * .025, tier));
            case FIELD_TUNE, VECTOR_DOMAIN, CANOPY -> {
                Point chest = feet.plus(new Point(0, 1.2, 0));
                out.add(ring(chest, new Point(0, 1, 0), .95 + level * .06, rgb, alpha, variant + time * .04, advanced ? 20 : 12, tier));
            }
            case LISTEN -> {
                Point ear = feet.plus(new Point(0, 1.55, 0)).plus(side.scale(.25));
                out.add(ring(ear, side, .15 + .08 * Math.sin(time * .15), rgb, alpha, 0, 10, tier));
            }
            case GATHER, DISCHARGE, CONSTRUCT_LOCK, CONSTRUCT_PULL, CONSTRUCT_STEER -> {
                for (int i = 0; i < (advanced ? 5 : 3); i++) {
                    double a = variant + i * Math.PI * 2 / (advanced ? 5 : 3) + time * .06;
                    Point p = hand.plus(side.scale(Math.cos(a) * .4)).plus(up.scale(Math.sin(a) * .4));
                    line(strokes, p, hand.plus(p.minus(hand).scale(.3)), .011 * tier.widthScale, rgb, (int) alpha);
                }
            }
        }
        out.add(new FxLib.Parts(List.copyOf(strokes), List.of(), List.of()));
        return out.build();
    }

    private static void electro(FxLib.Builder parts, double power, boolean advanced, Point feet, Point hand, double age, double since, long seed, double env, VisualTier tier) {
        int arcs = advanced ? 4 : 2;
        for (int i = 0; i < arcs; i++) {
            double a = seed * .37 + i * Math.PI * 2 / arcs + age * .21;
            Point tip = i % 2 == 0 ? feet.plus(new Point(Math.cos(a) * .9, .05, Math.sin(a) * .9))
                    : hand.plus(new Point(Math.cos(a) * .55, .35 - .3 * Math.sin(age * .6 + i), Math.sin(a) * .55));
            parts.add(FxLib.lightning(hand, tip, seed + i * 977L, age, 7, .18, .018 * (.6 + power), 0xFFFFFF, EM, env * (.6 + .4 * power), tier));
        }
        parts.add(reticle(hand, .16 + .06 * power, EM, 200 * env, age * .12, tier));
        if (since >= 0) {
            parts.add(FxLib.sparkShower(hand, new Point(0, 1, 0), seed, since, advanced ? 22 : 12, 5, 1.3, 9, 14, 0xBFF4FF, 0x2A6CFF, tier));
            if (advanced) parts.add(FxLib.spokes(hand, seed, since, 10, 10, .9 * power, EM, 190, tier));
        }
    }

    private static void melt(FxLib.Builder parts, double power, boolean advanced, Point hand, Point look, double age, double since, long seed, double time, double env, VisualTier tier) {
        double core = .07 + .13 * env * power;
        parts.add(new FxLib.Parts(List.of(), List.of(new Billboard(hand, core * tier.widthScale, MD, FxLib.alpha(230 * tier.glow, env), RailThunderArrayGrandGeometry.RADIAL, 0),
                new Billboard(hand, core * .45, 0xFFFFFF, FxLib.alpha(250, env), RailThunderArrayGrandGeometry.RADIAL, 0)), List.of()));
        parts.add(ring(hand, look, .28 + .1 * power, MD, 170 * env, time * .15, advanced ? 18 : 12, tier));
        if (advanced) parts.add(ring(hand, new Point(look.z(), .3, -look.x()), .38 + .1 * power, 0xE2FFE8, 120 * env, -time * .11, 14, tier));
        parts.add(FxLib.embers(hand.minus(new Point(0, .2, 0)), .35, .5, seed, time, advanced ? 10 : 6, 0xF0FFF2, MD, tier));
        if (since >= 0) {
            double f = Math.max(0, 1 - since / 10);
            parts.add(FxLib.lensStreak(hand, 1.2 + 1.4 * power, MD, 220, f, tier));
            if (look != null) parts.add(FxLib.dispersion(hand, hand.plus(look.scale(2.5 + 2 * power)), .03, 120 * f, tier));
            if (advanced) parts.add(FxLib.spokes(hand, seed, since, 12, 12, 1.1 * power, MD, 200, tier));
        }
    }

    private static void teleport(FxLib.Builder parts, double power, boolean advanced, Point feet, Point hand, double since, double time, double env, VisualTier tier) {
        // Coordinate lattice on the ground: a 5x5 grid that flickers row by row.
        double half = .9 + .3 * power, step = half * 2 / 4;
        List<Stroke> strokes = new ArrayList<>();
        for (int i = 0; i <= 4; i++) {
            double o = -half + i * step, flick = .55 + .45 * Math.sin(time * .5 + i * 1.3);
            int a = FxLib.alpha(150 * env * flick, 1);
            line(strokes, feet.plus(new Point(o, .04, -half)), feet.plus(new Point(o, .04, half)), .012, TP, a);
            line(strokes, feet.plus(new Point(-half, .04, o)), feet.plus(new Point(half, .04, o)), .012, TP, a);
        }
        parts.add(new FxLib.Parts(List.copyOf(strokes), List.of(), List.of()));
        parts.add(brackets(hand, .2 + .06 * power, TP, 220 * env, tier));
        if (advanced) parts.add(cube(feet.plus(new Point(0, 1, 0)), .75 + .15 * power + (since >= 0 ? since * .06 : 0), TP, 150 * env, time * .03, tier));
        if (since >= 0) parts.add(FxLib.spokes(hand, (long) (time * 7), since, 10, advanced ? 10 : 6, .7 * power, TP, 180, tier));
    }

    private static void vector(FxLib.Builder parts, double power, boolean advanced, Point feet, double since, double time, double env, VisualTier tier) {
        Point chest = feet.plus(new Point(0, 1.1, 0));
        parts.add(arrows(chest, .75 + .15 * power, 6, time * .09, 0, VM, 190 * env, tier));
        if (advanced) parts.add(arrows(chest.plus(new Point(0, .15, 0)), 1 + .2 * power, 8, -time * .07, .45, 0xFFD9D9, 150 * env, tier));
        if (since >= 0) {
            double f = Math.max(0, 1 - since / 12);
            parts.add(FxLib.groundGlow(chest, .05, VM, 1, .4 + since * .28 * (1 + power), 210 * f, tier));
            if (advanced) parts.add(FxLib.spokes(chest, (long) (time * 3), since, 12, 8, 1.2 * power, VM, 170, tier));
        }
    }

    // ---- shapes ------------------------------------------------------------------------------------------------------------

    static void line(List<Stroke> out, Point a, Point b, double width, int rgb, int alpha) {
        if (a == null || b == null || !a.finite() || !b.finite() || b.minus(a).length() < 1e-6) return;
        out.add(new Stroke(new Segment(a, b), width, rgb, Math.max(1, Math.min(255, alpha)), RailThunderArrayGrandGeometry.ARC_GLOW));
    }

    /** Four-tick charge reticle: an open circle of eight dashes around the hand, slowly turning. */
    static FxLib.Parts reticle(Point c, double r, int rgb, double alpha, double turn, VisualTier tier) {
        List<Stroke> s = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double a0 = turn + i * Math.PI / 4, a1 = a0 + Math.PI / 8;
            line(s, c.plus(new Point(Math.cos(a0) * r, Math.sin(a0) * r, 0)), c.plus(new Point(Math.cos(a1) * r, Math.sin(a1) * r, 0)), .012 * tier.widthScale, rgb, (int) alpha);
        }
        return new FxLib.Parts(List.copyOf(s), List.of(), List.of());
    }

    /** A ring of {@code n} segments around {@code axis}. */
    static FxLib.Parts ring(Point c, Point axis, double r, int rgb, double alpha, double turn, int n, VisualTier tier) {
        if (axis == null || axis.length() < 1e-6) axis = new Point(0, 1, 0);
        Point f = axis.scale(1 / axis.length()), ref = Math.abs(f.y()) > .9 ? new Point(1, 0, 0) : new Point(0, 1, 0);
        Point u = f.cross(ref), v;
        if (u.length() < 1e-6) return FxLib.Parts.EMPTY;
        u = u.scale(1 / u.length()); v = u.cross(f);
        List<Stroke> s = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double a0 = turn + i * Math.PI * 2 / n, a1 = a0 + Math.PI * 2 / n * .6;
            line(s, c.plus(u.scale(Math.cos(a0) * r)).plus(v.scale(Math.sin(a0) * r)), c.plus(u.scale(Math.cos(a1) * r)).plus(v.scale(Math.sin(a1) * r)), .014 * tier.widthScale, rgb, (int) alpha);
        }
        return new FxLib.Parts(List.copyOf(s), List.of(), List.of());
    }

    /** Corner brackets (a targeting frame) facing up around the hand. */
    static FxLib.Parts brackets(Point c, double r, int rgb, double alpha, VisualTier tier) {
        List<Stroke> s = new ArrayList<>();
        double k = r * .45;
        for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) {
            Point corner = c.plus(new Point(sx * r, sy * r, 0));
            line(s, corner, corner.plus(new Point(-sx * k, 0, 0)), .014 * tier.widthScale, rgb, (int) alpha);
            line(s, corner, corner.plus(new Point(0, -sy * k, 0)), .014 * tier.widthScale, rgb, (int) alpha);
        }
        return new FxLib.Parts(List.copyOf(s), List.of(), List.of());
    }

    /** A turning wireframe cube: the spatial cell a teleporter folds. */
    static FxLib.Parts cube(Point c, double half, int rgb, double alpha, double turn, VisualTier tier) {
        Point[] v = new Point[8];
        double cs = Math.cos(turn), sn = Math.sin(turn);
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? -half : half, y = (i & 2) == 0 ? -half : half, z = (i & 4) == 0 ? -half : half;
            v[i] = c.plus(new Point(x * cs - z * sn, y, x * sn + z * cs));
        }
        int[][] edges = {{0,1},{2,3},{4,5},{6,7},{0,2},{1,3},{4,6},{5,7},{0,4},{1,5},{2,6},{3,7}};
        List<Stroke> s = new ArrayList<>();
        for (int[] e : edges) line(s, v[e[0]], v[e[1]], .012 * tier.widthScale, rgb, (int) alpha);
        return new FxLib.Parts(List.copyOf(s), List.of(), List.of());
    }

    /** {@code n} force arrows orbiting {@code c} on a ring tilted by {@code tilt}. */
    static FxLib.Parts arrows(Point c, double r, int n, double turn, double tilt, int rgb, double alpha, VisualTier tier) {
        List<Stroke> s = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double a = turn + i * Math.PI * 2 / n, len = .35;
            Point at = c.plus(new Point(Math.cos(a) * r, Math.sin(a) * r * tilt, Math.sin(a) * r));
            Point dir = new Point(-Math.sin(a), Math.cos(a) * tilt, Math.cos(a));
            dir = dir.scale(1 / dir.length());
            Point tail = at.minus(dir.scale(len / 2)), head = at.plus(dir.scale(len / 2));
            Point side = new Point(-dir.z(), 0, dir.x()).scale(.09);
            int al = (int) alpha;
            line(s, tail, head, .016 * tier.widthScale, rgb, al);
            line(s, head, head.minus(dir.scale(.12)).plus(side), .014 * tier.widthScale, rgb, al);
            line(s, head, head.minus(dir.scale(.12)).minus(side), .014 * tier.widthScale, rgb, al);
        }
        return new FxLib.Parts(List.copyOf(s), List.of(), List.of());
    }
}

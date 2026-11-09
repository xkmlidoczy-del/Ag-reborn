package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGeometry.Segment;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Billboard;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Stroke;
import cn.academy.port.highpower.NewSkillRuntime;
import cn.academy.port.highpower.core.PulseShieldRules;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure presentation of the seven post-0.81.2 skills, keyed by (kind, phase) of the authenticated server visual. Each cue
 * lives {@link #LIFE} ticks and is built only from the receipt's own points; nothing here is a hit, route or damage region.
 */
public final class NewSkillFx {
    public static final int LIFE = 24, MAX_QUADS = 700;
    private static final int ICE = 0xB6E4FF, BLUE = 0x4AA8FF, IVORY = 0xFFF1D0, ORANGE = 0xFFB060, GREEN = 0x6AF26A, GREEN_CORE = 0xD8F8D8, WHITE = 0xF4F7FA, GREY = 0xA9B3BD;

    /** One live cue: the receipt fields that matter for drawing, plus the entity-relative caster position for this frame. */
    public record Cue(int kind, int phase, double age, Point point, Point dir, List<Point> points, long seed) {}
    private NewSkillFx() {}

    public static VisualTier effective(VisualTier tier) { return tier != null && tier.enhanced() ? tier : VisualTier.STANDARD; }

    public static FxLib.Parts build(Cue cue, Point caster, double time, VisualTier requested) {
        VisualTier tier = effective(requested);
        if (cue == null || cue.point == null || !cue.point.finite() || !Double.isFinite(cue.age) || cue.age < 0 || cue.age >= LIFE || caster == null || !caster.finite()) return FxLib.Parts.EMPTY;
        var kinds = NewSkillRuntime.Kind.values();
        if (cue.kind < 0 || cue.kind >= kinds.length) return FxLib.Parts.EMPTY;
        FxLib.Builder out = new FxLib.Builder();
        switch (kinds[cue.kind]) {
            case PULSE_SHIELD -> pulse(out, cue, caster, tier);
            case RAIL_RELAY -> relay(out, cue, caster, time, tier);
            case REVERSE_JET -> jet(out, cue, caster, time, tier);
            case INCIDENT_BYPASS -> bypass(out, cue, caster, tier);
            case PHASE_CORRIDOR -> corridor(out, cue, tier);
            case ZENITH_DOMINION -> dominion(out, cue, tier);
            case IMPULSE_SPLIT -> split(out, cue, tier);
            case CONSTRUCT -> { }
        }
        return out.build();
    }

    private static double fade(Cue c) { return Math.max(.05, 1 - c.age / LIFE); }
    private static Point up(Point p, double dy) { return p.plus(new Point(0, dy, 0)); }

    private static void pulse(FxLib.Builder out, Cue c, Point caster, VisualTier tier) {
        double w = tier.widthScale, f = fade(c);
        boolean detect = c.phase == NewSkillRuntime.DETECT;
        if (c.phase == NewSkillRuntime.START || detect) {
            double reach = PulseShieldRules.RANGE * (detect ? 1 : Math.min(1, (c.age + 1) / 10));
            Point footprint = detect ? new Point(c.point.x(), caster.y(), c.point.z()) : c.point;
            out.add(FxLib.groundGlow(up(footprint, .05), .1, ICE, 1, reach, (detect ? 120 : 170) * f, tier));
        }
        if (!detect && c.phase != NewSkillRuntime.APPLY) return;
        for (Point t : c.points) {
            if (t == null || !t.finite()) continue;
            if (detect) {
                out.add(new FxLib.Parts(List.of(), List.of(
                        new Billboard(t, .35 * w, ICE, FxLib.alpha(175 * tier.glow, f), RailThunderArrayGrandGeometry.RING, 0),
                        new Billboard(t, .24 * w, BLUE, FxLib.alpha(195 * tier.glow, f), RailThunderArrayGrandGeometry.STAR, c.age * .2)), List.of()));
            } else {
                out.add(FxLib.lightning(c.point, t, c.seed + t.hashCode(), c.age, 9, .34, .025, 0xFFFFFF, BLUE, f, tier));
                out.add(FxLib.sparkShower(t, new Point(0, 1, 0), c.seed ^ 0x77, c.age, 8, 4, 1.05, 7, 10, 0xFFFFFF, BLUE, tier));
            }
        }
    }

    private static void relay(FxLib.Builder out, Cue c, Point caster, double time, VisualTier tier) {
        double w = tier.widthScale, f = fade(c);
        List<Stroke> rails = new ArrayList<>();
        List<Point> pts = c.points;
        for (int i = 0; i + 1 < pts.size(); i++) {
            Point a = pts.get(i), b = pts.get(i + 1);
            if (!RailThunderArrayGeometry.validEndpoints(a, b) || b.minus(a).length() < 1e-6) continue;
            Point side = b.minus(a).cross(new Point(0, 1, 0));
            side = side.length() < 1e-6 ? new Point(.15, 0, 0) : side.scale(.14 / side.length());
            rails.add(new Stroke(new Segment(a.plus(side), b.plus(side)), .06 * w, BLUE, FxLib.alpha(120 * tier.glow, f), RailThunderArrayGrandGeometry.ARC_GLOW));
            rails.add(new Stroke(new Segment(a.minus(side), b.minus(side)), .06 * w, IVORY, FxLib.alpha(120 * tier.glow, f), RailThunderArrayGrandGeometry.ARC_GLOW));
            rails.add(new Stroke(new Segment(a.plus(side), b.plus(side)), .012, 0xFFFFFF, FxLib.alpha(220, f), RailThunderArrayGrandGeometry.WHITE));
            rails.add(new Stroke(new Segment(a.minus(side), b.minus(side)), .012, 0xFFFFFF, FxLib.alpha(220, f), RailThunderArrayGrandGeometry.WHITE));
        }
        out.add(new FxLib.Parts(List.copyOf(rails), List.of(), List.of()));
        for (int i = 0; i < pts.size(); i++) {
            Point a = pts.get(i);
            out.add(new FxLib.Parts(List.of(), List.of(new Billboard(a, .8 * w, IVORY, FxLib.alpha(210 * tier.glow, f), RailThunderArrayGrandGeometry.STAR, i + c.age * .1),
                    new Billboard(a, .4 * w, 0xFFFFFF, FxLib.alpha(235 * tier.glow, f), RailThunderArrayGrandGeometry.RADIAL, 0)), List.of()));
            out.add(FxLib.groundGlow(up(a, .04), 1.4 * w, BLUE, 70 * f, (.3 + c.age * .12) * w, 150 * f, tier));
        }
        if (c.phase == NewSkillRuntime.RIDE) {
            Point back = caster.minus(c.dir.length() < 1e-6 ? new Point(0, 0, 0) : c.dir.scale(1 / c.dir.length()).scale(2.5));
            out.add(FxLib.sparkShower(caster, back.minus(caster), c.seed ^ 0x153, time % 12, 22, 6, .5, 3, 12, 0xFFFFFF, ORANGE, tier));
            out.add(FxLib.lensStreak(up(caster, 1), 3.2 * w, IVORY, 130, f, tier));
        }
    }

    private static void jet(FxLib.Builder out, Cue c, Point caster, double time, VisualTier tier) {
        double w = tier.widthScale, f = fade(c);
        Point dir = c.dir.length() < 1e-6 ? new Point(0, 1, 0) : c.dir.scale(-1 / c.dir.length());
        Point feet = up(caster, .1);
        out.add(FxLib.sparkShower(feet, dir, c.seed ^ 0x243, c.age % 12, 40, 9, .45, 2, 14, GREEN_CORE, GREEN, tier));
        out.add(new FxLib.Parts(List.of(new Stroke(new Segment(feet, feet.plus(dir.scale(3.2 * w))), .3 * w, GREEN, FxLib.alpha(125 * tier.glow, f), RailThunderArrayGrandGeometry.GLOW_ADD),
                new Stroke(new Segment(feet, feet.plus(dir.scale(3.2 * w))), .04 * w, 0xFFFFFF, FxLib.alpha(220, f), RailThunderArrayGrandGeometry.GLOW_ADD)),
                List.of(new Billboard(feet, .9 * w, GREEN, FxLib.alpha(150 * tier.glow, f), RailThunderArrayGrandGeometry.RADIAL, 0),
                        new Billboard(feet, 1.6 * w, GREEN_CORE, FxLib.alpha(150 * tier.glow, f), RailThunderArrayGrandGeometry.STAR, time * .05)), List.of()));
        out.add(FxLib.groundGlow(up(caster, .04), 2.2 * w, GREEN, 80 * f, (.4 + c.age * .12) * w, 140 * f, tier));
        out.add(FxLib.spokes(feet, c.seed ^ 0x24, Math.min(c.age, 10), 11, 8, 1.8 * w, GREEN_CORE, 160, tier));
    }

    private static void bypass(FxLib.Builder out, Cue c, Point caster, VisualTier tier) {
        double w = tier.widthScale, f = fade(c);
        Point chest = up(caster, 1.0);
        if (c.phase == NewSkillRuntime.DIVERT) {
            Point v = c.dir.length() < 1e-6 ? new Point(0, 0, 1) : c.dir.scale(1 / c.dir.length());
            Point side = v.cross(new Point(0, 1, 0));
            side = side.length() < 1e-6 ? new Point(1, 0, 0) : side.scale(1 / side.length());
            List<Stroke> split = new ArrayList<>();
            for (int s : new int[] {-1, 1}) {
                Point end = c.point.plus(side.scale(s * 2.2 * w)).plus(v.scale(1.6 * w));
                split.add(new Stroke(new Segment(c.point, end), .16 * w, GREY, FxLib.alpha(110 * tier.glow, f), RailThunderArrayGrandGeometry.GLOW_ADD));
                split.add(new Stroke(new Segment(c.point, end), .02 * w, 0xFFFFFF, FxLib.alpha(225, f), RailThunderArrayGrandGeometry.GLOW_ADD));
            }
            out.add(new FxLib.Parts(List.copyOf(split), List.of(new Billboard(c.point, 1.0 * w, WHITE, FxLib.alpha(220 * tier.glow, f), RailThunderArrayGrandGeometry.STAR, c.age * .2)), List.of()));
            out.add(FxLib.spokes(c.point, c.seed ^ 0x332, c.age, LIFE, 10, 2.0 * w, WHITE, 190, tier));
            return;
        }
        // Armed window: three offset thin slices of light around the body (the staggered afterimage) and a closing ring.
        List<Stroke> slices = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            double ang = i * Math.PI * 2 / 3 + c.age * .08;
            Point at = caster.plus(new Point(Math.cos(ang) * .7 * w, 0, Math.sin(ang) * .7 * w));
            slices.add(new Stroke(new Segment(at, up(at, 2.0)), .1 * w, GREY, FxLib.alpha(100 * tier.glow, f), RailThunderArrayGrandGeometry.ARC_GLOW));
            slices.add(new Stroke(new Segment(at, up(at, 2.0)), .014, 0xFFFFFF, FxLib.alpha(210, f), RailThunderArrayGrandGeometry.WHITE));
            out.add(FxLib.dispersion(at, up(at, 2.0), .03 * w, 90 * f, tier));
        }
        out.add(new FxLib.Parts(List.copyOf(slices), List.of(), List.of()));
        out.add(FxLib.groundGlow(up(caster, .04), 1.2 * w, GREY, 70 * f, (3.2 - c.age * .12) * w, 150 * f, tier));
        out.add(FxLib.embers(caster, 1.1 * w, 2.4, c.seed ^ 0x33, c.age * 3, 14, WHITE, GREY, tier));
        out.add(FxLib.lensStreak(chest, 2.6 * w, WHITE, 100, f, tier));
    }

    private static void corridor(FxLib.Builder out, Cue c, VisualTier tier) {
        if (c.phase != NewSkillRuntime.JUMP || c.points.size() < 2 || c.age >= 12) return;
        Point from = c.points.get(0), to = c.points.get(1);
        if (!RailThunderArrayGeometry.validEndpoints(from, to)) return;
        var basis = RailThunderArrayGeometry.basis(to.minus(from));
        if (basis == null) return;
        double u = c.age / 12, fade = 1 - u;
        var strokes = new ArrayList<Stroke>(16);
        fold(strokes, from, basis.side(), .26 + .53 * u, 1.15 - .3 * u, GREY, 175 * fade);
        fold(strokes, to, basis.side(), .7 - .4 * u, 1.15 + .15 * u, WHITE, 235 * fade);
        var highlights = List.of(new Billboard(up(from, .92), .19, GREY, FxLib.alpha(140, fade), RailThunderArrayGrandGeometry.RADIAL, 0),
                new Billboard(up(to, .92), .24, WHITE, FxLib.alpha(210 * tier.glow, fade), RailThunderArrayGrandGeometry.STAR, 0));
        out.add(new FxLib.Parts(strokes, highlights, List.of()));
    }

    private static void fold(List<Stroke> strokes, Point feet, Point side, double half, double height, int color, double alpha) {
        for (int x : new int[] {-1, 1}) {
            for (int y : new int[] {-1, 1}) {
                Point corner = up(feet, 1 + y * height * .63).plus(side.scale(x * half));
                Point across = corner.minus(side.scale(x * half * .56));
                Point vertical = up(corner, -y * height * .38);
                strokes.add(line(corner, across, .021, color, alpha));
                strokes.add(line(corner, vertical, .016, color, alpha));
            }
        }
    }

    private static void dominion(FxLib.Builder out, Cue c, VisualTier tier) {
        boolean release = c.phase == NewSkillRuntime.RELEASE;
        if (!release && c.phase != NewSkillRuntime.START && c.phase != NewSkillRuntime.LIFT) return;
        double f = fade(c);
        var lines = new ArrayList<Stroke>();
        var sprites = new ArrayList<Billboard>();
        for (Point target : c.points) {
            if (!RailThunderArrayGeometry.validPoint(target)) continue;
            Point away = new Point(target.x() - c.point.x(), 0, target.z() - c.point.z());
            double horizontal = away.length();
            boolean radial = away.dot(away) >= 1e-6;
            Point outward = radial ? away.scale(1 / horizontal) : new Point(0, 0, 0);
            if (release) {
                // The admitted release moves outward from the recorded caster and down at the original ratio.
                Point push = outward.scale(.6).plus(new Point(0, -.9, 0));
                Point tip = target.plus(push.scale(.65 + Math.min(1.1, c.age * .095)));
                lines.add(line(target, tip, .11, GREY, 145 * f));
                lines.add(line(target, tip, .023, WHITE, 230 * f));
                sprites.add(new Billboard(target, .28 + .032 * c.age, WHITE, FxLib.alpha(200 * tier.glow, f), RailThunderArrayGrandGeometry.STAR, 0));
                continue;
            }
            Point side = radial ? new Point(-outward.z(), 0, outward.x()) : new Point(1, 0, 0);
            double scale = .72 - .27 * Math.min(1, c.age / 6);
            double rise = c.phase == NewSkillRuntime.LIFT ? .18 : 0;
            // Short bent strokes compress toward the real selected body; the effect has no area marker.
            for (int x : new int[] {-1, 1}) {
                Point edge = target.plus(side.scale(x * scale)).plus(new Point(0, -.66 + rise, 0));
                Point bent = target.plus(side.scale(x * scale * .52)).plus(new Point(0, .12 + rise, 0));
                Point tip = target.plus(side.scale(x * scale * .32)).plus(new Point(0, .64 + rise, 0));
                lines.add(line(edge, bent, .034, GREY, 170 * f));
                lines.add(line(bent, tip, .024, WHITE, 225 * f));
            }
            sprites.add(new Billboard(target, .18, WHITE, FxLib.alpha(125 * tier.glow, f), RailThunderArrayGrandGeometry.RADIAL, 0));
        }
        out.add(new FxLib.Parts(lines, sprites, List.of()));
    }

    private static void split(FxLib.Builder out, Cue c, VisualTier tier) {
        double f = fade(c);
        if (c.phase == NewSkillRuntime.DIVERT) {
            List<Stroke> streak = List.of();
            if (c.dir != null && c.dir.finite() && c.dir.length() > 1e-6) {
                // The packet supplies the first projectile's resulting direction. The copied projectile renders as its own entity.
                Point axis = c.dir.scale(1 / c.dir.length());
                Point tip = c.point.plus(axis.scale(.35 + Math.min(1.2, c.age * .14)));
                streak = List.of(line(c.point, tip, .086, GREY, 150 * f), line(c.point, tip, .017, WHITE, 225 * f));
            }
            out.add(new FxLib.Parts(streak, List.of(new Billboard(c.point, .26, WHITE,
                    FxLib.alpha(210 * tier.glow, f), RailThunderArrayGrandGeometry.STAR, c.age * .2)), List.of()));
            return;
        }
        Point chest = up(c.point, 1);
        var marks = new ArrayList<Stroke>();
        for (Point at : c.points) {
            if (!RailThunderArrayGeometry.validEndpoints(chest, at)) continue;
            var basis = RailThunderArrayGeometry.basis(at.minus(chest));
            if (basis == null) continue;
            Point a = at.plus(basis.side().scale(.32)), b = at.minus(basis.side().scale(.32));
            marks.add(line(a, at.plus(basis.forward().scale(.24)), .028, WHITE, 205 * f));
            marks.add(line(b, at.plus(basis.forward().scale(.24)), .028, WHITE, 205 * f));
        }
        out.add(new FxLib.Parts(marks, List.of(new Billboard(chest, .23, GREY,
                FxLib.alpha(150 * tier.glow, f), RailThunderArrayGrandGeometry.RADIAL, 0)), List.of()));
    }

    private static Stroke line(Point from, Point to, double halfWidth, int color, double alpha) {
        return new Stroke(new Segment(from, to), halfWidth, color, FxLib.alpha(alpha, 1), RailThunderArrayGrandGeometry.ARC_GLOW);
    }
}

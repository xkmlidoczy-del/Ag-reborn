package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGeometry.Segment;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Billboard;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Decal;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Stroke;
import java.util.ArrayList;
import java.util.List;

import static cn.academy.port.client.RailThunderArrayGrandGeometry.*;

/** Bounded optional LV4 rail conductors, arcing slipstream and witnessed surface contact. */
public final class RailgunDecor {
    static final int MAX_QUADS = 1100, SHOT_TICKS = 16, CHARGE_TICKS = 14;
    private static final int IVORY = 0xFFF3D3, COPPER = 0xFFAD60, ION_BLUE = 0x8BD9FF;
    private RailgunDecor() {}

    static FxLib.Parts charge(Point eye, Point axis, double age, VisualTier tier) {
        var basis = RailThunderArrayGeometry.basis(axis);
        if (!tier.enhanced() || basis == null || !RailThunderArrayGeometry.validPoint(eye)
                || age < 0 || age >= CHARGE_TICKS) return FxLib.Parts.EMPTY;
        var strokes = new ArrayList<Stroke>(8);
        var sprites = new ArrayList<Billboard>(2);
        Point root = eye.plus(basis.forward().scale(.85)).minus(basis.up().scale(.21));
        Point tip = root.plus(basis.forward().scale(.92));
        double rise = Math.min(1, (age + 1) / 4), fade = 1 - age / CHARGE_TICKS;
        for (int sign : new int[]{-1, 1}) {
            Point side = basis.side().scale(sign * (.23 - .08 * rise));
            Point near = root.plus(side), far = tip.plus(side);
            strokes.add(stroke(near, far, .026, ION_BLUE, 165 * rise * fade, ARC_GLOW));
            Point pulse = near.lerp(far, (age * .18) % 1);
            sprites.add(new Billboard(pulse, .086, IVORY, FxLib.alpha(205, fade), RADIAL, 0));
        }
        return new FxLib.Parts(strokes, sprites, List.of());
    }

    static FxLib.Parts shot(Point origin, Point axis, double length, Point face, double age, long seed,
                            VisualTier tier) {
        if (!tier.enhanced() || age < 0 || age >= SHOT_TICKS || !Double.isFinite(length) || length <= 0)
            return FxLib.Parts.EMPTY;
        Point end = origin.plus(axis.scale(length));
        if (!RailThunderArrayGeometry.validEndpoints(origin, end)) return FxLib.Parts.EMPTY;
        var basis = RailThunderArrayGeometry.basis(axis);
        if (basis == null) return FxLib.Parts.EMPTY;
        boolean contact = face != null;
        double opacity = 1 - age / SHOT_TICKS, decay = opacity * opacity;
        Point litEnd = end.minus(axis.scale(Math.min(.035, length * .1)));
        var strokes = new ArrayList<Stroke>(44);
        var sprites = new ArrayList<Billboard>(3);
        var decals = new ArrayList<Decal>(2);

        // Tight opposed rails form a copper sleeve around the existing core and narrow to the same endpoint.
        if (length > .7) {
            Point waist = origin.lerp(litEnd, .64);
            for (int sign : new int[]{-1, 1}) {
                Point offset = basis.side().scale(sign * .15);
                Point start = origin.plus(axis.scale(.4)).plus(offset);
                Point middle = waist.plus(offset.scale(.6));
                addInFront(strokes, start, middle, .047, COPPER, 115 * decay, GLOW_ADD, end, face);
                addInFront(strokes, middle, litEnd, .026, IVORY, 135 * decay, ARC_GLOW, end, face);
            }
        }

        // Short forks shed from the rail in sequence, leaving the beam axis free for the hard core.
        int forks = Math.min(8, (int) (length / 3));
        var random = new FxLib.Rng(seed);
        for (int i = 0; i < forks; i++) {
            double fraction = (i + .6) / forks;
            double angle = random.range(0, Math.PI * 2);
            double local = age - 2 * fraction;
            if (local < 0 || local > 10) continue;
            double alpha = (1 - local / 10) * decay;
            Point spoke = basis.side().scale(Math.cos(angle)).plus(basis.up().scale(Math.sin(angle)));
            Point stem = origin.lerp(litEnd, fraction);
            Point fork = stem.lerp(litEnd, .025).plus(spoke.scale(.18 + .024 * local));
            Point tip = stem.lerp(litEnd, .065).plus(spoke.scale(.34 + .032 * local));
            Point child = fork.lerp(litEnd, .032).plus(spoke.cross(axis).scale(.22));
            addInFront(strokes, stem, fork, .026, ION_BLUE, 195 * alpha, ARC_GLOW, end, face);
            addInFront(strokes, fork, tip, .016, IVORY, 185 * alpha, ARC_GLOW, end, face);
            addInFront(strokes, fork, child, .012, ION_BLUE, 125 * alpha, ARC_GLOW, end, face);
        }

        if (contact && age < 8) {
            var surface = RailThunderArrayGeometry.basis(face);
            double flash = 1 - age / 8, radius = .32 + age * .075;
            Point impact = end.plus(face.scale(.018));
            decals.add(new Decal(impact, surface.side(), surface.up(), radius, COPPER, FxLib.alpha(200, flash), RADIAL));
            decals.add(new Decal(impact, surface.side(), surface.up(), .63 * radius, IVORY, FxLib.alpha(235, flash * flash), RADIAL));
            sprites.add(new Billboard(impact, .12 + .12 * flash, IVORY, FxLib.alpha(220, flash), STAR, .35));
            for (int i = 0; i < 6; i++) {
                double angle = i * (Math.PI / 3) + random.range(-.18, .18);
                Point tangent = surface.side().scale(Math.cos(angle)).plus(surface.up().scale(Math.sin(angle)));
                Point near = impact.plus(tangent.scale(.06 + age * .017)).plus(face.scale(age * .021));
                Point far = near.plus(tangent.scale(.13 + age * .032)).plus(face.scale(.09 + age * .023));
                strokes.add(stroke(near, far, .023, i % 2 == 0 ? IVORY : COPPER, 190 * flash, ARC_GLOW));
            }
        }
        return new FxLib.Parts(strokes, sprites, decals);
    }

    private static Stroke stroke(Point from, Point to, double width, int color, double alpha, int texture) {
        return new Stroke(new Segment(from, to), width, color, FxLib.alpha(alpha, 1), texture);
    }

    private static void addInFront(List<Stroke> strokes, Point from, Point to, double width, int color,
                                   double alpha, int texture, Point end, Point face) {
        if (face != null && (from.minus(end).dot(face) < 0 || to.minus(end).dot(face) < 0)) return;
        strokes.add(stroke(from, to, width, color, alpha, texture));
    }
}

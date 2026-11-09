package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGeometry.Segment;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Billboard;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Decal;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Stroke;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic effect building blocks shared by every enhanced material: ballistic spark showers with motion-blur
 * tails, rising embers, radial spoke bursts, anamorphic lens streaks, flat ground light pools and shock rings, and RGB
 * dispersion fringes. Pure functions of (seed, age/time, geometry): no state, no randomness source, no gameplay reads.
 */
public final class FxLib {
    /** One layer set: line strokes, camera-facing sprites and world-plane decals. */
    public record Parts(List<Stroke> strokes, List<Billboard> sprites, List<Decal> decals) {
        public static final Parts EMPTY = new Parts(List.of(), List.of(), List.of());
    }
    /** Mutable collector so callers can compose many blocks without intermediate copies. */
    public static final class Builder {
        private final List<Stroke> strokes = new ArrayList<>();
        private final List<Billboard> sprites = new ArrayList<>();
        private final List<Decal> decals = new ArrayList<>();
        public Builder add(Parts parts) { strokes.addAll(parts.strokes); sprites.addAll(parts.sprites); decals.addAll(parts.decals); return this; }
        public Parts build() { return new Parts(List.copyOf(strokes), List.copyOf(sprites), List.copyOf(decals)); }
    }

    private FxLib() {}

    /** SplitMix64 stream: stable across platforms, cheap, and independent of java.util.Random. */
    static final class Rng {
        private long state;
        Rng(long seed) { state = seed * 0x9E3779B97F4A7C15L + 0x1234567L; }
        double next() {
            state += 0x9E3779B97F4A7C15L;
            long z = state;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            z ^= z >>> 31;
            return (z >>> 11) * 0x1.0p-53;
        }
        double range(double lo, double hi) { return lo + (hi - lo) * next(); }
    }

    static int alpha(double base, double factor) { return Math.max(1, Math.min(255, (int) Math.round(base * factor))); }

    private static Point unit(Point p) {
        if (p == null || !p.finite()) return null;
        double l = p.length();
        return l < 1e-9 ? null : p.scale(1 / l);
    }
    private record Basis(Point forward, Point side, Point up) {}
    private static Basis basis(Point axis) {
        Point f = unit(axis);
        if (f == null) return null;
        Point ref = Math.abs(f.y()) > .9 ? new Point(1, 0, 0) : new Point(0, 1, 0);
        Point side = unit(f.cross(ref));
        return side == null ? null : new Basis(f, side, side.cross(f));
    }
    private static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t),
                bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
    /** White-hot to colour to dim: the cooling ramp used by sparks and embers. */
    static int cooling(int hot, int cool, double t) { return t < .35 ? mix(0xFFFFF0, hot, t / .35) : mix(hot, cool, (t - .35) / .65); }

    private static void stroke(List<Stroke> out, Point from, Point to, double width, int rgb, int alpha, int texture) {
        if (from == null || to == null || !RailThunderArrayGeometry.validEndpoints(from, to) || to.minus(from).length() < 1e-6) return;
        out.add(new Stroke(new Segment(from, to), width, rgb, Math.max(1, Math.min(255, alpha)), texture));
    }

    /**
     * Ballistic spark shower from {@code origin} around {@code axis}: every spark is a motion-blurred streak (head and tail
     * of its parabola) with a white-hot head that cools to {@code cool}. Sparks live {@code life} ticks at most.
     */
    public static Parts sparkShower(Point origin, Point axis, long seed, double age, int count, double speed, double spread,
                                    double gravity, double life, int hot, int cool, VisualTier tier) {
        Basis b = basis(axis);
        if (tier == null || !tier.enhanced() || b == null || origin == null || !origin.finite() || !Double.isFinite(age) || age < 0 || count <= 0 || life <= 0) return Parts.EMPTY;
        List<Stroke> strokes = new ArrayList<>(count * 2);
        List<Billboard> sprites = new ArrayList<>(count);
        Rng rng = new Rng(seed);
        double w = tier.widthScale;
        for (int i = 0; i < count; i++) {
            double theta = spread * Math.sqrt(rng.next()), phi = rng.range(0, Math.PI * 2), v = speed * rng.range(.45, 1.35), lifeI = life * rng.range(.45, 1);
            if (age >= lifeI) continue;
            Point dir = b.forward.scale(Math.cos(theta)).plus(b.side.scale(Math.cos(phi) * Math.sin(theta))).plus(b.up.scale(Math.sin(phi) * Math.sin(theta)));
            double t = age / 20, tailT = Math.max(0, t - .075);
            Point head = origin.plus(dir.scale(v * t)).plus(new Point(0, -gravity * t * t / 2, 0));
            Point tail = origin.plus(dir.scale(v * tailT)).plus(new Point(0, -gravity * tailT * tailT / 2, 0));
            double frac = age / lifeI, fade = Math.pow(1 - frac, 1.4);
            int color = cooling(hot, cool, frac);
            stroke(strokes, tail, head, .016 * w, color, alpha(240, fade), RailThunderArrayGrandGeometry.WHITE);
            stroke(strokes, tail, head, .055 * w, color, alpha(120 * tier.glow, fade), RailThunderArrayGrandGeometry.ARC_GLOW);
            sprites.add(new Billboard(head, .075 * w, color, alpha(210 * tier.glow, fade), RailThunderArrayGrandGeometry.RADIAL, 0));
        }
        return new Parts(List.copyOf(strokes), List.copyOf(sprites), List.of());
    }

    /** Rising embers drifting around {@code center}: looping life cycle, sway and flicker, all from {@code time} (ticks). */
    public static Parts embers(Point center, double radius, double height, long seed, double time, int count, int hot, int cool, VisualTier tier) {
        if (tier == null || !tier.enhanced() || center == null || !center.finite() || !Double.isFinite(time) || count <= 0 || radius <= 0) return Parts.EMPTY;
        List<Billboard> sprites = new ArrayList<>(count);
        Rng rng = new Rng(seed);
        double w = tier.widthScale;
        for (int i = 0; i < count; i++) {
            double angle = rng.range(0, Math.PI * 2), dist = radius * Math.sqrt(rng.next()), cycle = rng.range(50, 110), offset = rng.next(), sway = rng.range(.2, .6), size = rng.range(.05, .13);
            double phase = ((time / cycle + offset) % 1 + 1) % 1;
            double flicker = .65 + .35 * Math.sin(time * .35 + i * 1.7);
            Point at = center.plus(new Point(Math.cos(angle) * dist + Math.sin(time * .05 + i) * sway, phase * height, Math.sin(angle) * dist + Math.cos(time * .043 + i * 1.3) * sway));
            double life = Math.pow(Math.sin(Math.PI * phase), .7);
            sprites.add(new Billboard(at, size * w, cooling(hot, cool, phase), alpha(215 * tier.glow, life * flicker), RailThunderArrayGrandGeometry.RADIAL, 0));
        }
        return new Parts(List.of(), List.copyOf(sprites), List.of());
    }

    /** Radial spoke burst (god-ray / impact star): {@code count} tapered rays spinning slowly and shrinking over {@code life} ticks. */
    public static Parts spokes(Point center, long seed, double age, double life, int count, double length, int rgb, double alphaBase, VisualTier tier) {
        if (tier == null || !tier.enhanced() || center == null || !center.finite() || !Double.isFinite(age) || age < 0 || age >= life || count <= 0 || length <= 0) return Parts.EMPTY;
        List<Billboard> sprites = new ArrayList<>(count);
        Rng rng = new Rng(seed);
        double fade = Math.pow(1 - age / life, .8), grow = Math.min(1, .35 + age / 2.0);
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2 / count + rng.range(-.18, .18) + age * .04, len = length * rng.range(.45, 1.15) * grow;
            sprites.add(new Billboard(center, len, rgb, alpha(alphaBase * tier.glow, fade * rng.range(.6, 1)), RailThunderArrayGrandGeometry.SPOKE, angle));
        }
        return new Parts(List.of(), List.copyOf(sprites), List.of());
    }

    /** Horizontal anamorphic lens streak through {@code center}: a long, thin, hot additive sprite aligned to the camera's right. */
    public static Parts lensStreak(Point center, double length, int rgb, double alphaBase, double envelope, VisualTier tier) {
        if (tier == null || !tier.enhanced() || center == null || !center.finite() || length <= 0 || envelope <= 0) return Parts.EMPTY;
        double stretch = 14, radius = length / stretch;
        return new Parts(List.of(), List.of(new Billboard(center, radius, rgb, alpha(alphaBase * tier.glow, envelope), RailThunderArrayGrandGeometry.RADIAL, 0, stretch),
                new Billboard(center, radius * .5, 0xFFFFFF, alpha(alphaBase * .6 * tier.glow, envelope), RailThunderArrayGrandGeometry.RADIAL, 0, stretch * .55)), List.of());
    }

    /** Flat light pool plus optional expanding shock ring on a horizontal plane (a lit ground circle, not a hit region). */
    public static Parts groundGlow(Point center, double radius, int rgb, double alphaBase, double ringRadius, double ringAlpha, VisualTier tier) {
        if (tier == null || !tier.enhanced() || center == null || !center.finite() || radius <= 0) return Parts.EMPTY;
        Point x = new Point(1, 0, 0), z = new Point(0, 0, 1);
        List<Decal> decals = new ArrayList<>(2);
        decals.add(new Decal(center, x, z, radius, rgb, alpha(alphaBase * tier.glow, 1), RailThunderArrayGrandGeometry.RADIAL));
        if (ringRadius > 0 && ringAlpha >= 1) decals.add(new Decal(center, x, z, ringRadius, rgb, alpha(ringAlpha * tier.glow, 1), RailThunderArrayGrandGeometry.RING));
        return new Parts(List.of(), List.of(), List.copyOf(decals));
    }

    /** Lens-style RGB dispersion along a path: red and blue fringes either side of a neutral core, additive. */
    public static Parts dispersion(Point from, Point to, double width, double alphaBase, VisualTier tier) {
        Basis b = from == null || to == null ? null : basis(to.minus(from));
        if (tier == null || !tier.enhanced() || b == null || width <= 0) return Parts.EMPTY;
        List<Stroke> strokes = new ArrayList<>(3);
        Point shift = b.side.scale(width * 1.5).plus(b.up.scale(width * .6));
        stroke(strokes, from.plus(shift), to.plus(shift), width * .8, 0xFF3A2A, alpha(alphaBase * tier.glow, 1), RailThunderArrayGrandGeometry.ARC_GLOW);
        stroke(strokes, from.minus(shift), to.minus(shift), width * .8, 0x2A6AFF, alpha(alphaBase * tier.glow, 1), RailThunderArrayGrandGeometry.ARC_GLOW);
        stroke(strokes, from, to, width * .6, 0x60FFA0, alpha(alphaBase * .5 * tier.glow, 1), RailThunderArrayGrandGeometry.ARC_GLOW);
        return new Parts(List.copyOf(strokes), List.of(), List.of());
    }

    /**
     * A crackling bolt from {@code from} to {@code to}: a jagged additive glow plus a thin white core, anchored at both
     * ends, re-rolled twice per tick of {@code age} so it flickers. Deterministic in (seed, age).
     */
    public static Parts lightning(Point from, Point to, long seed, double age, int steps, double amplitude, double width, int core, int glowColor, double alphaScale, VisualTier tier) {
        Basis b = from == null || to == null ? null : basis(to.minus(from));
        if (tier == null || !tier.enhanced() || b == null || !Double.isFinite(age) || age < 0 || steps < 2 || steps > 24 || amplitude < 0 || width <= 0) return Parts.EMPTY;
        double length = to.minus(from).length();
        Rng rng = new Rng(seed * 31 + (long) Math.floor(age * 2));
        List<Stroke> strokes = new ArrayList<>(steps * 2);
        Point prev = from;
        for (int k = 1; k <= steps; k++) {
            double u = k / (double) steps, amp = Math.min(amplitude, length * .08) * Math.pow(Math.sin(Math.PI * u), .8);
            Point next = k == steps ? to : from.lerp(to, u).plus(b.side.scale(rng.range(-1, 1) * amp)).plus(b.up.scale(rng.range(-1, 1) * amp));
            if (next.minus(prev).length() > 1e-6) {
                stroke(strokes, prev, next, width * 3.2 * tier.widthScale, glowColor, alpha(110 * tier.glow, alphaScale), RailThunderArrayGrandGeometry.ARC_GLOW);
                stroke(strokes, prev, next, width * .55, core, alpha(235, alphaScale), RailThunderArrayGrandGeometry.WHITE);
            }
            prev = next;
        }
        return new Parts(List.copyOf(strokes), List.of(), List.of());
    }
}

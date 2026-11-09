package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGeometry.Segment;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional NEW151 presentation of caller-confirmed paths, with no game or lifecycle ownership.
 * One receipt contributes at most eight strokes; clipping splits each at most once. The caller
 * admits a whole controller or falls back to classic, and is the sole authority for HIT and LOD.
 */
public final class RailThunderArrayGrandGeometry {
    public static final int WHITE = 0, GLOW = 1, RAIL_IN = 2, RAIL_TILE = 3, RAIL_OUT = 4;
    /** Additive decorative materials (tier STANDARD and above only): radial bloom, star flare, shock ring, tapered additive beam glow (full UV: dissolves along the path). */
    public static final int RADIAL = 5, STAR = 6, RING = 7, GLOW_ADD = 8, ARC_GLOW = 9, SPOKE = 10, LAST_TEXTURE = 10;
    public static boolean additive(int texture) { return texture >= RADIAL; }
    /** Camera-facing sprite materials (bloom, star, ring, spoke); the rest are line (ribbon) materials. */
    public static boolean sprite(int texture) { return texture >= RADIAL && texture <= RING || texture == SPOKE; }
    public static boolean lineProfile(int texture) { return texture == GLOW; }
    public static final int MAX_CONTROLLERS = 4, MAX_STROKES_PER_RECEIPT = 8;
    public static final int MAX_STROKES_PER_CONTROLLER = 128, MAX_QUADS_PER_CONTROLLER = 256;
    public static final int MAX_QUADS_GLOBAL = 1024, MAX_VERTICES_GLOBAL = 4096, MAX_PARTICLES = 0;
    public static final double CORE_HALF_WIDTH = .08, MID_HALF_WIDTH = .20, SOFT_HALF_WIDTH = .45;
    public static final double MAX_HALF_WIDTH = SOFT_HALF_WIDTH, SHORT_PATH_LENGTH = 4;
    public static final double LIFETIME_TICKS = 5, SECONDARY_DELAY_TICKS = .5;
    public static final double PREPARATION_LENGTH = 2, PREPARATION_RADIUS = .8;
    public static final double NEAR_DEPTH = .20, CENTER_CLEAR = .022, MAX_ANGULAR_HALF_WIDTH = .024;
    private static final double EPS = 1e-8, ROUNDING_MARGIN = 1e-9, MAX_CAMERA_DISTANCE = 256;

    public record Stroke(Segment segment, double halfWidth, int rgb, int alpha, int texture) {}
    /** Camera-relative doubles; a/b are start +/− side, c/d are end −/+ side.
     * minV/maxV retain the source segment's longitudinal texture fractions after clipping.
     */
    public record Quad(Point a, Point b, Point c, Point d, float minV, float maxV,
                       int rgb, int alpha, int texture) {}
    /** Camera-facing decorative sprite (bloom, flare, ring). Never a hit region; dropped when it cannot be shown whole. */
    public record Billboard(Point center, double radius, int rgb, int alpha, int texture, double roll, double stretch) {
        public Billboard(Point center, double radius, int rgb, int alpha, int texture, double roll) { this(center, radius, rgb, alpha, texture, roll, 1); }
    }
    /** Flat decal in a world plane (ground light pools, shock rings); corners are centre +/- u*r +/- v*r. Never a hit region. */
    public record Decal(Point center, Point u, Point v, double radius, int rgb, int alpha, int texture) {}
    private record Frame(Point forward, Point side, Point up) {}
    private record Interval(double from, double to) {}

    private RailThunderArrayGrandGeometry() {}

    /** Two open inward rails on this one confirmed preview ray; never a ring or another axis. */
    public static List<Stroke> preparation(Point from, Point to, int axis, double clock, boolean low) {
        if (!validPath(from, to, axis) || !Double.isFinite(clock) || clock < 0) return List.of();
        Point direction = unit(to.minus(from));
        Frame frame = pathFrame(direction);
        double length = to.minus(from).length(), reach = Math.min(PREPARATION_LENGTH, length);
        double scale = Math.min(1, length / PREPARATION_LENGTH);
        double angle = axis * Math.PI / 3;
        Point radial = frame.side.scale(Math.cos(angle)).plus(frame.up.scale(Math.sin(angle)));
        double pulse = .8 + .2 * Math.sin((clock % 40) * Math.PI / 20);
        List<Stroke> out = new ArrayList<>(low ? 2 : 4);
        for (int sign : new int[] {-1, 1}) {
            // End insets exceed the widest ribbon even after projection; the decoration stays
            // inside the first min(2m, accepted path) and never closes around the source.
            Point a = from.plus(direction.scale(reach * .10)).plus(radial.scale(sign * .68 * scale));
            Point b = from.plus(direction.scale(reach * .93)).plus(radial.scale(sign * .10 * scale));
            if (!low) add(out, new Segment(a, b), .075 * scale, RailThunderArrayGeometry.ORANGE,
                    opacity(170, pulse), GLOW);
            add(out, new Segment(a, b), .011 * scale, RailThunderArrayGeometry.IVORY,
                    opacity(190, pulse), WHITE);
        }
        return List.copyOf(out);
    }

    /**
     * Five caller-owned ticks: establish in the first tick, plateau until tick three, fade by five.
     * All three primary layers retain the exact supplied endpoints. No receipt/status is inferred.
     */
    public static List<Stroke> emission(Point from, Point to, int axis, long token, double age,
                                         boolean hold, boolean hit, boolean low) {
        if (!validPath(from, to, axis) || !Double.isFinite(age) || age < 0 || age >= LIFETIME_TICKS)
            return List.of();
        double length = to.minus(from).length(), scale = Math.min(1, length / SHORT_PATH_LENGTH);
        double envelope = age < 1 ? .2 + .8 * age : age <= 3 ? 1 : (LIFETIME_TICKS - age) / 2;
        int core = hold ? RailThunderArrayGeometry.BLUE_WHITE : RailThunderArrayGeometry.IVORY;
        int shoulder = hold ? RailThunderArrayGeometry.BLUE : RailThunderArrayGeometry.ORANGE;
        List<Stroke> out = new ArrayList<>(MAX_STROKES_PER_RECEIPT);
        Segment path = new Segment(from, to);
        if (!low) add(out, path, SOFT_HALF_WIDTH * scale, shoulder, opacity(110, envelope), GLOW);
        for (var part : RailThunderArrayGeometry.rail(from, to))
            // Original railgun pixels are already warm; multiply them by white for TAP.
            // HOLD uses neutral glow instead, so its blue tint cannot multiply baked orange.
            add(out, part.segment(), MID_HALF_WIDTH * scale, hold ? shoulder : 0xFFFFFF,
                    opacity(158, envelope), hold ? GLOW : RAIL_IN + part.texture());
        add(out, path, CORE_HALF_WIDTH * scale, core, opacity(239, envelope), WHITE);
        Frame frame = pathFrame(unit(to.minus(from)));
        if (!low && age > SECONDARY_DELAY_TICKS) {
            double phase = Math.min(1, (age - SECONDARY_DELAY_TICKS) / 1.5);
            double t0 = .16 + .20 * phase, t1 = t0 + .40;
            double sign = ((token ^ (token >>> 32) ^ axis) & 1) == 0 ? 1 : -1;
            // Two transverse components keep the accent separate when one points into camera depth.
            // hypot(.30, .22) + .035 stays below the .45m total decorative radial footprint.
            Point offset = frame.side.scale(sign * .30 * scale).plus(frame.up.scale(.22 * scale));
            Segment secondary = new Segment(from.lerp(to, t0).plus(offset),
                    from.lerp(to, t1).plus(offset.scale(.45)));
            add(out, secondary, .035 * scale, RailThunderArrayGeometry.BLUE_WHITE,
                    opacity(183, envelope * Math.min(1, (age - SECONDARY_DELAY_TICKS) / .5)), WHITE);
        }
        if (hit) {
            // Two short separated ticks immediately inside the accepted contact endpoint.
            // Inset exceeds their width, so camera-facing corners cannot extend past that endpoint.
            Point contact = to.minus(frame.forward.scale(.015 * scale));
            for (int sign : new int[] {-1, 1}) {
                Point diagonal = frame.side.scale(sign).plus(frame.up.scale(.35 * sign));
                add(out, new Segment(contact.plus(diagonal.scale(.055 * scale)),
                                contact.plus(diagonal.scale(.15 * scale))),
                        .014 * scale, core, opacity(204, envelope), WHITE);
            }
        }
        return List.copyOf(out);
    }

    /** Per-controller stroke ceiling for a tier; LOW/OFF equal the frozen constant. */
    public static int maxStrokes(VisualTier tier) { return MAX_STROKES_PER_CONTROLLER * tier.budgetScale; }
    public static int maxQuads(VisualTier tier) { return MAX_QUADS_PER_CONTROLLER * tier.budgetScale; }
    public static int maxGlobalQuads(VisualTier tier) { return MAX_QUADS_GLOBAL * tier.budgetScale; }
    /** Widest decorative half-width a tier may submit; the classic constant for LOW/OFF. */
    public static double maxHalfWidth(VisualTier tier) { return tier.enhanced() ? SOFT_HALF_WIDTH * tier.widthScale * 2 : MAX_HALF_WIDTH; }

    /**
     * Decorative-only extra layers for STANDARD/CINEMATIC, built from the same caller-confirmed endpoints.
     * Corona and end flares at layer 1; deterministic travelling sparks and a contact burst at layer 2.
     * Every stroke is camera-clipped by {@link #project(Stroke, Point, Point, Point, VisualTier)}; none is a hit or damage region.
     */
    public static List<Stroke> amplify(Point from, Point to, int axis, long token, double age,
                                        boolean hold, boolean hit, VisualTier tier) {
        if (tier == null || !tier.enhanced() || !validPath(from, to, axis) || !Double.isFinite(age) || age < 0
                || age >= LIFETIME_TICKS) return List.of();
        double length = to.minus(from).length(), scale = Math.min(1, length / SHORT_PATH_LENGTH);
        double envelope = age < 1 ? .2 + .8 * age : age <= 3 ? 1 : (LIFETIME_TICKS - age) / 2;
        int shoulder = hold ? RailThunderArrayGeometry.BLUE : RailThunderArrayGeometry.ORANGE;
        int core = hold ? RailThunderArrayGeometry.BLUE_WHITE : RailThunderArrayGeometry.IVORY;
        double w = tier.widthScale, limit = maxHalfWidth(tier);
        Point forward = unit(to.minus(from));
        Frame frame = pathFrame(forward);
        Segment path = new Segment(from, to);
        List<Stroke> out = new ArrayList<>(24);
        // The corona texture itself ramps in at the source and dissolves toward the target (no stepped pieces,
        // no square cap); glow gain keeps wide tiers from saturating to white.
        addWide(out, path, SOFT_HALF_WIDTH * scale * w * .9, shoulder, opacity((int) Math.round(120 * tier.glow), envelope), GLOW_ADD, limit);
        if (tier.layers >= 2) addWide(out, path, SOFT_HALF_WIDTH * scale * w * 1.7, shoulder, opacity((int) Math.round(60 * tier.glow), envelope), GLOW_ADD, limit);
        if (tier.layers >= 2) {
            long seed = token * 0x9E3779B97F4A7C15L + axis * 0xBF58476D1CE4E5B9L;
            int sparks = 10;
            for (int i = 0; i < sparks; i++) {
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                double unit1 = ((seed >>> 11) & 0xFFFFF) / (double) 0xFFFFF, unit2 = ((seed >>> 31) & 0xFFFFF) / (double) 0xFFFFF;
                double u = (unit1 + age * (.55 + .3 * unit2)) % 1;
                double span = Math.min(.12, .9 / Math.max(1, length));
                double angle = unit2 * Math.PI * 2, radius = (.10 + .22 * unit1) * scale * w;
                Point offset = frame.side.scale(Math.cos(angle) * radius).plus(frame.up.scale(Math.sin(angle) * radius));
                Point a = from.lerp(to, u).plus(offset), b = from.lerp(to, Math.min(1, u + span)).plus(offset);
                addWide(out, new Segment(a, b), .026 * scale, core, opacity(215, envelope), WHITE, limit);
            }
            if (hit) {
                Point contact = to.minus(forward.scale(.03 * scale));
                for (int k = 0; k < 8; k++) {
                    seed = seed * 6364136223846793005L + 1442695040888963407L;
                    double jitter = ((seed >>> 13) & 0xFFFF) / 65535.0 - .5;
                    double angle = (k + jitter * .6) * Math.PI / 4, reach = (.55 + .45 * (((seed >>> 29) & 0xFF) / 255.0)) * w * scale;
                    Point dir = frame.side.scale(Math.cos(angle)).plus(frame.up.scale(Math.sin(angle)));
                    double fade = Math.max(.1, 1 - age / LIFETIME_TICKS);
                    addWide(out, new Segment(contact.plus(dir.scale(.10 * scale)), contact.plus(dir.scale(reach))),
                            .022 * scale, core, opacity(225, envelope * fade), WHITE, limit);
                }
            }
        }
        arcs(out, from, to, frame, token, axis, age, scale, w, core, shoulder, envelope, tier, limit);
        return List.copyOf(out);
    }

    /**
     * CINEMATIC particle/post-style layers for one confirmed path: anamorphic lens streaks, radial spoke bursts, RGB
     * dispersion along the beam, a muzzle spark spray and, on contact, a ballistic spark shower. Decorative only.
     */
    public static FxLib.Parts fx(Point from, Point to, int axis, long token, double age, boolean hold, boolean hit, VisualTier tier) {
        if (tier == null || tier.layers < 3 || !validPath(from, to, axis) || !Double.isFinite(age) || age < 0 || age >= LIFETIME_TICKS) return FxLib.Parts.EMPTY;
        double length = to.minus(from).length(), scale = Math.min(1, length / SHORT_PATH_LENGTH), w = tier.widthScale;
        double envelope = age < 1 ? .2 + .8 * age : age <= 3 ? 1 : (LIFETIME_TICKS - age) / 2;
        int shoulder = hold ? RailThunderArrayGeometry.BLUE : RailThunderArrayGeometry.ORANGE, core = hold ? RailThunderArrayGeometry.BLUE_WHITE : RailThunderArrayGeometry.IVORY;
        int cool = hold ? 0x3070E0 : 0xE05A20;
        Point forward = unit(to.minus(from));
        long seed = token * 0x9E3779B97F4A7C15L + axis * 0xBF58476D1CE4E5B9L;
        FxLib.Builder out = new FxLib.Builder();
        out.add(FxLib.lensStreak(from, 3.4 * scale * w, shoulder, 150, envelope, tier));
        out.add(FxLib.spokes(from, seed ^ 0x51, age, LIFETIME_TICKS, 14, 1.9 * scale * w, shoulder, 150, tier));
        out.add(FxLib.dispersion(from, to, .05 * scale * w, 120 * envelope, tier));
        out.add(FxLib.sparkShower(from, forward, seed ^ 0x77, age, 18, 8 * scale, .55, 6, 12, core, cool, tier));
        if (hit) {
            Point contact = to.minus(forward.scale(.03 * scale));
            out.add(FxLib.spokes(contact, seed ^ 0x99, age, LIFETIME_TICKS, 18, 3.1 * scale * w, core, 200, tier));
            out.add(FxLib.lensStreak(contact, 4.4 * scale * w, shoulder, 170, envelope, tier));
            out.add(FxLib.sparkShower(contact, forward.scale(-1), seed ^ 0xAB, age, 46, 14 * scale, 1.35, 10, 18, core, cool, tier));
        }
        return out.build();
    }

    /** Largest sprite radius a tier may submit (metres): bloom/ring decoration only, never a hit region. */
    public static double maxSpriteRadius(VisualTier tier) { return tier.enhanced() ? SOFT_HALF_WIDTH * tier.widthScale * 8 : 0; }

    /** Sprite layers for the same confirmed path: source bloom, travelling energy beads, contact bloom, star flare and shock ring. */
    public static List<Billboard> billboards(Point from, Point to, int axis, long token, double age,
                                              boolean hold, boolean hit, VisualTier tier) {
        if (tier == null || !tier.enhanced() || !validPath(from, to, axis) || !Double.isFinite(age) || age < 0
                || age >= LIFETIME_TICKS) return List.of();
        double length = to.minus(from).length(), scale = Math.min(1, length / SHORT_PATH_LENGTH), w = tier.widthScale;
        double envelope = age < 1 ? .2 + .8 * age : age <= 3 ? 1 : (LIFETIME_TICKS - age) / 2;
        int shoulder = hold ? RailThunderArrayGeometry.BLUE : RailThunderArrayGeometry.ORANGE;
        int core = hold ? RailThunderArrayGeometry.BLUE_WHITE : RailThunderArrayGeometry.IVORY;
        List<Billboard> out = new ArrayList<>(8);
        out.add(new Billboard(from, .60 * scale * w, shoulder, opacity((int) Math.round(150 * tier.glow), envelope), RADIAL, 0));
        out.add(new Billboard(from, .34 * scale * w, core, opacity((int) Math.round(235 * tier.glow), envelope), RADIAL, 0));
        out.add(new Billboard(from, 1.00 * scale * w, core, opacity((int) Math.round(160 * tier.glow), envelope), STAR, age * .9 + axis));
        if (tier.layers >= 2) {
            long seed = token * 0x9E3779B97F4A7C15L + axis * 0xD6E8FEB86659FD93L;
            for (int i = 0; i < 4; i++) {
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                double unit = ((seed >>> 17) & 0xFFFF) / 65535.0;
                double u = (unit + age * (.45 + .1 * i)) % 1;
                out.add(new Billboard(from.lerp(to, u), (.20 + .06 * (i % 2)) * scale * w, core, opacity((int) Math.round(205 * tier.glow), envelope), RADIAL, 0));
            }
        }
        if (hit) {
            Point contact = to.minus(unit(to.minus(from)).scale(.03 * scale));
            double fade = Math.max(.12, 1 - age / LIFETIME_TICKS);
            out.add(new Billboard(contact, .90 * scale * w, shoulder, opacity((int) Math.round(170 * tier.glow), envelope), RADIAL, 0));
            out.add(new Billboard(contact, .48 * scale * w, core, opacity((int) Math.round(240 * tier.glow), envelope), RADIAL, 0));
            out.add(new Billboard(contact, 1.7 * scale * w, core, opacity((int) Math.round(190 * tier.glow), envelope * fade), STAR, -age * .7));
            if (tier.layers >= 2)
                out.add(new Billboard(contact, Math.min(maxSpriteRadius(tier) * .9, (.30 + .62 * age) * scale * w * 1.6), core, opacity((int) Math.round(150 * tier.glow), envelope * fade), RING, 0));
        }
        return List.copyOf(out);
    }

    /** Projects a flat decal to one world-plane quad (UV 0..1); empty when any corner is behind the near plane or non-finite. */
    public static List<Quad> projectDecal(Decal decal, Point camera, Point look, Point up, VisualTier tier) {
        if (decal == null || decal.center == null || decal.u == null || decal.v == null || !decal.center.finite() || !decal.u.finite() || !decal.v.finite()
                || !RailThunderArrayGeometry.validPoint(camera) || !Double.isFinite(decal.radius) || decal.radius <= 0 || decal.radius > 64
                || decal.rgb < 0 || decal.rgb > 0xFFFFFF || decal.alpha <= 0 || decal.alpha > 255 || !sprite(decal.texture)) return List.of();
        Frame frame = cameraFrame(look, up);
        if (frame == null) return List.of();
        Point rel = decal.center.minus(camera);
        if (!rel.finite() || rel.length() > MAX_CAMERA_DISTANCE + decal.radius) return List.of();
        Point ru = decal.u.scale(decal.radius), rv = decal.v.scale(decal.radius);
        Point a = rel.minus(ru).plus(rv), b = rel.minus(ru).minus(rv), c = rel.plus(ru).minus(rv), d = rel.plus(ru).plus(rv);
        for (Point corner : List.of(a, b, c, d)) if (!corner.finite() || corner.dot(frame.forward) < NEAR_DEPTH) return List.of();
        return List.of(new Quad(a, b, c, d, 0f, 1f, decal.rgb, decal.alpha, decal.texture));
    }

    /** Projects a sprite to one camera-facing quad (UV 0..1 on both axes); empty when behind the near plane or invalid. */
    public static List<Quad> projectBillboard(Billboard sprite, Point camera, Point look, Point up, VisualTier tier) {
        if (sprite == null || sprite.center == null || !sprite.center.finite() || !RailThunderArrayGeometry.validPoint(camera)
                || !Double.isFinite(sprite.radius) || sprite.radius <= 0
                || sprite.rgb < 0 || sprite.rgb > 0xFFFFFF || sprite.alpha <= 0 || sprite.alpha > 255
                || !sprite(sprite.texture) || !Double.isFinite(sprite.roll) || !Double.isFinite(sprite.stretch) || sprite.stretch < .1 || sprite.stretch > 64) return List.of();
        Frame frame = cameraFrame(look, up);
        if (frame == null) return List.of();
        // Oversized sprites shrink to the tier's ceiling instead of vanishing.
        double radius = Math.min(sprite.radius, maxSpriteRadius(tier));
        if (radius <= 0) return List.of();
        Point rel = sprite.center.minus(camera);
        double depth = rel.dot(frame.forward);
        if (!rel.finite() || rel.length() > MAX_CAMERA_DISTANCE || depth < NEAR_DEPTH + radius) return List.of();
        double cos = Math.cos(sprite.roll), sin = Math.sin(sprite.roll);
        Point right = frame.side.scale(cos).plus(frame.up.scale(sin)).scale(radius * sprite.stretch);
        Point upward = frame.up.scale(cos).minus(frame.side.scale(sin)).scale(radius);
        // Keep the aim reticle readable: fade a sprite whose centre sits within a small cone of the view axis.
        double angular = Math.hypot(rel.dot(frame.side), rel.dot(frame.up)) / depth;
        double calm = Math.min(1, Math.max(.35, angular / .06));
        int alpha = Math.max(1, (int) Math.round(sprite.alpha * calm));
        Point a = rel.minus(right).plus(upward), b = rel.minus(right).minus(upward);
        Point c = rel.plus(right).minus(upward), d = rel.plus(right).plus(upward);
        return List.of(new Quad(a, b, c, d, 0f, 1f, sprite.rgb, alpha, sprite.texture));
    }

    /**
     * Independent optional projection: near plane, angular width and an expanded empty aim center.
     * Subtraction and clipping stay in doubles even at the world border; at most two quads result.
     */
    public static List<Quad> project(Stroke stroke, Point camera, Point look, Point up) {
        return project(stroke, camera, look, up, VisualTier.LOW);
    }

    /** Tier-aware projection: widened angular cap and width ceiling; LOW/OFF are bit-identical to the frozen path. */
    public static List<Quad> project(Stroke stroke, Point camera, Point look, Point up, VisualTier tier) {
        double maxWidth = maxHalfWidth(tier), angular = MAX_ANGULAR_HALF_WIDTH * tier.angularScale;
        if (!validStroke(stroke, maxWidth) || !RailThunderArrayGeometry.validPoint(camera)) return List.of();
        Frame frame = cameraFrame(look, up);
        if (frame == null) return List.of();
        Point originalA = stroke.segment.start().minus(camera);
        Point originalB = stroke.segment.end().minus(camera);
        if (!originalA.finite() || !originalB.finite() || originalA.length() > MAX_CAMERA_DISTANCE
                || originalB.length() > MAX_CAMERA_DISTANCE) return List.of();
        double za = originalA.dot(frame.forward), zb = originalB.dot(frame.forward);
        double safeDepth = NEAR_DEPTH + stroke.halfWidth + ROUNDING_MARGIN;
        if (za < safeDepth && zb < safeDepth) return List.of();
        double start = 0, end = 1;
        if (za < safeDepth) start = (safeDepth - za) / (zb - za);
        else if (zb < safeDepth) end = (safeDepth - za) / (zb - za);
        if (!Double.isFinite(start) || !Double.isFinite(end) || end - start < EPS) return List.of();
        Point a = originalA.lerp(originalB, start), b = originalA.lerp(originalB, end);
        double actualWidth = Math.min(stroke.halfWidth,
                Math.min(a.dot(frame.forward), b.dot(frame.forward)) * angular);
        if (!Double.isFinite(actualWidth) || actualWidth <= 0) return List.of();
        Point direction = unit(b.minus(a));
        if (direction == null) return List.of();
        Point side = unit(direction.cross(a.lerp(b, .5).scale(-1)));
        if (side == null) side = unit(direction.cross(frame.up));
        if (side == null) side = unit(direction.cross(frame.side));
        if (side == null) return List.of();
        Point offset = side.scale(actualWidth);
        List<Quad> out = new ArrayList<>(2);
        for (Interval interval : outsideCenter(a, b, frame, actualWidth)) {
            Point p = a.lerp(b, interval.from), q = a.lerp(b, interval.to);
            if (q.minus(p).length() < EPS) continue;
            float minV = (float) (start + (end - start) * interval.from);
            float maxV = (float) (start + (end - start) * interval.to);
            Quad quad = new Quad(p.plus(offset), p.minus(offset), q.minus(offset), q.plus(offset),
                    minV, maxV, stroke.rgb, stroke.alpha, stroke.texture);
            if (!validQuad(quad, frame)) return List.of();
            out.add(quad);
        }
        return List.copyOf(out);
    }

    private static List<Interval> outsideCenter(Point a, Point b, Frame frame, double width) {
        double enter = 0, exit = 1;
        // Full plane-normal support keeps all four corners, and the entire quad, outside the cone.
        double support = width * Math.sqrt(1 + CENTER_CLEAR * CENTER_CLEAR) + ROUNDING_MARGIN;
        for (Point plane : List.of(frame.side.minus(frame.forward.scale(CENTER_CLEAR)),
                frame.side.scale(-1).minus(frame.forward.scale(CENTER_CLEAR)),
                frame.up.minus(frame.forward.scale(CENTER_CLEAR)),
                frame.up.scale(-1).minus(frame.forward.scale(CENTER_CLEAR)))) {
            double va = a.dot(plane) - support, vb = b.dot(plane) - support, delta = vb - va;
            if (Math.abs(delta) < EPS) {
                if (va > 0) return List.of(new Interval(0, 1));
            } else {
                double crossing = -va / delta;
                if (delta > 0) exit = Math.min(exit, crossing); else enter = Math.max(enter, crossing);
            }
            if (enter >= exit) return List.of(new Interval(0, 1));
        }
        List<Interval> out = new ArrayList<>(2);
        if (enter > EPS) out.add(new Interval(0, Math.min(1, enter)));
        if (exit < 1 - EPS) out.add(new Interval(Math.max(0, exit), 1));
        return out;
    }

    private static boolean validQuad(Quad q, Frame frame) {
        if (!Float.isFinite(q.minV) || !Float.isFinite(q.maxV) || q.minV < 0 || q.maxV > 1
                || q.minV > q.maxV) return false;
        for (Point p : List.of(q.a, q.b, q.c, q.d)) {
            double depth = p.dot(frame.forward);
            if (!p.finite() || depth < NEAR_DEPTH
                    || Math.max(Math.abs(p.dot(frame.side)), Math.abs(p.dot(frame.up)))
                    < CENTER_CLEAR * depth) return false;
        }
        return true;
    }

    private static boolean validStroke(Stroke stroke) { return validStroke(stroke, MAX_HALF_WIDTH); }

    private static boolean validStroke(Stroke stroke, double maxWidth) {
        return stroke != null && stroke.segment != null
                && RailThunderArrayGeometry.validEndpoints(stroke.segment.start(), stroke.segment.end())
                && stroke.segment.end().minus(stroke.segment.start()).length() >= EPS
                && Double.isFinite(stroke.halfWidth) && stroke.halfWidth > 0 && stroke.halfWidth <= maxWidth
                && stroke.rgb >= 0 && stroke.rgb <= 0xFFFFFF && stroke.alpha > 0 && stroke.alpha <= 255
                && stroke.texture >= WHITE && stroke.texture <= LAST_TEXTURE && !sprite(stroke.texture);
    }

    private static boolean validPath(Point from, Point to, int axis) {
        return axis >= 0 && axis < RailThunderArrayGeometry.MAX_AXES
                && RailThunderArrayGeometry.validEndpoints(from, to) && to.minus(from).length() >= EPS;
    }

    private static Frame pathFrame(Point forward) {
        Point reference = Math.abs(forward.y()) > .9 ? new Point(1, 0, 0) : new Point(0, 1, 0);
        Point side = unit(forward.cross(reference));
        return new Frame(forward, side, side.cross(forward));
    }

    private static Frame cameraFrame(Point look, Point up) {
        Point forward = unit(look), cameraUp = unit(up);
        if (forward == null || cameraUp == null) return null;
        Point side = unit(forward.cross(cameraUp));
        if (side == null) return null;
        return new Frame(forward, side, side.cross(forward));
    }

    private static Point unit(Point p) {
        if (p == null || !p.finite()) return null;
        double magnitude = Math.max(Math.abs(p.x()), Math.max(Math.abs(p.y()), Math.abs(p.z())));
        if (magnitude == 0) return null;
        Point scaled = p.scale(1 / magnitude);
        double length = scaled.length();
        return length < EPS || !Double.isFinite(length) ? null : scaled.scale(1 / length);
    }

    private static void add(List<Stroke> out, Segment segment, double width, int rgb, int alpha, int texture) {
        Stroke stroke = new Stroke(segment, width, rgb, alpha, texture);
        if (validStroke(stroke)) out.add(stroke);
    }

    /**
     * Crackling electric arcs wandering around the confirmed path (the Electromaster identity). Deterministic per
     * token/axis and re-rolled every half tick; zero displacement at both ends so they stay attached to the source
     * and the contact. Decorative only: well inside the tier's width ceiling and never a hit region.
     */
    private static void arcs(List<Stroke> out, Point from, Point to, Frame frame, long token, int axis, double age,
                             double scale, double w, int core, int glowColor, double envelope, VisualTier tier, double limit) {
        int count = tier.layers >= 2 ? 3 : 1, steps = 8;
        long flicker = (long) Math.floor(age * 2);
        double length = to.minus(from).length();
        for (int a = 0; a < count; a++) {
            long seed = (token * 0x9E3779B97F4A7C15L) ^ (axis * 0xD6E8FEB86659FD93L) ^ ((a + 1) * 0xA24BAED4963EE407L) ^ (flicker * 0x9FB21C651E98DF25L);
            Point prev = from;
            for (int k = 1; k <= steps; k++) {
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                double r1 = ((seed >>> 11) & 0xFFFFF) / (double) 0xFFFFF * 2 - 1, r2 = ((seed >>> 33) & 0xFFFFF) / (double) 0xFFFFF * 2 - 1;
                double u = k / (double) steps, envelopeAmp = Math.pow(Math.sin(Math.PI * u), .8);
                double amp = Math.min(.9 * scale * w, length * .06) * envelopeAmp;
                Point next = k == steps ? to : from.lerp(to, u).plus(frame.side.scale(r1 * amp)).plus(frame.up.scale(r2 * amp));
                if (next.minus(prev).length() > 1e-6) {
                    addWide(out, new Segment(prev, next), .085 * scale * w, glowColor, opacity((int) Math.round(120 * tier.glow), envelope), ARC_GLOW, limit);
                    addWide(out, new Segment(prev, next), .016 * scale, core, opacity(235, envelope), WHITE, limit);
                    // One short fork per arc, at the fourth joint, leaning away from the path.
                    if (k == 4 && tier.layers >= 2) {
                        Point lean = frame.side.scale(r2).plus(frame.up.scale(r1));
                        Point tip = next.plus(lean.scale(.55 * scale * w)).plus(unit(to.minus(from)).scale(.35 * scale * w));
                        addWide(out, new Segment(next, tip), .05 * scale * w, glowColor, opacity((int) Math.round(80 * tier.glow), envelope), ARC_GLOW, limit);
                        addWide(out, new Segment(next, tip), .011 * scale, core, opacity(200, envelope), WHITE, limit);
                    }
                }
                prev = next;
            }
        }
    }

    private static void addWide(List<Stroke> out, Segment segment, double width, int rgb, int alpha, int texture, double limit) {
        Stroke stroke = new Stroke(segment, Math.min(width, limit), rgb, alpha, texture);
        if (validStroke(stroke, limit)) out.add(stroke);
    }

    private static int opacity(int base, double envelope) {
        return Math.max(1, Math.min(255, (int) Math.round(base * envelope)));
    }
}

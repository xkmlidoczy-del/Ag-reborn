package cn.academy.port.client;

import static cn.academy.port.client.FxMotifs.glowLine;

import cn.academy.port.VisualTier;
import cn.academy.port.highpower.ConstructRuntime;
import cn.academy.port.highpower.core.ConstructRules;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Billboard;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Stroke;
import java.util.ArrayList;
import java.util.List;

/**
 * V-ADV-01 presentation in the vector palette, technological: the preview is a scanned bounding frame with a hex survey grid
 * and a block-count gauge; the held structure is boxed, tethered to the hand by a vector beam with packets, ringed by
 * stabiliser orbits and floated on an anti-gravity plate; a throw leaves chevrons and a streak; landing prints a grid lock.
 * Pure and deterministic; the moving blocks themselves are drawn by the physics backend.
 */
public final class ConstructDecor {
    public static final int MAX_QUADS = 1100, PREVIEW_LIFE = ConstructRules.PREVIEW_TICKS, HOLD_STALE = 8, BEAT_LIFE = 30, BIG_SIZE = 10;
    private static final int AIR = 0xDCE8F2, CYAN = 0x8FD8FF, TEAL = 0x7FF5E8, WARM = 0xFFE9C0, RED = 0xFF6A5A;
    private static final Point X = new Point(1, 0, 0), Y = new Point(0, 1, 0), Z = new Point(0, 0, 1);

    private ConstructDecor() {}

    private static double t01(double x) { return x != x ? 0 : Math.max(0, Math.min(1, x)); }
    private static Point up(Point a, double dy) { return a.plus(new Point(0, dy, 0)); }

    /** Nearest point of the structure's box to the hand: where the grip lands (the centre if the hand is inside the box). */
    static Point grip(Point hand, Point min, Point max, Point center) {
        if (!FxMotifs.ok(hand, min, max, center)) return center;
        Point g = new Point(Math.max(min.x(), Math.min(max.x(), hand.x())), Math.max(min.y(), Math.min(max.y(), hand.y())), Math.max(min.z(), Math.min(max.z(), hand.z())));
        return g.minus(hand).length() < 1e-6 ? center : g;
    }

    /** Kept for callers without a bounding box: the grip is taken at the centre. */
    public static FxLib.Parts controlBeat(int phase, Point center, Point hand, double age, VisualTier tier) { return controlBeat(phase, center, center, center, hand, age, tier); }

    /**
     * One-shot beats use phase age, not the repeatedly refreshed HOLD packet's age. The grab pulls sparks and data packets from the
     * grip point on the structure's surface into the hand, so the effect reads as the hand taking hold of the body.
     */
    public static FxLib.Parts controlBeat(int phase, Point center, Point min, Point max, Point hand, double age, VisualTier tier) {
        if(tier==null || tier.layers<3 || !FxMotifs.ok(center,hand) || !Double.isFinite(age) || age<0) return FxLib.Parts.EMPTY;
        var out=new FxLib.Builder();
        if(phase==ConstructRuntime.PREVIEW && age<12) {
            out.add(FxMotifs.reticle(center,X,Y,1.8-.8*t01(age/10),0,TEAL,230,tier));
        } else if(phase==ConstructRuntime.HOLD && age<cn.academy.port.highpower.core.ConstructPose.PULL_TICKS) {
            // The hand closes on the structure: sparks gather at the grip and a ring contracts on it. Nothing travels between the two.
            double f=1-t01(age/cn.academy.port.highpower.core.ConstructPose.PULL_TICKS);
            Point g=FxMotifs.ok(min,max)?grip(hand,min,max,center):center;
            double size=FxMotifs.ok(min,max)?Math.max(1,max.minus(min).length()):1;
            double t=t01(age/cn.academy.port.highpower.core.ConstructPose.PULL_TICKS);
            out.add(FxMotifs.converging(g,12,Math.max(.5,Math.min(1.8,size*.1)),t,444,AIR,200*f,tier));
            Point d=FxMotifs.unit(hand.minus(g));
            Point[] fr=FxMotifs.frame(d==null?Y:d);
            out.add(FxMotifs.ring(g,fr[1],fr[2],(.4+1.0*f)*Math.max(.6,Math.min(1.4,size*.08)),CYAN,210*f));
        }
        return out.build();
    }

    /**
     * The dash at a locked structure: the target stays boxed, a landing mark sits where the caster will stop, data packets run the
     * path and chevrons trail behind the caster. Everything stays on the ground line or around the target, clear of the view.
     */
    public static FxLib.Parts dashFx(Point min, Point max, Point from, Point stand, double age, double time, VisualTier tier) {
        if (tier == null || tier.layers < 3 || !FxMotifs.ok(min, max, from, stand) || !Double.isFinite(age) || age < 0 || !Double.isFinite(time)) return FxLib.Parts.EMPTY;
        if (max.minus(min).length() > 128 || from.minus(stand).length() > 96) return FxLib.Parts.EMPTY;
        double w = 1 + .12 * (tier.widthScale - 1), size = Math.max(1, max.minus(min).length());
        var out = new FxLib.Builder();
        double breathe = .75 + .25 * Math.sin(time * .5);
        out.add(FxMotifs.box(min, max, RED, 150 * breathe, tier));
        Point mark = new Point(stand.x(), min.y() + .05, stand.z());
        out.add(FxMotifs.reticle(mark, X, Z, 1.1 * w * (1.3 - .3 * breathe), time * .15, TEAL, 230, tier));
        out.add(FxMotifs.ring(mark, X, Z, 1.7 * w, CYAN, 150 * breathe));
        if (age >= cn.academy.port.highpower.core.ConstructPose.CROUCH_TICKS) {
            Point path = stand.minus(from); double len = path.length();
            Point d = FxMotifs.unit(path);
            if (d != null && len > 1) {
                out.add(FxMotifs.packets(from, stand, 6, time, .2, 1.2 * w, AIR, 200, tier));
                out.add(FxMotifs.chevrons(from.minus(d.scale(.5)), d.scale(-1), 3.5, 5, .7 * w, time * .5, AIR, 190, tier));
            }
        } else {
            double t = t01(age / cn.academy.port.highpower.core.ConstructPose.CROUCH_TICKS);
            out.add(FxMotifs.converging(new Point(from.x(), from.y() + .1, from.z()), 10, 1.6 * (1 - t) + .3, t, 777, AIR, 190, tier));
        }
        return out.build();
    }

    /** {@code age} counts ticks since this cue was received. */
    public static FxLib.Parts fx(int phase, Point center, Point min, Point max, Point aux, int blocks, double age, double time, VisualTier tier) {
        if (tier == null || tier.layers < 3 || !FxMotifs.ok(center) || !Double.isFinite(age) || age < 0 || !Double.isFinite(time)) return FxLib.Parts.EMPTY;
        if (!FxMotifs.ok(min, max) || max.minus(min).length() > 128 || center.minus(min).length() > 128) { min = center; max = center; }
        double size = Math.max(1, max.minus(min).length()), w = 1 + .12 * (tier.widthScale - 1);
        FxLib.Builder out = new FxLib.Builder();
        Point floor = new Point(center.x(), Math.min(min.y(), center.y()) + .05, center.z());
        switch (phase) {
            case ConstructRuntime.PREVIEW -> {
                if (age > PREVIEW_LIFE) break;
                double breathe = .75 + .25 * Math.sin(time * .3);
                out.add(FxMotifs.box(min, max, TEAL, 220 * breathe, tier));
                out.add(FxMotifs.hexGrid(floor, X, Z, Math.max(.4, size * .08) * w, 3, time * .02, TEAL, 170, tier));
                out.add(FxMotifs.reticle(center, X, Z, Math.max(.8, size * .25) * w, time * .03, AIR, 210, tier));
                out.add(FxMotifs.scanRings(floor, Math.max(1, max.y() - min.y()), Math.max(.8, size * .35), 3, time, .04, CYAN, 190, tier));
                int filled = (int) Math.round(16 * t01(blocks / 4096.0 * 4));
                out.add(FxMotifs.gauge(up(floor, .02), X, Z, Math.max(1.2, size * .45) * w, 16, Math.max(1, filled), CYAN, 200, tier));
            }
            case ConstructRuntime.REFUSED -> {
                if (age > BEAT_LIFE) break;
                double f = 1 - t01(age / BEAT_LIFE);
                out.add(FxMotifs.reticle(center, X, Z, .9 * w, time * .05, RED, 230 * f + 10, tier));
                List<Stroke> cross = new ArrayList<>();
                glowLine(cross, center.plus(new Point(-.6, .6, 0)), center.plus(new Point(.6, -.6, 0)), .1 * w, RED, 120 * f * tier.glow + 5, .016, 220 * f + 10);
                glowLine(cross, center.plus(new Point(-.6, -.6, 0)), center.plus(new Point(.6, .6, 0)), .1 * w, RED, 120 * f * tier.glow + 5, .016, 220 * f + 10);
                out.add(new FxLib.Parts(List.copyOf(cross), List.of(), List.of()));
            }
            case ConstructRuntime.HOLD -> {
                if (age > HOLD_STALE) break;
                // Large structures sit beside the crosshair and fill much of the view: thin box, no orbits or mid-height ring across it.
                boolean big = size > BIG_SIZE;
                out.add(FxMotifs.box(min, max, AIR, big ? 110 : 190, tier));
                double r = Math.max(1.2, size * .55);
                if (!big) {
                    out.add(FxMotifs.orbit(center, X, Z, r * w, 6, time * .07, .22, CYAN, 220, tier));
                    out.add(FxMotifs.orbit(center, X, Y, r * .85 * w, 4, -time * .09, .18, TEAL, 200, tier));
                    out.add(FxMotifs.ring(center, X, Z, r * w, CYAN, 120));
                }
                Point plate = new Point(center.x(), min.y() - .4, center.z());
                out.add(FxMotifs.hexGrid(plate, X, Z, Math.max(.4, size * .07) * w, 2, time * .03, TEAL, big ? 130 : 190, tier));
                for (int k = 0; k < 3; k++) {
                    double t = ((time * .05 + k / 3.0) % 1 + 1) % 1;
                    out.add(FxMotifs.ring(up(plate, -1.5 * t), X, Z, r * (1 - .4 * t) * w, CYAN, (big ? 120 : 180) * (1 - t)));
                }
                if (FxMotifs.ok(aux) && aux.length() > 1e-6 && aux.minus(center).length() < 64) {
                    // The structure is in the hand: a grip glow where the fingers meet its surface, no beam between them.
                    Point g = grip(aux, min, max, center), to = g.minus(aux);
                    double len = to.length();
                    if (len > 1e-6) {
                        Point[] fr = FxMotifs.frame(to.scale(-1 / len));
                        double gripR = Math.max(.3, Math.min(.9, size * .05)) * w;
                        out.add(FxMotifs.reticle(g, fr[1], fr[2], gripR, time * .1, AIR, 220, tier));
                        out.add(FxMotifs.ring(g, fr[1], fr[2], gripR * (1.2 + .12 * Math.sin(time * .4)), CYAN, 160));
                    }
                    out.add(new FxLib.Parts(List.of(), List.of(new Billboard(g, .3 * w, AIR, FxLib.alpha(200 * tier.glow, 1), RailThunderArrayGrandGeometry.RADIAL, 0)), List.of()));
                }
            }
            case ConstructRuntime.THROWN -> {
                if (age > BEAT_LIFE) break;
                double f = 1 - t01(age / BEAT_LIFE);
                Point d = FxMotifs.unit(aux);
                if (d != null) {
                    out.add(FxMotifs.chevrons(center.minus(d.scale(size)), d, size * 2.5, 6, Math.max(.6, size * .2) * w, age * .3, AIR, 240 * f + 10, tier));
                    Point[] fr = FxMotifs.frame(d);
                    Point tail = center.minus(d.scale(size * .7));
                    out.add(FxMotifs.ring(tail, fr[1], fr[2], (size * .35 + age * .12) * w, AIR, 150 * f * f));
                    out.add(FxMotifs.ring(tail, fr[1], fr[2], (size * .25 + age * .08) * w, WARM, 120 * f * f));
                    out.add(FxMotifs.converging(tail, 10, size * .8 * w, t01(age / 10.0), 444, AIR, 170 * f, tier));
                }
                out.add(FxLib.spokes(center, 444, age, 12, 10, (1 + size * .25) * w, AIR, 140, tier));
            }
            case ConstructRuntime.LANDED -> {
                if (age > BEAT_LIFE) break;
                double t = t01(age / BEAT_LIFE), f = 1 - t;
                out.add(FxMotifs.hexGrid(floor, X, Z, Math.max(.45, size * .08) * w, 3, t, TEAL, 230 * f + 10, tier));
                out.add(FxMotifs.reticle(floor, X, Z, Math.max(1.5, size * .6) * w * (1.4 - .4 * t), t, AIR, 230 * f + 10, tier));
                out.add(FxMotifs.ring(floor, X, Z, (1 + size * 1.2 * t) * w, AIR, 230 * f));
                out.add(FxMotifs.voxels(floor, Math.max(1, max.y() - min.y()), Math.max(1, size * .4), 32, 1 - Math.min(1, t * 2.5), 4444, TEAL, 230, tier));
                out.add(FxLib.sparkShower(floor, Y, 444, age, 24, 6, 1.4, 8, 14, AIR, CYAN, tier));
            }
            default -> { }
        }
        return out.build();
    }
}

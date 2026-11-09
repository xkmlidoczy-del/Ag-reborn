package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import cn.academy.port.highpower.core.VectorParryRules.Point;

import static cn.academy.port.highpower.core.CirculationListeningRules.*;

/** Standalone sensor policy: bounded queries, actual motion, coarse world bins and CP only. */
public final class CirculationListeningRulesRegressionTest {
    private static final Point ORIGIN = p(0, 0, 0);
    private static final Object WORLD = new Object();
    private static final UUID ID = new UUID(433, 1);
    private static int checks;

    public static void main(String[] args) {
        sampleContinuity();
        motionBoundaries();
        sphericalRange();
        directionQuantization();
        rotationAndTranslation();
        strengthQuantization();
        boundedSelection();
        presentationShape();
        economics();
        System.out.println("CirculationListeningRulesRegressionTest passed " + checks + " checks");
    }

    private static void sampleContinuity() {
        Track valid = track(p(0, 0, 8), p(1, 0, 0), Kind.ARROW);
        check(continuous(valid.previous(), valid.current()), "two actual ticks with consistent velocity accept");
        check(cue(ORIGIN, valid.previous(), valid.current()) != null, "moving native arrow can produce a cue");
        check(cue(ORIGIN, null, valid.current()) == null, "first observation never produces a cue");
        check(cue(ORIGIN, valid.previous(), null) == null, "absent current observation refuses");
        check(!continuous(valid.current(), valid.previous()), "reverse clock refuses");
        Sample current = valid.current(), before = valid.previous();
        for (long tick : new long[] {-1, 0, 98, 99, 100, 101, 103, 104, Long.MAX_VALUE}) {
            check(!continuous(before, copy(current, ID, WORLD, tick, Kind.ARROW,
                    current.position(), current.velocity(), false)), "gaps, duplicates and invalid clocks refuse");
        }
        check(continuous(before, copy(current, new UUID(433, 1), WORLD, current.tick(), Kind.ARROW,
                current.position(), current.velocity(), false)), "same UUID value is stable across snapshots");
        for (UUID id : new UUID[] {null, new UUID(0, 0), new UUID(433, 2)}) {
            check(!continuous(before, copy(current, id, WORLD, current.tick(), Kind.ARROW,
                    current.position(), current.velocity(), false)), "absent, zero or changed identity refuses");
        }
        for (Object world : new Object[] {null, new Object()}) {
            check(!continuous(before, copy(current, ID, world, current.tick(), Kind.ARROW,
                    current.position(), current.velocity(), false)), "absent or changed world refuses");
        }
        Object worldA = new String("same dimension key"), worldB = new String("same dimension key");
        Sample worldBefore = copy(before, ID, worldA, before.tick(), Kind.ARROW,
                before.position(), before.velocity(), false);
        check(!continuous(worldBefore, copy(current, ID, worldB, current.tick(), Kind.ARROW,
                current.position(), current.velocity(), false)), "equal keys do not establish same native world object");
        for (Kind kind : new Kind[] {null, Kind.RUNNER}) {
            check(!continuous(before, copy(current, ID, WORLD, current.tick(), kind,
                    current.position(), current.velocity(), true)), "absent or changed source kind refuses");
        }
        for (Point invalid : new Point[] {null, p(Double.NaN, 0, 0), p(0, Double.NaN, 0),
                p(0, 0, Double.NaN), p(Double.POSITIVE_INFINITY, 0, 0),
                p(0, Double.NEGATIVE_INFINITY, 0), p(0, 0, Double.POSITIVE_INFINITY)}) {
            check(!continuous(before, copy(current, ID, WORLD, current.tick(), Kind.ARROW,
                    invalid, current.velocity(), false)), "nonfinite or absent current position refuses");
            check(!continuous(before, copy(current, ID, WORLD, current.tick(), Kind.ARROW,
                    current.position(), invalid, false)), "nonfinite or absent current velocity refuses");
            check(!continuous(copy(before, ID, WORLD, before.tick(), Kind.ARROW,
                    invalid, before.velocity(), false), current), "nonfinite or absent prior position refuses");
            check(!continuous(copy(before, ID, WORLD, before.tick(), Kind.ARROW,
                    before.position(), invalid, false), current), "nonfinite or absent prior velocity refuses");
            check(cue(invalid, before, current) == null, "invalid listener refuses");
        }
        check(!clock(-1) && !clock(Long.MAX_VALUE) && clock(0)
                && clock(Long.MAX_VALUE - PAYMENT_PERIOD), "clock leaves space for physical scheduling");
    }

    private static void motionBoundaries() {
        for (Kind kind : Kind.values()) {
            double minimum = kind == Kind.ARROW ? ARROW_MIN_SPEED : RUNNER_MIN_SPEED;
            double maximum = kind == Kind.ARROW ? ARROW_MAX_SPEED : RUNNER_MAX_SPEED;
            for (double speed : new double[] {minimum, Math.nextUp(minimum), 1, Math.nextDown(maximum), maximum}) {
                Track t = track(p(0, 0, 8), p(speed, 0, 0), kind);
                check(continuous(t.previous(), t.current()), "inclusive physical speed bounds accept " + kind + " " + speed);
            }
            for (double speed : new double[] {0, Double.MIN_VALUE, Math.nextDown(minimum),
                    Math.nextUp(maximum), Double.MAX_VALUE}) {
                Track t = track(p(0, 0, 8), p(speed, 0, 0), kind);
                check(!continuous(t.previous(), t.current()), "out-of-bound velocity refuses " + kind + " " + speed);
            }
            Track moving = track(p(0, 0, 8), p(1, 0, 0), kind);
            Sample before = moving.previous(), current = moving.current();
            check(!continuous(copy(before, ID, WORLD, before.tick(), kind,
                    current.position(), before.velocity(), true), current), "stationary source with stale velocity refuses");
            if (kind == Kind.ARROW) {
                check(!continuous(before, copy(current, ID, WORLD, current.tick(), kind,
                        current.position(), ORIGIN, true)), "arrow with zero current native velocity refuses");
                check(!continuous(copy(before, ID, WORLD, before.tick(), kind,
                        before.position(), ORIGIN, true), current), "zero prior arrow velocity refuses");
                check(!continuous(before, copy(current, ID, WORLD, current.tick(), kind,
                        p(-4, 0, 8), current.velocity(), true)), "arrow displacement must agree with prior native velocity");
            }
            check(!continuous(before, copy(current, ID, WORLD, current.tick(), kind,
                    p(19, 0, 8), current.velocity(), true)), "large discontinuity cannot borrow speed from saved velocity");
            check(!continuous(copy(before, ID, WORLD, before.tick(), kind,
                    p(-Double.MAX_VALUE, 0, 0), before.velocity(), true),
                    copy(current, ID, WORLD, current.tick(), kind,
                            p(Double.MAX_VALUE, 0, 0), current.velocity(), true)), "overflowing displacement fails closed");
        }
        Track runner = track(p(0, 0, 8), p(.3, 0, 0), Kind.RUNNER);
        check(cue(ORIGIN, runner.previous(), runner.current()).kind() == 1, "sprinting actual horizontal motion is runner kind");
        for (Point reported : new Point[] {ORIGIN, p(Double.MIN_VALUE, 0, 0), p(.01, -.0784, 0),
                p(-.4, 0, 0), p(0, .1, 1), p(1.5, 4, 0)}) {
            Sample start = new Sample(ID, WORLD, 100, Kind.RUNNER, p(0, 0, 8), reported, true);
            Sample finish = new Sample(ID, WORLD, 102, Kind.RUNNER, p(.5, 0, 8), reported, true);
            check(continuous(start, finish) && cue(ORIGIN, start, finish) != null,
                    "real .25m/tick sprint accepts zero or stale bounded native delta");
            check(!continuous(start, new Sample(ID, WORLD, 102, Kind.RUNNER, start.position(), reported, true)),
                    "sprint flag and any saved delta cannot cue a stationary source");
            check(!continuous(start, new Sample(ID, WORLD, 102, Kind.RUNNER, finish.position(), reported, false)),
                    "actual walking movement with stale delta still refuses");
        }
        Sample zeroStart = new Sample(ID, WORLD, 100, Kind.RUNNER, p(0, 0, 8), ORIGIN, true);
        for (double speed : new double[] {RUNNER_MIN_SPEED, RUNNER_MAX_SPEED}) {
            check(continuous(zeroStart, new Sample(ID, WORLD, 102, Kind.RUNNER,
                    p(speed * 2, 0, 8), ORIGIN, true)), "zero reported delta preserves inclusive actual runner speed edges");
        }
        for (double speed : new double[] {Math.nextDown(RUNNER_MIN_SPEED), Math.nextUp(RUNNER_MAX_SPEED)}) {
            check(!continuous(zeroStart, new Sample(ID, WORLD, 102, Kind.RUNNER,
                    p(speed * 2, 0, 8), ORIGIN, true)), "zero reported delta cannot relax actual runner speed bounds");
        }
        for (Point reported : new Point[] {p(Math.nextUp(RUNNER_MAX_SPEED), 0, 0),
                p(0, Math.nextUp(MAX_RUNNER_VERTICAL_SPEED), 0), p(Double.NaN, 0, 0),
                p(0, 0, Double.POSITIVE_INFINITY)}) {
            check(!continuous(zeroStart, new Sample(ID, WORLD, 102, Kind.RUNNER,
                    p(.5, 0, 8), reported, true)), "nonfinite or defensively unbounded reported runner delta refuses");
        }
        check(!continuous(copy(runner.previous(), ID, WORLD, 100, Kind.RUNNER,
                runner.previous().position(), runner.previous().velocity(), false), runner.current()),
                "starting sprint on current frame cannot fabricate a continuous sprint interval");
        check(!continuous(runner.previous(), copy(runner.current(), ID, WORLD, 102, Kind.RUNNER,
                runner.current().position(), runner.current().velocity(), false)), "walking current frame refuses");
        Track vertical = track(p(0, 8, 0), p(0, .8, 0), Kind.RUNNER);
        check(!continuous(vertical.previous(), vertical.current()), "vertical-only motion is not a runner disturbance");
        Track jump = track(p(0, 1, 8), p(.3, .4, 0), Kind.RUNNER);
        check(continuous(jump.previous(), jump.current()), "ordinary bounded sprint jump retains horizontal speed meaning");
        Track invalidFall = track(p(0, 1, 8), p(.3, Math.nextUp(MAX_RUNNER_VERTICAL_SPEED), 0), Kind.RUNNER);
        check(!continuous(invalidFall.previous(), invalidFall.current()), "excessive runner vertical velocity refuses");
        Track arrow = track(p(0, 0, 8), p(1, 0, 0), Kind.ARROW);
        check(continuous(arrow.previous(), copy(arrow.current(), ID, WORLD, 102, Kind.ARROW,
                p(-.01, -.05, 8), p(.99, -.05, 0), false)), "small native arrow drag and gravity remain continuous");
        check(!continuous(arrow.previous(), copy(arrow.current(), ID, WORLD, 102, Kind.ARROW,
                p(3, 0, 8), p(1, 0, 0), false)), "bounded speed alone cannot excuse disagreement with prior velocity");
    }

    private static void sphericalRange() {
        check(RANGE == cn.academy.port.core.SkillTuning.number("circulation_listening", "reach") && MAX_CUES == 8 && QUERY_LIMIT == 64 && SAMPLE_INTERVAL_TICKS == 2,
                "provisional physical and query fences remain explicit");
        for (Point edge : new Point[] {p(RANGE, 0, 0), p(-RANGE, 0, 0), p(0, RANGE, 0),
                p(0, -RANGE, 0), p(0, 0, RANGE), p(0, 0, -RANGE)}) {
            check(withinRange(ORIGIN, edge), "inclusive sphere boundary accepts");
            Track t = track(edge, p(1, 0, 0), Kind.ARROW);
            check(cue(ORIGIN, t.previous(), t.current()) != null, "edge source can produce coarse cue");
        }
        for (Point outside : new Point[] {p(Math.nextUp(RANGE), 0, 0), p(0, Math.nextUp(RANGE), 0),
                p(0, 0, -Math.nextUp(RANGE)), p(RANGE, 0, Double.MIN_VALUE),
                p(RANGE, RANGE, RANGE), p(Double.MAX_VALUE, 0, 0)}) {
            check(!withinRange(ORIGIN, outside), "exact outward point or cube-only corner refuses");
        }
        check(withinRange(ORIGIN, ORIGIN), "coincident coordinates are geometrically in range");
        Track coincident = track(ORIGIN, p(1, 0, 0), Kind.ARROW);
        check(cue(ORIGIN, coincident.previous(), coincident.current()) == null, "coincident source cannot invent a direction");
        check(!withinRange(null, ORIGIN) && !withinRange(ORIGIN, null), "missing endpoints refuse");
        check(!withinRange(p(-Double.MAX_VALUE, 0, 0), p(Double.MAX_VALUE, 0, 0)), "range subtraction overflow refuses");
    }

    private static void directionQuantization() {
        for (int azimuth = 0; azimuth < 16; azimuth++) {
            double center = azimuth * Math.PI / 8;
            Cue centerCue = at(p(10 * Math.sin(center), 0, 10 * Math.cos(center)), 1, Kind.ARROW);
            check(centerCue.azimuth() == azimuth && centerCue.elevation() == 0, "all sixteen world centers have stable bins");
            for (double side : new double[] {-1, 1}) {
                double inside = center + side * (Math.PI / 16 - 1e-9);
                double outside = center + side * (Math.PI / 16 + 1e-9);
                check(at(p(10 * Math.sin(inside), 0, 10 * Math.cos(inside)), 1, Kind.ARROW).azimuth() == azimuth,
                        "inside azimuth half-bin retains current center");
                check(at(p(10 * Math.sin(outside), 0, 10 * Math.cos(outside)), 1, Kind.ARROW).azimuth()
                        == Math.floorMod(azimuth + (int) side, 16), "outside azimuth half-bin crosses one world bin");
            }
        }
        double boundary = 10 * Math.tan(Math.toRadians(15));
        for (double sign : new double[] {-1, 1}) {
            check(at(p(0, sign * boundary, 10), 1, Kind.ARROW).elevation() == 0,
                    "represented 15-degree elevation edge remains level");
            check(at(p(0, sign * Math.nextDown(boundary), 10), 1, Kind.ARROW).elevation() == 0,
                    "inward elevation neighbor remains level");
            check(at(p(0, sign * Math.nextUp(boundary), 10), 1, Kind.ARROW).elevation() == (int) sign,
                    "outward elevation neighbor uses up/down bin");
            Cue vertical = at(p(0, sign * 10, 0), 1, Kind.ARROW);
            check(vertical.azimuth() == 0 && vertical.elevation() == (int) sign,
                    "exact vertical uses explicit canonical azimuth and correct elevation");
        }
        check(at(p(-0.0, 0, 10), 1, Kind.ARROW).azimuth() == 0, "negative zero does not create an extra azimuth");
    }

    private static void rotationAndTranslation() {
        Random random = new Random(433);
        for (int i = 0; i < 1000; i++) {
            int bin = random.nextInt(16);
            double angle = bin * Math.PI / 8 + (random.nextDouble() - .5) * .1;
            double elevation = (random.nextInt(3) - 1) * .5;
            Point source = p(8 * Math.sin(angle), 8 * Math.tan(elevation), 8 * Math.cos(angle));
            Track original = track(source, p(1, 0, 0), Kind.ARROW);
            Cue expected = cue(ORIGIN, original.previous(), original.current());
            check(expected != null && expected.azimuth() == bin, "random interior world angle matches reference bin");
            Point offset = p(random.nextDouble() * 59_998_000 - 29_999_000,
                    random.nextDouble() * 500 - 250, random.nextDouble() * 59_998_000 - 29_999_000);
            Track translated = translate(original, offset);
            check(expected.equals(cue(offset, translated.previous(), translated.current())),
                    "far world translation leaves presentation and motion invariant");
            Track rotated = new Track(rotate(original.previous()), rotate(original.current()));
            Cue turned = cue(ORIGIN, rotated.previous(), rotated.current());
            check(turned != null && turned.azimuth() == (expected.azimuth() + 4) % 16
                    && turned.elevation() == expected.elevation() && turned.strength() == expected.strength(),
                    "quarter-turn rotation moves four world bins with identical physical strength");
        }
    }

    private static void strengthQuantization() {
        for (Kind kind : Kind.values()) {
            double min = kind == Kind.ARROW ? ARROW_MIN_SPEED : RUNNER_MIN_SPEED;
            double max = kind == Kind.ARROW ? ARROW_MAX_SPEED : RUNNER_MAX_SPEED;
            check(at(p(0, 0, 20), min, kind).strength() == 1, "farthest slow source has weak cue");
            check(at(p(0, 0, .1), max, kind).strength() == 3, "near fast source has strong cue");
            int prior = 0;
            for (int i = 0; i <= 100; i++) {
                double speed = min + (max - min) * i / 100;
                Cue cue = at(p(0, 0, 10), speed, kind);
                check(cue.strength() >= prior && cue.strength() >= 1 && cue.strength() <= 3,
                        "speed can only strengthen a bounded coarse cue");
                prior = cue.strength();
            }
            prior = 3;
            for (int i = 1; i <= 100; i++) {
                Cue cue = at(p(0, 0, i * .2), (min + max) / 2, kind);
                check(cue.strength() <= prior && cue.strength() >= 1 && cue.strength() <= 3,
                        "increasing distance can only weaken a bounded coarse cue");
                prior = cue.strength();
            }
        }
    }

    private static void boundedSelection() {
        List<Track> tracks = new ArrayList<>();
        for (int i = 1; i <= 64; i++) {
            double angle = (i % 16) * Math.PI / 8;
            double radius = 1 + i * .25;
            tracks.add(track(p(radius * Math.sin(angle), 0, radius * Math.cos(angle)), p(1, 0, 0), Kind.ARROW));
        }
        Selection selected = collect(ORIGIN, tracks);
        check(!selected.overloaded() && selected.cues().size() == 8, "exactly64 queried sources emit at most8 cues");
        List<Cue> nearest = new ArrayList<>();
        for (int i = 0; i < 8; i++) nearest.add(cue(ORIGIN, tracks.get(i).previous(), tracks.get(i).current()));
        check(selected.cues().equals(nearest), "nearest eight win independently of input encounter order");
        Random random = new Random(64_433);
        for (int i = 0; i < 100; i++) {
            Collections.shuffle(tracks, random);
            check(collect(ORIGIN, tracks).equals(selected), "permuted bounded query produces same ordered coarse result");
        }
        List<Track> oversized = new ArrayList<>(tracks);
        oversized.add(null);
        Selection overflow = collect(ORIGIN, oversized);
        check(overflow.overloaded() && overflow.cues().isEmpty(), "even ineligible65th candidate suppresses entire batch");
        int[] reads = {0};
        Iterable<Track> endless = () -> new Iterator<>() {
            public boolean hasNext() { return true; }
            public Track next() {
                reads[0]++;
                if (reads[0] > 64) throw new AssertionError("Candidate budget exceeded");
                return tracks.get((reads[0] - 1) % tracks.size());
            }
        };
        check(collect(ORIGIN, endless).overloaded() && reads[0] == 64,
                "unbounded iterator is refused without reading a65th source");
        List<Track> invalid = new ArrayList<>(Collections.nCopies(64, null));
        check(collect(ORIGIN, invalid).cues().isEmpty() && !collect(ORIGIN, invalid).overloaded(),
                "ineligible candidates count against budget but never create cues");
        invalid.add(null);
        check(collect(ORIGIN, invalid).overloaded(), "65ineligible candidates also overload instead of overscanning");
        check(collect(ORIGIN, List.of()).cues().isEmpty() && collect(null, tracks).cues().isEmpty()
                && collect(ORIGIN, null).cues().isEmpty(), "absent query or listener yields no fabricated cues");
        List<Cue> mutable = new ArrayList<>(selected.cues());
        Selection snapshot = new Selection(mutable, false);
        mutable.clear();
        check(snapshot.cues().size() == 8, "selection snapshots cannot be altered through caller list");
        rejects(() -> snapshot.cues().clear(), "returned selection is immutable");
        rejects(() -> new Selection(selected.cues(), true), "overloaded result cannot carry a prefix");
        rejects(() -> new Selection(Collections.nCopies(9, selected.cues().getFirst()), false), "oversized output refuses");
        List<Track> ties = new ArrayList<>();
        for (Point p : new Point[] {p(0, 0, 4), p(4, 0, 0), p(0, 0, -4), p(-4, 0, 0)}) {
            ties.add(track(p, p(1, 0, 0), Kind.ARROW));
        }
        List<Cue> tieResult = collect(ORIGIN, ties).cues();
        Collections.reverse(ties);
        check(collect(ORIGIN, ties).cues().equals(tieResult), "equal-distance ties use coarse bins rather than query order");
    }

    private static void presentationShape() {
        check(Cue.class.getRecordComponents().length == 4, "cue has exactly four coarse presentation fields");
        for (var component : Cue.class.getRecordComponents()) {
            check(component.getType() == int.class, "cue cannot contain UUIDs, entity references, world coordinates or raw speed");
        }
        for (int[] values : new int[][] {{-1, 0, 1, 0}, {16, 0, 1, 0}, {0, -2, 1, 0}, {0, 2, 1, 0},
                {0, 0, 0, 0}, {0, 0, 4, 0}, {0, 0, 1, -1}, {0, 0, 1, 2}}) {
            rejects(() -> new Cue(values[0], values[1], values[2], values[3]), "out-of-domain cue refuses");
        }
        for (int azimuth = 0; azimuth < 16; azimuth++) for (int elevation = -1; elevation <= 1; elevation++) {
            for (int strength = 1; strength <= 3; strength++) for (int kind = 0; kind <= 1; kind++) {
                check(new Cue(azimuth, elevation, strength, kind).azimuth() == azimuth, "all and only bounded presentation states exist");
            }
        }
    }

    private static void economics() {
        String[] activation = {"2", "1", ".4", ".1", ".02"};
        String[] sustain = {".25", ".125", ".05", ".0125", ".0025"};
        int[] cooldown = {10, 5, 4, 4, 4};
        for (int i = 0; i < 5; i++) {
            BigDecimal q = BigDecimal.valueOf(i + 1);
            equal(theoreticalActivationCost(q), new BigDecimal(activation[i]), "activation anchor is inherited exactly");
            equal(theoreticalSustainCost(q), new BigDecimal(sustain[i]), "per20tick CP anchor is inherited exactly");
            check(cooldownTicks(q) == cooldown[i], "inherited cooldown retains4tick physical floor");
        }
        check(PAYMENT_PERIOD == 20 && RESTART_FENCE == 4, "sustain and restart use physical clocks");
        BigDecimal previous = null;
        for (int i = 0; i <= 40; i++) {
            BigDecimal q = BigDecimal.ONE.add(BigDecimal.valueOf(i, 1));
            equal(theoreticalActivationCost(q), VectorParryRules.theoreticalActivationCost(q), "activation delegates without rounding");
            equal(theoreticalSustainCost(q), VectorParryRules.theoreticalSustainCost(q), "sustain delegates without rounding");
            equal(theoreticalCooldown(q), VectorParryRules.theoreticalCooldown(q), "theoretical cooldown delegates without rounding");
            check(activationCost(q) == VectorParryRules.activationCost(q)
                    && sustainCost(q) == VectorParryRules.sustainCost(q)
                    && cooldownTicks(q) == VectorParryRules.cooldownTicks(q), "native economics match existing implementation");
            if (previous != null) check(theoreticalActivationCost(q).compareTo(previous) < 0, "growth is continuous between anchors");
            previous = theoreticalActivationCost(q);
        }
        rejects(() -> activationCost(null), "absent growth coordinate refuses");
        rejects(() -> sustainCost(new BigDecimal("1e-257")), "unsupported growth precision refuses");
    }

    private static Track track(Point current, Point velocity, Kind kind) {
        Point prior = p(current.x() - 2 * velocity.x(), current.y() - 2 * velocity.y(), current.z() - 2 * velocity.z());
        return new Track(new Sample(ID, WORLD, 100, kind, prior, velocity, kind == Kind.RUNNER),
                new Sample(ID, WORLD, 102, kind, current, velocity, kind == Kind.RUNNER));
    }
    private static Cue at(Point source, double speed, Kind kind) {
        Track t = track(source, p(speed, 0, 0), kind);
        Cue result = cue(ORIGIN, t.previous(), t.current());
        check(result != null, "valid moving source produces a cue");
        return result;
    }
    private static Sample copy(Sample source, UUID identity, Object world, long tick, Kind kind,
                               Point position, Point velocity, boolean sprinting) {
        return new Sample(identity, world, tick, kind, position, velocity, sprinting);
    }
    private static Track translate(Track source, Point offset) {
        return new Track(translate(source.previous(), offset), translate(source.current(), offset));
    }
    private static Sample translate(Sample source, Point offset) {
        return copy(source, source.identity(), source.world(), source.tick(), source.kind(),
                p(source.position().x() + offset.x(), source.position().y() + offset.y(), source.position().z() + offset.z()),
                source.velocity(), source.sprinting());
    }
    private static Sample rotate(Sample source) {
        return copy(source, source.identity(), source.world(), source.tick(), source.kind(),
                p(source.position().z(), source.position().y(), -source.position().x()),
                p(source.velocity().z(), source.velocity().y(), -source.velocity().x()), source.sprinting());
    }
    private static Point p(double x, double y, double z) { return new Point(x, y, z); }
    private static void equal(BigDecimal actual, BigDecimal expected, String reason) {
        check(actual.compareTo(expected) == 0, reason);
    }
    private static void rejects(Runnable action, String reason) {
        try { action.run(); throw new AssertionError(reason); }
        catch (IllegalArgumentException | NullPointerException | UnsupportedOperationException expected) { checks++; }
    }
    private static void check(boolean result, String reason) {
        checks++;
        if (!result) throw new AssertionError(reason);
    }
}

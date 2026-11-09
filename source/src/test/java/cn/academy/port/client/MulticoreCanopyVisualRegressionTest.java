package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.client.MulticoreCanopyState.Core;
import cn.academy.port.client.MulticoreCanopyState.Frame;
import cn.academy.port.client.MulticoreCanopyState.Identity;
import cn.academy.port.client.MulticoreCanopyState.Segment;
import cn.academy.port.client.MulticoreCanopyState.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Pure production input, receipt lifetime, original-art placement, finite volumes and shared-budget coverage. */
public final class MulticoreCanopyVisualRegressionTest {
    private static int checks;
    private static final UUID OWNER = new UUID(251, 1);
    private static final Vector EYE = v(0, 64, 0), LOOK = v(0, 0, 1);
    private static final List<Core> CORES = List.of(new Core(0, v(-.9, 64, 2), true), new Core(1, v(-.45, 64.65, 2), true),
            new Core(2, v(.45, 64.65, 2), true), new Core(3, v(.9, 64, 2), true),
            new Core(4, v(.45, 63.35, 2), true), new Core(5, v(-.45, 63.35, 2), true));
    private static final Target TARGET = new Target(42, v(0, 64, 30), MulticoreCanopyState.PREPARED);
    private static Vector v(double x, double y, double z) { return new Vector(x, y, z); }
    private static void check(boolean value, String description) { checks++; if (!value) throw new AssertionError(description); }
    private static void denied(Runnable action, String description) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(description);
    }
    private static Frame frame(long input, long sequence, int phase, int remaining, List<Core> cores, List<Target> targets, List<Segment> path) {
        return new Frame(new Identity(9, OWNER, 2, input, 5), sequence, phase, 0, remaining, EYE.subtract(v(0, 1.6, 0)),
                cores, targets, path, phase == MulticoreCanopyState.HELD, 0, phase == MulticoreCanopyState.WARNING ? 12 : 0);
    }
    private static Frame fired(int core, int status) {
        var c = CORES.get(core);
        return frame(2, 4, MulticoreCanopyState.FIRE, 200, CORES, List.of(TARGET), List.of(new Segment(c.id(), core, c.center(), TARGET.point(), status)));
    }
    public static void main(String[] args) {
        input(); receipts(); lifetime(); hidden(); originalOrbs(); terminalSources(); actualRays(); volumeWinding(); budget();
        System.out.println("PASS MulticoreCanopyVisualRegressionTest: " + checks + " checks");
    }
    private static void input() {
        var state = new MulticoreCanopyInputState(); var first = new HighPowerInput.Input(0, 1, 9, 2);
        Object world = new Object(), player = new Object(), connection = new Object();
        var context = new MulticoreCanopyInputState.DispatchContext(world, player, connection, 7, 1, 3, "251");
        state.begin(first, -100, context);
        check(state.holding() && !state.permits(1, 2) && state.queuedFor(context), "unsent Pre edge cannot authorize visual receipt");
        check(state.flushQueuedStart().start().equals(first) && state.permits(1, 2), "Post sends deployment identity once");
        state.accepted(1, 2);
        check(state.releaseWhenUp(true) == null, "held input never invents local firing");
        var released = state.releaseWhenUp(false);
        check(first.equals(released) && state.deferRelease(released, context), "first release waits for native Post movement");
        check(state.cancel(first) == null && state.permits(1, 2), "disposed released delegate preserves armed canopy");
        check(first.equals(state.flushDeferredRelease(context)) && state.flushDeferredRelease(context) == null, "release dispatches once");
        var second = new HighPowerInput.Input(0, 2, 9, 2);
        state.begin(second, -100, context);
        check(state.permits(1, 2) && !state.permits(2, 2), "armed identity remains valid before second physical edge dispatch");
        state.release(second);
        var quick = state.flushQueuedStart();
        check(quick.start().equals(second) && quick.release().equals(second), "same-tick second tap preserves start-before-release");
        check(state.deferRelease(quick.release(), context) && second.equals(state.flushDeferredRelease(context)), "quick tap release is also Post deferred");
        state.accepted(2, 2); state.accepted(1, 2);
        check(state.permits(2, 2) && !state.permits(1, 2), "second controller input watermark advances without old input replay");
        state.ended(1, 2);
        check(state.permits(2, 2), "old ending cannot retire fresh second press");
        check(state.cancelWhenBlocked(true).equals(second) && !state.permits(2, 2), "focus loss cancels released armed identity");
        denied(() -> state.begin(second, -100, context), "cancel cannot replay old identity");
        denied(() -> state.begin(new HighPowerInput.Input(0, 3, 10, 2), -100, context), "same epoch rejects actor replacement");
        var third = new HighPowerInput.Input(0, 3, 9, 2); state.begin(third, 19, context); state.flushQueuedStart();
        state.deferRelease(state.release(third), context);
        var moved = new MulticoreCanopyInputState.DispatchContext(new Object(), player, connection, 7, 1, 3, "251");
        check(state.flushDeferredRelease(moved) == null && state.flushDeferredRelease(context) == null, "world mismatch discards stale release permanently");
        state.reset();
        for (int i = 1; i <= 80; i++) {
            var next = new HighPowerInput.Input(0, i, 9, 2); state.begin(next, 19); state.flushQueuedStart(); state.release(next);
        }
        check(state.pendingCount() == 16 && !state.permits(1, 2), "unanswered input permission memory remains bounded");
    }
    private static void receipts() {
        var state = new MulticoreCanopyState();
        Frame first = frame(1, 1, 0, 240, CORES.subList(0, 1), List.of(), List.of());
        check(state.accept(first, true), "first actual deployment accepted");
        Frame armed = frame(1, 2, 1, 210, CORES, List.of(), List.of());
        check(state.accept(armed, true), "same paid controller arms after six fixed cores");
        check(MulticoreCanopyState.controlAvailable(armed, 209, 0), "armed controller permits second edge");
        Frame warning = frame(2, 3, 2, 208, CORES, List.of(TARGET), List.of());
        check(state.accept(warning, true), "second physical input advances under same controller token");
        check(!state.accept(frame(1, 4, 2, 208, CORES, List.of(TARGET), List.of()), true), "old input cannot regain ownership");
        check(!state.accept(frame(2, 4, 2, 209, CORES, List.of(TARGET), List.of()), true), "rearming cannot refresh fixed lifetime");
        var shifted = new ArrayList<>(CORES); shifted.set(0, new Core(0, CORES.getFirst().center().add(v(.1, 0, 0)), true));
        check(!state.accept(frame(2, 4, 2, 207, shifted, List.of(TARGET), List.of()), true), "camera or owner movement cannot slide fixed core origin");
        check(!state.accept(frame(2, 4, 0, 207, CORES, List.of(TARGET), List.of()), true), "firing controller cannot regress to deployment");
        Target stranger = new Target(43, TARGET.point(), 0);
        check(!state.accept(frame(2, 4, 2, 207, CORES, List.of(TARGET, stranger), List.of()), true), "frozen roster rejects new target identity");
        check(state.accept(fired(0, MulticoreCanopyState.REFUSED), true), "actual immune or cancelled ray remains a valid emission receipt");
        check(!state.accept(fired(0, MulticoreCanopyState.REFUSED), true), "duplicate FIRE cannot refresh visual flash");
        Frame ended = frame(2, 5, 5, 0, List.of(), List.of(), List.of());
        check(state.accept(ended, true) && state.current(OWNER) == null, "terminal clears logical controller");
        check(!state.accept(frame(2, 6, 2, 190, CORES, List.of(TARGET), List.of()), true), "terminal tombstone rejects late revival");
        var sixteen = new Frame(warning.id(), 7, 3, 0, 180, warning.ownerFeet(), CORES, List.of(TARGET), List.of(), true, 16, 0);
        check(MulticoreCanopyState.valid(sixteen), "six reusable origins can serve sixteen sequential frozen slots");
        var badRay = frame(2, 4, 4, 200, CORES, List.of(TARGET), List.of(new Segment(0, 0, EYE, TARGET.point(), 4)));
        check(!MulticoreCanopyState.valid(badRay), "a ray cannot be drawn from owner eye instead of its actual numbered core");
        check(!MulticoreCanopyState.valid(frame(2, 4, 1, 200, CORES, List.of(TARGET), fired(0, 4).path())), "armed state never carries an implied actual ray");
        check(MulticoreCanopyState.eligible(5, true, true) && !MulticoreCanopyState.eligible(4, true, true)
                && !MulticoreCanopyState.eligible(5, false, true), "native LV5 and learned direct prerequisite required");
    }
    private static void lifetime() {
        var lease = new MulticoreCanopyState.Lease();
        Frame f = frame(1, 1, 0, 240, CORES, List.of(), List.of()); lease.received(f);
        for (int i = 0; i < 10; i++) lease.tick(false, true);
        check(lease.remaining() == 230, "lifetime starts at first deployment");
        lease.received(frame(1, 2, 1, 240, CORES, List.of(), List.of()));
        check(lease.remaining() == 230, "refresh or arming cannot extend local lease");
        for (int i = 0; i < 20; i++) lease.tick(true, true);
        check(lease.remaining() == 230 && lease.quiet() == 0, "pause freezes lease and quiet timer");
        for (int i = 0; i < 11; i++) check(lease.tick(false, true), "live lease survives normal bounded heartbeat window");
        check(!lease.tick(false, true), "missing server heartbeat retires context");
        var status = MulticoreCanopyState.status(frame(1, 2, 1, 221, CORES, List.of(), List.of()), 221);
        check(status.available() == 6 && status.count() == 6 && status.seconds() == 12 && status.key().equals("armed"), "small HUD reports actual cores lifetime and mode");
    }
    private static void hidden() {
        var state = new MulticoreCanopyState(); var visibility = new MulticoreCanopyState.Visibility();
        Frame shown = frame(2, 1, 2, 220, CORES, List.of(TARGET), List.of()); check(state.accept(shown, true), "visible roster captured");
        Target lost = new Target(TARGET.id(), TARGET.point(), MulticoreCanopyState.LOST);
        Frame hidden = frame(2, 2, 2, 219, CORES, List.of(lost), List.of());
        check(state.accept(hidden, true), "hidden target retains exact last visible point");
        Target leaked = new Target(TARGET.id(), TARGET.point().add(v(1, 0, 0)), MulticoreCanopyState.LOST);
        check(!state.accept(frame(2, 3, 2, 218, CORES, List.of(leaked), List.of()), true), "hidden target position updates are rejected");
        visibility.received(hidden);
        for (int i = 0; i < 9; i++) { visibility.tick(false); visibility.received(hidden); check(visibility.shown(lost), "short stale grace remains visible"); }
        visibility.tick(false); visibility.received(hidden);
        check(!visibility.shown(lost), "repeated hidden packets cannot reset ten-tick retirement");
        check(state.accept(frame(2, 3, 2, 209, CORES, List.of(), List.of()), true), "server may retire hidden identity");
        check(!state.accept(frame(2, 4, 2, 208, CORES, List.of(TARGET), List.of()), true), "retired identity never reenters frozen roster");
        check(MulticoreCanopyMesh.target(lost, EYE, LOOK).size() < MulticoreCanopyMesh.target(TARGET, EYE, LOOK).size(), "stale target uses visibly incomplete faint brackets");
    }
    private static void originalOrbs() {
        for (Core core : CORES) {
            var faces = MulticoreCanopyMesh.core(core, EYE, LOOK, 0, 0);
            for (var face : faces) if (face.kind() == MulticoreCanopyMesh.ORB || face.kind() == MulticoreCanopyMesh.ORB_GLOW) {
                Vector center = face.a().add(face.b()).add(face.c()).add(face.d()).scale(.25);
                check(center.subtract(core.center()).length() < 1e-10, "original orb UV center stays on exact server core, without classic asymmetric Y offset");
                check(face.color() == 0xFFFFFF, "white vertex tint preserves original orb and glow artwork");
                check(MulticoreCanopyMesh.materialFace(face).kind() == 3, "orb material bypasses shield and beam shader branches");
                check(face.texture() >= 4 && face.texture() <= 9, "only original five frames plus glow are orb textures");
            }
            for (Core other : CORES) if (other.id() != core.id())
                check(core.center().subtract(other.center()).length() > MulticoreCanopyMesh.GLOW_HALF_SIZE * 2, "six separate original glow billboards have real air gaps");
        }
        for (int i = 0; i < 6; i++) check((MulticoreCanopyMesh.emissionPower(fired(2, 4), i, 0) > 0) == (i == 2), "only actual emitted core is brightest");
        Frame held = frame(2, 3, 3, 210, CORES, List.of(TARGET), List.of());
        for (int i = 0; i < 6; i++) check(MulticoreCanopyMesh.emissionPower(held, i, 0) == 0, "held status alone never flashes all cores");
        check(MulticoreCanopyMesh.emissionPower(fired(2, 4), 2, 4) == 0, "emission energy fades within four ticks");
    }
    private static void actualRays() {
        for (int status : new int[]{1, 2, 3, 4, 5}) {
            Frame f = fired(0, status); var mesh = MulticoreCanopyMesh.path(f, EYE, LOOK, 0);
            check(!mesh.isEmpty(), "actual authenticated ray is renderable even when damage is refused");
            check(mesh.stream().anyMatch(face -> face.kind() == MulticoreCanopyMesh.HIT_GLINT) == (status == 4), "only confirmed native damage earns hit glint");
            for (int part = 0; part < 3; part++) {
                int texture = part;
                check(mesh.stream().anyMatch(face -> face.kind() == MulticoreCanopyMesh.BEAM_HALO && face.texture() == texture), "original small-ray blend parts remain intact");
            }
            check(MulticoreCanopyMesh.path(f, EYE, LOOK, 4).isEmpty(), "actual flash has finite lifetime");
        }
        Frame armed = frame(1, 2, 1, 230, CORES, List.of(), List.of());
        check(MulticoreCanopyMesh.path(armed, EYE, LOOK, 0).isEmpty(), "arming renders no actual beam");
        Core c = CORES.getFirst();
        Frame far = frame(2, 4, 4, 200, CORES, List.of(), List.of(new Segment(0, 0, c.center(), c.center().add(LOOK.scale(90)), 3)));
        check(MulticoreCanopyState.valid(far) && !MulticoreCanopyMesh.path(far, EYE, LOOK, 0).isEmpty(), "authentic long-range ray is not silently capped at64m");
    }
    private static void terminalSources() {
        var state = new MulticoreCanopyState(); Frame fire = fired(2, MulticoreCanopyState.DAMAGED);
        check(state.accept(fire, true), "terminal source fixture accepts real last-shot FIRE");
        Frame ended = frame(2, 5, 5, 0, List.of(), List.of(), List.of());
        check(state.accept(ended, true) && state.current(OWNER) == null, "same-frame ENDED closes logical controller immediately");
        check(!MulticoreCanopyState.controlAvailable(ended, 0, 0), "terminal source tail grants no firing controls or lease");
        var sources = MulticoreCanopyMesh.terminalSources(fire, 0);
        check(sources.size() == 1 && sources.getFirst().core().id() == 2, "terminal tail retains only actual emitted core ID");
        check(sources.getFirst().core().center().equals(fire.path().getFirst().start()), "terminal source is exactly the received ray origin");
        int previousAlpha = 256;
        for (int age = 0; age < MulticoreCanopyState.FLASH_TICKS; age++) {
            var live = MulticoreCanopyMesh.terminalSources(fire, age);
            check(live.size() == 1 && live.getFirst().core().id() == 2, "terminal fade cannot revive non-emitting cores");
            var faces = MulticoreCanopyMesh.sourceTail(live.getFirst(), EYE, LOOK, age);
            var orb = faces.stream().filter(face -> face.kind() == MulticoreCanopyMesh.ORB).findFirst().orElseThrow();
            check(orb.alpha() > 0 && orb.alpha() < previousAlpha, "terminal source sprite fades on existing flash age"); previousAlpha = orb.alpha();
            check(orb.a().add(orb.b()).add(orb.c()).add(orb.d()).scale(.25).subtract(CORES.get(2).center()).length() < 1e-10,
                    "source tail stays centered on frozen server origin throughout fade");
            check(orb.color() == 0xFFFFFF && MulticoreCanopyMesh.materialFace(orb).kind() == 3, "tail preserves original white-tinted passthrough material");
            check(faces.stream().noneMatch(face -> face.kind() == MulticoreCanopyMesh.PLANNED), "terminal source tail carries no target or steering preview");
            check(!MulticoreCanopyMesh.path(fire, EYE, LOOK, age).isEmpty(), "source and actual ray remain visible on the same bounded flash clock");
        }
        check(MulticoreCanopyMesh.terminalSources(fire, 4).isEmpty() && MulticoreCanopyMesh.terminalSources(fire, Float.NaN).isEmpty(),
                "expired or invalid age cannot extend source tail");
        check(MulticoreCanopyMesh.terminalSources(ended, 0).isEmpty(), "ENDED cannot itself mint any source tail");
        Frame emptyFire = frame(2, 4, 4, 200, CORES, List.of(TARGET), List.of());
        check(MulticoreCanopyMesh.terminalSources(emptyFire, 0).isEmpty(), "missed opportunity without actual ray has no source flash");
        var rays = new ArrayList<Segment>();
        for (var core : CORES) rays.add(new Segment(core.id(), core.id(), core.center(), TARGET.point(), 3));
        Frame volley = frame(2, 4, 4, 200, CORES, List.of(TARGET), rays);
        check(MulticoreCanopyMesh.terminalSources(volley, 0).size() == 6, "actual six-ray tap keeps all six distinct terminal emission sources");
    }
    private static void volumeWinding() {
        for (Vector direction : List.of(LOOK, v(0, -1, 0), FoldedMirrorGeometry.unit(v(1, 2, 3)).orElseThrow())) {
            Vector start = CORES.getFirst().center(), end = start.add(direction.scale(8)), camera = start.subtract(direction.scale(30));
            Frame f = frame(2, 4, 4, 200, CORES, List.of(), List.of(new Segment(0, 0, start, end, 3)));
            for (var face : MulticoreCanopyMesh.path(f, camera, direction, 0)) if (MulticoreCanopyMesh.culled(face)) {
                Vector normal = face.b().subtract(face.a()).cross(face.c().subtract(face.a()));
                if (face.a().equals(start)) check(normal.dot(direction) < 0, "near cap points outward");
                else if (face.a().equals(end)) check(normal.dot(direction) > 0, "far cap points outward");
                else {
                    Vector middle = face.a().add(face.b()).add(face.c()).add(face.d()).scale(.25);
                    Vector axis = start.add(direction.scale(middle.subtract(start).dot(direction)));
                    check(normal.dot(middle.subtract(axis)) > 0, "all volume sides wind outward");
                }
                for (Vector vertex : face.vertices()) {
                    double along = vertex.subtract(start).dot(direction);
                    check(along >= -1e-9 && along <= 8 + 1e-9, "volume never extends beyond authentic core or endpoint");
                }
                check(!MulticoreCanopyMesh.additive(face), "closed volume uses ordinary alpha, preventing front-back additive whitening");
            }
        }
    }
    private static void budget() {
        List<FoldedMirrorMesh.Face> faces = new ArrayList<>();
        var beam = MulticoreCanopyMesh.path(fired(0, 4), EYE, LOOK, 0);
        for (int i = 0; i < 100; i++) faces.addAll(MulticoreCanopyMesh.core(CORES.getFirst(), EYE, LOOK, 0, 0));
        faces.addAll(beam);
        var budget = new MulticoreCanopyMesh.Budget(); var rendered = MulticoreCanopyMesh.ordered(faces, EYE, LOOK, budget);
        check(rendered.size() <= 1536 && budget.used() <= 1536, "all owners and material batches share one post-clipping quad cap");
        long wanted = beam.stream().filter(f -> f.kind() == MulticoreCanopyMesh.BEAM_CORE).count();
        check(rendered.stream().filter(f -> f.kind() == MulticoreCanopyMesh.BEAM_CORE).count() == wanted, "actual solid beams win over decorative rims");
        check(!budget.reserve(4), "native font decoration is skipped before exceeding shared cap");
        for (var face : rendered) for (Vector point : face.vertices())
            check(point.subtract(EYE).dot(LOOK) >= FoldedMirrorMesh.NEAR_DEPTH - 1e-9, "near clipping survives every budgeted material");
        check(MulticoreCanopyMesh.MAX_VISIBLE_CONTEXTS == 4 && MulticoreCanopyMesh.SPAWNED_PARTICLES == 0, "contexts and particles remain bounded");
    }
}

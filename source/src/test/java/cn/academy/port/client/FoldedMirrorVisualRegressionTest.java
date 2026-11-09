package cn.academy.port.client;

import cn.academy.port.core.TargetedContextTermination;
import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Plate;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;

/** Production physical edges, classic binding, receipt fences and perspective geometry, without game initialization. */
public final class FoldedMirrorVisualRegressionTest {
    private static int checks;
    private static final UUID OWNER = new UUID(221, 1);
    private static final Vector ZERO = v(0, 0, 0), SOURCE = v(0, 64, 0), LOOK = v(0, 0, 1);
    private static final Plate A = new Plate(0, v(0, 64, 6), unit(v(-1, 0, 1)));
    private static final Plate B = new Plate(1, v(6, 64, 6), unit(v(1, 0, -1)));
    private static final List<Plate> PLATES = List.of(A, B);
    private static final List<FoldedMirrorState.Segment> PATH = List.of(segment(SOURCE, A.center()), segment(A.center(), B.center()), segment(B.center(), v(6, 64, 14)));
    private static Vector v(double x, double y, double z) { return new Vector(x, y, z); }
    private static Vector unit(Vector p) { return FoldedMirrorGeometry.unit(p).orElseThrow(); }
    private static FoldedMirrorState.Segment segment(Vector a, Vector b) { return new FoldedMirrorState.Segment(a, b); }
    private static void check(boolean success, String description) { checks++; if (!success) throw new AssertionError(description); }
    private static void denied(Runnable action, String description) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(description);
    }
    private static FoldedMirrorState.Frame frame(long input, long token, long sequence, int phase, int status, int remaining,
                                                 List<Plate> plates, List<FoldedMirrorState.Segment> path) {
        return new FoldedMirrorState.Frame(new FoldedMirrorState.Identity(9, OWNER, 2, input, token), sequence, phase, status, remaining,
                SOURCE.subtract(v(0, 1.5, 0)), plates, phase == FoldedMirrorState.EDIT ? A.center() : ZERO,
                phase == FoldedMirrorState.EDIT ? A.normal() : ZERO, path);
    }
    private static FoldedMirrorState.Frame f(long input, long sequence, int phase, int remaining, List<Plate> plates) {
        return frame(input, 5, sequence, phase, 0, remaining, plates, List.of());
    }
    public static void main(String[] args) throws Exception {
        input(); binding(); deferredNativeOrdering(); recallDuringCooldown(); receipts(); leasesAndStatus(); plates(); rays(); volumeWinding(); textureCoordinates(); materialsAndTiming(); materialPhases(); budget(); integration();
        System.out.println("PASS FoldedMirrorVisualRegressionTest: " + checks + " checks");
    }
    private static void volumeWinding() {
        for (Vector direction : List.of(LOOK, unit(v(1, 2, 3)), v(0, -1, 0))) {
            Vector end = SOURCE.add(direction.scale(8));
            var straight = frame(2, 5, 2, 2, FoldedMirrorState.DAMAGED, 399, PLATES, List.of(segment(SOURCE, end)));
            var mesh = FoldedMirrorMesh.path(straight, SOURCE.subtract(direction.scale(30)), direction, 0);
            for (var face : mesh) if (FoldedMirrorMesh.culled(face)) {
                Vector normal = face.b().subtract(face.a()).cross(face.c().subtract(face.a()));
                if (face.a().equals(SOURCE)) check(normal.dot(direction) < 0, "near cap faces outward from the exact source endpoint");
                else if (face.a().equals(end)) check(normal.dot(direction) > 0, "far cap faces outward from the exact final endpoint");
                else {
                    Vector middle = face.a().add(face.b()).add(face.c()).add(face.d()).scale(.25);
                    Vector axis = SOURCE.add(direction.scale(middle.subtract(SOURCE).dot(direction)));
                    check(normal.dot(middle.subtract(axis)) > 0, "all eight cylinder walls have outward winding for culling");
                }
                for (Vector vertex : face.vertices()) {
                    double distance = vertex.subtract(SOURCE).dot(direction);
                    check(distance >= -1e-10 && distance <= 8 + 1e-10, "caps and walls never extend beyond the authenticated finite leg");
                }
                check(!FoldedMirrorMesh.additive(face), "culled solid volumes use ordinary alpha, preventing additive front/back whitening");
            }
            check(mesh.stream().filter(f -> f.kind() == FoldedMirrorMesh.BEAM_HALO).noneMatch(FoldedMirrorMesh::culled), "soft envelope remains visible from either side");
        }
    }
    private static void input() {
        var state = new FoldedMirrorInputState(); var press = new HighPowerInput.Input(0, 1, 9, 2);
        state.begin(press, -100, true);
        check(state.holding() && state.current().editing() && state.current().physicalKey() == -100, "press snapshots physical key and sneak mode");
        check(state.queuedStart() != null && !state.permits(1, 2), "Pre queues input without granting unsent receipt permission");
        check(state.flushQueuedStart().start().equals(press) && state.flushQueuedStart() == null, "Post dispatches one queued authenticated start");
        for (int i = 0; i < 100; i++) check(state.releaseWhenUp(true) == null && state.current().editing(), "held key cannot locally reinterpret mode or auto-fire");
        check(state.release(new HighPowerInput.Input(1, 1, 9, 2)) == null, "wrong slot cannot release another owner");
        check(state.release(press).equals(press) && !state.holding(), "release owns exactly one request");
        state.accepted(1, 2);
        check(state.releaseWhenUp(false) == null && state.cancel(press) == null && state.permits(1, 2), "delegate disposal after release preserves paid array");
        var second = new HighPowerInput.Input(0, 2, 9, 2); state.begin(second, -100, false); state.flushQueuedStart();
        check(state.permits(1, 2) && state.permits(2, 2) && !state.current().editing(), "new normal press retains prior paid context until receipt");
        denied(() -> state.begin(new HighPowerInput.Input(0, 3, 9, 2), -100, true), "already held key cannot spawn another action");
        check(state.releaseWhenUp(false).equals(second), "raw release fallback survives silent delegate replacement");
        state.accepted(2, 2); state.accepted(1, 2);
        check(!state.permits(1, 2) && state.permits(2, 2), "late acceptance cannot regress input identity");
        check(state.cancelWhenBlocked(false) == null && state.cancelWhenBlocked(true).equals(second) && !state.permits(2, 2), "explicit focus or menu cancel removes persistent state");
        denied(() -> state.begin(second, -100, false), "cancel preserves high watermark against replay");
        denied(() -> state.begin(new HighPowerInput.Input(0, 3, 10, 2), -100, false), "same epoch cannot be stolen by replacement actor");
        for (int i = 1; i <= 80; i++) {
            var next = new HighPowerInput.Input(0, i, 10, 3); state.begin(next, 19, false); state.flushQueuedStart(); state.release(next);
        }
        check(state.pendingCount() == 16 && !state.permits(1, 3), "pending input storage bounded independently of network response");
        state.accepted(80, 3); state.ended(80, 3); check(!state.permits(80, 3), "terminal receipt closes accepted permission");
        state.reset(); check(state.current() == null && !state.holding() && state.pendingCount() == 0, "session clear resets all live input state");
        denied(() -> state.begin(new HighPowerInput.Input(4, 1, 9, 2), 19, false), "no fifth slot");
    }
    /** This is the actual classic binding, including its silent snapshot rebuild and raw-key fallback. */
    private static final class Rig implements ClassicClientInputBinding.Host {
        final int[] keys = {-100, -99, 19, 33}; final int slot;
        final Set<Integer> down = new HashSet<>(); final List<String> wire = new ArrayList<>();
        final FoldedMirrorInputState input = new FoldedMirrorInputState();
        final ClassicClientInputBinding binding;
        boolean editing, terminal, live = true, activated = true, overloadFine = true, interfering, consuming;
        boolean learned = true, parentLearned = true, mapped = true, enabled = true;
        String category = "meltdowner"; int cooldown, remaining = 400, quiet, level = 2;
        FoldedMirrorState.Frame array;
        Object world = new Object(), player = new Object(), connection = new Object();
        long session = 1, revision; int preset;
        boolean nativeSneaking;
        final List<Boolean> observedServerModes = new ArrayList<>();
        long next;
        Rig(int slot, boolean editing) { this.slot = slot; this.editing = editing; binding = new ClassicClientInputBinding(this); binding.runtime().updateDefaultGroup(); }
        public boolean available() { return live; } public boolean inGame() { return live; }
        public boolean categoryActivated() { return activated; }
        public boolean canUseAbility() { return activated && overloadFine && !interfering && !consuming; }
        public boolean terminalOpen() { return terminal; } public int actorId() { return 9; } public long ownerEpoch() { return 2; }
        public boolean physicalDown(int key) { return down.contains(key); }
        public boolean registeredMapping(String skill) { return skill.equals("folded_mirror_array"); }
        public boolean eligible(ClassicClientInputBinding.Node node) {
            boolean paid = array != null && input.permits(array.id().input(), array.id().epoch())
                    && FoldedMirrorState.recallAvailable(array, remaining, quiet);
            return live && category.equals("meltdowner") && canUseAbility() && mapped && enabled
                    && FoldedMirrorState.eligible(level, parentLearned, learned) && (cooldown == 0 || cooldown > 0 && paid);
        }
        public String mappedSkill(int slot) { return slot == this.slot ? "folded_mirror_array" : ""; }
        public int mappedKey(int slot) { return keys[slot]; } public int slotCount() { return 4; }
        public void rawActivation(boolean desired) {} public void replaceOverrides(int[] keys) {}
        public long down(ClassicClientInputBinding.Node node) {
            var identity = new HighPowerInput.Input(node.slot, ++next, node.actorId(), node.ownerEpoch());
            input.begin(identity, node.key, editing, context()); return next;
        }
        private HighPowerInput.Input identity(ClassicClientInputBinding.Node node, long n) {
            return HighPowerInput.parse(new HighPowerInput.Input(node.slot, n, node.actorId(), node.ownerEpoch()).wire());
        }
        public void up(ClassicClientInputBinding.Node node, long n) { release(input.release(identity(node, n))); }
        public void abort(ClassicClientInputBinding.Node node, long n) { var value = input.cancel(identity(node, n)); if (value != null) wire.add("abort:" + value.wire()); }
        public boolean cancelPending(ClassicClientInputBinding.Node node, long n) { return false; }
        public void direction(ClassicClientInputBinding.ContextIdentity context, int direction, ClassicClientInputBinding.Edge edge, boolean silent) {}
        public void localContextEnd(ClassicClientInputBinding.ContextIdentity context) {}
        public void contextAbort(TargetedContextTermination.Request request) {}
        public void modernCleanup(Collection<ClassicClientInputBinding.Node> defaults) {}
        void release(HighPowerInput.Input identity) { if (identity != null) wire.add("release:" + identity.wire()); }
        FoldedMirrorInputState.DispatchContext context() {
            return new FoldedMirrorInputState.DispatchContext(world, player, connection, session, preset, revision, mappedSkill(slot) + ":" + keys[slot]);
        }
        void pre() {
            binding.tickStart(); var held = input.current();
            if (held != null && !terminal && live) release(input.releaseWhenUp(physicalDown(held.physicalKey())));
        }
        void post() {
            if (input.queuedStart() == null) return;
            if (!input.queuedFor(context()) || !live || terminal || !eligible(null)) {
                var cancelled = input.cancel(); if (cancelled != null) wire.add("abort:" + cancelled.wire()); return;
            }
            var dispatch = input.flushQueuedStart();
            wire.add("start:" + dispatch.start().wire()); observedServerModes.add(nativeSneaking); release(dispatch.release());
        }
        void tick() { pre(); post(); }
    }
    private static void binding() {
        for (int slot = 0; slot < 4; slot++) for (boolean editing : List.of(false, true)) {
            Rig rig = new Rig(slot, editing); rig.down.add(rig.keys[slot]); rig.tick();
            var original = rig.input.current().identity(); check(rig.wire.size() == 1 && rig.input.current().editing() == editing, "each existing slot captures one press mode");
            rig.editing = !editing; for (int i = 0; i < 15; i++) rig.tick();
            check(rig.wire.size() == 1 && rig.input.current().editing() == editing, "crouch change and twelve-tick threshold never locally fire");
            rig.binding.modernSnapshotFallback();
            check(rig.wire.size() == 1 && rig.input.holding(), "real silent snapshot rebuild neither releases nor aborts owned hold");
            rig.tick(); check(rig.wire.size() == 1, "snapshot-held physical key cannot retrigger");
            rig.down.remove(rig.keys[slot]); rig.tick(); rig.tick();
            check(rig.wire.equals(List.of("start:" + original.wire(), "release:" + original.wire())), "raw release after actual binding rebuild is sent exactly once");
            rig.input.accepted(original.input(), original.epoch()); rig.binding.presetEdit();
            check(rig.input.permits(original.input(), original.epoch()) && rig.wire.size() == 2, "released delegate disposal retains paid stationary array");
            rig.tick(); rig.down.add(rig.keys[slot]); rig.tick();
            check(rig.next == 2 && rig.input.current().editing() == !editing, "release then repress captures fresh mode in same real slot");
            rig.terminal = true; rig.tick();
            check(rig.wire.getLast().startsWith("abort:") && !rig.input.holding(), "GUI invalidation aborts actual held delegate");
            int before = rig.wire.size(); rig.live = false; rig.binding.discard(); rig.down.clear(); rig.tick();
            check(rig.wire.size() == before, "discarded session emits no release or abort replay");
        }
    }
    private static Rig paidRig(int slot) {
        Rig rig = new Rig(slot, true); rig.down.add(rig.keys[slot]); rig.tick();
        rig.down.remove(rig.keys[slot]); rig.tick(); rig.input.accepted(1, 2);
        rig.array = f(1, 2, 0, 400, List.of(A)); rig.editing = false; rig.cooldown = 20; rig.wire.clear();
        return rig;
    }
    private static void deferredNativeOrdering() {
        for (int slot = 0; slot < 4; slot++) {
            Rig rig = new Rig(slot, true); rig.down.add(rig.keys[slot]); rig.pre();
            var identity = rig.input.current().identity();
            check(rig.wire.isEmpty() && rig.input.queuedStart() != null, "same-tick crouch plus skill queues at real binding Pre without early server action");
            rig.nativeSneaking = true; rig.wire.add("vanilla:crouch"); rig.post();
            check(rig.wire.equals(List.of("vanilla:crouch", "start:" + identity.wire())) && rig.observedServerModes.equals(List.of(true)),
                    "vanilla crouch arrives before the ordinary authenticated skill start; server mode uses its own observation");
            rig.post(); check(rig.wire.size() == 2, "repeated Post cannot duplicate a physical start");
            rig.down.remove(rig.keys[slot]); rig.pre(); rig.post();
            check(rig.wire.getLast().equals("release:" + identity.wire()), "ordinary release follows dispatched start exactly once");

            rig = new Rig(slot, false); rig.down.add(rig.keys[slot]); rig.pre();
            identity = rig.input.current().identity(); rig.down.clear(); rig.pre();
            check(rig.wire.isEmpty() && !rig.input.holding(), "release before Post is queued rather than sent ahead of start");
            rig.post(); rig.post();
            check(rig.wire.equals(List.of("start:" + identity.wire(), "release:" + identity.wire())), "same-frame tap dispatches start then release once");

            rig = new Rig(slot, true); rig.down.add(rig.keys[slot]); rig.pre();
            rig.binding.modernSnapshotFallback(); rig.nativeSneaking = true; rig.post();
            check(rig.wire.size() == 1 && rig.observedServerModes.equals(List.of(true)), "silent decoded-snapshot binding rebuild preserves pending native-ordered start");
            rig.down.clear(); rig.tick(); check(rig.wire.size() == 2 && rig.wire.getLast().startsWith("release:"), "snapshot fallback still observes original physical release once");

            for (int cancellation = 0; cancellation < 10; cancellation++) {
                Rig invalid = new Rig(slot, true); invalid.down.add(invalid.keys[slot]); invalid.pre();
                switch (cancellation) {
                    case 0 -> invalid.input.cancel();
                    case 1 -> invalid.terminal = true;
                    case 2 -> invalid.live = false;
                    case 3 -> invalid.world = new Object();
                    case 4 -> invalid.player = new Object();
                    case 5 -> invalid.connection = new Object();
                    case 6 -> invalid.session++;
                    case 7 -> invalid.preset++;
                    case 8 -> invalid.revision++;
                    case 9 -> invalid.keys[slot] = 44;
                    default -> throw new AssertionError();
                }
                invalid.post(); invalid.post();
                check(invalid.wire.stream().noneMatch(w -> w.startsWith("start:") || w.startsWith("release:")) && invalid.input.queuedStart() == null,
                        "cancel/menu/death/world/player/connection/session/preset/revision/remap before Post prevents stale dispatch " + cancellation);
            }
            rig = new Rig(slot, false);
            for (int edge = 1; edge <= 5; edge++) {
                rig.down.add(rig.keys[slot]); rig.pre(); rig.post(); rig.post();
                rig.down.clear(); rig.pre(); rig.post();
                check(rig.wire.size() == edge * 2 && rig.next == edge, "repeated genuine physical edges each retain one start followed by one release");
            }
        }
        var pending = new FoldedMirrorInputState(); var identity = new HighPowerInput.Input(0, 1, 9, 2);
        pending.begin(identity, 19, true); pending.release(identity);
        check(pending.cancel(identity).equals(identity) && pending.flushQueuedStart() == null, "explicit cancellation after an early queued release discards both unsent actions");
        pending.begin(new HighPowerInput.Input(0, 2, 9, 2), 19, true); pending.reset();
        check(pending.flushQueuedStart() == null && !pending.permits(2, 2), "session reset discards deferred packets and receipt permissions");
    }
    private static void recallDuringCooldown() {
        for (int slot = 0; slot < 4; slot++) {
            Rig rig = paidRig(slot); rig.down.add(rig.keys[slot]); rig.tick();
            check(rig.input.holding() && rig.next == 2 && rig.wire.size() == 1, "paid array can start free recall during positive shot cooldown in every real slot");
            for (int i = 0; i < 12; i++) rig.tick();
            check(rig.wire.size() == 1 && rig.array.phase() == FoldedMirrorState.ACTIVE, "holding recall does not locally clear array or predict an emission");
            rig.down.remove(rig.keys[slot]); rig.tick();
            check(rig.wire.size() == 2 && rig.wire.getLast().startsWith("release:"), "recall release reaches server exactly once despite cooldown");
            rig = paidRig(slot); rig.down.add(rig.keys[slot]); rig.tick(); rig.down.remove(rig.keys[slot]); rig.tick();
            check(rig.wire.size() == 2 && rig.array.path().isEmpty() && rig.array.phase() == FoldedMirrorState.ACTIVE,
                    "short release during cooldown sends intent only; client cannot invent a paid shot or impact");
            for (int blocked = 0; blocked < 14; blocked++) {
                Rig denied = paidRig(slot);
                switch (blocked) {
                    case 0 -> denied.array = f(1, 2, 1, 0, List.of());
                    case 1 -> denied.remaining = 0;
                    case 2 -> denied.quiet = FoldedMirrorState.QUIET_TICKS;
                    case 3 -> denied.activated = false;
                    case 4 -> denied.overloadFine = false;
                    case 5 -> denied.interfering = true;
                    case 6 -> denied.consuming = true;
                    case 7 -> denied.learned = false;
                    case 8 -> denied.parentLearned = false;
                    case 9 -> denied.category = "electromaster";
                    case 10 -> denied.mapped = false;
                    case 11 -> denied.enabled = false;
                    case 12 -> denied.input.ended(1, 2);
                    case 13 -> denied.cooldown = -1;
                    default -> throw new AssertionError();
                }
                denied.down.add(denied.keys[slot]); denied.tick();
                check(denied.wire.isEmpty(), "cooldown recall exception preserves unpaid/lifetime/session and all other ability gates " + blocked);
            }
        }
        check(!FoldedMirrorState.recallAvailable(f(2, 3, 3, 0, List.of()), 400, 0), "ended array cannot reopen recall despite forged remaining time");
    }
    private static void receipts() {
        var state = new FoldedMirrorState();
        check(state.accept(f(1, 1, 1, 0, List.of()), true), "unpaid edit admits a ghost with no array lifetime");
        check(state.accept(f(1, 2, 0, 400, List.of(A)), true), "first paid plate initializes immutable lifetime once");
        check(!state.accept(f(1, 2, 0, 400, List.of(A)), true), "duplicate receipt cannot renew lifetime");
        check(state.accept(f(2, 3, 1, 396, List.of(A)), true), "second press edits existing token");
        check(state.accept(f(2, 4, 0, 392, PLATES), true), "second plate appends without resetting lifetime");
        check(!state.accept(f(2, 5, 0, 400, PLATES), true), "replacement heartbeat cannot refill the array lifetime");
        var moved = List.of(new Plate(0, A.center().add(v(.01, 0, 0)), A.normal()), B);
        check(!state.accept(f(2, 5, 0, 391, moved), true), "body or camera movement cannot move a frozen mirror center");
        var rotated = List.of(new Plate(0, A.center(), unit(v(1, 0, 1))), B);
        check(!state.accept(f(2, 5, 0, 391, rotated), true), "unannounced heartbeat cannot turn a frozen mirror");
        var fire = frame(3, 5, 5, 2, FoldedMirrorState.DAMAGED, 390, PLATES, PATH);
        check(state.accept(fire, true), "confirmed reflected beam follows two actual fixed planes");
        check(state.accept(f(3, 6, 0, 386, PLATES), true), "paid array persists after a brief beam receipt");
        check(!state.accept(f(2, 7, 0, 382, PLATES), true), "older press cannot regain array ownership");
        check(state.accept(f(4, 7, 3, 0, List.of()), true) && state.size() == 0, "recall or expiry retires token");
        check(!state.accept(frame(4, 5, 99, 2, FoldedMirrorState.DAMAGED, 380, PLATES, PATH), true), "terminal token cannot resurrect with a later sequence");
        state.cancel(2, 9); state.cancel(1, 999);
        check(!state.accept(frame(9, 6, 1, 1, 0, 0, List.of(), List.of()), true), "cancel-before-receipt fence cannot be rewound");
        check(state.accept(frame(10, 6, 1, 1, 0, 0, List.of(), List.of()), true), "fresh physical input can begin a fresh array");
        state.clear(); check(!state.accept(frame(10, 6, 2, 0, 0, 400, List.of(A), List.of()), true), "same-connection clear retains token tombstones");
        check(!FoldedMirrorState.valid(frame(1, 1, 1, 1, FoldedMirrorState.DAMAGED, 0, List.of(), PATH)), "unpaid edit cannot claim confirmed damage");
        check(!FoldedMirrorState.valid(frame(1, 1, 1, 2, 4, 400, PLATES, List.of(PATH.get(0), segment(A.center().add(v(.001, 0, 0)), B.center())))), "gaps between claimed beam segments are rejected");
        check(!FoldedMirrorState.valid(frame(1, 1, 1, 2, 4, 400, PLATES, List.of(PATH.get(0), segment(A.center(), v(-6, 64, 6))))), "target-directed second leg cannot violate actual plane reflection");
        var forty = List.of(PATH.get(0), PATH.get(1), segment(B.center(), v(6, 64, 34)));
        check(FoldedMirrorState.valid(frame(1, 1, 1, 2, 4, 400, PLATES, forty)), "total forty meters across all three legs is valid");
        check(!FoldedMirrorState.valid(frame(1, 1, 1, 2, 4, 400, PLATES, List.of(PATH.get(0), PATH.get(1), segment(B.center(), v(6, 64, 34.00001))))), "range never resets at either mirror");
        denied(() -> new Plate(0, A.center(), v(0, 0, 2)), "nonunit normals rejected rather than silently repaired");
        var invalidGhost = new FoldedMirrorState.Frame(f(1, 1, 1, 0, List.of()).id(), 1, 1, 0, 0, SOURCE,
                List.of(), v(30_000_000, 64, 0), LOOK, List.of());
        check(!FoldedMirrorState.valid(invalidGhost), "out-of-world ghost slab rejected before renderer can construct its geometry");
        state.resetSession();
        for (int i = 1; i <= 65; i++) {
            var id = new FoldedMirrorState.Identity(i, new UUID(221, i), 1, 1, 1);
            var f = new FoldedMirrorState.Frame(id, 1, 0, 0, 400, SOURCE, List.of(A), ZERO, ZERO, List.of());
            check(state.accept(f, false) == (i <= 64), "observer context cap is hard and bounded");
        }
        check(state.size() == 64 && state.watermarkCount() == 64, "overflow allocates neither context nor replay fence");
    }
    private static void leasesAndStatus() {
        for (int level = 0; level <= 7; level++) check(FoldedMirrorState.eligible(level, true, true) == (level >= 2 && level <= 5), "eligibility includes all LV2..5 and no other level");
        check(!FoldedMirrorState.eligible(2, false, true) && !FoldedMirrorState.eligible(5, true, false), "learned NEW211 parent and learned NEW221 both required");
        var edit = f(1, 1, 1, 0, List.of()); var lease = new FoldedMirrorState.Lease(); lease.received(edit);
        check(FoldedMirrorState.status(edit, 0).seconds() == -1 && FoldedMirrorState.status(edit, 0).key().equals("adjusting"), "unpaid edit omits nonexistent zero-second array timer");
        for (int i = 1; i < 100; i++) { if (i % 4 == 0) lease.received(edit); check(lease.tick(false, true), "unpaid edit lease stays bounded while receiving heartbeats"); }
        check(!lease.tick(false, true), "unpaid edit ends at its own hundred-tick ceiling");
        lease = new FoldedMirrorState.Lease(); lease.received(edit); lease.tick(false, true); lease.received(f(1, 2, 0, 400, List.of(A)));
        check(lease.remaining() == 400, "first paid placement starts lifetime independently of edit timer");
        for (int i = 1; i < 400; i++) { if (i % 4 == 0) lease.received(f(2, i + 2, 1, 400, PLATES)); check(lease.tick(false, true), "heartbeats cannot extend a paid array"); }
        check(!lease.tick(false, true) && lease.remaining() == 0, "paid array expires absolutely at four hundred ticks");
        lease = new FoldedMirrorState.Lease(); lease.received(f(1, 2, 0, 400, PLATES));
        for (int i = 0; i < 1000; i++) check(lease.tick(true, true) && lease.remaining() == 400, "pause does not consume or extend server-time lease");
        check(!lease.tick(true, false), "focus or session invalidation still clears while paused");
        for (int i = 1; i < 12; i++) check(lease.tick(false, true), "heartbeat absence has finite grace");
        check(!lease.tick(false, true), "quiet stale geometry disappears at twelve ticks");
        var prepared = FoldedMirrorState.status(f(2, 4, 1, 379, PLATES), 379);
        check(prepared.count() == 2 && prepared.seconds() == 19 && prepared.key().equals("adjusting") && !prepared.confirmedDamage(), "status contains count real remaining seconds and adjustment without fake success");
        var refused = frame(3, 5, 5, 2, FoldedMirrorState.REFUSED, 378, PLATES, PATH);
        check(FoldedMirrorState.status(refused, 378).key().equals("refused") && !FoldedMirrorState.status(refused, 378).confirmedDamage(), "cancelled native damage remains visibly refused");
        check(FoldedMirrorState.status(frame(3, 5, 5, 2, FoldedMirrorState.DAMAGED, 378, PLATES, PATH), 378).confirmedDamage(), "only actual FIRE damage receipt can label confirmed damage");
    }
    private static void plates() {
        for (Vector normal : List.of(A.normal(), LOOK, v(0, 1, 0), v(0, -1, 0), unit(v(1, 2, 3)))) {
            Plate p = new Plate(0, v(3, 64, 5), normal); var basis = FoldedMirrorGeometry.frame(p);
            var mesh = FoldedMirrorMesh.plate(p, false);
            check(mesh.size() == 144 && FoldedMirrorMesh.ELLIPSE_STEPS == 48, "smooth thin ellipse uses bounded real faces");
            boolean front = false, back = false;
            for (var face : mesh) for (Vector point : face.vertices()) {
                Vector local = point.subtract(p.center()); double depth = local.dot(normal);
                double radius = Math.hypot(local.dot(basis.right()) / .45, local.dot(basis.up()) / .65);
                check(radius <= 1 + 1e-12 && Math.abs(depth) <= .020000000001, "mirror mesh exactly fits physical radii and slab thickness");
                front |= Math.abs(depth - .02) < 1e-10; back |= Math.abs(depth + .02) < 1e-10;
            }
            check(front && back, "side faces show real finite thickness");
            var ghost = FoldedMirrorMesh.plate(p, true);
            check(ghost.size() == 24 && ghost.stream().allMatch(face -> face.kind() == FoldedMirrorMesh.GHOST), "planned mirror has only twelve separated two-segment dashes and no fake paid disk");
            check(mesh.stream().filter(face -> face.kind() == FoldedMirrorMesh.FILL).allMatch(face -> face.texture() == FoldedMirrorMesh.SHIELD_TEXTURE && face.color() == 0xFFFFFF && face.alpha() == 255),
                    "paid mirror preserves the original artwork's white RGB and baked transparency without multiplying away its contrast");
        }
        Plate left = new Plate(0, v(-.8, 64, 4), LOOK), right = new Plate(1, v(.8, 64, 4), LOOK);
        for (var face : FoldedMirrorMesh.plate(left, false)) for (var point : face.vertices()) check(point.x() <= -.35 + 1e-12, "left plate cannot fill interplate gap");
        for (var face : FoldedMirrorMesh.plate(right, false)) for (var point : face.vertices()) check(point.x() >= .35 - 1e-12, "right plate cannot fill interplate gap");
        var near = new FoldedMirrorMesh.Face(v(-1, 0, -.5), v(1, 0, -.5), v(1, 0, .5), v(-1, 0, .5), 1, 255, 0, -1);
        var clipped = FoldedMirrorMesh.nearClip(near, ZERO, LOOK); check(!clipped.isEmpty(), "crossing plate clips true silhouette");
        for (var face : clipped) for (var point : face.vertices()) check(point.z() >= .08 - 1e-12 && point.z() <= .5, "near clip remains inside original polygon");
        check(FoldedMirrorMesh.nearClip(near, v(0, 0, 1), LOOK).isEmpty(), "behind-camera plate is not mirrored onto screen");
    }
    private static void rays() {
        var planned = frame(1, 5, 1, 1, 0, 400, PLATES, PATH);
        var confirmed = frame(2, 5, 2, 2, FoldedMirrorState.DAMAGED, 399, PLATES, PATH);
        var dots = FoldedMirrorMesh.path(planned, SOURCE, LOOK, 0);
        check(!dots.isEmpty() && dots.stream().allMatch(face -> face.kind() == FoldedMirrorMesh.PLANNED), "server planned route emits only separated dim dashes");
        var fired = FoldedMirrorMesh.path(confirmed, SOURCE, LOOK, 0);
        check(fired.stream().anyMatch(face -> face.kind() == FoldedMirrorMesh.BEAM_EDGE && face.texture() == FoldedMirrorMesh.WHITE_TEXTURE)
                && fired.stream().anyMatch(face -> face.kind() == FoldedMirrorMesh.BEAM_HALO && face.texture() >= 0)
                && fired.stream().anyMatch(face -> face.color() == FoldedMirrorMesh.CORE), "actual receipt pairs green-white and green volumes with the original textured envelope");
        check(fired.stream().filter(face -> face.kind() == FoldedMirrorMesh.REFRACTION).count() == 16, "exactly two genuine crossing points receive bounded sharp arcs");
        check(FoldedMirrorMesh.path(confirmed, SOURCE, LOOK, 6).isEmpty(), "confirmation beam has six-tick lifetime");
        check(FoldedMirrorMesh.path(confirmed, SOURCE, v(0, 0, -1), 0).isEmpty(), "beam behind camera never becomes a screen wedge");
        int observed = 0;
        for (double x : new double[]{-.6, -.1, 0, .1, .6}) for (double y : new double[]{-.15, 0, .15})
            for (double z : new double[]{-.15, 0, .15}) for (int yaw : new int[]{-25, 0, 25}) for (int pitch : new int[]{-15, 0, 15}) {
                Vector camera = SOURCE.add(v(x, y, z)); double ya = Math.toRadians(yaw), pi = Math.toRadians(pitch);
                Vector look = unit(v(Math.sin(ya) * Math.cos(pi), Math.sin(pi), Math.cos(ya) * Math.cos(pi)));
                Vector right = unit(look.cross(v(0, 1, 0))), up = right.cross(look);
                for (var face : FoldedMirrorMesh.path(confirmed, camera, look, 0)) {
                    if (face.kind() == FoldedMirrorMesh.REFRACTION) continue;
                    for (Vector vertex : face.vertices()) {
                        double limit = face.kind() == FoldedMirrorMesh.BEAM_HALO ? FoldedMirrorMesh.HALO_RADIUS : FoldedMirrorGeometry.BEAM_RADIUS;
                        boolean bounded = false;
                        // Independently project volume vertices onto the authoritative finite segments; a surface-face midpoint is no longer the axis.
                        for (var segment : PATH) {
                            Vector delta = segment.end().subtract(segment.start());
                            double t = vertex.subtract(segment.start()).dot(delta) / delta.dot(delta);
                            if (t < -1e-8 || t > 1 + 1e-8) continue;
                            Vector axis = segment.start().add(delta.scale(Math.clamp(t, 0, 1))), c = axis.subtract(camera), p = vertex.subtract(camera);
                            double depth = p.dot(look), centerDepth = c.dot(look);
                            if (vertex.subtract(axis).length() > limit + 1e-8 || centerDepth < .20 - 1e-8 || depth <= .08) continue;
                            double angle = Math.hypot(p.dot(right) / depth - c.dot(right) / centerDepth, p.dot(up) / depth - c.dot(up) / centerDepth);
                            if (angle > FoldedMirrorMesh.angularLimit(face.kind()) + 1e-8) continue;
                            bounded = true;
                            for (int fov : new int[]{60, 70, 90, 110}) check(angle * 406 / Math.tan(Math.toRadians(fov / 2.0)) < (face.kind() == FoldedMirrorMesh.BEAM_HALO ? 7.1 : 4.3),
                                    "at 812 pixels high, the cosmetic envelope stays below 7.1 pixels half-width and all physical volume below 4.3 across native FOVs");
                            break;
                        }
                        check(bounded, "every volume/glow vertex stays within an authentic finite centerline radius, near plane and explicit angular bound under camera motion");
                        observed++;
                    }
                }
            }
        check(observed > 20_000, "projection regression covers moving first-person history rather than one centered image");
        var far = FoldedMirrorMesh.path(confirmed, SOURCE.subtract(v(0, 0, 30)), LOOK, 0);
        boolean contracted = false;
        for (var face : far) if (face.kind() == FoldedMirrorMesh.BEAM_EDGE) for (var vertex : face.vertices()) {
            double distance = vertex.subtract(A.center()).length();
            if (distance < .08) {
                check(distance <= .035 * .32 + 1e-9, "both finite volume endpoints contract at the actual mirror contact"); contracted = true;
            }
        }
        check(contracted, "actual refraction taper is present");
        var firstRing = fired.stream().filter(face -> face.kind() == FoldedMirrorMesh.BEAM_CORE).limit(8).map(FoldedMirrorMesh.Face::a).toList();
        Vector center = ZERO; for (Vector point : firstRing) center = center.add(point.scale(1.0 / 8));
        check(center.subtract(SOURCE.add(LOOK.scale(.20))).length() < 1e-10 && new HashSet<>(firstRing).size() == 8,
                "eight distinct core walls surround the exact clipped centerline in a real cross-section");
        double minX = firstRing.stream().mapToDouble(Vector::x).min().orElseThrow(), maxX = firstRing.stream().mapToDouble(Vector::x).max().orElseThrow();
        double projectedPixels = (maxX - minX) / center.subtract(SOURCE).dot(LOOK) * 406 / Math.tan(Math.toRadians(35));
        check(projectedPixels > 5.0 && projectedPixels < 5.5, "near-eye volume has a bounded five-pixel core instead of a jagged subpixel wire at 812p/70 FOV");
        for (int kind : new int[]{FoldedMirrorMesh.BEAM_CORE, FoldedMirrorMesh.BEAM_EDGE}) {
            var volume = far.stream().filter(face -> face.kind() == kind).toList();
            check(volume.size() == 96, "three tapered eight-sided closed volumes have a bounded 96 faces per layer");
            check(volume.stream().filter(face -> face.vertices().contains(SOURCE)).count() == 4
                    && volume.stream().filter(face -> face.vertices().contains(PATH.getLast().end())).count() == 4,
                    "four cap quads terminate each end on the authentic endpoint instead of extending past range");
        }
    }
    private static void textureCoordinates() {
        Plate plate = new Plate(0, v(3, 64, 5), LOOK); var basis = FoldedMirrorGeometry.frame(plate);
        for (var face : FoldedMirrorMesh.plate(plate, false)) if (face.kind() == FoldedMirrorMesh.FILL) {
            for (int i = 0; i < 4; i++) {
                Vector local = face.vertices().get(i).subtract(plate.center()); var uv = face.uvs().get(i);
                check(Math.abs(uv.u() - (.5 + local.dot(basis.right()) / .45 * .43)) < 1e-12
                        && Math.abs(uv.v() - (.5 - local.dot(basis.up()) / .65 * .43)) < 1e-12,
                        "shield artwork is continuous in physical mirror coordinates across every wedge");
            }
        }
        var crossed = new FoldedMirrorMesh.Face(v(-1, 0, -.5), v(1, 0, -.5), v(1, 0, .5), v(-1, 0, .5), 1, 255, FoldedMirrorMesh.FILL, 3);
        for (var face : FoldedMirrorMesh.nearClip(crossed, ZERO, LOOK)) for (int i = 0; i < 4; i++) {
            Vector p = face.vertices().get(i); var uv = face.uvs().get(i);
            check(Math.abs(uv.u() - (p.z() + .5)) < 1e-12 && Math.abs(uv.v() - (1 - p.x()) / 2) < 1e-12,
                    "near-plane intersections interpolate existing UVs instead of stretching or restarting the artwork");
        }
        var fire = frame(2, 5, 2, 2, FoldedMirrorState.DAMAGED, 399, PLATES, PATH);
        for (var face : FoldedMirrorMesh.path(fire, SOURCE, LOOK, 0)) if (face.texture() == 0) {
            Vector start = face.a().add(face.b()).scale(.5);
            if (start.subtract(SOURCE).length() < .25) check(Math.abs(face.uvA().u() - .20 / .24) < 1e-10,
                    "near-eye clipping preserves the visible tail of the original blend-in texture");
        }
    }
    private static void materialsAndTiming() throws Exception {
        var shield = ImageIO.read(Path.of("src/main/resources/assets/academy/textures/effects/mdshield.png").toFile());
        int maxAlpha = 0;
        for (int y = 0; y < shield.getHeight(); y++) for (int x = 0; x < shield.getWidth(); x++) maxAlpha = Math.max(maxAlpha, shield.getRGB(x, y) >>> 24);
        int center = shield.getRGB(shield.getWidth() / 2, shield.getHeight() / 2);
        check(maxAlpha == 115 && (center & 0xFFFFFF) == 0xFFFFFF, "original shield's bright white center is already less than 46 percent opaque at full modulation");
        check((shield.getRGB(0, 0) >>> 24) == 0, "original transparent texture boundary remains intact");
        var fire = frame(2, 5, 2, 2, FoldedMirrorState.DAMAGED, 399, PLATES, PATH);
        int previous = Integer.MAX_VALUE;
        for (int age = 0; age < FoldedMirrorState.FLASH_TICKS; age++) {
            var mesh = FoldedMirrorMesh.path(fire, SOURCE, LOOK, age);
            int alpha = mesh.stream().filter(f -> f.kind() == FoldedMirrorMesh.BEAM_CORE).mapToInt(FoldedMirrorMesh.Face::alpha).max().orElseThrow();
            check(alpha < previous, "receipt core fades monotonically within the existing six ticks"); previous = alpha;
            check(mesh.stream().filter(f -> f.kind() == FoldedMirrorMesh.BEAM_HALO).allMatch(f -> f.alpha() <= 128 && FoldedMirrorMesh.additive(f)),
                    "wider halo is faint and additive instead of an opaque damage silhouette");
            check(mesh.stream().filter(f -> f.kind() == FoldedMirrorMesh.BEAM_EDGE).allMatch(f -> f.alpha() <= 50 && !FoldedMirrorMesh.additive(f))
                    && mesh.stream().filter(f -> f.kind() == FoldedMirrorMesh.BEAM_CORE).allMatch(f -> f.alpha() <= 230 && !FoldedMirrorMesh.additive(f)),
                    "classic-style inner/outer volumes use bounded ordinary alpha rather than additive white overdraw");
        }
        check(FoldedMirrorMesh.path(fire, SOURCE, LOOK, FoldedMirrorState.FLASH_TICKS).isEmpty()
                && FoldedMirrorMesh.path(fire, SOURCE, LOOK, -1).isEmpty(), "neither a speculative flash nor extended damaging-looking afterglow is added");
        String material = Files.readString(Path.of("src/main/java/cn/academy/port/client/FoldedMirrorRenderTypes.java"));
        check(material.contains("setDepthTestState(LEQUAL_DEPTH_TEST)") && material.contains("setWriteMaskState(COLOR_WRITE)")
                && !material.contains("NO_DEPTH_TEST") && !material.contains("COLOR_DEPTH_WRITE"), "all NEW221 layers are terrain-occluded and cannot write a transparent depth wall");
        check(material.contains("new TextureStateShard(key.texture(), true, false)") && material.contains("4096, false, false"),
                "original artwork is filtered and material batches cannot resort ordered faces");
        check(material.contains("GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE") && material.contains("RenderSystem.defaultBlendFunc()"),
                "additive accents preserve target alpha and restore the normal blend function");
        check(material.contains("setCullState(key.cull() ? CULL : NO_CULL)"), "scoped material culls outward beam volumes while preserving two-sided mirrors and glow");
    }
    private static boolean onPath(Vector point) {
        for (var segment : PATH) {
            Vector delta = segment.end().subtract(segment.start()); double t = point.subtract(segment.start()).dot(delta) / delta.dot(delta);
            if (t >= -1e-8 && t <= 1 + 1e-8 && point.subtract(segment.start().add(delta.scale(t))).length() < 1e-7) return true;
        }
        return false;
    }
    private static void materialPhases() throws Exception {
        var fire = frame(2, 5, 2, 2, FoldedMirrorState.DAMAGED, 399, PLATES, PATH);
        var atQuarter = FoldedMirrorMesh.path(fire, SOURCE, LOOK, .25f);
        check(!atQuarter.isEmpty() && atQuarter.stream().allMatch(face -> face.material().phase() == .25f),
                "fractional FIRE phase reaches every face without extending the receipt lifetime");
        check(!FoldedMirrorMesh.path(fire, SOURCE, LOOK, 5.99f).isEmpty()
                && FoldedMirrorMesh.path(fire, SOURCE, LOOK, 6f).isEmpty(), "subtick animation expires exactly at six ticks");
        var active = FoldedMirrorMesh.plate(A, false, 2f, A.center(), .7f);
        check(active.stream().allMatch(face -> face.material().equals(new FoldedMirrorMesh.Material(2f, .5f, .5f, .7f))),
                "local response uses the exact physical contact UV and one frozen phase for the whole mirror");
        check(FoldedMirrorMesh.plate(A, true, 2f, A.center(), 1).stream().allMatch(face -> face.material().contactPower() == 0),
                "unpaid ghost never acquires a contact response");
        check(FoldedMirrorMesh.plate(A, false, 2f, A.center().add(v(4, 4, 4)), 1).stream().allMatch(face -> face.material().contactPower() == 0),
                "outside contact cannot light the mirror face");
        var crossing = new FoldedMirrorMesh.Face(v(-1, 0, -.5), v(1, 0, -.5), v(1, 0, .5), v(-1, 0, .5),
                0xFFFFFF, 255, FoldedMirrorMesh.FILL, 3).withMaterial(new FoldedMirrorMesh.Material(3, .41f, .63f, .2f));
        check(FoldedMirrorMesh.nearClip(crossing, ZERO, LOOK).stream().allMatch(face -> face.material().equals(crossing.material())),
                "near clipping retains owner-specific phase and contact uniforms");
        var core = atQuarter.stream().filter(face -> face.kind() == FoldedMirrorMesh.BEAM_CORE).findFirst().orElseThrow();
        var halo = atQuarter.stream().filter(face -> face.kind() == FoldedMirrorMesh.BEAM_HALO).findFirst().orElseThrow();
        check(FoldedMirrorMesh.layer(halo) < FoldedMirrorMesh.layer(core), "green halo is composited before the luminous center");
        var straight = frame(2, 5, 2, 2, FoldedMirrorState.DAMAGED, 399, PLATES, List.of(segment(SOURCE, SOURCE.add(LOOK.scale(8)))));
        for (Vector eye : List.of(SOURCE, SOURCE.add(v(.25, .04, .11)))) {
            for (var face : FoldedMirrorMesh.path(straight, eye, unit(v(.05, .02, 1)), .25f)) {
                if (!FoldedMirrorMesh.culled(face)) continue;
                for (int index = 0; index < 4; index++) check(Math.abs(face.uvs().get(index).u()
                        - face.vertices().get(index).subtract(SOURCE).dot(LOOK)) < 1e-10,
                        "core flow retains physical longitudinal distance across section joins, caps and moving near-plane clipping");
            }
        }
        String effect = Files.readString(Path.of("src/main/java/cn/academy/port/client/FoldedMirrorEffects.java"));
        check(effect.contains("materialPreview != null ? FoldedMirrorMaterialPreview.phaseAge() :")
                && effect.contains("flash.age + partial") && effect.contains("!face.material().equals(activeMaterial)"),
                "stage freezes the full shader clock and live subtick uniforms have explicit batch boundaries");
        String shader = Files.readString(Path.of("src/main/java/cn/academy/port/client/FoldedMirrorMaterial.java"));
        check(shader.contains("energy = null;") && shader.contains("IOException | RuntimeException")
                && shader.contains("GL20.GL_LINK_STATUS") && shader.contains("candidate.close()")
                && shader.contains("foldedMirrorForceFallback") && shader.contains("GameRenderer.getPositionTexColorShader()"),
                "reload, compile and link failures all retain a safe original-texture fallback");
        String fragment = Files.readString(Path.of("src/main/resources/assets/academy/shaders/core/folded_mirror_energy.fsh"));
        check(!fragment.contains("GameTime") && fragment.contains("color.a <= 0.001"),
                "material has no independent game clock or hard texture alpha cutoff");
        check(fragment.contains("Contact.xy") && fragment.contains("source.a *") && fragment.contains("VisualPhase"),
                "contact response is bounded inside the original textured face without a fallback white quad");
        check(fragment.contains("texture(Sampler0, vec2(uv.x, 0.5)).a")
                && fragment.contains("longitudinal * vertexColor.a * envelope")
                && !fragment.contains("source.a * vertexColor.a * envelope"),
                "original ray alpha controls authentic endcap fade without squeezing the halo shoulders a second time");
        check(fragment.contains("smoothstep(0.23, 0.64, min(source.r, source.b))")
                && fragment.contains("0.22 + detail * 0.78"),
                "mirror selects pale original swirl highlights while leaving uniform green wash transparent");
    }
    private static void budget() {
        var budget = new FoldedMirrorMesh.Budget(); int count = 0;
        for (int i = 0; i < 10000; i++) if (budget.take()) count++;
        check(count == FoldedMirrorMesh.MAX_MESH_QUADS && budget.used() == count, "post-clip frame budget applies to all owners and textures");
        check(FoldedMirrorMesh.SPAWNED_PARTICLES == 0, "no particle controllers or hit sparks allocated");
        var crowded = new ArrayList<FoldedMirrorMesh.Face>();
        var fire = frame(2, 5, 2, 2, FoldedMirrorState.DAMAGED, 399, PLATES, PATH);
        var essential = FoldedMirrorMesh.path(fire, SOURCE, LOOK, 0).stream().filter(f -> f.kind() == FoldedMirrorMesh.BEAM_CORE).toList();
        for (int i = 0; i < 20; i++) crowded.addAll(FoldedMirrorMesh.plate(A, false));
        crowded.addAll(essential);
        var ordered = FoldedMirrorMesh.ordered(crowded, SOURCE, LOOK, new FoldedMirrorMesh.Budget());
        check(ordered.size() == FoldedMirrorMesh.MAX_MESH_QUADS && ordered.stream().filter(f -> f.kind() == FoldedMirrorMesh.BEAM_CORE).count() == essential.size(),
                "post-clipping budget preserves real receipt cores even when decorative plates saturate it");
        int layer = -1;
        for (var face : ordered) { check(FoldedMirrorMesh.layer(face) >= layer, "blending order is deterministic after priority budgeting"); layer = FoldedMirrorMesh.layer(face); }
    }
    private static void integration() throws Exception {
        String effects = Files.readString(Path.of("src/main/java/cn/academy/port/client/FoldedMirrorEffects.java"));
        String client = Files.readString(Path.of("src/main/java/cn/academy/port/client/AcademyClient.java"));
        String controller = Files.readString(Path.of("src/main/java/cn/academy/port/highpower/FoldedMirrorArray.java"));
        check(effects.contains("FoldedMirrorArray.clientReceiver = FoldedMirrorEffects::receive"), "actual payload reaches production renderer");
        check(effects.contains("FoldedMirrorRenderTypes.type(") && !effects.contains("ClassicRenderTypes.world(") && !effects.contains("disableDepthTest") && !effects.contains("addParticle("), "fixed mirrors and rays use isolated depth-tested materials with no fake impacts");
        check(effects.contains("activeType != type || activeLayer != layer") && effects.contains("buffers.endBatch(activeType)")
                && effects.contains("face.uvA()") && effects.contains("face.uvD()"), "actual renderer flushes material/layer boundaries and emits interpolated UVs");
        check(effects.contains("FoldedMirrorRenderTypes.type(texture, FoldedMirrorMesh.additive(face), FoldedMirrorMesh.culled(face))"),
                "actual candidate renderer enables the tested outward-volume culling while retaining two-sided plate and glow materials");
        check(effects.contains("RenderSystem.getShaderColor().clone()") && effects.contains("RenderSystem.setShaderColor(1, 1, 1, 1)")
                && effects.contains("RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3])"), "renderer scopes shader tint and restores the incoming state");
        check(effects.contains("generation != capturedGeneration") && effects.contains("!AcademyClient.sameInputSessionEpoch(capturedSession)") && effects.contains("owner.getUUID().equals(packet.ownerUuid())"), "queued receipts verify exact session owner and generation");
        check(effects.contains("FoldedMirrorState.eligible(state.level, state.learned(DualPoint.ID), state.learned(FoldedMirrorArray.ID))"), "real local render uses tested LV2..5 and learned-parent gate");
        check(effects.contains("public static boolean canRecallLocal()") && effects.contains("marker != null && validOwner(marker, mc) && !AcademyClient.state.consumptionInProgress()")
                && effects.contains("FoldedMirrorState.recallAvailable(marker.frame, marker.lease.remaining(), marker.lease.quiet())"), "actual recall exception requires live authenticated paid local array and preserves consumption guard");
        check(client.contains("state.canUse(node.skill)||node.skill.equals(cn.academy.port.highpower.FoldedMirrorArray.ID)")
                && client.contains("state.cooldowns.getOrDefault(node.skill,0)>0&&FoldedMirrorEffects.canRecallLocal()")
                && client.contains("cn.academy.port.preset.PresetSkills.mappedUsable(state,node.skill)&&SkillCatalog.enabled(state.category,node.skill)"), "production binding bypasses only positive cooldown for this one live paid skill while preserving mapping and feature gate");
        check(effects.contains("mc.getCameraEntity() == mc.player") && effects.contains("mc.screen == null") && effects.contains("mc.isWindowActive()"), "camera/menu/focus protections run before local geometry");
        check(effects.contains("status_unpaid") && effects.contains("status.count()") && effects.contains("status.seconds()") && effects.contains("status.key()"), "real small HUD renders count lifetime and truthful edit/refusal cues");
        check(effects.contains("next.phase() == FoldedMirrorState.FIRE") && effects.contains("FoldedMirrorMesh.path(flash.frame"), "solid beam queue requires actual FIRE receipt");
        check(client.contains("foldedMirrorInputs.begin(identity,node.key,down(mc.options.keyShift),foldedMirrorDispatchContext(mc))"), "real binding captures configured physical mode and session only at press");
        check(client.contains("foldedMirrorInputs.release(identity)") && client.contains("foldedMirrorInputs.releaseWhenUp(physicalDown(mirrorHeld.physicalKey()))"), "real direct and snapshot-fallback physical releases share once-only latch");
        check(client.contains("foldedMirrorInputs.cancel(identity)") && client.contains("foldedMirrorInputs.cancelWhenBlocked(") && client.contains("foldedMirrorInputs.reset();FoldedMirrorEffects.clear();"), "owned abort and all invalidation paths reach the new state");
        check(client.contains("activationState.receive(state,t,next->state=next,AcademyClient::modernSnapshotFallback)"), "decoded native snapshot bridge reaches the exercised real binding fallback");
        check(client.contains("request(\"hp_folded_mirror_start\",dispatch.start().wire())") && client.contains("request(\"hp_folded_mirror_release\",identity.wire())"), "production Post transport sends exact authenticated start and release actions");
        String press = client.substring(client.indexOf("private static long press("), client.indexOf("private static void terminal("));
        String flush = client.substring(client.indexOf("private static void flushFoldedMirrorStart()"), client.indexOf("public static boolean foldedMirrorInputMatches"));
        int postStart=client.indexOf("finishContextDisposals(ClientTickEvent.Post event)");
        String post=client.substring(postStart,client.indexOf('\n',postStart));
        int disposal=post.indexOf("binding.finishLocalDisposals()"),mirrorDispatch=post.indexOf("flushFoldedMirrorStart()"),sweepDispatch=post.indexOf("flushDisintegrationSweepStart()");
        check(!press.contains("request(\"hp_folded_mirror_start\"") && disposal>=0 && mirrorDispatch>disposal && sweepDispatch>mirrorDispatch
                        &&post.split("flushFoldedMirrorStart\\(\\)",-1).length==2,
                "start is absent from Pre press and dispatched exactly once after ordinary Post lifecycle work, before sweep and additive later delegates");
        check(flush.contains("foldedMirrorInputs.queuedFor(foldedMirrorDispatchContext(mc))") && flush.contains("if(!ready){sendPersistentCancellation(")
                && flush.indexOf("request(\"hp_folded_mirror_start\"") < flush.indexOf("releaseFoldedMirrorInput(dispatch.release())"), "Post revalidates original session/preset/mapping and sends start before queued release");
        check(!flush.contains("PRESS_SHIFT_KEY") && !flush.contains("ServerboundPlayerCommandPacket") && !flush.contains("isShiftKeyDown"), "deferred dispatch neither synthesizes a crouch packet nor sends a client-trusted mode");
        check(controller.contains("if(count>2)") && controller.contains("if(count>3)") && controller.contains("sum()>40"), "packet decoder enforces two plates three legs and one forty-meter range");
        String serverFire = controller.substring(controller.indexOf("private static boolean fire(Context c)"), controller.indexOf("private static Plan trace("));
        check(controller.contains("if(now(p)-hold.started>=FoldedMirrorRules.RECALL_TICKS){finish(c);return true;}")
                && serverFire.contains("!AbilityStorage.get(p).canUse(ID)"), "server classifies recall before fire and retains authoritative shot cooldown gate");
        check(client.contains("dualPointInputs.release(identity)") && client.contains("scatterPlateInputs.release(identity)") && client.contains("railArrayInputs.release(identity)"), "existing key bindings remain wired");
    }
}

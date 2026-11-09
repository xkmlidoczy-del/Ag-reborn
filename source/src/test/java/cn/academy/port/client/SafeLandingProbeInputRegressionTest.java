package cn.academy.port.client;

import cn.academy.port.core.TargetedContextTermination;
import cn.academy.port.highpower.HighPowerInput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Drives the production NEW313 helper through the unchanged real classic binding/coordinator. */
public final class SafeLandingProbeInputRegressionTest {
    private static final String ID = "safe_landing_probe";
    private static int checks, scenarios;
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static void equal(Object actual, Object expected, String message) { check(Objects.equals(actual, expected), message + ": " + actual + " != " + expected); }
    private static void scenario(String name, Runnable test) { test.run(); scenarios++; System.out.println("PASS " + name); }
    private record Loss(String name, Consumer<Rig> apply) {}
    private static final class Rig implements ClassicClientInputBinding.Host {
        final SafeLandingProbeInputState input = new SafeLandingProbeInputState();
        ClassicClientInputBinding binding;
        Object world = new Object(), player = new Object(), connection = new Object(), pose = new Object();
        Object boundWorld = world, boundPlayer = player, boundConnection = connection;
        long session = 1, epoch = 71, revision = 1, counter;
        int actor = 19, preset, selectedSlot, cooldown, previewRequests, confirmRequests, releases;
        boolean inGame = true, category = true, activated = true, usable = true, learned = true, enabled = true;
        boolean screen, focused = true, camera = true, mapped = true, grounded = true, paused, failAbort;
        double x, y = 64, z;
        String mappings = "tp-original";
        final int[] keys = {-100, -99, 19, 33};
        final Set<Integer> physical = new HashSet<>();
        final List<String> wire = new ArrayList<>();
        final List<HighPowerInput.Input> hidden = new ArrayList<>();
        Rig() { this(0); }
        Rig(int slot) { selectedSlot = slot; install(); }
        void install() {
            boundWorld = world; boundPlayer = player; boundConnection = connection;
            binding = new ClassicClientInputBinding(this); binding.runtime().updateDefaultGroup();
        }
        public boolean available() { return world == boundWorld && player == boundPlayer && connection == boundConnection; }
        public boolean inGame() { return inGame; }
        public boolean categoryActivated() { return category && activated; }
        public boolean canUseAbility() { return activated && usable; }
        public boolean terminalOpen() { return screen; }
        public int actorId() { return actor; }
        public long ownerEpoch() { return epoch; }
        public boolean physicalDown(int key) { return physical.contains(key); }
        public boolean registeredMapping(String skill) { return ID.equals(skill); }
        public boolean eligible(ClassicClientInputBinding.Node node) { return input.eligible(gates(node.slot, node.key)); }
        public String mappedSkill(int slot) { return slot == selectedSlot ? ID : ""; }
        public int mappedKey(int slot) { return keys[slot]; }
        public int slotCount() { return 4; }
        public void rawActivation(boolean desired) { activated = desired; input.cancelBeforeClear(this::sendAbort); }
        public void replaceOverrides(int[] keys) {}
        public long down(ClassicClientInputBinding.Node node) {
            if (paused) return 0;
            boolean confirmation = input.acceptedIdentity() != null;
            var candidate = new HighPowerInput.Input(node.slot, counter + 1, node.actorId(), node.ownerEpoch());
            var press = input.press(candidate, gates(node.slot, node.key)); if (press == null) return 0;
            if (press.dispatch()) {
                counter++; wire.add("probe:" + press.identity().wire());
                if (confirmation) confirmRequests++; else previewRequests++;
            }
            return press.identity().input();
        }
        public void up(ClassicClientInputBinding.Node node, long value) {
            releases++; input.release(new HighPowerInput.Input(node.slot, value, node.actorId(), node.ownerEpoch()));
        }
        public void abort(ClassicClientInputBinding.Node node, long value) {
            input.cancel(new HighPowerInput.Input(node.slot, value, node.actorId(), node.ownerEpoch()), this::sendAbort);
        }
        public boolean cancelPending(ClassicClientInputBinding.Node node, long value) { return false; }
        public void direction(ClassicClientInputBinding.ContextIdentity context, int direction, ClassicClientInputBinding.Edge edge, boolean silent) {}
        public void localContextEnd(ClassicClientInputBinding.ContextIdentity context) {}
        public void contextAbort(TargetedContextTermination.Request request) { throw new AssertionError("No classic motion route"); }
        public void modernCleanup(Collection<ClassicClientInputBinding.Node> defaults) {}
        SafeLandingProbeInputState.Gates gates(int slot, int key) {
            var context = new SafeLandingProbeInputState.DispatchContext(world, player, connection, session,
                    actor, epoch, preset, revision, mappings, pose, x, y, z);
            return new SafeLandingProbeInputState.Gates(context, slot, key, available() && inGame && grounded,
                    categoryActivated(), canUseAbility(), category && learned, enabled,
                    mapped && selectedSlot == slot, !screen, focused, camera, cooldown == 0);
        }
        void tick() {
            var current = input.current();
            if (current != null) input.tick(gates(current.slot(), keys[current.slot()]), paused, this::sendAbort);
            binding.tickStart();
        }
        void press() { physical.add(keys[selectedSlot]); tick(); }
        void release() { physical.remove(keys[selectedSlot]); tick(); }
        void preview(long value, int remaining) { input.preview(value, epoch, remaining); }
        void end(long value) { input.ended(value, epoch); }
        void sendAbort(HighPowerInput.Input identity) {
            check(input.current() == identity, "captured latest identity remains owned during transport");
            if (failAbort) { failAbort = false; throw new IllegalStateException("injected abort failure"); }
            if (identity.actor() == actor && identity.epoch() == epoch && input.ownsEnvironment(world, player, connection))
                wire.add("abort:" + identity.wire());
            hidden.add(identity);
        }
        long aborts() { return wire.stream().filter(value -> value.startsWith("abort:")).count(); }
    }
    public static void main(String[] args) {
        scenario("all four classic bindings require two fresh edges and receipt acknowledgement", () -> {
            for (int slot = 0; slot < 4; slot++) {
                Rig r = new Rig(slot); r.press();
                check(r.binding.runtime() instanceof ClassicClientRuntimeCoordinator, "actual coordinator");
                equal(ClassicClientInputBinding.kind(ID), ClassicClientInputBinding.Kind.SINGLE_KEY, "canonical binding kind unchanged");
                equal(r.previewRequests, 1, "one first request");
                r.cooldown = 60;
                for (int tick = 0; tick < 10; tick++) r.tick();
                equal(r.wire.size(), 1, "held input has no repeats or predicted confirmation");
                r.release(); equal(r.releases, 1, "actual coordinator release callback");
                r.press(); equal(r.wire.size(), 1, "unacknowledged repress suppressed");
                r.release(); r.preview(1, 100);
                r.press(); equal(r.confirmRequests, 1, "second accepted fresh edge confirms");
                check(r.input.permits(1, r.epoch) && r.input.permits(2, r.epoch), "frozen original and newer confirm admitted together");
                r.release(); equal(r.wire.size(), 2, "both releases inert");
                r.end(2); check(r.input.current() == null, "terminal latest receipt closes both identities");
                equal(r.aborts(), 0L, "authoritative success never emits cancellation");
                check(!r.input.eligible(r.gates(slot, r.keys[slot])), "terminal gives no cooldown bypass");
            }
        });
        scenario("held key never confirms an acknowledged preview", () -> {
            Rig r = new Rig(); r.press(); r.preview(1, 100);
            for (int i = 0; i < 30; i++) { r.preview(1, 100 - i); r.tick(); }
            equal(r.confirmRequests, 0, "receipt cannot invent a fresh key edge");
            equal(r.wire.size(), 1, "no automatic aim or heartbeat request");
            r.release(); r.press(); equal(r.confirmRequests, 1, "real release/repress confirms");
        });
        List<Loss> losses = List.of(
                new Loss("GUI", r -> r.screen = true), new Loss("focus", r -> r.focused = false),
                new Loss("camera", r -> r.camera = false), new Loss("category", r -> r.category = false),
                new Loss("activation", r -> r.activated = false), new Loss("ability", r -> r.usable = false),
                new Loss("learned/level", r -> r.learned = false), new Loss("configuration", r -> r.enabled = false),
                new Loss("mapping", r -> r.mapped = false), new Loss("slot", r -> r.selectedSlot = 1),
                new Loss("preset", r -> r.preset++), new Loss("revision", r -> r.revision++),
                new Loss("mapping signature", r -> r.mappings = "changed"), new Loss("key", r -> r.keys[0] = 30),
                new Loss("actor", r -> r.actor++), new Loss("owner epoch", r -> r.epoch++),
                new Loss("world", r -> r.world = new Object()), new Loss("player", r -> r.player = new Object()),
                new Loss("connection", r -> r.connection = new Object()), new Loss("session", r -> r.session++),
                new Loss("pose", r -> r.pose = new Object()), new Loss("grounded", r -> r.grounded = false),
                new Loss("drift", r -> r.x = .25001), new Loss("nonfinite", r -> r.x = Double.NaN),
                new Loss("death/spectator", r -> r.inGame = false));
        scenario("every owner loss cancels pending preview accepted preview and pending confirmation after release", () -> {
            for (Loss loss : losses) for (int phase = 0; phase < 3; phase++) {
                Rig r = new Rig(); r.press();
                if (phase > 0) r.preview(1, 100);
                r.release(); if (phase == 2) { r.press(); r.release(); }
                var captured = r.input.current(); loss.apply.accept(r); r.tick();
                check(r.input.current() == null, "ownership removed: " + loss.name + "/" + phase);
                equal(r.hidden, List.of(captured), "latest captured cancellation once: " + loss.name + "/" + phase);
                check(!r.input.permits(captured.input(), captured.epoch()), "late packet fenced");
                int messages = r.wire.size(); r.tick(); equal(r.wire.size(), messages, "no repeat abort");
            }
        });
        scenario("movement at the exact quarter-meter threshold remains eligible", () -> {
            Rig r = new Rig(); r.press(); r.preview(1, 100); r.release(); r.x = .25; r.tick();
            check(r.input.current() != null, "exact boundary allowed");
            r.x = .2500001; r.tick(); equal(r.aborts(), 1L, "strictly greater drift cancelled");
        });
        scenario("cosmetic loss aborts the latest confirmation before clearing its input", () -> {
            Rig r = new Rig(); r.press(); r.preview(1, 100); r.release(); r.press();
            r.input.cancelVisual(1, r.epoch, r::sendAbort);
            equal(r.wire.getLast(), "abort:0:2:19:71", "old preview loss captures newer confirmation");
            check(r.input.current() == null, "clear follows send");
            check(!r.input.cancelVisual(1, r.epoch, r::sendAbort), "duplicate cosmetic loss ignored");
        });
        scenario("failed transport retains cancellation ownership for one successful retry", () -> {
            Rig r = new Rig(); r.press(); r.preview(1, 100); r.failAbort = true;
            try { r.input.cancelVisual(1, r.epoch, r::sendAbort); throw new AssertionError("missing injected failure"); }
            catch (IllegalStateException expected) { check(r.input.current() != null, "failed send retains identity"); }
            r.input.cancelVisual(1, r.epoch, r::sendAbort); equal(r.aborts(), 1L, "retry sends once");
        });
        scenario("real ENDED and REFUSED are no-wire even after release and stale cosmetic callbacks", () -> {
            for (int phase = 0; phase < 3; phase++) {
                Rig r = new Rig(); r.press(); if (phase > 0) r.preview(1, 100);
                r.release(); if (phase == 2) { r.press(); r.release(); }
                long latest = r.input.current().input(); r.end(latest); r.end(latest);
                r.input.cancelVisual(1, r.epoch, r::sendAbort); r.tick();
                equal(r.aborts(), 0L, "terminal resolution never re-aborts");
            }
        });
        scenario("missing acknowledgements and quiet receipts are finite", () -> {
            Rig pending = new Rig(); pending.press(); pending.release();
            for (int i = 1; i < SafeLandingProbeInputState.ACK_TIMEOUT_TICKS; i++) pending.tick();
            equal(pending.aborts(), 1L, "pending ack timeout once");
            Rig accepted = new Rig(); accepted.press(); accepted.preview(1, 100); accepted.release();
            for (int i = 1; i < SafeLandingProbeInputState.QUIET_TICKS; i++) accepted.tick();
            equal(accepted.aborts(), 1L, "quiet receipt lease abort once");
        });
        scenario("heartbeat cannot renew the frozen hundred-tick deadline", () -> {
            Rig r = new Rig(); r.press(); r.preview(1, 100);
            for (int i = 0; i < 100; i++) { r.preview(1, 100); r.tick(); }
            equal(r.aborts(), 1L, "native lifetime expires despite malformed renewing heartbeats");
        });
        scenario("snapshot and new session fences never replay a held key", () -> {
            Rig r = new Rig(); r.press(); r.preview(1, 100); r.binding.modernSnapshotFallback(); r.tick();
            equal(r.wire.size(), 1, "silent snapshot does not abort or repeat");
            r.release(); r.press(); equal(r.confirmRequests, 1, "new physical edge still confirms");
            r.end(2); r.binding.discard(); r.input.reset(); r.world = new Object(); r.player = new Object();
            r.connection = new Object(); r.session++; r.epoch++; r.install(); r.binding.establishSessionFence(); r.tick();
            equal(r.wire.size(), 2, "new session held key fenced");
            r.release(); r.press(); equal(r.previewRequests, 2, "new session fresh press starts new preview");
        });
        scenario("pause freezes cosmetic deadlines but GUI still cancels", () -> {
            Rig r = new Rig(); r.press(); r.preview(1, 100); r.paused = true;
            for (int i = 0; i < 200; i++) r.tick(); equal(r.aborts(), 0L, "pause preserves lease");
            r.screen = true; r.tick(); equal(r.aborts(), 1L, "GUI loss wins over pause");
        });
        System.out.println("SafeLandingProbeInputRegressionTest: " + scenarios + " scenarios, " + checks + " checks passed");
    }
}

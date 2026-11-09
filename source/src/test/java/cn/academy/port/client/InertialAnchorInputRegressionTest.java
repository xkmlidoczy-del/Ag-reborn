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

/** Executes the production ownership helper through the actual classic binding and runtime coordinator. */
public final class InertialAnchorInputRegressionTest {
    private static final String ID = "inertial_anchor";
    private static int checks, scenarios;
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    private static void equal(Object actual, Object expected, String message) {
        check(Objects.equals(actual, expected), message + ": " + actual + " != " + expected);
    }
    private static void scenario(String name, Runnable body) {
        body.run(); scenarios++; System.out.println("PASS " + name);
    }
    private record Loss(String name, Consumer<Rig> apply) {}

    private static final class Rig implements ClassicClientInputBinding.Host {
        final InertialAnchorInputState input = new InertialAnchorInputState();
        ClassicClientInputBinding binding;
        Object world = new Object(), player = new Object(), connection = new Object();
        Object boundWorld = world, boundPlayer = player, boundConnection = connection;
        long session = 1, epoch = 71, revision = 1, counter;
        int actor = 19, preset, selectedSlot, cooldown, starts, offTaps, abortCallbacks, releaseCallbacks;
        boolean inGame = true, category = true, activated = true, overload = true, interfering, consuming;
        boolean learned = true, enabled = true, screen, focused = true, camera = true, mapped = true, paused;
        String mappings = "inertial-anchor-original";
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
        public boolean canUseAbility() { return activated && overload && !interfering && !consuming; }
        public boolean terminalOpen() { return screen; }
        public int actorId() { return actor; }
        public long ownerEpoch() { return epoch; }
        public boolean physicalDown(int key) { return physical.contains(key); }
        public boolean registeredMapping(String skill) { return ID.equals(skill); }
        public boolean eligible(ClassicClientInputBinding.Node node) { return input.eligible(gates(node.slot, node.key)); }
        public String mappedSkill(int slot) { return slot == selectedSlot ? ID : ""; }
        public int mappedKey(int slot) { return keys[slot]; }
        public int slotCount() { return 4; }
        public void rawActivation(boolean desired) { activated = desired; cancel(input.cancel()); }
        public void replaceOverrides(int[] keys) {}
        public long down(ClassicClientInputBinding.Node node) {
            if (paused) return 0;
            var identity = new HighPowerInput.Input(node.slot, counter + 1, node.actorId(), node.ownerEpoch());
            boolean off = input.acceptedIdentity() != null;
            var press = input.press(identity, gates(node.slot, node.key));
            if (press == null) return 0;
            if (press.dispatch()) {
                counter++;
                wire.add("toggle:" + press.identity().wire());
                if (off) offTaps++; else starts++;
            }
            return press.identity().input();
        }
        public void up(ClassicClientInputBinding.Node node, long value) {
            releaseCallbacks++;
            input.release(new HighPowerInput.Input(node.slot, value, node.actorId(), node.ownerEpoch()));
        }
        public void abort(ClassicClientInputBinding.Node node, long value) {
            abortCallbacks++;
            cancel(input.cancel(new HighPowerInput.Input(node.slot, value, node.actorId(), node.ownerEpoch())));
        }
        public boolean cancelPending(ClassicClientInputBinding.Node node, long value) { return false; }
        public void direction(ClassicClientInputBinding.ContextIdentity context, int direction,
                              ClassicClientInputBinding.Edge edge, boolean silent) {}
        public void localContextEnd(ClassicClientInputBinding.ContextIdentity context) {}
        public void contextAbort(TargetedContextTermination.Request request) { throw new AssertionError("No classic context route"); }
        public void modernCleanup(Collection<ClassicClientInputBinding.Node> defaults) {}
        InertialAnchorInputState.Gates gates(int slot, int key) {
            var context = new InertialAnchorInputState.DispatchContext(world, player, connection, session,
                    actor, epoch, preset, revision, mappings);
            return new InertialAnchorInputState.Gates(context, slot, key, available() && inGame,
                    categoryActivated(), canUseAbility(), category && learned, enabled,
                    mapped && selectedSlot == slot, !screen, focused, camera, cooldown == 0);
        }
        void tick() {
            var current = input.current();
            if (current != null) cancel(input.tick(gates(current.slot(), keys[current.slot()]), paused));
            binding.tickStart();
        }
        void press() { physical.add(keys[selectedSlot]); tick(); }
        void release() { physical.remove(keys[selectedSlot]); tick(); }
        void accept(long value) { input.accepted(value, epoch); }
        void end(long value) { input.ended(value, epoch); }
        void cancel(HighPowerInput.Input identity) {
            if (identity == null) return;
            hidden.add(identity);
            if (identity.actor() == actor && identity.epoch() == epoch) wire.add("abort:" + identity.wire());
        }
        long aborts() { return wire.stream().filter(value -> value.startsWith("abort:")).count(); }
    }

    private static boolean retainedDeathOrder(List<String> calls) {
        int discard = calls.indexOf("discardBinding");
        return discard >= 3 && calls.get(discard - 3).equals("cancelInertialAnchorBeforeDiscard")
                && calls.get(discard - 2).equals("cancelSafeLandingProbeBeforeDiscard")
                && calls.get(discard - 1).equals("cancelZeroGapArmorBeforeDiscard");
    }
    private static void verifyDeathCleanupOrdering() throws Exception {
        var node = new org.objectweb.asm.tree.ClassNode();
        try (var in = InertialAnchorInputRegressionTest.class.getClassLoader().getResourceAsStream(
                "cn/academy/port/client/AcademyClient.class")) {
            check(in != null, "actual compiled client is available");
            new org.objectweb.asm.ClassReader(in).accept(node, 0);
        }
        boolean verified = false;
        for (var method : node.methods) if (method.name.equals("tick")) {
            List<String> calls = new ArrayList<>();
            for (var instruction : method.instructions)
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call && call.owner.equals(node.name)) calls.add(call.name);
            check(retainedDeathOrder(calls), "NEW422 cancels before unchanged adjacent NEW313/NEW252 cleanup");
            int discard = calls.indexOf("discardBinding");
            java.util.Collections.swap(calls, discard - 3, discard - 2);
            check(!retainedDeathOrder(calls), "old interposed NEW422 ordering is rejected");
            verified = true;
        }
        check(verified, "compiled tick death branch inspected");
    }

    public static void main(String[] args) throws Exception {
        verifyDeathCleanupOrdering();
        scenario("cooldown installed before visual acknowledgement preserves every held slot", () -> {
            for (int slot = 0; slot < 4; slot++) {
                Rig r = new Rig(slot); r.press();
                equal(r.starts, 1, "one start");
                check(r.binding.runtime() instanceof ClassicClientRuntimeCoordinator, "real runtime");
                equal(ClassicClientInputBinding.kind(ID), ClassicClientInputBinding.Kind.SINGLE_KEY, "ordinary classic callback class");
                r.cooldown = 60;
                for (int tick = 0; tick < 20; tick++) r.tick();
                equal(r.wire.size(), 1, "no held repeat or cooldown abort before acknowledgement");
                equal(r.abortCallbacks, 0, "eligibility prevents coordinator abort");
                equal(r.input.pendingCount(), 1, "one bounded pending input");
                check(r.input.acceptedIdentity() == null, "pending is not a predicted active state");
                r.release();
                equal(r.releaseCallbacks, 1, "real SINGLE_KEY release reaches production helper");
                equal(r.wire.size(), 1, "release emits no termination");
                r.accept(1); check(r.input.acceptedIdentity() != null, "late matching receipt accepted after release");
                r.press(); equal(r.offTaps, 1, "fresh off edge allowed during own cooldown");
                equal(r.starts, 1, "off does not become another start dispatch");
                for (int tick = 0; tick < 20; tick++) { r.accept(1); r.tick(); }
                equal(r.wire.size(), 2, "held off input neither repeats nor aborts");
                check(r.input.permits(1, r.epoch), "original accepted identity remains until its server end");
                r.release(); equal(r.wire.size(), 2, "off release also inert");
                r.end(1); check(r.input.current() == null, "original end resolves pending off");
                check(!r.input.eligible(r.gates(slot, r.keys[slot])), "ended identity gives no cooldown bypass");
            }
        });
        scenario("pending release and repress never send an unacknowledged duplicate", () -> {
            Rig r = new Rig(); r.press(); r.cooldown = 60; r.release(); r.press();
            equal(r.wire.size(), 1, "no second start before acknowledgement");
            equal(r.input.pendingCount(), 1, "pending remains bounded");
            r.release(); r.accept(1); r.press(); equal(r.offTaps, 1, "new accepted control can then close");
        });
        scenario("suppressed pending repress retains the identity for explicit classic V abort", () -> {
            Rig r = new Rig(); r.press(); r.cooldown = 60; r.release(); r.press();
            equal(r.wire.size(), 1, "repress sends no duplicate start");
            r.binding.activateKeyDown();
            equal(r.aborts(), 1L, "V aborts the retained original pending identity");
            equal(r.hidden.getFirst().input(), 1L, "abort identifies the actual original request");
            check(r.input.current() == null, "explicit abort clears pending state");
            r.tick(); equal(r.aborts(), 1L, "held key cannot replay cancellation");
        });
        List<Loss> losses = List.of(
                new Loss("GUI", r -> r.screen = true), new Loss("focus", r -> r.focused = false),
                new Loss("camera", r -> r.camera = false), new Loss("category", r -> r.category = false),
                new Loss("activation", r -> r.activated = false), new Loss("overload", r -> r.overload = false),
                new Loss("interference", r -> r.interfering = true), new Loss("consumption", r -> r.consuming = true),
                new Loss("learned/level", r -> r.learned = false), new Loss("configuration", r -> r.enabled = false),
                new Loss("mapping", r -> r.mapped = false), new Loss("slot", r -> r.selectedSlot = 1),
                new Loss("preset", r -> r.preset++), new Loss("revision", r -> r.revision++),
                new Loss("mapping signature", r -> r.mappings = "changed"), new Loss("key", r -> r.keys[0] = 30),
                new Loss("actor", r -> r.actor++), new Loss("owner epoch", r -> r.epoch++),
                new Loss("world", r -> r.world = new Object()), new Loss("player", r -> r.player = new Object()),
                new Loss("connection", r -> r.connection = new Object()), new Loss("local session", r -> r.session++),
                new Loss("death/spectator", r -> r.inGame = false));
        scenario("all non-cooldown gates cancel both pending and accepted control before and after release", () -> {
            for (Loss loss : losses) for (boolean accepted : new boolean[]{false, true}) for (boolean released : new boolean[]{false, true}) {
                Rig r = new Rig(); r.press(); r.cooldown = 60; if (accepted) r.accept(1); if (released) r.release();
                var identity = r.input.current(); loss.apply.accept(r); r.tick();
                String where = loss.name + "/accepted=" + accepted + "/released=" + released;
                check(r.input.current() == null, "ownership closed: " + where);
                equal(r.hidden, List.of(identity), "matching local cancellation once: " + where);
                check(!r.input.permits(1, identity.epoch()), "late acknowledgement fenced: " + where);
                int messages = r.wire.size(); r.tick(); equal(r.wire.size(), messages, "no duplicate abort: " + where);
                if (r.actor == identity.actor() && r.epoch == identity.epoch()) equal(r.aborts(), 1L, "authenticated abort: " + where);
                else equal(r.aborts(), 0L, "never transmit old actor/epoch identity: " + where);
            }
        });
        scenario("no initial non-cooldown gate can be bypassed by cooldown", () -> {
            for (Loss loss : losses) {
                Rig r = new Rig(); loss.apply.accept(r); r.cooldown = 60; r.press();
                equal(r.wire.size(), 0, "blocked start: " + loss.name);
            }
        });
        scenario("failed acknowledgement expires once and sends matching authenticated abort", () -> {
            for (boolean released : new boolean[]{false, true}) {
                Rig r = new Rig(); r.press(); r.cooldown = 60;
                int elapsed = 0; if (released) { r.release(); elapsed++; }
                for (; elapsed < InertialAnchorInputState.ACK_TIMEOUT_TICKS - 1; elapsed++) r.tick();
                equal(r.aborts(), 0L, "pending lives only until the bounded deadline");
                check(r.input.acceptedIdentity() == null, "no fake accepted context");
                r.tick(); equal(r.aborts(), 1L, "timeout abort exactly once");
                equal(r.hidden.getFirst().input(), 1L, "original authenticated input is aborted");
                check(r.input.current() == null, "timeout ownership removed");
                r.accept(1); check(!r.input.permits(1, r.epoch), "late accepted receipt cannot revive timeout");
                r.tick(); equal(r.aborts(), 1L, "no timeout replay");
                r.release(); r.press(); equal(r.starts, 1, "cooldown still blocks a new activation");
            }
        });
        scenario("old accepted receipts cannot extend a lost off acknowledgement", () -> {
            Rig r = new Rig(); r.press(); r.accept(1); r.release(); r.cooldown = 60; r.press();
            for (int tick = 0; tick < InertialAnchorInputState.ACK_TIMEOUT_TICKS; tick++) { r.accept(1); r.tick(); }
            equal(r.aborts(), 1L, "off timeout abort");
            equal(r.hidden.getFirst().input(), 2L, "latest authenticated input fences the accepted older activation");
            check(r.input.current() == null, "off acknowledgement timeout clears control");
        });
        scenario("accepted control also expires when all receipt and end traffic disappears", () -> {
            Rig r = new Rig(); r.press(); r.accept(1); r.cooldown = 60;
            for (int tick = 0; tick < InertialAnchorInputState.RECEIPT_TIMEOUT_TICKS; tick++) r.tick();
            equal(r.aborts(), 1L, "lost receipt lease abort");
            check(!r.input.eligible(r.gates(0, r.keys[0])), "expired acceptance cannot bypass cooldown");
        });
        scenario("duplicates reordered ends and stale callbacks cannot erase a newer request", () -> {
            Rig r = new Rig(); r.press(); r.accept(1); r.accept(1); r.release();
            r.input.ended(1, r.epoch + 1); r.end(99); check(r.input.permits(1, r.epoch), "wrong ends ignored");
            r.end(1); r.press(); check(r.input.permits(2, r.epoch), "new independent start captured");
            r.end(1); r.accept(1); check(r.input.permits(2, r.epoch), "old duplicate traffic cannot resolve new pending");
            check(r.input.acceptedIdentity() == null, "stale acceptance does not become active");
            check(r.input.cancel(new HighPowerInput.Input(0, 1, r.actor, r.epoch)) == null, "stale delegate abort ignored");
            r.accept(2); r.end(1); check(r.input.permits(2, r.epoch), "old end cannot erase new accepted control");
            r.end(2); r.end(2); check(r.input.current() == null, "matching duplicate end is idempotent");
            check(!r.input.begin(new HighPowerInput.Input(0, 2, r.actor, r.epoch), r.gates(0, r.keys[0])), "reused input fenced");
        });
        scenario("silent snapshot rebuild and physical key fencing preserve toggle ownership", () -> {
            Rig r = new Rig(); r.press(); r.cooldown = 60; r.binding.modernSnapshotFallback();
            for (int tick = 0; tick < 10; tick++) r.tick();
            equal(r.wire.size(), 1, "snapshot rebuild sends no callback abort or repeat");
            r.accept(1); r.release(); r.press(); equal(r.offTaps, 1, "release-repress clears the classic fence");
            r.screen = true; r.tick(); equal(r.aborts(), 1L, "GUI still closes original accepted toggle after rebuild");
        });
        scenario("actual session discard neither replays held input nor sends stale termination", () -> {
            Rig r = new Rig(); r.press(); r.accept(1); r.binding.discard(); r.input.reset();
            r.world = new Object(); r.player = new Object(); r.connection = new Object(); r.session++; r.epoch++;
            r.install(); r.binding.establishSessionFence(); r.tick(); equal(r.wire.size(), 1, "held key fenced across session");
            check(!r.input.permits(1, r.epoch - 1), "old receipt rejected");
            r.release(); r.press(); equal(r.starts, 2, "fresh physical edge uses the new session");
        });
        scenario("paused clock remains bounded by resumed ticks and explicit GUI cancellation", () -> {
            Rig r = new Rig(); r.press(); r.cooldown = 60; r.paused = true;
            for (int tick = 0; tick < 200; tick++) r.tick(); equal(r.aborts(), 0L, "pause does not consume timeout");
            r.screen = true; r.tick(); equal(r.aborts(), 1L, "screen cancels even while paused");
        });
        scenario("visual loss dispatches exact ownership before clearing and retains failed dispatch", () -> {
            Rig r = new Rig(); r.press(); r.accept(1); r.release(); r.press();
            check(!r.input.cancelVisual(1, r.epoch + 1, ignored -> { throw new AssertionError("wrong epoch sent"); }), "wrong visual epoch ignored");
            try {
                r.input.cancelVisual(1, r.epoch, identity -> {
                    equal(identity.input(), 2L, "accepted receipt loss aborts latest pending off");
                    check(r.input.current() == identity, "input still owned at transport call");
                    throw new IllegalStateException("injected transport failure");
                });
                throw new AssertionError("missing injected failure");
            } catch (IllegalStateException expected) {
                check(r.input.current() != null, "failed transport retains exact cancellation identity");
            }
            check(r.input.cancelVisual(1, r.epoch, r::cancel), "retry remains allowed");
            equal(r.aborts(), 1L, "successful retry sends once");
            check(r.input.current() == null, "ownership clears after successful send");
            check(!r.input.cancelVisual(1, r.epoch, r::cancel), "duplicate cosmetic loss sends nothing");
        });
        scenario("old cancellation callback cannot clear an owner created during dispatch", () -> {
            Rig r = new Rig(); r.press(); r.accept(1);
            var next = new HighPowerInput.Input(0, 2, r.actor, r.epoch);
            check(r.input.cancelBeforeClear(old -> {
                r.input.cancel();
                check(r.input.begin(next, r.gates(0, r.keys[0])), "new owner created during callback");
            }), "old abort completed");
            check(r.input.current() == next && r.input.permits(2, r.epoch), "identity comparison preserves newer ownership");
        });
        System.out.println("InertialAnchorInputRegressionTest: " + scenarios + " scenarios, " + checks + " checks passed");
    }
}

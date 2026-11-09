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
public final class CirculationReliefInputRegressionTest {
    private static final String ID = "circulation_relief";
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
        final CirculationReliefInputState input = new CirculationReliefInputState();
        ClassicClientInputBinding binding;
        Object world = new Object(), player = new Object(), connection = new Object();
        Object boundWorld = world, boundPlayer = player, boundConnection = connection;
        long session = 1, epoch = 71, revision = 1, counter;
        int actor = 19, preset, selectedSlot, cooldown, starts, offTaps, abortCallbacks, releaseCallbacks;
        boolean inGame = true, category = true, activated = true, overload = true, interfering, consuming;
        boolean learned = true, enabled = true, screen, focused = true, camera = true, mapped = true, paused;
        String mappings = "circulation-relief-original";
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
        CirculationReliefInputState.Gates gates(int slot, int key) {
            var context = new CirculationReliefInputState.DispatchContext(world, player, connection, session,
                    actor, epoch, preset, revision, mappings);
            return new CirculationReliefInputState.Gates(context, slot, key, available() && inGame,
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

    public static void main(String[] args) {
        scenario("cooldown does not abort pending or accepted toggle, release is free", () -> {
            for (int slot = 0; slot < 4; slot++) {
                Rig r = new Rig(slot); r.press(); r.cooldown = 60;
                for (int i = 0; i < 20; i++) r.tick();
                equal(r.starts, 1, "one initial start"); equal(r.aborts(), 0L, "server cooldown does not self-abort");
                r.release(); equal(r.wire.size(), 1, "key up is free");
                r.accept(1); r.press(); equal(r.offTaps, 1, "accepted off tap bypasses own cooldown");
                r.release(); equal(r.wire.size(), 2, "off release is free");
                r.end(1); check(r.input.current() == null, "original authoritative end resolves pending off");
                check(!r.input.eligible(r.gates(slot, r.keys[slot])), "ended identity has no cooldown bypass");
            }
        });
        scenario("long network silence never sends destructive abort or prevents matching recovery", () -> {
            for (boolean accepted : new boolean[]{false, true}) {
                Rig r = new Rig(); r.press(); r.cooldown = 60; r.release(); if (accepted) r.accept(1);
                for (int i = 0; i < 1000; i++) r.tick();
                equal(r.wire.size(), 1, "no timeout abort");
                check(r.input.current() != null && r.input.permits(1, r.epoch), "exact owner retained for late receipt");
                r.accept(1); check(r.input.acceptedIdentity() != null, "late server acknowledgement recovers");
                r.press(); equal(r.offTaps, 1, "off remains available after lag");
                for (int i = 0; i < 1000; i++) { r.accept(1); r.tick(); }
                equal(r.wire.size(), 2, "old accepted heartbeat never repeats off or aborts pending off");
                r.end(1); check(r.input.current() == null, "server end remains terminal after lag");
            }
        });
        scenario("pending re-press cannot invent duplicate toggle and V abort uses original identity", () -> {
            Rig r = new Rig(); r.press(); r.cooldown = 60; r.release(); r.press();
            equal(r.wire.size(), 1, "unacknowledged re-press suppressed"); equal(r.input.pendingCount(), 1, "bounded pending input");
            r.binding.activateKeyDown(); equal(r.aborts(), 1L, "explicit V cancellation is sent");
            equal(r.hidden.getFirst().input(), 1L, "exact pending identity canceled");
            check(!r.input.permits(1, r.epoch), "late receipt cannot restore explicit cancel");
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
        scenario("explicit invalidity still cancels pending and accepted control after release and lag", () -> {
            for (Loss loss : losses) for (boolean accepted : new boolean[]{false, true}) for (boolean paused : new boolean[]{false, true}) {
                Rig r = new Rig(); r.press(); r.cooldown = 60; r.release(); if (accepted) r.accept(1);
                for (int i = 0; i < 80; i++) r.tick();
                var identity = r.input.current(); r.paused = paused; loss.apply.accept(r); r.tick();
                String where = loss.name + "/accepted=" + accepted + "/paused=" + paused;
                check(r.input.current() == null, "ownership closed: " + where);
                equal(r.hidden, List.of(identity), "matching local cancel once: " + where);
                check(!r.input.permits(1, identity.epoch()), "late acknowledgement fenced: " + where);
                int messages = r.wire.size(); r.tick(); equal(r.wire.size(), messages, "no repeated cancel: " + where);
            }
        });
        scenario("invalid initial gates cannot dispatch", () -> {
            for (Loss loss : losses) { Rig r = new Rig(); loss.apply.accept(r); r.cooldown = 60; r.press(); equal(r.wire.size(), 0, "blocked " + loss.name); }
        });
        scenario("authenticated rejection closes only pending request and preserves accepted owner", () -> {
            Rig r = new Rig(); r.press(); r.input.rejected(1, r.epoch);
            check(r.input.current() == null, "rejected start releases pending gate");
            r.release(); r.press(); r.accept(2); r.release(); r.cooldown = 60;
            r.input.rejected(2, r.epoch);
            check(r.input.permits(2, r.epoch), "late rejection cannot destroy accepted controller");
            r.press(); r.input.rejected(3, r.epoch);
            check(r.input.acceptedIdentity().input() == 2 && r.input.pendingCount() == 0, "rejected off preserves prior accepted toggle");
            r.release(); r.press(); equal(r.offTaps, 2, "fresh off edge remains available after rejection");
        });
        scenario("old end cannot clear newer server ownership", () -> {
            Rig r = new Rig(); r.press(); r.accept(1); r.release(); r.end(1);
            r.press(); r.accept(2); r.end(1);
            check(r.input.permits(2, r.epoch), "late end fenced by exact input");
            check(!r.input.permits(1, r.epoch), "old acknowledgement rejected");
            var current = r.input.current();
            check(r.input.cancelBeforeClear(value -> {
                equal(value, current, "abort captures exact owner before clearing");
                check(r.input.current() == current, "owner remains during send");
            }), "explicit clear sends once");
            check(r.input.current() == null, "owner cleared after send");
        });
        System.out.println("CirculationReliefInputRegressionTest: " + checks + " checks in " + scenarios + " scenarios passed");
    }
}

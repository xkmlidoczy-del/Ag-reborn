package cn.academy.port.client;

import cn.academy.port.core.TargetedContextTermination;
import cn.academy.port.highpower.HighPowerInput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** The actual classic binding/coordinator drives the production fresh-edge/Post-dispatch state. */
public final class DeflectionStrikeInputRegressionTest {
    private static final String ID = "deflection_strike";
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private record Loss(String name, Consumer<Rig> action) {}
    private static final class Rig implements ClassicClientInputBinding.Host {
        final DeflectionStrikeInputState input = new DeflectionStrikeInputState();
        ClassicClientInputBinding binding;
        Object world = new Object(), player = new Object(), connection = new Object();
        Object boundWorld = world, boundPlayer = player, boundConnection = connection;
        long session = 1, epoch = 71, revision = 1, counter;
        int actor = 19, preset, selectedSlot, cooldown;
        boolean inGame = true, category = true, activated = true, usable = true, learned = true, enabled = true;
        boolean screen, focused = true, camera = true, mapped = true, paused, sourceReady = true, oldEligibility;
        String mappings = "tp-original";
        final int[] keys = {-100, -99, 19, 33};
        final Set<Integer> physical = new HashSet<>();
        final List<String> wire = new ArrayList<>();
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
        public boolean eligible(ClassicClientInputBinding.Node node) {
            return oldEligibility ? input.eligible(gates(node.slot, node.key)) : input.delegateEligible(gates(node.slot, node.key));
        }
        public String mappedSkill(int slot) { return slot == selectedSlot ? ID : ""; }
        public int mappedKey(int slot) { return keys[slot]; }
        public int slotCount() { return 4; }
        public void rawActivation(boolean desired) { activated = desired; input.cancelBeforeClear(this::abort); }
        public void replaceOverrides(int[] keys) {}
        public long down(ClassicClientInputBinding.Node node) {
            if (paused) return 0;
            var candidate = new HighPowerInput.Input(node.slot, counter + 1, node.actorId(), node.ownerEpoch());
            return input.press(candidate, gates(node.slot, node.key)) ? ++counter : 0;
        }
        public void up(ClassicClientInputBinding.Node node, long value) {
            input.release(new HighPowerInput.Input(node.slot, value, node.actorId(), node.ownerEpoch()));
        }
        public void abort(ClassicClientInputBinding.Node node, long value) {
            input.cancel(new HighPowerInput.Input(node.slot, value, node.actorId(), node.ownerEpoch()), this::abort);
        }
        public boolean cancelPending(ClassicClientInputBinding.Node node, long value) { return false; }
        public void direction(ClassicClientInputBinding.ContextIdentity context, int direction, ClassicClientInputBinding.Edge edge, boolean silent) {}
        public void localContextEnd(ClassicClientInputBinding.ContextIdentity context) {}
        public void contextAbort(TargetedContextTermination.Request request) { throw new AssertionError("No classic deflection route"); }
        public void modernCleanup(Collection<ClassicClientInputBinding.Node> defaults) {}
        DeflectionStrikeInputState.Gates gates(int slot, int key) {
            var context = new DeflectionStrikeInputState.DispatchContext(world, player, connection, session, actor, epoch, preset, revision, mappings);
            return new DeflectionStrikeInputState.Gates(context, slot, key, available() && inGame, categoryActivated(),
                    canUseAbility(), category && learned, enabled, mapped && selectedSlot == slot, !screen, focused, camera, cooldown == 0, sourceReady);
        }
        void pre() {
            var current = input.current();
            if (current != null) {
                input.releaseWhenUp(physicalDown(input.physicalKey()));
                input.tick(gates(current.slot(), keys[current.slot()]), paused, this::abort);
            }
            binding.tickStart();
        }
        void post() {
            binding.finishLocalDisposals();
            var queued = input.queuedStart();
            if (queued != null) input.flushPost(gates(queued.slot(), keys[queued.slot()]), id -> wire.add("start:" + id.wire()), this::abort);
        }
        void press() { physical.add(keys[selectedSlot]); pre(); }
        void release() { physical.remove(keys[selectedSlot]); pre(); }
        void abort(HighPowerInput.Input identity) { wire.add("abort:" + identity.wire()); }
        long starts() { return wire.stream().filter(s -> s.startsWith("start:")).count(); }
    }
    public static void main(String[] args) {
        for (int slot = 0; slot < 4; slot++) {
            Rig r = new Rig(slot); r.press();
            check(r.binding.runtime() instanceof ClassicClientRuntimeCoordinator, "actual coordinator");
            check(ClassicClientInputBinding.kind(ID) == ClassicClientInputBinding.Kind.SINGLE_KEY, "old binding kind preserved");
            check(r.wire.isEmpty() && r.input.queuedStart() != null, "Pre never transmits before native movement");
            r.release(); check(r.wire.isEmpty(), "release before Post sends nothing");
            r.post(); check(r.starts() == 1 && r.wire.size() == 1, "down/up before Post produces exactly one start");
            check(r.wire.getFirst().equals("start:" + slot + ":1:19:71"), "wire contains identity only, no crouch or aim");
            for (int tick = 0; tick < 5; tick++) { r.pre(); r.post(); }
            check(r.starts() == 1, "no replay on later Post");
            check(r.input.permits(1, r.epoch), "normal release retains receipt admission");
            r.input.ended(1, r.epoch); r.press(); r.post();
            check(r.starts() == 2, "next fresh edge dispatches once");
            for (int tick = 0; tick < 20; tick++) { r.pre(); r.post(); }
            check(r.starts() == 2, "hold has no repeat or continuing movement");
            r.input.ended(2, r.epoch); r.binding.modernSnapshotFallback(); r.pre(); r.post();
            check(r.starts() == 2, "snapshot after success cannot replay held physical edge");
            r.release(); r.press(); r.post(); check(r.starts() == 3, "physical release survives silent delegate replacement");
        }
        for (boolean releaseOnArrival : new boolean[]{false, true}) {
            Rig r = new Rig(2); r.press(); r.post(); r.cooldown = 10;
            if (releaseOnArrival) r.release(); else r.pre();
            check(r.wire.equals(List.of("start:2:1:19:71")) && r.input.permits(1,71),
                    "own cooldown preserves actual delegate on held/released arrival, release=" + releaseOnArrival);
            for (int tick = 0; tick < DeflectionStrikeState.LIFE_TICKS; tick++) { r.pre(); r.post(); }
            check(r.wire.size() == 1 && r.input.permits(1,71), "receipt remains admitted across full visual lifetime without another tap/charge");
            check(!r.input.eligible(r.gates(2,19)) && !r.input.press(new HighPowerInput.Input(2,2,19,71),r.gates(2,19)),
                    "preserving delegate cannot bypass new-press cooldown");
            if (!releaseOnArrival) r.release();
            r.press(); r.post(); check(r.wire.size() == 1 && r.input.pendingCount() == 1, "fresh physical edge during cooldown sends no duplicate tap");
            r.release(); r.input.ended(1,71);
            check(r.input.current() == null && !r.input.permits(1,71), "terminal receipt retires released input normally");
            r.cooldown = 0; r.press(); r.post(); check(r.starts() == 2, "fresh edge after cooldown still works");

            // Retain the precise packaged .51 failure as a mutation witness, through the compiled coordinator.
            Rig old = new Rig(2); old.oldEligibility = true; old.press(); old.post(); old.cooldown = 10;
            if (releaseOnArrival) old.release(); else old.pre();
            check(old.wire.equals(List.of("start:2:1:19:71", "abort:2:1:19:71")) && old.input.pendingCount() == 0,
                    "old eligibility reproduces self-abort and missing effect, release=" + releaseOnArrival);
        }
        List<Loss> losses = List.of(new Loss("GUI", r -> r.screen = true), new Loss("focus", r -> r.focused = false),
                new Loss("camera", r -> r.camera = false), new Loss("death", r -> r.inGame = false),
                new Loss("category", r -> r.category = false), new Loss("activation", r -> r.activated = false),
                new Loss("ability", r -> r.usable = false), new Loss("learned", r -> r.learned = false),
                new Loss("enabled", r -> r.enabled = false), new Loss("mapped", r -> r.mapped = false),
                new Loss("slot", r -> r.selectedSlot++), new Loss("preset", r -> r.preset++),
                new Loss("revision", r -> r.revision++), new Loss("mapping signature", r -> r.mappings = "changed"),
                new Loss("physical mapping", r -> r.keys[0]++), new Loss("actor", r -> r.actor++),
                new Loss("owner epoch", r -> r.epoch++), new Loss("session", r -> r.session++),
                new Loss("world", r -> r.world = new Object()), new Loss("player", r -> r.player = new Object()),
                new Loss("connection", r -> r.connection = new Object()));
        for (Loss loss : losses) for (boolean released : new boolean[]{false, true}) {
            Rig r = new Rig(0); r.press(); if (released) r.release();
            loss.action.accept(r); r.post();
            check(r.starts() == 0 && r.input.current() == null, "between-Pre/Post loss discards queued start: " + loss.name);
            int sent = r.wire.size(); r.post(); check(r.wire.size() == sent, "cancel is one-shot: " + loss.name);
            Rig pending = new Rig(0); pending.press(); pending.post(); pending.release();
            loss.action.accept(pending); pending.pre();
            check(!pending.input.permits(1, pending.epoch), "released pending receipt fenced on control loss: " + loss.name);
            Rig cooling = new Rig(0); cooling.press(); cooling.post(); cooling.cooldown = 10;
            if (released) cooling.release();
            loss.action.accept(cooling); cooling.pre();
            check(cooling.input.current() == null && !cooling.input.permits(1,71),
                    "own cooldown never masks genuine category/unbind/death/disconnect/control loss: " + loss.name);
            int afterLoss = cooling.wire.size(); cooling.pre(); cooling.post();
            check(cooling.wire.size() == afterLoss, "real invalidation is one-shot while cooling: " + loss.name);
        }
        Rig queuedSourceLoss = new Rig(0); queuedSourceLoss.press(); queuedSourceLoss.sourceReady = false;
        queuedSourceLoss.pre(); queuedSourceLoss.post();
        check(queuedSourceLoss.starts() == 0 && queuedSourceLoss.input.current() == null, "queued source ground/motion loss cancels before dispatch");
        Rig sentSourceLoss = new Rig(0); sentSourceLoss.press(); sentSourceLoss.post(); sentSourceLoss.release();
        sentSourceLoss.sourceReady = false; sentSourceLoss.cooldown = 30; sentSourceLoss.pre();
        check(sentSourceLoss.input.permits(1,71) && sentSourceLoss.input.ownsControl(sentSourceLoss.gates(0,-100)),
                "sent authentic receipt survives native post-move ground/motion change and its cooldown");
        sentSourceLoss.press(); sentSourceLoss.post();
        check(sentSourceLoss.starts() == 1, "ground loss permits no new airborne start");
        sentSourceLoss.input.ended(1,71); check(!sentSourceLoss.input.permits(1,71), "success still terminal after source changes");
        Rig sentGuiLoss = new Rig(0); sentGuiLoss.press(); sentGuiLoss.post(); sentGuiLoss.sourceReady = false; sentGuiLoss.screen = true; sentGuiLoss.pre();
        check(sentGuiLoss.input.current() == null, "GUI loss still cancels sent request regardless of later source state");
        Rig cooldown = new Rig(0); cooldown.press(); cooldown.cooldown = 10; cooldown.post();
        check(cooldown.starts() == 0, "cooldown appearing before Post cancels unsent start");
        Rig snapshot = new Rig(0); snapshot.press(); snapshot.binding.modernSnapshotFallback(); snapshot.release(); snapshot.post();
        check(snapshot.starts() == 1, "same-tick snapshot/down-up preserves single queued start");
        Rig timeout = new Rig(0); timeout.press(); timeout.post(); timeout.release();
        for (int i = 0; i < DeflectionStrikeInputState.ACK_TIMEOUT_TICKS; i++) timeout.pre();
        check(timeout.input.current() == null && !timeout.input.permits(1, 71), "missing receipt has bounded ownership");
        Rig newSession = new Rig(0); newSession.press(); newSession.input.reset(); newSession.binding.discard();
        newSession.world = new Object(); newSession.epoch++; newSession.install(); newSession.binding.establishSessionFence();
        newSession.pre(); newSession.post(); check(newSession.starts() == 0, "replacement session cannot inherit held edge");
        newSession.release(); newSession.press(); newSession.post(); check(newSession.starts() == 1, "fresh edge works after session fence");
        System.out.println("DeflectionStrikeInputRegressionTest: " + checks + " checks passed");
    }
}

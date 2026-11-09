package cn.academy.port.client;

import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.SkillConsumption;
import cn.academy.port.core.TargetedContextTermination;
import cn.academy.port.highpower.HighPowerInput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** The actual classic binding/coordinator drives the production fresh-edge/Post-dispatch state. */
public final class BrakingTouchInputRegressionTest {
    private static final String ID = "braking_touch";
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private record Loss(String name, Consumer<Rig> action) {}
    private static final class Rig implements ClassicClientInputBinding.Host {
        final BrakingTouchInputState input = new BrakingTouchInputState();
        ClassicClientInputBinding binding;
        Object world = new Object(), player = new Object(), connection = new Object();
        Object boundWorld = world, boundPlayer = player, boundConnection = connection;
        long session = 1, epoch = 71, revision = 1, counter;
        int actor = 19, preset, selectedSlot, cooldown;
        boolean inGame = true, category = true, activated = true, usable = true, learned = true, enabled = true;
        boolean crouching, screen, focused = true, camera = true, mapped = true, paused, sourceReady = true, paymentReady = true, oldEligibility;
        String mappings = "vm-original";
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
        public boolean canUseAbility() {
            var current = input.current();
            return activated && usable && (paymentReady || current != null && input.ownsControl(gates(current.slot(), keys[current.slot()])));
        }
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
        public void contextAbort(TargetedContextTermination.Request request) { throw new AssertionError("No classic braking route"); }
        public void modernCleanup(Collection<ClassicClientInputBinding.Node> defaults) {}
        BrakingTouchInputState.Gates gates(int slot, int key) {
            var context = new BrakingTouchInputState.DispatchContext(world, player, connection, session, actor, epoch, preset, revision, mappings);
            return new BrakingTouchInputState.Gates(context, slot, key, available() && inGame, categoryActivated(),
                    activated && usable, category && learned, enabled, mapped && selectedSlot == slot, !screen, focused, camera, cooldown == 0, sourceReady, paymentReady);
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
            flushRelease();
            var queued = input.queuedStart();
            if (queued != null) {
                if (paused) input.cancelBeforeClear(this::abort);
                else input.flushStartPost(gates(queued.slot(), keys[queued.slot()]), crouching, id -> wire.add("start:" + id.wire()), this::abort);
            }
            flushRelease();
        }
        void flushRelease() {
            var queued = input.queuedRelease();
            if (queued != null) input.flushReleasePost(gates(queued.slot(), keys[queued.slot()]), id -> wire.add("release:" + id.wire()), this::abort);
        }
        void press() { physical.add(keys[selectedSlot]); pre(); }
        void release() { physical.remove(keys[selectedSlot]); pre(); }
        void abort(HighPowerInput.Input identity) { wire.add("abort:" + identity.wire()); }
        long starts() { return wire.stream().filter(s -> s.startsWith("start:")).count(); }
    }
    /** Exercise the shared gate inside a real payment callback, with three concurrently bound skills. */
    private static final class PaymentRig implements ClassicClientInputBinding.Host {
        final AbilityProgress state = new AbilityProgress();
        final BrakingTouchInputState brake = new BrakingTouchInputState();
        final DeflectionStrikeInputState deflect = new DeflectionStrikeInputState();
        final Set<Integer> physical = new HashSet<>();
        final List<String> events = new ArrayList<>();
        final Object world = new Object(), player = new Object(), connection = new Object();
        final String[] skills = {ID, "dir_blast", "deflection_strike"};
        final ClassicClientInputBinding binding;
        long input;
        PaymentRig() {
            state.category = "vecmanip"; state.level = 1; state.activated = true; state.cp = 100;
            for (String id : skills) state.experience.put(id, 0d);
            binding = new ClassicClientInputBinding(this); binding.runtime().updateDefaultGroup();
        }
        BrakingTouchInputState.Gates brakeGates() {
            var context = new BrakingTouchInputState.DispatchContext(world, player, connection, 1, 19, 71, 0, 1, "mappings");
            return new BrakingTouchInputState.Gates(context, 0, 19, true, true, true, true, true, true, true, true, true,
                    state.cooldowns.getOrDefault(ID, 0) == 0, true, !state.consumptionInProgress());
        }
        DeflectionStrikeInputState.Gates deflectionGates() {
            var context = new DeflectionStrikeInputState.DispatchContext(world, player, connection, 1, 19, 71, 0, 1, "mappings");
            return new DeflectionStrikeInputState.Gates(context, 2, 33, true, true, !state.consumptionInProgress(),
                    true, true, true, true, true, true, true, true);
        }
        public boolean available() { return true; }
        public boolean inGame() { return true; }
        public boolean categoryActivated() { return state.isActivated(); }
        public boolean canUseAbility() {
            return state.activated && state.overloadFine && !state.interfering
                    && (!state.consumptionInProgress() || brake.ownsControl(brakeGates()));
        }
        public boolean terminalOpen() { return false; }
        public int actorId() { return 19; }
        public long ownerEpoch() { return 71; }
        public boolean physicalDown(int key) { return physical.contains(key); }
        public boolean registeredMapping(String id) { return List.of(skills).contains(id); }
        public boolean eligible(ClassicClientInputBinding.Node node) {
            return node.slot == 0 ? brake.delegateEligible(brakeGates())
                    : node.slot == 2 ? deflect.delegateEligible(deflectionGates()) : state.canUse(node.skill);
        }
        public String mappedSkill(int slot) { return skills[slot]; }
        public int mappedKey(int slot) { return slot == 0 ? 19 : slot == 1 ? 20 : 33; }
        public int slotCount() { return 3; }
        public void rawActivation(boolean value) { state.activated = value; }
        public void replaceOverrides(int[] keys) {}
        public long down(ClassicClientInputBinding.Node node) {
            var id = new HighPowerInput.Input(node.slot, ++input, 19, 71);
            boolean accepted = node.slot == 0 ? brake.press(id, brakeGates())
                    : node.slot != 2 || deflect.press(id, deflectionGates());
            if (accepted) events.add("start:" + node.skill);
            return accepted ? input : 0;
        }
        public void up(ClassicClientInputBinding.Node node, long value) {}
        public void abort(ClassicClientInputBinding.Node node, long value) {
            events.add("abort:" + node.skill);
            if (node.slot == 0) brake.cancel(new HighPowerInput.Input(0, value, 19, 71), ignored -> {});
            if (node.slot == 2) deflect.cancel(new HighPowerInput.Input(2, value, 19, 71), ignored -> {});
        }
        public boolean cancelPending(ClassicClientInputBinding.Node node, long value) { return false; }
        public void direction(ClassicClientInputBinding.ContextIdentity id, int direction, ClassicClientInputBinding.Edge edge, boolean silent) {}
        public void localContextEnd(ClassicClientInputBinding.ContextIdentity id) {}
        public void contextAbort(TargetedContextTermination.Request request) {}
        public void modernCleanup(Collection<ClassicClientInputBinding.Node> defaults) {}
        void beginBrake() {
            physical.add(19); binding.tickStart(); brake.flushStartPost(brakeGates(), true, ignored -> {}, ignored -> {});
        }
        void busy(Runnable insidePayment) {
            state.bindConsumption(() -> SkillConsumption.Config.DEFAULT, event -> {
                check(state.consumptionInProgress(), "actual payment callback owns the busy guard");
                insidePayment.run();
            }, () -> {}, () -> true);
            check(state.consumeSkill(ID, 1, 0, false), "actual finite payment succeeds after input callbacks");
        }
    }
    private static void paymentIsolation() {
        PaymentRig fresh = new PaymentRig(); fresh.beginBrake();
        fresh.busy(() -> {
            check(fresh.canUseAbility(), "valid brake owner keeps the shared coordinator gate open during payment");
            fresh.physical.addAll(List.of(20, 33)); fresh.binding.tickStart();
            check(fresh.events.equals(List.of("start:braking_touch")), "fresh classic and high-power delegates remain blocked during another skill's payment");
            check(fresh.brake.permits(1, 71), "held brake survives its payment callback");
        });
        PaymentRig held = new PaymentRig(); held.beginBrake(); held.physical.addAll(List.of(20, 33)); held.binding.tickStart();
        held.deflect.flushPost(held.deflectionGates(), ignored -> {}, ignored -> {});
        check(held.binding.activeDefault(0) && held.binding.activeDefault(1) && held.binding.activeDefault(2),
                "real coordinator permits three simultaneous active delegates before payment");
        held.busy(() -> {
            held.binding.tickStart();
            check(held.events.equals(List.of("start:braking_touch", "start:dir_blast", "start:deflection_strike",
                            "abort:dir_blast", "abort:deflection_strike")),
                    "unrelated existing classic and high-power holds abort despite the brake payment exemption");
            check(held.brake.permits(1, 71), "only brake retains its authentic hold during payment");
        });
    }
    private static void completedReceiptReplacement() {
        for (int phase : List.of(BrakingTouchState.BRAKED, BrakingTouchState.LANDED)) {
            Rig rig = new Rig(2); rig.crouching = phase == BrakingTouchState.LANDED; rig.press(); rig.post();
            var old = rig.input.current(); var next = new HighPowerInput.Input(2,2,19,71);
            check(!rig.input.press(next,rig.gates(2,19)), "held unknown input cannot be replaced");
            rig.input.accepted(1,71,phase);
            check(!rig.input.press(next,rig.gates(2,19)), "held completed input cannot be replaced");
            rig.release();
            check(!rig.input.press(next,rig.gates(2,19)), "queued physical release must flush before replacement");
            rig.post();
            for (int tick=1;tick<4;tick++) {
                rig.cooldown=4-tick;rig.pre();rig.post();
                check(!rig.input.press(next,rig.gates(2,19)) && rig.input.current().equals(old), "ordinary cooldown blocks replacement without erasing completed receipt");
            }
            rig.cooldown=0;
            check(!rig.input.press(new HighPowerInput.Input(2,1,19,71),rig.gates(2,19)), "replacement cannot replay prior input number");
            check(!rig.input.press(new HighPowerInput.Input(2,2,19,70),rig.gates(2,19)), "replacement cannot borrow a stale owner epoch");
            rig.paymentReady=false;
            check(!rig.input.press(next,rig.gates(2,19)) && rig.input.current().equals(old), "payment busy rejects replacement without losing old receipt");
            rig.paymentReady=true;rig.press();rig.post();
            check(rig.starts()==2 && rig.input.current().input()==2 && rig.input.permits(2,71), "fresh physical press at4tick cooldown replaces12tick completed cosmetic ownership");
            check(!rig.input.permits(1,71), "old receipt no longer matches replacement");
            rig.input.ended(1,71);rig.input.accepted(1,71,phase);
            check(rig.input.permits(2,71) && rig.input.pendingCount()==1, "late old end or completion cannot erase or acknowledge newer input");
            check(!rig.input.cancel(old,rig::abort) && !rig.input.release(old), "old delegate release or abort cannot touch newer input");
            check(rig.wire.stream().noneMatch(v->v.startsWith("abort:")), "completed replacement does not emit a gameplay abort");
        }
        Rig unknown = new Rig(2);unknown.press();unknown.release();unknown.post();
        check(!unknown.input.press(new HighPowerInput.Input(2,2,19,71),unknown.gates(2,19)), "released unacknowledged tap remains protected");
        Rig arm = new Rig(2);arm.crouching=true;arm.press();arm.post();arm.input.accepted(1,71,BrakingTouchState.ARMED);
        check(!arm.input.press(new HighPowerInput.Input(2,2,19,71),arm.gates(2,19)), "unspent acknowledged arm cannot be replaced");
    }
    public static void main(String[] args) {
        completedReceiptReplacement();
        paymentIsolation();
        for (int slot = 0; slot < 4; slot++) {
            Rig tap = new Rig(slot); tap.press(); tap.release();
            check(tap.wire.isEmpty(), "native Pre never sends start/end before vanilla movement"); tap.post();
            check(tap.wire.equals(List.of("start:" + slot + ":1:19:71", "release:" + slot + ":1:19:71")), "quick tap emits start then end exactly once");
            check(tap.input.permits(1, 71), "released non-crouch tap retains delayed receipt ownership");
            tap.input.accepted(1, 71, BrakingTouchState.BRAKED);
            tap.cooldown = 1; tap.paymentReady = false; tap.pre(); tap.post();
            check(tap.input.permits(1, 71) && tap.wire.size() == 2, "tap's own cooldown/payment does not erase effect");
            tap.input.ended(1, 71); check(tap.input.current() == null, "visual expiry retires released tap");
            tap.press(); tap.post(); check(tap.starts() == 1, "fresh press during cooldown rejected");
            tap.cooldown = 0; tap.paymentReady = true; tap.pre(); tap.post();
            check(tap.starts() == 1, "cooldown expiry cannot synthesize a fresh edge");
            tap.release(); tap.press(); tap.post(); check(tap.starts() == 2, "new physical eligible edge works");

            Rig arm = new Rig(slot); arm.press(); arm.crouching = true; arm.post();
            check(arm.input.permits(1, 71), "crouch is sampled at Post, not Pre");
            arm.input.accepted(1, 71, BrakingTouchState.ARMED); arm.cooldown = 1; arm.paymentReady = false;
            for (int i = 0; i < 35; i++) { arm.pre(); arm.post(); }
            check(arm.wire.size() == 1 && arm.input.permits(1, 71), "held arm survives same-session cooldown without repeats");
            arm.binding.modernSnapshotFallback(); arm.pre(); arm.post();
            check(arm.input.holding(), "snapshot delegate replacement preserves physical ownership");
            arm.release(); check(!arm.input.permits(1, 71), "physical release immediately fences late landing");
            arm.post(); check(arm.input.current() == null && arm.wire.size() == 2, "released arm sends one end then retires");

            Rig fastArm = new Rig(slot); fastArm.crouching = true; fastArm.press(); fastArm.release(); fastArm.post();
            check(fastArm.wire.equals(List.of("start:" + slot + ":1:19:71", "release:" + slot + ":1:19:71"))
                    && !fastArm.input.permits(1,71), "same-tick crouch tap starts then cancels, never success");
            Rig landed = new Rig(slot); landed.crouching = true; landed.press(); landed.post();
            landed.input.accepted(1,71,BrakingTouchState.LANDED); landed.release(); landed.post();
            check(landed.input.permits(1,71), "release after confirmed landing preserves its short cosmetic lifetime");
            landed.input.ended(1,71); check(landed.input.current() == null, "confirmed landing lifecycle ends cleanly");
        }
        List<Loss> losses = List.of(new Loss("world", r -> r.world = new Object()),
                new Loss("player", r -> r.player = new Object()), new Loss("connection", r -> r.connection = new Object()),
                new Loss("session", r -> r.session++), new Loss("actor", r -> r.actor++), new Loss("epoch", r -> r.epoch++),
                new Loss("preset", r -> r.preset++), new Loss("revision", r -> r.revision++), new Loss("mapping", r -> r.mappings = "changed"),
                new Loss("key", r -> r.keys[r.selectedSlot]++), new Loss("death", r -> r.inGame = false),
                new Loss("category", r -> r.category = false), new Loss("activation", r -> r.activated = false),
                new Loss("ability", r -> r.usable = false), new Loss("learned", r -> r.learned = false),
                new Loss("enabled", r -> r.enabled = false), new Loss("mapped", r -> r.mapped = false),
                new Loss("screen", r -> r.screen = true), new Loss("focus", r -> r.focused = false),
                new Loss("camera", r -> r.camera = false), new Loss("source", r -> r.sourceReady = false));
        for (Loss loss : losses) for (boolean dispatched : List.of(false, true)) {
            Rig rig = new Rig(2); rig.crouching = true; rig.press(); if (dispatched) rig.post();
            loss.action.accept(rig); rig.pre(); rig.post();
            check(rig.input.current() == null && rig.wire.stream().filter(v -> v.startsWith("abort:")).count() == 1,
                    loss.name + " invalidation aborts exactly once before clearing ownership");
            check(!rig.input.permits(1,71), loss.name + " invalidation blocks late receipts");
        }
        Rig cooldownAtPost = new Rig(2); cooldownAtPost.press(); cooldownAtPost.cooldown = 1; cooldownAtPost.post();
        check(cooldownAtPost.starts() == 0 && cooldownAtPost.wire.equals(List.of("abort:2:1:19:71")), "unsent starts cannot borrow cooldown exemption");
        Rig paidAtPost = new Rig(2); paidAtPost.press(); paidAtPost.paymentReady = false; paidAtPost.post();
        check(paidAtPost.starts() == 0, "unsent starts cannot borrow payment exemption");
        Rig old = new Rig(2); old.oldEligibility = true; old.crouching = true; old.press(); old.post(); old.cooldown = 1; old.pre();
        check(old.input.current() == null && old.wire.size() == 2, "negative control reproduces unscoped cooldown abort");
        Rig expiry = new Rig(2); expiry.press(); expiry.post(); expiry.input.ended(1,71);
        for (int i = 0; i < 80; i++) { expiry.pre(); expiry.post(); }
        check(expiry.starts() == 1 && !expiry.input.permits(1,71), "server ending cannot replay a still-held key");
        expiry.release(); expiry.press(); expiry.post(); check(expiry.starts() == 2, "release/new edge rearms after end");
        Rig timeout = new Rig(2); timeout.press(); timeout.release(); timeout.post();
        timeout.paused = true; for (int i = 0; i < 80; i++) timeout.pre();
        check(timeout.input.permits(1,71), "pause freezes acknowledgement timeout");
        timeout.paused = false; for (int i = 0; i < BrakingTouchInputState.ACK_TIMEOUT_TICKS; i++) timeout.pre();
        check(timeout.input.current() == null && timeout.wire.size() == 3, "lost acknowledgement cannot leak ownership");
        Rig pausedPost = new Rig(2); pausedPost.press(); pausedPost.paused = true; pausedPost.post();
        check(pausedPost.starts() == 0 && pausedPost.input.current() == null, "pause between Pre/Post never dispatches stale start");
        System.out.println("BrakingTouchInputRegressionTest: " + checks + " checks passed");
    }
}

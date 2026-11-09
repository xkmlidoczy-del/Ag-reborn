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
public final class VectorParryInputRegressionTest {
    private static final String ID = "vector_parry";
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private record Loss(String name, Consumer<Rig> action) {}
    private static final class Rig implements ClassicClientInputBinding.Host {
        final VectorParryInputState input = new VectorParryInputState();
        ClassicClientInputBinding binding;
        Object world = new Object(), player = new Object(), connection = new Object();
        Object boundWorld = world, boundPlayer = player, boundConnection = connection;
        long session = 1, epoch = 71, revision = 1, counter;
        int actor = 19, preset, selectedSlot, cooldown;
        boolean inGame = true, category = true, activated = true, usable = true, learned = true, enabled = true;
        boolean screen, focused = true, camera = true, mapped = true, paused, sourceReady = true, paymentReady = true, oldEligibility;
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
        public void contextAbort(TargetedContextTermination.Request request) { throw new AssertionError("No classic parry route"); }
        public void modernCleanup(Collection<ClassicClientInputBinding.Node> defaults) {}
        VectorParryInputState.Gates gates(int slot, int key) {
            var context = new VectorParryInputState.DispatchContext(world, player, connection, session, actor, epoch, preset, revision, mappings);
            return new VectorParryInputState.Gates(context, slot, key, available() && inGame, categoryActivated(),
                    activated && usable, category && learned, enabled, mapped && selectedSlot == slot, !screen, focused, camera, cooldown == 0, sourceReady, paymentReady);
        }
        void pre() {
            var current = input.current();
            if (current != null) {
                input.releaseWhenUp(physicalDown(input.physicalKey()));
                input.tick(gates(current.slot(), keys[current.slot()]), this::abort);
            }
            binding.tickStart();
        }
        void post() {
            binding.finishLocalDisposals();
            flushRelease();
            var queued = input.queuedStart();
            if (queued != null) {
                if (paused) input.cancelBeforeClear(this::abort);
                else input.flushStartPost(gates(queued.slot(), keys[queued.slot()]), id -> wire.add("start:" + id.wire()), this::abort);
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
        final VectorParryInputState parry = new VectorParryInputState();
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
        VectorParryInputState.Gates parryGates() {
            var context = new VectorParryInputState.DispatchContext(world, player, connection, 1, 19, 71, 0, 1, "mappings");
            return new VectorParryInputState.Gates(context, 0, 19, true, true, true, true, true, true, true, true, true,
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
                    && (!state.consumptionInProgress() || parry.ownsControl(parryGates()));
        }
        public boolean terminalOpen() { return false; }
        public int actorId() { return 19; }
        public long ownerEpoch() { return 71; }
        public boolean physicalDown(int key) { return physical.contains(key); }
        public boolean registeredMapping(String id) { return List.of(skills).contains(id); }
        public boolean eligible(ClassicClientInputBinding.Node node) {
            return node.slot == 0 ? parry.delegateEligible(parryGates())
                    : node.slot == 2 ? deflect.delegateEligible(deflectionGates()) : state.canUse(node.skill);
        }
        public String mappedSkill(int slot) { return skills[slot]; }
        public int mappedKey(int slot) { return slot == 0 ? 19 : slot == 1 ? 20 : 33; }
        public int slotCount() { return 3; }
        public void rawActivation(boolean value) { state.activated = value; }
        public void replaceOverrides(int[] keys) {}
        public long down(ClassicClientInputBinding.Node node) {
            var id = new HighPowerInput.Input(node.slot, ++input, 19, 71);
            boolean accepted = node.slot == 0 ? parry.press(id, parryGates())
                    : node.slot != 2 || deflect.press(id, deflectionGates());
            if (accepted) events.add("start:" + node.skill);
            return accepted ? input : 0;
        }
        public void up(ClassicClientInputBinding.Node node, long value) {}
        public void abort(ClassicClientInputBinding.Node node, long value) {
            events.add("abort:" + node.skill);
            if (node.slot == 0) parry.cancel(new HighPowerInput.Input(0, value, 19, 71), ignored -> {});
            if (node.slot == 2) deflect.cancel(new HighPowerInput.Input(2, value, 19, 71), ignored -> {});
        }
        public boolean cancelPending(ClassicClientInputBinding.Node node, long value) { return false; }
        public void direction(ClassicClientInputBinding.ContextIdentity id, int direction, ClassicClientInputBinding.Edge edge, boolean silent) {}
        public void localContextEnd(ClassicClientInputBinding.ContextIdentity id) {}
        public void contextAbort(TargetedContextTermination.Request request) {}
        public void modernCleanup(Collection<ClassicClientInputBinding.Node> defaults) {}
        void beginParry() {
            physical.add(19); binding.tickStart(); parry.flushStartPost(parryGates(), ignored -> {}, ignored -> {});
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
        PaymentRig fresh = new PaymentRig(); fresh.beginParry();
        fresh.busy(() -> {
            check(fresh.canUseAbility(), "valid parry owner keeps the shared coordinator gate open during payment");
            fresh.physical.addAll(List.of(20, 33)); fresh.binding.tickStart();
            check(fresh.events.equals(List.of("start:vector_parry")), "fresh classic and high-power delegates remain blocked during another skill's payment");
            check(fresh.parry.permits(1, 71), "held parry survives its payment callback");
        });
        PaymentRig held = new PaymentRig(); held.beginParry(); held.physical.addAll(List.of(20, 33)); held.binding.tickStart();
        held.deflect.flushPost(held.deflectionGates(), ignored -> {}, ignored -> {});
        check(held.binding.activeDefault(0) && held.binding.activeDefault(1) && held.binding.activeDefault(2),
                "real coordinator permits three simultaneous active delegates before payment");
        held.busy(() -> {
            held.binding.tickStart();
            check(held.events.equals(List.of("start:vector_parry", "start:dir_blast", "start:deflection_strike",
                            "abort:dir_blast", "abort:deflection_strike")),
                    "unrelated existing classic and high-power holds abort despite the parry payment exemption");
            check(held.parry.permits(1, 71), "only parry retains its authentic hold during payment");
        });
    }
    public static void main(String[] args) {
        for (int slot = 0; slot < 4; slot++) {
            Rig r = new Rig(slot); r.press();
            check(r.binding.runtime() instanceof ClassicClientRuntimeCoordinator, "actual coordinator owns input");
            check(ClassicClientInputBinding.kind(ID) == ClassicClientInputBinding.Kind.SINGLE_KEY, "original binding kind preserved");
            check(r.wire.isEmpty() && r.input.queuedStart() != null, "Pre never transmits before native movement");
            r.release(); check(r.wire.isEmpty(), "release before Post is deferred");
            r.post();
            check(r.wire.equals(List.of("start:" + slot + ":1:19:71", "release:" + slot + ":1:19:71")),
                    "fast tap sends authentic identity-only start then release once");
            check(!r.input.permits(1, 71) && r.input.current() == null, "release-before-ack cannot admit a delayed start");
            r.input.accepted(1, 71); r.input.ended(1, 71);
            for (int tick = 0; tick < 5; tick++) { r.pre(); r.post(); }
            check(r.wire.size() == 2, "late acknowledgement and subsequent Post cannot replay");
            r.press(); r.post(); check(r.starts() == 2 && r.input.permits(2,71), "next physical edge works");
            r.input.accepted(2,71); check(r.input.pendingCount() == 0, "accepted hold has no pending receipt");
            for (int tick = 0; tick < 80; tick++) { r.cooldown = 1; r.pre(); r.post(); }
            check(r.starts() == 2 && r.wire.size() == 3 && r.input.permits(2,71), "renewed own cooldown never aborts or repeats a held defense");
            r.binding.modernSnapshotFallback(); r.pre(); r.post();
            check(r.wire.size() == 3 && r.input.holding(), "CP snapshot replacement preserves held identity");
            r.release();
            check(r.input.queuedRelease() != null && !r.input.permits(2,71), "actual physical release survives silent delegate replacement");
            r.post(); check(r.wire.getLast().equals("release:" + slot + ":2:19:71"), "snapshot replacement still sends one normal release");
            check(r.input.current() == null, "released owner retired without retaining an unbounded receipt");
        }
        Rig ownPayment = new Rig(2); ownPayment.press(); ownPayment.post(); ownPayment.paymentReady = false; ownPayment.cooldown = 1;
        ownPayment.pre(); ownPayment.post();
        check(ownPayment.wire.equals(List.of("start:2:1:19:71")) && ownPayment.input.permits(1,71),
                "established hold survives its own payment guard and one-tick cooldown");
        check(!ownPayment.input.eligible(ownPayment.gates(2,19)), "payment and cooldown still reject fresh start eligibility");
        ownPayment.release(); ownPayment.post();
        check(ownPayment.wire.equals(List.of("start:2:1:19:71", "release:2:1:19:71")), "own payment does not swallow release");
        ownPayment.press(); ownPayment.post(); check(ownPayment.starts() == 1, "new press during own cooldown stays rejected");
        ownPayment.paymentReady = true; ownPayment.cooldown = 0; ownPayment.pre(); ownPayment.post();
        check(ownPayment.starts() == 1, "cooldown expiry alone never replays a physically held rejected edge");
        ownPayment.release(); ownPayment.press(); ownPayment.post(); check(ownPayment.starts() == 2, "fresh eligible edge works again");

        Rig old = new Rig(2); old.oldEligibility = true; old.press(); old.post(); old.cooldown = 1; old.pre();
        check(old.wire.equals(List.of("start:2:1:19:71", "abort:2:1:19:71")) && old.input.current() == null,
                "mutation witness reproduces old coordinator self-abort on own cooldown");
        Rig freshCooldown = new Rig(0); freshCooldown.cooldown = 1; freshCooldown.press(); freshCooldown.post();
        check(freshCooldown.wire.isEmpty(), "fresh cooling edge sends no start");
        Rig betweenCooldown = new Rig(0); betweenCooldown.press(); betweenCooldown.cooldown = 1; betweenCooldown.post();
        check(betweenCooldown.starts() == 0 && betweenCooldown.input.current() == null, "cooldown arriving before unsent Post rejects the start");
        Rig betweenPayment = new Rig(0); betweenPayment.press(); betweenPayment.paymentReady = false; betweenPayment.post();
        check(betweenPayment.starts() == 0, "unsent Post cannot borrow payment ownership");

        Rig late = new Rig(1); late.press(); late.post(); late.release();
        late.input.accepted(1,71); check(!late.input.permits(1,71), "release fences acknowledgements before release packet is sent");
        late.post(); late.press(); late.post(); late.input.accepted(1,71); late.input.ended(1,71);
        check(late.input.permits(2,71) && late.input.holding(), "old accepted/end receipts cannot retire a newer held identity");
        late.input.ended(2,71);
        for (int tick = 0; tick < 4; tick++) { late.pre(); late.post(); }
        check(late.starts() == 2 && !late.input.permits(2,71), "server end keeps physical edge latched and cannot reopen defense");
        late.release(); late.post(); late.press(); late.post(); check(late.starts() == 3, "fresh press after authoritative end and actual release works");

        Rig quiet = new Rig(0); quiet.press(); quiet.post(); quiet.input.accepted(1,71); quiet.input.visualLost(1,71);
        for (int tick = 0; tick < 120; tick++) { quiet.cooldown = 1; quiet.pre(); quiet.post(); }
        check(quiet.wire.equals(List.of("start:0:1:19:71")) && quiet.input.permits(1,71), "missing visual heartbeat never destructively aborts the authoritative hold");
        quiet.release(); quiet.post(); check(quiet.wire.getLast().equals("release:0:1:19:71"), "visual loss keeps identity needed for actual release");

        List<Loss> losses = List.of(new Loss("GUI", r -> r.screen = true), new Loss("focus", r -> r.focused = false),
                new Loss("camera", r -> r.camera = false), new Loss("death", r -> r.inGame = false),
                new Loss("category", r -> r.category = false), new Loss("activation", r -> r.activated = false),
                new Loss("ability", r -> r.usable = false), new Loss("learned", r -> r.learned = false),
                new Loss("enabled", r -> r.enabled = false), new Loss("unbind", r -> r.mapped = false),
                new Loss("slot", r -> r.selectedSlot++), new Loss("preset", r -> r.preset++),
                new Loss("revision", r -> r.revision++), new Loss("mapping signature", r -> r.mappings = "changed"),
                new Loss("physical mapping", r -> r.keys[0]++), new Loss("actor", r -> r.actor++),
                new Loss("owner epoch", r -> r.epoch++), new Loss("session", r -> r.session++),
                new Loss("world", r -> r.world = new Object()), new Loss("player", r -> r.player = new Object()),
                new Loss("connection", r -> r.connection = new Object()), new Loss("source", r -> r.sourceReady = false));
        for (Loss loss : losses) for (boolean released : new boolean[]{false, true}) {
            Rig queued = new Rig(0); queued.press(); if (released) queued.release();
            loss.action.accept(queued); queued.post();
            check(queued.starts() == 0 && queued.input.current() == null, "authentic context loss discards unsent start: " + loss.name);
            int sent = queued.wire.size(); queued.post(); check(queued.wire.size() == sent, "unsent cancel is one-shot: " + loss.name);
            Rig active = new Rig(0); active.press(); active.post(); active.input.accepted(1,71); active.cooldown = 1;
            if (released) active.release();
            loss.action.accept(active); active.pre(); active.post();
            check(active.input.current() == null && !active.input.permits(1,71), "real context loss always fences held input even with own cooldown: " + loss.name);
            check(active.wire.equals(List.of("start:0:1:19:71", "abort:0:1:19:71")), "lost context sends no normal release or new start: " + loss.name);
            int afterLoss = active.wire.size(); active.pre(); active.post(); active.input.accepted(1,71);
            check(active.wire.size() == afterLoss && !active.input.permits(1,71), "cancelled context cannot replay: " + loss.name);
        }
        Rig normalRelease = new Rig(0); normalRelease.press(); normalRelease.post(); var identity = normalRelease.input.current();
        normalRelease.release();
        check(!normalRelease.input.cancel(identity, normalRelease::abort), "replaced delegate cannot abort a normal release waiting for Post");
        normalRelease.post(); check(normalRelease.wire.getLast().equals("release:0:1:19:71"), "separate normal release keeps ownership until sent");
        Rig reload = new Rig(0); reload.press(); reload.post(); reload.input.cancelBeforeClear(reload::abort); reload.pre(); reload.post();
        check(reload.wire.equals(List.of("start:0:1:19:71", "abort:0:1:19:71")), "reload cancellation is one-shot and never replays a held edge");
        Rig paused = new Rig(0); paused.press(); paused.paused = true; paused.post(); check(paused.starts() == 0, "pause before Post cannot create a controller");
        Rig newSession = new Rig(0); newSession.press(); newSession.input.reset(); newSession.binding.discard();
        newSession.world = new Object(); newSession.epoch++; newSession.install(); newSession.binding.establishSessionFence();
        newSession.pre(); newSession.post(); check(newSession.starts() == 0, "replacement session cannot inherit held edge");
        newSession.release(); newSession.press(); newSession.post(); check(newSession.starts() == 1, "fresh edge works after session fence");
        for (String action : List.of("hp_vector_parry_start", "hp_vector_parry_release", "hp_vector_parry_abort"))
            check(action.length() <= 32, "request action remains within wire bound");
        paymentIsolation();
        System.out.println("VectorParryInputRegressionTest: " + checks + " checks passed");
    }
}

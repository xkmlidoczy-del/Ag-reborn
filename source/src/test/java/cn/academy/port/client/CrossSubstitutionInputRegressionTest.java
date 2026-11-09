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
public final class CrossSubstitutionInputRegressionTest {
    private static final String ID = "cross_substitution";
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private record Loss(String name, Consumer<Rig> action) {}
    private static final class Rig implements ClassicClientInputBinding.Host {
        final CrossSubstitutionInputState input = new CrossSubstitutionInputState();
        ClassicClientInputBinding binding;
        Object world = new Object(), player = new Object(), connection = new Object();
        Object boundWorld = world, boundPlayer = player, boundConnection = connection;
        long session = 1, epoch = 71, revision = 1, counter;
        int actor = 19, preset, selectedSlot, cooldown;
        boolean inGame = true, category = true, activated = true, usable = true, learned = true, enabled = true;
        boolean screen, focused = true, camera = true, mapped = true, paused, sourceReady = true;
        boolean crouching;
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
        public boolean eligible(ClassicClientInputBinding.Node node) { return input.delegateEligible(gates(node.slot, node.key)); }
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
        public void contextAbort(TargetedContextTermination.Request request) { throw new AssertionError("No classic motion route"); }
        public void modernCleanup(Collection<ClassicClientInputBinding.Node> defaults) {}
        CrossSubstitutionInputState.Gates gates(int slot, int key) {
            var context = new CrossSubstitutionInputState.DispatchContext(world, player, connection, session, actor, epoch, preset, revision, mappings);
            return new CrossSubstitutionInputState.Gates(context, slot, key, available() && inGame, categoryActivated(),
                    canUseAbility(), category && learned, enabled, mapped && selectedSlot == slot, !screen, focused, camera, cooldown == 0, sourceReady, crouching);
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
            check(ClassicClientInputBinding.kind(ID) == ClassicClientInputBinding.Kind.SINGLE_KEY, "single fresh tap binding");
            check(r.wire.isEmpty(), "Pre never transmits ahead of native sneak/movement");
            r.release(); r.post();
            check(r.starts() == 1 && r.wire.size() == 1, "down/up before Post dispatches exactly once with inert release");
            check(r.wire.getFirst().equals("start:" + slot + ":1:19:71"), "wire is identity only");
            r.input.accepted(1, 71, 400, false);
            r.sourceReady = false; r.cooldown = 30;
            for (int i = 0; i < 40; i++) { r.pre(); r.post(); }
            check(r.input.permits(1,71) && r.input.acceptedRemainingTicks() == 360,
                    "release, ordinary movement and cooldown preserve original preparation");
            r.input.accepted(1,71,400,false);
            check(r.input.acceptedRemainingTicks() == 360, "heartbeat cannot renew preparation");
            r.sourceReady = true; r.cooldown = 0; r.press(); r.post(); r.release();
            check(r.starts() == 2 && r.input.permits(1,71) && r.input.permits(2,71), "second fresh tap awaits authoritative B");
            r.input.accepted(2,71,390,false);
            check(!r.input.permits(1,71) && r.input.acceptedRemainingTicks() <= 360,
                    "B adopts latest nonce without renewing the first mark");
            r.press(); r.post(); r.release(); r.input.accepted(3,71,0,true);
            check(r.starts() == 3 && r.input.firing() && r.input.permits(3,71), "third tap owns real salvo receipts");
            r.sourceReady = false; r.cooldown = 60;
            for (int i = 0; i < 28; i++) { r.pre(); r.post(); }
            check(r.input.permits(3,71) && r.wire.size() == 3, "salvo survives release and movement across all three native flights");
            int left = r.input.acceptedRemainingTicks(); r.input.accepted(3,71,0,true);
            check(left == r.input.acceptedRemainingTicks(), "publication/contact receipts cannot extend the salvo window");
            r.input.ended(3,71);
            check(r.input.current() == null && !r.input.permits(3,71), "terminal retires all local ownership");
        }
        List<Loss> losses = List.of(new Loss("GUI", r -> r.screen=true), new Loss("focus", r -> r.focused=false),
                new Loss("camera", r -> r.camera=false), new Loss("death", r -> r.inGame=false),
                new Loss("category", r -> r.category=false), new Loss("activation", r -> r.activated=false),
                new Loss("ability", r -> r.usable=false), new Loss("learned", r -> r.learned=false),
                new Loss("enabled", r -> r.enabled=false), new Loss("mapped", r -> r.mapped=false),
                new Loss("slot", r -> r.selectedSlot++), new Loss("preset", r -> r.preset++),
                new Loss("revision", r -> r.revision++), new Loss("signature", r -> r.mappings="changed"),
                new Loss("physical mapping", r -> r.keys[0]++), new Loss("actor", r -> r.actor++),
                new Loss("owner epoch", r -> r.epoch++), new Loss("session", r -> r.session++),
                new Loss("world", r -> r.world=new Object()), new Loss("player", r -> r.player=new Object()),
                new Loss("connection", r -> r.connection=new Object()));
        for (var loss : losses) for (int phase = 0; phase < 3; phase++) {
            Rig r = new Rig(0); r.press();
            if (phase > 0) { r.post(); r.input.accepted(1,71,phase==1?400:0,phase==2); }
            r.release(); loss.action.accept(r); r.pre(); r.post();
            check(r.input.current() == null && !r.input.permits(1,71), "explicit loss fences queue/preparation/salvo: " + loss.name);
            long sent = r.wire.size(); r.pre(); r.post(); check(sent == r.wire.size(), "cancellation is one-shot");
        }
        Rig timeout = new Rig(0); timeout.press(); timeout.post(); timeout.release();
        for (int i = 0; i < CrossSubstitutionInputState.ACK_TIMEOUT_TICKS; i++) timeout.pre();
        check(timeout.input.current() == null, "unacknowledged request has bounded ownership");
        Rig pause = new Rig(0); pause.press(); pause.post(); pause.release(); pause.input.accepted(1,71,400,false); pause.paused=true;
        for (int i = 0; i < 500; i++) pause.pre();
        check(pause.input.acceptedRemainingTicks() == 400, "pause does not consume server preparation lease");
        Rig expiry = new Rig(0); expiry.press(); expiry.post(); expiry.release(); expiry.input.accepted(1,71,400,false);
        for (int i = 0; i < 400; i++) expiry.pre();
        check(expiry.input.current() == null && !expiry.input.permits(1,71), "original 400 ticks never renew");
        Rig crouch = new Rig(0); crouch.crouching=true; crouch.sourceReady=false; crouch.cooldown=20; crouch.press(); crouch.post();
        check(crouch.starts() == 1, "sneak-tap reaches authoritative free preflight refusal despite cooldown or source state");
        crouch.input.ended(1,71); crouch.release();
        check(crouch.input.current()==null,"unprepared swap refusal releases input without inventing preparation");
        for(int slot=0;slot<4;slot++) {
            Rig swap = new Rig(slot); swap.press();swap.post();swap.release();swap.input.accepted(1,71,400,false);
            swap.press();swap.post();swap.release();swap.input.accepted(2,71,390,false);
            int before = swap.input.acceptedRemainingTicks();
            swap.crouching=true;swap.cooldown=20;swap.sourceReady=false;swap.press();swap.post();swap.release();
            swap.input.accepted(3,71,380,false);
            check(swap.starts()==3 && swap.input.permits(3,71) && !swap.input.permits(2,71) && !swap.input.firing(),
                    "swap refusal adopts fresh authenticated nonce while retaining preparation, slot="+slot);
            check(swap.input.acceptedRemainingTicks()<=before && swap.input.acceptedRemainingTicks()<=380,
                    "swap refusal cannot renew original preparation lease");
            swap.cooldown=0;swap.sourceReady=true;swap.press();swap.post();
            for(int i=0;i<10;i++){swap.pre();swap.post();}
            check(swap.starts()==4 && swap.input.permits(4,71),"held crouch tap dispatches a single swap attempt");
            swap.input.ended(4,71);
            check(!swap.input.permits(3,71) && !swap.input.permits(4,71) && !swap.input.firing(),
                    "confirmed or admitted-failed swap retires preparation with no needle-flight ownership");
            swap.release();check(swap.input.current()==null,"release after terminal swap clears held control");
        }
        Rig jump = new Rig(0); jump.press(); jump.post(); jump.release(); jump.input.accepted(1,71,400,false);
        jump.press(); jump.sourceReady=false; jump.post();
        check(jump.starts()==1 && jump.input.permits(1,71) && jump.input.queuedStart()==null, "invalid unsent second tap cannot erase known A");
        jump.screen=true; jump.pre();
        check(jump.wire.contains("abort:0:1:19:71") && jump.wire.contains("abort:0:2:19:71"), "cancel fences known and unsent identities");
        Rig held = new Rig(0); held.press(); held.post(); held.input.accepted(1,71,400,false);
        for (int i=0;i<20;i++){held.pre();held.post();}
        check(held.starts()==1,"holding cannot place B or fire");
        held.input.reset(); held.binding.discard(); held.world=new Object(); held.epoch++; held.install(); held.binding.establishSessionFence();
        held.pre(); held.post(); check(held.starts()==1,"replacement session fences held key");
        held.release(); held.press(); held.post(); check(held.starts()==2,"new physical edge works after session fence");
        System.out.println("CrossSubstitutionInputRegressionTest: " + checks + " checks passed");
    }
}

package cn.academy.port.client;

import static cn.academy.port.client.RemoteScatterRelayState.*;

import cn.academy.port.core.TargetedContextTermination;
import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.client.RemoteScatterRelayState.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Actual classic physical-key seam plus pure NEW253 receipts, fixed route rendering and budgets. */
public final class RemoteScatterRelayVisualRegressionTest {
    private static int checks;
    private static final UUID OWNER = new UUID(253, 1);
    private static final Vector ZERO = v(0, 0, 0), SOURCE = v(0, 64, 0), LOOK = v(0, 0, 1), END = v(16, 64, 24);
    private static final Node A = new Node(0, v(0, 64, 8), LOOK), B = new Node(1, v(8, 64, 8), v(1, 0, 0));
    private static final Node C = new Node(2, v(8, 64, 16), LOOK), D = new Node(3, v(16, 64, 16), v(1, 0, 0));
    private static final List<Node> NODES = List.of(A, B, C, D);
    private static final List<Segment> PATH = List.of(segment(SOURCE,A.center()),segment(A.center(),B.center()),segment(B.center(),C.center()),segment(C.center(),D.center()),segment(D.center(),END));
    private static Vector v(double x,double y,double z) { return new Vector(x,y,z); }
    private static Segment segment(Vector a,Vector b) { return new Segment(a,b); }
    private static void check(boolean success,String description) { checks++; if(!success)throw new AssertionError(description); }
    private static void denied(Runnable action,String description) { try { action.run(); } catch(IllegalArgumentException expected) { checks++; return; } throw new AssertionError(description); }
    private static Frame frame(long input,long token,long sequence,int phase,int status,int remaining,List<Node> nodes,Vector endpoint,List<Segment> path) {
        return new Frame(new Identity(9,OWNER,2,input,token),sequence,phase,status,remaining,SOURCE.subtract(v(0,1.5,0)),nodes,
            phase==RemoteScatterRelayState.EDIT?A.center():endpoint,phase==RemoteScatterRelayState.EDIT?A.normal():ZERO,path);
    }
    private static Frame f(long input,long sequence,int phase,int remaining,List<Node> nodes) { return frame(input,5,sequence,phase,0,remaining,nodes,ZERO,List.of()); }
    public static void main(String[] args) throws Exception {
        input(); binding(); deferredNativeOrdering(); deferredFinalAim(); recallDuringCooldown(); receipts(); feedback(); resultNotes(); lifetime(); geometry(); budgets(); compactSceneBound(); fourControllerBudget(); integration();
        System.out.println("PASS RemoteScatterRelayVisualRegressionTest: "+checks+" checks");
    }
    private static void input() {
        var state = new RemoteScatterRelayInputState(); var press = new HighPowerInput.Input(0, 1, 9, 2);
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
    private static final class Rig implements ClassicClientInputBinding.Host {
        final int[] keys = {-100, -99, 19, 33}; final int slot;
        final Set<Integer> down = new HashSet<>(); final List<String> wire = new ArrayList<>();
        final RemoteScatterRelayInputState input = new RemoteScatterRelayInputState();
        final ClassicClientInputBinding binding;
        boolean editing, terminal, live = true, activated = true, overloadFine = true, interfering, consuming;
        boolean learned = true, parentLearned = true, mapped = true, enabled = true;
        String category = "meltdowner"; int cooldown, remaining = 400, quiet, level = 5;
        RemoteScatterRelayState.Frame array;
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
        public boolean registeredMapping(String skill) { return skill.equals("remote_scatter_relay"); }
        public boolean eligible(ClassicClientInputBinding.Node node) {
            boolean paid = array != null && input.permits(array.id().input(), array.id().epoch())
                    && RemoteScatterRelayState.recallAvailable(array, remaining, quiet);
            return live && category.equals("meltdowner") && canUseAbility() && mapped && enabled
                    && RemoteScatterRelayState.eligible(level, parentLearned, learned) && (cooldown == 0 || cooldown > 0 && paid);
        }
        public String mappedSkill(int slot) { return slot == this.slot ? "remote_scatter_relay" : ""; }
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
        void release(HighPowerInput.Input identity) { if (identity != null) input.deferRelease(identity,context()); }
        void flushRelease() {
            var identity=input.flushDeferredRelease(context()); if(identity==null)return;
            if(!live||terminal||!activated||!overloadFine||interfering||!learned||!parentLearned||!mapped||!enabled||level!=5||!category.equals("meltdowner")) {
                var cancelled=input.cancel(); if(cancelled!=null)wire.add("abort:"+cancelled.wire()); return;
            }
            wire.add("release:"+identity.wire());
        }
        RemoteScatterRelayInputState.DispatchContext context() {
            return new RemoteScatterRelayInputState.DispatchContext(world, player, connection, session, preset, revision, mappedSkill(slot) + ":" + keys[slot]);
        }
        void pre() {
            binding.tickStart(); var held = input.current();
            if (held != null && !terminal && live) release(input.releaseWhenUp(physicalDown(held.physicalKey())));
        }
        void post() {
            flushRelease();
            if (input.queuedStart() == null) return;
            if (!input.queuedFor(context()) || !live || terminal || !eligible(null)) {
                var cancelled = input.cancel(); if (cancelled != null) wire.add("abort:" + cancelled.wire()); return;
            }
            var dispatch = input.flushQueuedStart();
            wire.add("start:" + dispatch.start().wire()); observedServerModes.add(nativeSneaking); release(dispatch.release()); flushRelease();
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
        var pending = new RemoteScatterRelayInputState(); var identity = new HighPowerInput.Input(0, 1, 9, 2);
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
            check(rig.wire.size() == 1 && rig.array.phase() == RemoteScatterRelayState.ACTIVE, "holding recall does not locally clear array or predict an emission");
            rig.down.remove(rig.keys[slot]); rig.tick();
            check(rig.wire.size() == 2 && rig.wire.getLast().startsWith("release:"), "recall release reaches server exactly once despite cooldown");
            rig = paidRig(slot); rig.down.add(rig.keys[slot]); rig.tick(); rig.down.remove(rig.keys[slot]); rig.tick();
            check(rig.wire.size() == 2 && rig.array.path().isEmpty() && rig.array.phase() == RemoteScatterRelayState.ACTIVE,
                    "short release during cooldown sends intent only; client cannot invent a paid shot or impact");
            for (int blocked = 0; blocked < 14; blocked++) {
                Rig denied = paidRig(slot);
                switch (blocked) {
                    case 0 -> denied.array = f(1, 2, 1, 0, List.of());
                    case 1 -> denied.remaining = 0;
                    case 2 -> denied.quiet = RemoteScatterRelayState.QUIET_TICKS;
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
        check(!RemoteScatterRelayState.recallAvailable(f(2, 3, 3, 0, List.of()), 400, 0), "ended array cannot reopen recall despite forged remaining time");
    }
    private static void deferredFinalAim() {
        for(int slot=0;slot<4;slot++)for(boolean crouch:List.of(false,true)) {
            Rig rig=new Rig(slot,crouch);rig.down.add(rig.keys[slot]);rig.tick();
            var identity=rig.input.current().identity();rig.wire.clear();rig.down.clear();rig.pre();
            check(rig.wire.isEmpty()&&!rig.input.holding(),"physical release waits for native final-look packet in all four slots");
            rig.wire.add("vanilla:final-look");rig.post();rig.post();
            check(rig.wire.equals(List.of("vanilla:final-look","release:"+identity.wire())),"Post emits original release exactly once after vanilla final aim");
            check(!rig.input.deferRelease(identity,rig.context()),"already emitted release cannot be requeued");
        }
        for(int changed=0;changed<8;changed++) {
            Rig rig=new Rig(0,false);rig.down.add(rig.keys[0]);rig.tick();rig.wire.clear();rig.down.clear();rig.pre();
            switch(changed) {
                case 0->rig.world=new Object();case 1->rig.player=new Object();case 2->rig.connection=new Object();
                case 3->rig.session++;case 4->rig.preset++;case 5->rig.revision++;case 6->rig.keys[0]=20;case 7->rig.input.cancel();
            }
            rig.post();rig.post();check(rig.wire.isEmpty(),"pending release cannot cross lifecycle or preset/mapping replacement "+changed);
        }
        for(int invalid=0;invalid<10;invalid++) {
            Rig blocked=new Rig(0,false);blocked.down.add(blocked.keys[0]);blocked.tick();
            var id=blocked.input.current().identity();blocked.wire.clear();blocked.down.clear();blocked.pre();
            switch(invalid) {
                case 0->blocked.terminal=true;case 1->blocked.activated=false;case 2->blocked.overloadFine=false;
                case 3->blocked.interfering=true;case 4->blocked.learned=false;case 5->blocked.parentLearned=false;
                case 6->blocked.mapped=false;case 7->blocked.enabled=false;case 8->blocked.level=4;case 9->blocked.category="teleporter";
            }
            blocked.post();blocked.post();
            check(blocked.wire.equals(List.of("abort:"+id.wire())),"late invalid release aborts exactly once before server commit "+invalid);
        }
        Rig rig=new Rig(0,false);rig.down.add(rig.keys[0]);rig.tick();var first=rig.input.current().identity();rig.wire.clear();
        rig.down.clear();rig.pre();rig.down.add(rig.keys[0]);rig.pre();var second=rig.input.current().identity();
        rig.post();
        check(rig.wire.equals(List.of("release:"+first.wire(),"start:"+second.wire())),"rapid repress sends prior release before fresh start");
    }
    private static void receipts() {
        var state = new RemoteScatterRelayState();
        check(state.accept(f(1,1,1,0,List.of()),true), "unpaid edit has no fabricated nodes");
        check(state.accept(f(1,2,0,400,List.of(A)),true), "first real node starts immutable token");
        check(!state.accept(f(1,2,0,400,List.of(A)),true), "duplicate receipt rejected");
        var selected = frame(2,5,3,0,RemoteScatterRelayState.ENDPOINT,395,List.of(A),END,List.of(segment(SOURCE,A.center()),segment(A.center(),END)));
        check(state.accept(selected,true), "server-selected endpoint becomes fixed");
        check(!state.accept(frame(2,5,4,0,RemoteScatterRelayState.ENDPOINT,394,List.of(A),END.add(v(.1,0,0)),selected.path()),true), "camera or target motion cannot move selected endpoint");
        check(!state.accept(f(2,4,0,400,List.of(A)),true), "heartbeat cannot reset paid lifetime");
        check(!state.accept(f(2,4,0,394,List.of(new Node(0,A.center().add(v(.1,0,0)),A.normal()))),true), "node center cannot move");
        check(!state.accept(f(2,4,1,394,List.of(new Node(0,A.center(),v(1,0,0)))),true), "even edit receipts cannot rotate an existing fixed node");
        check(state.accept(f(3,4,0,390,List.of(A,B)),true), "appending next node clears the prior endpoint");
        Vector secondEnd = v(8,64,24);
        check(state.accept(frame(4,5,5,0,7,388,List.of(A,B),secondEnd,List.of(segment(SOURCE,A.center()),segment(A.center(),B.center()),segment(B.center(),secondEnd))),true), "new endpoint may be selected only after node append");
        var truncated = frame(5,5,6,2,4,380,List.of(A,B),secondEnd,List.of(segment(SOURCE,A.center()),segment(A.center(),B.center()),segment(B.center(),v(8,64,20))));
        check(state.accept(truncated,true), "native target body may truncate final leg before fixed endpoint");
        check(RemoteScatterRelayState.status(truncated,380).confirmedDamage(), "only FIRE DAMAGED confirms health loss");
        check(!state.accept(frame(4,5,7,0,7,378,List.of(A,B),secondEnd,truncated.path()),true), "older input cannot take back token");
        check(state.accept(f(6,7,3,0,List.of()),true), "clear retires receipt token");
        check(!state.accept(frame(6,5,99,2,4,370,NODES,END,PATH),true), "ended token cannot resurrect");
        state.cancel(2,10);state.cancel(1,999);
        check(!state.accept(frame(10,6,1,1,0,0,List.of(),ZERO,List.of()),true), "cancel-before-receipt watermark survives old epoch");
        check(state.accept(frame(11,6,1,1,0,0,List.of(),ZERO,List.of()),true), "fresh physical input may start next token");
        state.clear();check(!state.accept(frame(11,6,2,0,0,400,List.of(A),ZERO,List.of()),true), "same connection lifecycle clear retains replay fence");
        check(!RemoteScatterRelayState.valid(frame(1,1,1,0,4,400,NODES,END,PATH)), "preview cannot claim damage");
        check(!RemoteScatterRelayState.valid(frame(1,1,1,2,3,400,NODES,END,PATH)), "refusal cannot emit solid ray");
        var gap = new ArrayList<>(PATH);gap.set(1,segment(A.center().add(v(.001,0,0)),B.center()));
        check(!RemoteScatterRelayState.valid(frame(1,1,1,2,4,400,NODES,END,gap)), "path gap rejected");
        var invented = new ArrayList<>(PATH);invented.set(0,segment(SOURCE,v(0,64,9)));invented.set(1,segment(v(0,64,9),B.center()));
        check(!RemoteScatterRelayState.valid(frame(1,1,1,2,4,400,NODES,END,invented)), "relay turn belongs to exact ordered node center");
        check(!RemoteScatterRelayState.valid(frame(1,1,1,0,0,400,List.of(A),ZERO,List.of(segment(SOURCE,v(0,64,32.001))))), "a leg never exceeds thirty-two meters");
        check(!RemoteScatterRelayState.valid(frame(1,1,1,0,0,400,List.of(A),ZERO,List.of(segment(SOURCE,SOURCE)))), "zero-length receipt segment rejected");
        denied(()->new Node(4,v(0,64,20),LOOK), "fifth node rejected");
        denied(()->new Node(0,A.center(),v(0,0,2)), "non-unit normal rejected");
        check(!RemoteScatterRelayState.hasEndpoint(new Frame(selected.id(),4,1,6,394,SOURCE,List.of(A),v(4,64,7),ZERO,List.of())), "invalid edit ghost never becomes a false endpoint");
        state.resetSession();
        for(int i=1;i<=65;i++) {
            var packet = new Frame(new Identity(i,new UUID(253,i),1,1,1),1,0,0,400,SOURCE,List.of(A),ZERO,ZERO,List.of());
            check(state.accept(packet,false)==(i<=64), "bounded observer contexts");
        }
        check(state.size()==64&&state.watermarkCount()==64,"overflow never allocates a replay fence");
    }
    private static void feedback() {
        var placing = frame(1,1,1,EDIT,PREPARED,0,List.of(),ZERO,List.of());
        check(RemoteScatterRelayState.valid(placing) && RemoteScatterRelayState.status(placing,0).key().equals("placing"),
                "crouch placement preview never claims to be plain-hold measurement");
        check(RemoteScatterRelayState.status(frame(1,1,1,ACTIVE,PREPARED,400,NODES,END,PATH),400).key().equals("prepared"),
                "ordinary prepared route keeps its original instructions");
        List<Integer> feedbackStatuses = List.of(TOO_FAR,SEGMENT_TOO_FAR,TOO_CLOSE,FULL,BODY_BLOCKED,NO_SPACE,UNLOADED,MEASURING,CLEARING);
        List<String> feedbackKeys = List.of("too_far","segment_too_far","too_close","full","body_blocked","no_space","unloaded","measuring","clearing");
        for (int i = 0; i < feedbackStatuses.size(); i++) {
            int status = feedbackStatuses.get(i);
            for (int phase : List.of(ACTIVE,EDIT,ENDED)) {
                var receipt = frame(1,1,1,phase,status,0,List.of(),ZERO,List.of());
                check(RemoteScatterRelayState.valid(receipt), "server feedback is accepted without inventing paid nodes: " + status + "/" + phase);
                check(RemoteScatterRelayState.status(receipt,0).key().equals(feedbackKeys.get(i)), "feedback reason survives phase mapping: " + status);
                check(!RemoteScatterRelayState.status(receipt,0).confirmedDamage(), "feedback never confirms native damage: " + status);
            }
            check(!RemoteScatterRelayState.valid(frame(1,1,1,FIRE,status,400,NODES,END,PATH)),
                    "placement and hold feedback cannot create an authoritative solid ray: " + status);
        }
        for (int status = PREPARED; status <= CLEARING; status++) {
            boolean fireStatus = status == MISS || status == BLOCKED || status == DAMAGED || status == ABSORBED;
            var shot = frame(1,1,1,FIRE,status,399,NODES,END,PATH);
            check(RemoteScatterRelayState.valid(shot) == fireStatus, "FIRE retains exactly the original four shot outcomes: " + status);
            check(RemoteScatterRelayState.status(shot,399).confirmedDamage() == (status == DAMAGED),
                    "only the original FIRE DAMAGED receipt claims a hit: " + status);
        }
        check(!RemoteScatterRelayState.valid(frame(1,1,1,FIRE,DAMAGED,0,List.of(),ZERO,List.of())),
                "native damage still requires an actual relay route");
        for (int status : List.of(-1,CLEARING+1,Integer.MAX_VALUE)) {
            var unknown = frame(1,1,1,EDIT,status,0,List.of(),ZERO,List.of());
            check(!RemoteScatterRelayState.valid(unknown), "unknown status remains outside the receipt protocol");
            check(RemoteScatterRelayState.status(unknown,0).key().equals("refused"), "unknown feedback has a generic refusal fallback");
        }
        var ended = frame(1,1,2,ENDED,PREPARED,0,List.of(),ZERO,List.of());
        check(RemoteScatterRelayState.terminalStatus(null,ended).key().equals("ended"), "a terminal receipt needs no preceding marker");
        for (int status = PREPARED; status <= CLEARING; status++) {
            var previous = frame(1,1,1,EDIT,status,0,List.of(),ZERO,List.of());
            boolean refusal = status == BLOCKED || status == REFUSED || status == DEGENERATE || status >= TOO_FAR && status <= UNLOADED;
            String expected = refusal ? RemoteScatterRelayState.status(previous,0).key() : "ended";
            var terminal = RemoteScatterRelayState.terminalStatus(previous,ended);
            check(terminal.key().equals(expected) && terminal.count() == 0 && terminal.seconds() == -1 && !terminal.confirmedDamage(),
                    "empty retirement preserves only real refusal reasons, never placement/measurement/clear modes: " + status);
            var paid = frame(1,1,1,EDIT,status,399,NODES,ZERO,List.of());
            check(RemoteScatterRelayState.terminalStatus(paid,ended).key().equals("ended"),
                    "clearing a paid route never retains its earlier feedback as a failed first placement: " + status);
        }
        var state = new RemoteScatterRelayState();
        var blocked = frame(1,1,1,ACTIVE,BLOCKED,0,List.of(),ZERO,List.of());
        check(state.accept(blocked,true), "obstructed first placement accepts an unpaid BLOCKED receipt");
        var previous = state.current(OWNER);
        check(state.accept(ended,true) && state.current(OWNER) == null, "immediate ENDED retires the failed placement context");
        check(RemoteScatterRelayState.terminalStatus(previous,ended).key().equals("blocked"),
                "ACTIVE BLOCKED followed immediately by ENDED retains the obstruction cue");
    }
    private static void resultNotes() {
        List<Integer> outcomes = List.of(DAMAGED,ABSORBED,MISS,BLOCKED);
        List<String> keys = List.of("damaged","absorbed","miss","blocked");
        for (int outcome = 0; outcome < outcomes.size(); outcome++) {
            var state = new RemoteScatterRelayState(); var note = new ResultNote(); var lease = new Lease();
            var shot = frame(1,5,1,FIRE,outcomes.get(outcome),400,NODES,END,PATH);
            check(state.accept(shot,true), "only an accepted authenticated FIRE is handed to result text");
            note.received(shot); lease.received(shot);
            check(note.remaining()==25 && note.status().key().equals(keys.get(outcome))
                    && note.status().confirmedDamage()==(outcomes.get(outcome)==DAMAGED),
                    "each real shot keeps its own result; FIRE alone never means damage");
            var preview = frame(1,5,2,ACTIVE,ENDPOINT,400,NODES,END,PATH);
            check(state.accept(preview,true), "ordinary same-input preview follows a result");
            note.received(preview); lease.received(preview);
            check(note.remaining()==25 && note.status().key().equals(keys.get(outcome)),
                    "normal preview preserves the preceding result text");
            for (int age = 1; age < 25; age++) {
                note.tick(false,true);
                check(lease.tick(false,true) && lease.remaining()==400-age
                        && note.remaining()==25-age && note.status().key().equals(keys.get(outcome)),
                        "result is readable for exactly its independent twenty-five effective client ticks");
                if (age==FLASH_TICKS) {
                    check(FLASH_TICKS==6 && RemoteScatterRelayMesh.path(shot,v(0,64,-5),LOOK,age-1).size()>0
                            && RemoteScatterRelayMesh.path(shot,v(0,64,-5),LOOK,age).isEmpty()
                            && note.remaining()==19,
                            "original solid VFX expires at six while text alone remains nineteen ticks");
                }
            }
            note.tick(false,true);
            check(note.status()==null && note.remaining()==0 && lease.tick(false,true) && lease.remaining()==375
                    && state.current(OWNER).equals(preview),
                    "twenty-fifth tick expires only text and cannot renew or retire the paid server lease");
            note.received(shot); note.received(preview);
            check(note.status()==null && note.remaining()==0, "expired result cannot be revived by replay or preview");
        }
        var note = new ResultNote();
        var shot = frame(1,5,7,FIRE,DAMAGED,399,NODES,END,PATH);
        note.received(shot); note.tick(false,true);
        var preview = frame(1,5,8,ACTIVE,ENDPOINT,398,NODES,END,PATH);
        note.received(preview); note.received(shot);
        check(note.remaining()==24 && note.status().key().equals("damaged"),
                "higher preview sequence and stale FIRE cannot renew result duration");
        note.received(frame(1,5,8,FIRE,ABSORBED,398,NODES,END,PATH));
        check(note.remaining()==24 && note.status().key().equals("damaged"),
                "duplicate sequence cannot replace damage with a different claimed outcome");
        note.received(null);
        note.received(frame(2,6,9,FIRE,PREPARED,398,NODES,END,PATH));
        note.received(frame(2,6,9,ACTIVE,DAMAGED,398,NODES,END,PATH));
        note.received(frame(2,6,9,FIRE,DAMAGED,398,List.of(),ZERO,List.of()));
        check(note.remaining()==24 && note.status().key().equals("damaged"),
                "invalid frames neither erase an existing result nor open a new identity");
        note.received(frame(1,5,9,FIRE,MISS,397,NODES,END,PATH));
        check(note.remaining()==25 && note.status().key().equals("miss") && !note.status().confirmedDamage(),
                "a newer accepted shot replaces old damage with its actual non-damage result");
        note.received(frame(1,5,10,EDIT,MEASURING,396,NODES,ZERO,List.of()));
        check(note.status()==null && note.remaining()==0, "EDIT immediately clears prior result text");
        for (Identity next : List.of(new Identity(9,OWNER,2,2,5),new Identity(9,OWNER,3,1,5),
                new Identity(9,OWNER,2,1,6),new Identity(10,OWNER,2,1,5),new Identity(9,new UUID(253,2),2,1,5))) {
            note = new ResultNote(); note.received(shot);
            note.received(new Frame(next,8,ACTIVE,ENDPOINT,398,SOURCE,NODES,END,ZERO,PATH));
            check(note.status()==null && note.remaining()==0, "new input, epoch, token, entity or owner clears previous result");
        }
        note = new ResultNote(); note.received(shot);
        note.received(frame(1,5,8,ENDED,PREPARED,0,List.of(),ZERO,List.of()));
        note.received(frame(1,5,9,FIRE,DAMAGED,397,NODES,END,PATH));
        check(note.status()==null && note.remaining()==0, "ENDED clears text and prevents same-identity resurrection");
        note = new ResultNote(); note.received(shot); note.tick(false,false); note.tick(false,true); note.received(shot);
        check(note.status()==null && note.remaining()==0,
                "menu/F1/invalid presentation discards text without replay when visible again");
        note = new ResultNote(); note.received(shot); note.tick(true,true); note.tick(false,true); note.received(shot);
        check(note.status()==null && note.remaining()==0, "pause clears even when the rest of the HUD remains eligible and cannot queue a replay");
        note = new ResultNote(); note.received(shot); note.clear(); note.received(shot);
        check(note.status()==null && note.remaining()==0,
                "explicit abort/reload/world/session clear retains the duplicate receipt fence");
        note = new ResultNote(); note.received(preview); note.newInput();
        note.received(frame(1,5,9,FIRE,DAMAGED,397,NODES,END,PATH));
        check(note.status()==null && note.remaining()==0,
                "fresh input fences an identity learned from ACTIVE before a late old FIRE can create text");
        note.received(frame(1,5,10,ACTIVE,ENDPOINT,396,NODES,END,PATH));
        note.received(frame(1,5,11,FIRE,DAMAGED,395,NODES,END,PATH));
        check(note.status()==null, "old-identity preview cannot reopen text after the physical input fence");
        note.received(frame(2,5,12,ACTIVE,ENDPOINT,394,NODES,END,PATH));
        check(note.status()==null, "replacement accepted ACTIVE identity carries no predicted result");
        note.received(frame(2,5,13,FIRE,ABSORBED,393,NODES,END,PATH));
        check(note.remaining()==25 && note.status().key().equals("absorbed") && !note.status().confirmedDamage(),
                "replacement identity may display its own subsequently accepted actual FIRE outcome");
        note.newInput();
        check(note.status()==null && note.remaining()==0, "fresh physical input also discards already visible result text immediately");
        note = new ResultNote(); note.newInput(); note.received(shot);
        check(note.remaining()==25 && note.status().key().equals("damaged"),
                "first physical input with no old identity does not block its first accepted result");
        var state = new RemoteScatterRelayState(); note = new ResultNote();
        check(state.accept(shot,true), "result integration begins with a real accepted receipt"); note.received(shot);
        var newInput = frame(2,5,8,ACTIVE,ENDPOINT,398,NODES,END,PATH);
        check(state.accept(newInput,true), "new authenticated input replaces prior local identity"); note.received(newInput);
        if (state.accept(shot,true)) note.received(shot);
        check(note.status()==null && state.current(OWNER).equals(newInput),
                "existing STATE gate prevents an old identity from restoring text after replacement");
        state.retire(newInput.id()); note.clear();
        var late = frame(2,5,9,FIRE,DAMAGED,397,NODES,END,PATH);
        if (state.accept(late,true)) note.received(late);
        check(note.status()==null, "retired or expired token cannot create new text through the receipt gate");
    }
    private static void lifetime() {
        for(int level=0;level<=7;level++)check(RemoteScatterRelayState.eligible(level,true,true)==(level==5),"NEW253 requires LV5");
        check(!RemoteScatterRelayState.eligible(5,false,true)&&!RemoteScatterRelayState.eligible(5,true,false),"parent and skill learning required");
        var lease=new RemoteScatterRelayState.Lease();lease.received(f(1,1,1,0,List.of()));
        for(int i=1;i<100;i++){if(i%4==0)lease.received(f(1,i+1,1,0,List.of()));check(lease.tick(false,true),"unpaid edit timeout bounded");}
        check(!lease.tick(false,true),"unpaid edit reaches its own timeout");
        lease=new RemoteScatterRelayState.Lease();lease.received(f(1,1,0,400,List.of(A)));
        for(int i=1;i<400;i++){if(i%4==0)lease.received(f(2,i+1,0,400,NODES));check(lease.tick(false,true),"new nodes and refreshed receipts cannot renew lifetime");}
        check(!lease.tick(false,true)&&lease.remaining()==0,"exactly four hundred ticks ends paid lease");
        lease=new RemoteScatterRelayState.Lease();lease.received(f(1,1,0,400,NODES));
        for(int i=0;i<50;i++)check(lease.tick(true,true)&&lease.remaining()==400&&lease.quiet()==0,"pause preserves timers");
        for(int i=1;i<12;i++)check(lease.tick(false,true),"brief packet gap tolerated");
        check(lease.tick(false,true)&&!lease.visible(),"missing server heartbeat only hides visuals at twelve ticks");
        var state=new RemoteScatterRelayState();var original=f(1,1,0,400,NODES);state.accept(original,true);
        var input=new RemoteScatterRelayInputState();var identity=new HighPowerInput.Input(0,1,9,2);
        input.begin(identity,19,false);input.flushQueuedStart();input.release(identity);input.accepted(1,2);
        for(int i=0;i<28;i++)check(lease.tick(false,true),"quiet token remains bounded and alive through recoverable packet gap");
        check(!lease.visible()&&lease.remaining()==360&&input.permits(1,2),"quiet does not cancel the physical accepted input or refresh lifetime");
        var heartbeat=f(1,2,0,359,NODES);
        check(state.accept(heartbeat,true),"fresh same-token heartbeat passes preserved generation fence");
        lease.received(heartbeat);
        check(lease.visible()&&lease.remaining()==359&&input.permits(1,2),"authenticated heartbeat resumes visuals with reduced remaining life");
        check(!state.accept(original,true),"pre-gap replay cannot resume or renew relay");
        for(int i=1;i<359;i++)check(lease.tick(false,true),"silence can hide but cannot extend or prematurely end server lifetime");
        check(!lease.tick(false,true)&&!lease.visible(),"actual immutable expiry retires even while quiet");
        state.retire(heartbeat.id());input.ended(1,2);
        check(!state.accept(f(1,3,0,1,NODES),true)&&!input.permits(1,2),"expired token cannot resurrect with late heartbeat");
        check(RemoteScatterRelayState.status(frame(1,1,1,0,7,399,NODES,END,PATH),399).key().equals("endpoint"),"fixed endpoint has distinct ready state");
        check(!RemoteScatterRelayState.status(frame(1,1,1,2,1,399,NODES,END,PATH),399).confirmedDamage(),"accepted ray with no native health change remains a miss");
    }
    private static void geometry() {
        var preview=frame(1,1,1,0,7,400,NODES,END,PATH);
        var fire=frame(2,1,2,2,4,399,NODES,END,PATH);
        Vector camera=v(0,64,-5);
        var dots=RemoteScatterRelayMesh.path(preview,camera,LOOK,0);
        check(!dots.isEmpty()&&dots.stream().allMatch(f->f.kind()==FoldedMirrorMesh.PLANNED),"entire five-leg preview is dotted only");
        var solid=RemoteScatterRelayMesh.path(fire,camera,LOOK,0);
        check(solid.stream().anyMatch(f->f.kind()==FoldedMirrorMesh.BEAM_CORE)&&solid.stream().noneMatch(f->f.kind()==FoldedMirrorMesh.PLANNED),"only paid FIRE uses solid original green-white rays");
        check(solid.stream().noneMatch(f->f.kind()==FoldedMirrorMesh.REFRACTION),"relay never invents a specular reflection or target hit spark");
        check(RemoteScatterRelayMesh.path(fire,camera,LOOK,6).isEmpty(),"solid flash retires after six ticks");
        check(RemoteScatterRelayMesh.labels(preview).size()==10,"four node numbers, five distances and one fixed endpoint label");
        for(int i=0;i<4;i++) {
            Node node=NODES.get(i);var plate=RemoteScatterRelayMesh.plate(node,false,1,0);
            check(plate.size()==144&&plate.stream().anyMatch(f->f.texture()==FoldedMirrorMesh.SHIELD_TEXTURE),"every node borrows original mdshield plate material");
            check(RemoteScatterRelayMesh.reached(fire,node),"confirmed crossing can energize its actual node");
            check(!RemoteScatterRelayMesh.reached(preview,node),"preview cannot energize actual contact");
            for(var face:plate)for(var vertex:face.vertices()) {
                Vector delta=vertex.subtract(node.center());
                check(Math.abs(delta.dot(node.normal()))<=.02000001,"visible node never expands physical slab thickness");
            }
        }
        for(var face:solid)for(var vertex:face.vertices())check(vertex.finite(),"all finite ray vertices");
        var clipped=RemoteScatterRelayMesh.clipped(solid,v(0,64,7.9),LOOK);
        for(var face:clipped)for(var vertex:face.vertices())check(vertex.subtract(v(0,64,7.9)).dot(LOOK)>=FoldedMirrorMesh.NEAR_DEPTH-1e-7,"near-eye clipping never opens an x-ray hole");
        Vector far=v(29_999_000,64,29_999_000),close=far.add(v(.03125,.015625,.0625));
        check(RemoteScatterRelayMesh.relative(close,far).equals(v(.03125,.015625,.0625)),"camera translation remains double precision at distant coordinates");
        List<Node> maximumNodes=new ArrayList<>();List<Segment> maximumPath=new ArrayList<>();Vector start=SOURCE;
        for(int i=0;i<4;i++){Vector end=SOURCE.add(v(0,0,32*(i+1)));maximumNodes.add(new Node(i,end,LOOK));maximumPath.add(segment(start,end));start=end;}
        Vector endpoint=SOURCE.add(v(0,0,160));maximumPath.add(segment(start,endpoint));
        var maximum=frame(1,1,1,0,7,400,maximumNodes,endpoint,maximumPath);
        check(RemoteScatterRelayState.valid(maximum),"exact four-node/five-leg 160-meter route is valid");
        check(RemoteScatterRelayMesh.path(maximum,camera,LOOK,0).size()==250,"all five thirty-two-meter legs are separately dotted without widening");
        check(FoldedMirrorGeometry.MAX_REFLECTIONS==2&&FoldedMirrorGeometry.MAX_LENGTH==40,"NEW221 limits remain untouched");
    }
    private static void budgets() {
        var budget=new RemoteScatterRelayMesh.SceneBudget();
        check(budget.takeGroup(250,20)&&budget.takeGroup(250,20)&&budget.takeGroup(250,20),"whole owner geometry and labels share the scene budget");
        check(!budget.takeGroup(220,0)&&budget.used()==810,"reservation overflow cannot partially emit rays");
        check(budget.takeGroup(200,14)&&budget.used()==1024&&budget.usedVertices()==4096&&!budget.takeGroup(0,1),"hard whole-scene 4096 VERTEX cap includes text glyphs");
        check(!budget.takeGroup(Integer.MAX_VALUE,Integer.MAX_VALUE)&&!budget.takeGroup(-1,0),"overflow and negative reservations rejected");
        check(RemoteScatterRelayMesh.SPAWNED_PARTICLES==0,"no particles spawned");
        check(RemoteScatterRelayMesh.enhanced(true,false)&&!RemoteScatterRelayMesh.enhanced(false,false)&&!RemoteScatterRelayMesh.enhanced(true,true),"one decision selects whole-group safe fallback");
        String longHud=RemoteScatterRelayMesh.boundedHudText("中😀".repeat(100));
        check(longHud.codePointCount(0,longHud.length())==79&&longHud.endsWith("…")&&(79*2+1)*4<=RemoteScatterRelayMesh.MAX_HUD_QUADS*4,
                "separate GUI text, shadow and bar are bounded inside the reserved scene vertices");
    }
    private static Frame shifted(Frame source,int owner) {
        Vector offset=v(owner*2.5,0,0);
        return new Frame(new Identity(owner+9,new UUID(253,owner+1),2,source.id().input(),source.id().token()),source.sequence(),source.phase(),source.status(),source.remainingTicks(),source.ownerFeet().add(offset),
                source.plates().stream().map(n->new Node(n.index(),n.center().add(offset),n.normal())).toList(),source.ghostCenter().add(offset),source.ghostNormal(),
                source.path().stream().map(s->segment(s.start().add(offset),s.end().add(offset))).toList());
    }
    private static void compactSceneBound() {
        // A near plane crosses at most two boundary edges of this convex 12-sector ellipse.
        // Each crossed fan triangle can replace one quad with three, so at most 12 + 2*2 = 16.
        int fillsPerNode=RemoteScatterRelayMesh.COMPACT_ELLIPSE_STEPS+4;
        Random random=new Random(253);
        for(int sample=0;sample<256;sample++) {
            Vector normal=FoldedMirrorGeometry.unit(v(random.nextDouble()-.5,random.nextDouble()-.5,random.nextDouble()-.5)).orElseThrow();
            Node node=new Node(0,SOURCE,normal);
            Vector look=FoldedMirrorGeometry.unit(v(random.nextDouble()-.5,random.nextDouble()-.5,random.nextDouble()-.5)).orElseThrow();
            var fills=RemoteScatterRelayMesh.compactPlate(node,false).stream().filter(f->f.kind()==FoldedMirrorMesh.FILL).toList();
            var ghost=RemoteScatterRelayMesh.compactPlate(node,true);
            for(double offset:List.of(-1.,-.6,-.1,0.,.1,.6,1.)) {
                Vector camera=SOURCE.subtract(look.scale(FoldedMirrorMesh.NEAR_DEPTH+offset));
                check(RemoteScatterRelayMesh.clipped(fills,camera,look).size()<=fillsPerNode,"convex fan clipping retains the proven sixteen-quad node bound");
                check(RemoteScatterRelayMesh.clipped(ghost,camera,look).size()<=18,"six ghost quads clip to at most eighteen quads");
            }
        }
        check(FoldedMirrorMesh.RAY_NEAR_DEPTH-FoldedMirrorGeometry.BEAM_RADIUS>FoldedMirrorMesh.NEAR_DEPTH,
                "all compact ribbon vertices clear the later clipping plane, so ray quad counts cannot expand");
        int nodeQuads=4*4*fillsPerNode,previewQuads=4*5*RemoteScatterRelayMesh.COMPACT_DASHES_PER_LEG;
        int fireQuads=4*2*5*6,ghostQuads=4*6*3;
        int sceneQuads=nodeQuads+previewQuads+fireQuads+ghostQuads+RemoteScatterRelayMesh.MAX_LABEL_QUADS+RemoteScatterRelayMesh.MAX_HUD_QUADS;
        check(sceneQuads==1016&&sceneQuads*4==4064&&sceneQuads<=RemoteScatterRelayMesh.MAX_SCENE_QUADS,
                "all legal four-controller compact geometry including clipping, ghosts, world labels and HUD has a 4064-vertex bound");
        var plate=RemoteScatterRelayMesh.compactPlate(A,false);
        var fill=plate.stream().filter(f->f.kind()==FoldedMirrorMesh.FILL).findFirst().orElseThrow();
        var rim=plate.stream().filter(f->f.kind()==FoldedMirrorMesh.RIM).findFirst().orElseThrow();
        var ghost=RemoteScatterRelayMesh.compactPlate(A,true).getFirst();
        List<FoldedMirrorMesh.Face> maximal=new ArrayList<>(Collections.nCopies(nodeQuads+previewQuads+fireQuads,fill));
        maximal.addAll(Collections.nCopies(ghostQuads,ghost));
        maximal.addAll(Collections.nCopies(RemoteScatterRelayMesh.MAX_SCENE_QUADS,rim));
        for(boolean shader:List.of(false,true)) {
            var selected=RemoteScatterRelayMesh.scene(maximal,maximal,RemoteScatterRelayMesh.MAX_LABEL_QUADS,shader);
            check(selected.vertices()==4064&&selected.faces().size()==sceneQuads-RemoteScatterRelayMesh.MAX_LABEL_QUADS-RemoteScatterRelayMesh.MAX_HUD_QUADS,
                    "even the independently combined clipping maxima retain every essential face and ghost after only rim decoration is removed");
        }
    }
    private static void fourControllerBudget() {
        var preview=frame(1,1,1,0,7,400,NODES,END,PATH);
        var fire=frame(2,1,2,2,4,399,NODES,END,PATH);
        for(int sample=0;sample<24;sample++) {
            Vector camera=sample==0?v(0,64,-5):sample>=20?v(0,64.05+(sample-20)*.02,8):v((sample%5)*4,64+(sample%3-1)*.6,6+(sample/5)*4);
            Vector look=sample>=20?v(0,1,0):FoldedMirrorGeometry.unit(v((sample%3-1)*.2,(sample%4-1)*.12,1)).orElseThrow();
            List<FoldedMirrorMesh.Face> detailed=new ArrayList<>(),compact=new ArrayList<>();
            for(int owner=0;owner<4;owner++) {
                Frame planned=shifted(preview,owner),shot=shifted(fire,owner);
                for(var node:planned.plates()) {
                    detailed.addAll(RemoteScatterRelayMesh.plate(node,false,1,1));
                    compact.addAll(RemoteScatterRelayMesh.compactPlate(node,false));
                }
                detailed.addAll(RemoteScatterRelayMesh.plate(planned.plates().getFirst(),true,1,0));
                compact.addAll(RemoteScatterRelayMesh.compactPlate(planned.plates().getFirst(),true));
                detailed.addAll(RemoteScatterRelayMesh.path(planned,camera,look,0));
                compact.addAll(RemoteScatterRelayMesh.compactPath(planned,camera,look,0));
                for(int age=0;age<2;age++) {
                    detailed.addAll(RemoteScatterRelayMesh.path(shot,camera,look,age));
                    compact.addAll(RemoteScatterRelayMesh.compactPath(shot,camera,look,age));
                }
            }
            var clipped=RemoteScatterRelayMesh.clipped(detailed,camera,look);
            var compactClipped=RemoteScatterRelayMesh.clipped(compact,camera,look);
            for(boolean shader:List.of(false,true)) {
                var scene=RemoteScatterRelayMesh.scene(clipped,compactClipped,RemoteScatterRelayMesh.MAX_LABEL_QUADS,shader);
                check(scene.vertices()==scene.faces().size()*4+(RemoteScatterRelayMesh.MAX_LABEL_QUADS+RemoteScatterRelayMesh.MAX_HUD_QUADS)*4&&scene.vertices()<=4096,
                        "four controllers, previews, two fire flashes, ghosts, world labels and GUI glyph shadows remain <=4096 actual vertices");
                check(!scene.faces().isEmpty(),"budget fallback preserves useful visible geometry");
                for(var face:scene.faces())for(var vertex:face.vertices())check(vertex.finite()&&vertex.subtract(camera).dot(look)>=FoldedMirrorMesh.NEAR_DEPTH-1e-7,
                        "compact fallback remains finite and near-plane clipped");
                if(sample==0) {
                    check(scene.compact()&&!scene.enhanced(),"crowded scene chooses one original-texture fallback rather than stacking renderers");
                    check(scene.faces().stream().filter(f->f.kind()==FoldedMirrorMesh.FILL).count()==4*4*RemoteScatterRelayMesh.COMPACT_ELLIPSE_STEPS,
                            "all sixteen true relay nodes remain represented");
                    check(scene.faces().stream().filter(f->f.kind()==FoldedMirrorMesh.BEAM_CORE).count()==4*2*5*3,
                            "all forty accepted ray legs remain complete after decoration is removed");
                    check(scene.faces().stream().filter(f->f.kind()==FoldedMirrorMesh.PLANNED).count()==4*5*RemoteScatterRelayMesh.COMPACT_DASHES_PER_LEG,
                            "all twenty preview legs retain bounded dotted guides");
                }
            }
        }
    }
    private static void integration() throws Exception {
        String client=Files.readString(Path.of("src/main/java/cn/academy/port/client/AcademyClient.java"));
        String effects=Files.readString(Path.of("src/main/java/cn/academy/port/client/RemoteScatterRelayEffects.java"));
        String types=Files.readString(Path.of("src/main/java/cn/academy/port/client/RemoteScatterRelayRenderTypes.java"));
        String server=Files.readString(Path.of("src/main/java/cn/academy/port/AcademyGameplay.java"));
        for(String action:List.of("hp_remote_relay_start","hp_remote_relay_release","hp_remote_relay_abort"))
            check(client.contains("\""+action+"\"")&&server.contains("case \""+action+"\""),"client transport reaches exact native server action "+action);
        int postStart=client.indexOf("@SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void finishContextDisposals(ClientTickEvent.Post event)");
        check(postStart>=0,"relay dispatch remains in the real lowest-priority native Post callback");
        String post=client.substring(postStart,client.indexOf('\n',postStart));
        check(post.contains("finishLocalDisposals();flushFoldedMirrorStart();flushDisintegrationSweepStart();")
                &&post.endsWith("flushDisplacedProjectionStart();flushRemoteScatterRelayRelease();flushRemoteScatterRelayStart();flushRemoteScatterRelayRelease();}"),
                "relay release/start/release appends after all retained native Post dispatches");
        check(post.indexOf("flushRemoteScatterRelayStart();")==post.lastIndexOf("flushRemoteScatterRelayStart();")
                &&post.indexOf("flushRemoteScatterRelayRelease();")<post.indexOf("flushRemoteScatterRelayStart();")
                &&post.lastIndexOf("flushRemoteScatterRelayRelease();")>post.indexOf("flushRemoteScatterRelayStart();"),
                "Post sends a prior release before the single start and same-frame release afterward");
        check(client.contains("remoteScatterRelayInputs.queuedFor(remoteScatterRelayDispatchContext(mc))"),"actual dispatch fences world player connection preset revision mapping");
        check(client.contains("remoteScatterRelayInputs.releaseWhenUp(physicalDown(relayHeld.physicalKey()))"),"physical release survives silent binding replacement");
        check(client.contains("remoteScatterRelayInputs.reset();RemoteScatterRelayEffects.clear();"),"actual session clear owns all relay input and visuals");
        check(client.contains("RemoteScatterRelayEffects.canRecallLocal()"),"free clear remains available during shot cooldown");
        for(String guard:List.of("capturedWorld","capturedPlayer","capturedConnection","capturedGeneration","sameInputSessionEpoch","owner.getUUID().equals(packet.ownerUuid())"))
            check(effects.contains(guard),"receipt lifecycle provenance includes "+guard);
        check(effects.contains("RemoteScatterRelayState.terminalStatus(old == null ? null : old.frame, receipt); statusTicks = 30;")
                && effects.contains("!mc.isPaused() && statusTicks > 0 && --statusTicks == 0"),
                "production terminal HUD retains the tested empty-refusal cue for thirty unpaused ticks");
        check(effects.contains("Font.DisplayMode.NORMAL")&&effects.contains("world.clip(new ClipContext")&&!effects.contains("SEE_THROUGH"),"labels are depth-tested and terrain-occluded");
        check(effects.contains("text.getString().length() * 2")&&effects.contains("RemoteScatterRelayMesh.scene(scene, compactScene, glyphQuads, materialReady)"),"production scene admission includes actual glyph shadow quads");
        check(effects.contains("selected.enhanced(), selected.compact()")&&effects.contains("!compact && FoldedMirrorMesh.culled(face)"),"compact finite ray ribbons use the complete original-texture fallback with correct two-sided rendering");
        check(types.contains("LEQUAL_DEPTH_TEST")&&types.contains("COLOR_WRITE")&&types.contains("GameRenderer::getPositionTexColorShader"),"fallback preserves terrain occlusion and no depth write");
        check(effects.contains("RemoteScatterRelayHudLayout.fit(")
                && effects.contains("mc.font.split(Component.literal(text), width), mc.font::width")
                && effects.contains("HighPowerRecoveryHud.relayReservedBox(width, height)"),
                "localized HUD wraps at native size around the actual reserved instrument layout");
        check(effects.indexOf("if (!STATE.accept(receipt, local)) return;") < effects.indexOf("RESULT.received(receipt);")
                && effects.contains("if (local) {") && effects.contains("RESULT.tick(mc.isPaused(), localEligible() && !mc.options.hideGui"),
                "result note is wired after authentication and clears when paused or hidden");
        check(effects.contains("void reload(RegisterShadersEvent event) { resultGeneration++; RESULT.clear(); }")
                && effects.contains("terminalStatus = null; statusTicks = 0; RESULT.clear();")
                && effects.contains("hudText(\"last_result\", hudText(\"result.\" + result.key()))"),
                "reload and abort clear the explicitly labelled actual result note");
        check(client.contains("remoteScatterRelayInputs.begin(identity,node.key,down(mc.options.keyShift),remoteScatterRelayDispatchContext(mc));\n            RemoteScatterRelayEffects.clearResultNote();"), "fresh local relay input clears only the previous result before a new receipt");
        Path icons=Path.of("src/main/resources/assets/academy/textures/abilities/meltdowner/skills");
        check(Arrays.equals(Files.readAllBytes(icons.resolve("remote_scatter_relay.png")),Files.readAllBytes(icons.resolve("folded_mirror_array.png"))),"new icon path is byte-identical original artwork");
        check(Files.readString(icons.resolve("remote_scatter_relay.LICENSE.txt")).contains("Lambda Innovation"),"reused icon retains source attribution");
        check(!effects.contains("addParticle(")&&!effects.contains("spawnParticle(")&&!effects.contains("setPos("),"presentation creates neither particles nor movement");
    }
}

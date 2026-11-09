package cn.academy.port.client;

import static cn.academy.port.client.ScatterLocatorState.*;
import cn.academy.port.core.TargetedContextTermination;
import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Plate;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.client.FoldedMirrorState.Identity;
import cn.academy.port.client.FoldedMirrorState.Segment;
import cn.academy.port.client.ScatterLocatorState.Frame;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** NEW223 real binding edges, post-native ordering, reserved leases and preview-only physical geometry. */
public final class ScatterLocatorVisualRegressionTest {
    private static int checks;
    private static final UUID OWNER = new UUID(223, 1);
    private static final Vector ZERO = v(0,0,0), SOURCE = v(0,64,0), LOOK = v(0,0,1);
    private static final Plate A = new Plate(0, v(0,64,8), LOOK);
    private static Vector v(double x,double y,double z) { return new Vector(x,y,z); }
    private static void check(boolean value,String message) { checks++; if(!value)throw new AssertionError(message); }
    private static void denied(Runnable action,String message) { try { action.run(); } catch(IllegalArgumentException expected) {checks++;return;} throw new AssertionError(message); }
    private static Frame frame(long input,long token,long sequence,int phase,int status,int remaining,List<Plate> plates,List<Segment> path,double held) {
        return new Frame(new Identity(9,OWNER,2,input,token),sequence,phase,status,remaining,SOURCE,plates,
                phase==EDIT?A.center():ZERO,phase==EDIT?A.normal():ZERO,path,held);
    }
    private static Frame f(long input,long sequence,int phase,int remaining,List<Plate> plates) {
        return frame(input,5,sequence,phase,PREPARED,remaining,plates,List.of(),plates.isEmpty()||phase==ENDED?0:24);
    }
    public static void main(String[] args) throws Exception {
        input(); binding(); deferredNativeOrdering(); deferredFinalAim(); recallDuringCooldown(); borrowingSlot();
        receipts(); lifetime(); geometry(); borrowedShot(); integration();
        System.out.println("PASS ScatterLocatorVisualRegressionTest: "+checks+" checks");
    }
    private static void input() {
        var state = new ScatterLocatorInputState(); var press = new HighPowerInput.Input(0, 1, 9, 2);
        state.begin(press, -100, true);
        check(state.holding() && state.current().editing() && state.current().physicalKey() == -100, "press snapshots physical key and sneak mode");
        check(state.queuedStart() != null && !state.permits(1, 2), "Pre queues input without granting unsent receipt permission");
        check(state.flushQueuedStart().start().equals(press) && state.flushQueuedStart() == null, "Post dispatches one queued authenticated start");
        for (int i = 0; i < 100; i++) check(state.releaseWhenUp(true) == null && state.current().editing(), "held key cannot locally reinterpret mode or auto-fire");
        check(state.release(new HighPowerInput.Input(1, 1, 9, 2)) == null, "wrong slot cannot release another owner");
        check(state.release(press).equals(press) && !state.holding(), "release owns exactly one request");
        state.accepted(1, 2);
        check(state.releaseWhenUp(false) == null && state.cancel(press) == null && state.permits(1, 2), "delegate disposal after release preserves reserved locator");
        var second = new HighPowerInput.Input(0, 2, 9, 2); state.begin(second, -100, false); state.flushQueuedStart();
        check(state.permits(1, 2) && state.permits(2, 2) && !state.current().editing(), "new normal press retains prior reserved context until receipt");
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
        final ScatterLocatorInputState input = new ScatterLocatorInputState();
        final ClassicClientInputBinding binding;
        boolean editing, terminal, live = true, activated = true, overloadFine = true, interfering, consuming;
        boolean learned = true, parentLearned = true, mapped = true, enabled = true;
        String category = "meltdowner"; int cooldown, remaining = 400, quiet, level = 5;
        ScatterLocatorState.Frame array;
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
        public boolean registeredMapping(String skill) { return skill.equals("scatter_locator_plate") || skill.equals("folded_mirror_array"); }
        public boolean eligible(ClassicClientInputBinding.Node node) {
            if (node != null && node.skill.equals("folded_mirror_array")) return live && canUseAbility();
            boolean paid = array != null && input.permits(array.id().input(), array.id().epoch())
                    && ScatterLocatorState.recallAvailable(array, remaining, quiet);
            return live && category.equals("meltdowner") && canUseAbility() && mapped && enabled
                    && ScatterLocatorState.eligible(level, parentLearned, learned) && (cooldown == 0 || paid);
        }
        public String mappedSkill(int slot) { return slot == this.slot ? "scatter_locator_plate" : slot == (this.slot + 1) % 4 ? "folded_mirror_array" : ""; }
        public int mappedKey(int slot) { return keys[slot]; } public int slotCount() { return 4; }
        public void rawActivation(boolean desired) {} public void replaceOverrides(int[] keys) {}
        public long down(ClassicClientInputBinding.Node node) {
            if (node.skill.equals("folded_mirror_array")) { wire.add("221:start"); return ++next; }
            var identity = new HighPowerInput.Input(node.slot, ++next, node.actorId(), node.ownerEpoch());
            input.begin(identity, node.key, editing, context()); return next;
        }
        private HighPowerInput.Input identity(ClassicClientInputBinding.Node node, long n) {
            return HighPowerInput.parse(new HighPowerInput.Input(node.slot, n, node.actorId(), node.ownerEpoch()).wire());
        }
        public void up(ClassicClientInputBinding.Node node, long n) { if (node.skill.equals("folded_mirror_array")) return; release(input.release(identity(node, n))); }
        public void abort(ClassicClientInputBinding.Node node, long n) { if (node.skill.equals("folded_mirror_array")) return; var value = input.cancel(identity(node, n)); if (value != null) wire.add("abort:" + value.wire()); }
        public boolean cancelPending(ClassicClientInputBinding.Node node, long n) { return false; }
        public void direction(ClassicClientInputBinding.ContextIdentity context, int direction, ClassicClientInputBinding.Edge edge, boolean silent) {}
        public void localContextEnd(ClassicClientInputBinding.ContextIdentity context) {}
        public void contextAbort(TargetedContextTermination.Request request) {}
        public void modernCleanup(Collection<ClassicClientInputBinding.Node> defaults) {}
        void release(HighPowerInput.Input identity) { if (identity != null) input.deferRelease(identity,context()); }
        void flushRelease() {
            var identity=input.flushDeferredRelease(context()); if(identity==null)return;
            if(!live||terminal||!activated||!overloadFine||interfering||!learned||!parentLearned||!mapped||!enabled||(level<2||level>5)||!category.equals("meltdowner")) {
                var cancelled=input.cancel(); if(cancelled!=null)wire.add("abort:"+cancelled.wire()); return;
            }
            wire.add("release:"+identity.wire());
        }
        ScatterLocatorInputState.DispatchContext context() {
            return new ScatterLocatorInputState.DispatchContext(world, player, connection, session, preset, revision, mappedSkill(slot) + ":" + keys[slot]);
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
            check(rig.input.permits(original.input(), original.epoch()) && rig.wire.size() == 2, "released delegate disposal retains reserved stationary locator");
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
        var pending = new ScatterLocatorInputState(); var identity = new HighPowerInput.Input(0, 1, 9, 2);
        pending.begin(identity, 19, true); pending.release(identity);
        check(pending.cancel(identity).equals(identity) && pending.flushQueuedStart() == null, "explicit cancellation after an early queued release discards both unsent actions");
        pending.begin(new HighPowerInput.Input(0, 2, 9, 2), 19, true); pending.reset();
        check(pending.flushQueuedStart() == null && !pending.permits(2, 2), "session reset discards deferred packets and receipt permissions");
    }
    private static void recallDuringCooldown() {
        for (int slot = 0; slot < 4; slot++) {
            Rig rig = paidRig(slot); rig.down.add(rig.keys[slot]); rig.tick();
            check(rig.input.holding() && rig.next == 2 && rig.wire.size() == 1, "reserved locator can start free recall during positive shot cooldown in every real slot");
            for (int i = 0; i < 12; i++) rig.tick();
            check(rig.wire.size() == 1 && rig.array.phase() == ScatterLocatorState.ACTIVE, "holding recall does not locally clear array or predict an emission");
            rig.down.remove(rig.keys[slot]); rig.tick();
            check(rig.wire.size() == 2 && rig.wire.getLast().startsWith("release:"), "recall release reaches server exactly once despite cooldown");
            rig = paidRig(slot); rig.down.add(rig.keys[slot]); rig.tick(); rig.down.remove(rig.keys[slot]); rig.tick();
            check(rig.wire.size() == 2 && rig.array.path().isEmpty() && rig.array.phase() == ScatterLocatorState.ACTIVE,
                    "short release during cooldown sends intent only; client cannot invent a attack or impact");
            for (int blocked = 0; blocked < 14; blocked++) {
                Rig denied = paidRig(slot);
                switch (blocked) {
                    case 0 -> denied.array = f(1, 2, 1, 0, List.of());
                    case 1 -> denied.remaining = 0;
                    case 2 -> denied.quiet = ScatterLocatorState.QUIET_TICKS;
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
                    case 13 -> denied.level = 1;
                    default -> throw new AssertionError();
                }
                denied.down.add(denied.keys[slot]); denied.tick();
                check(denied.wire.isEmpty(), "cooldown recall exception preserves unpaid/lifetime/session and all other ability gates " + blocked);
            }
        }
        check(!ScatterLocatorState.recallAvailable(f(2, 3, 3, 0, List.of()), 400, 0), "ended array cannot reopen recall despite forged remaining time");
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
                case 6->blocked.mapped=false;case 7->blocked.enabled=false;case 8->blocked.level=1;case 9->blocked.category="teleporter";
            }
            blocked.post();blocked.post();
            check(blocked.wire.equals(List.of("abort:"+id.wire())),"late invalid release aborts exactly once before server commit "+invalid);
        }
        Rig rig=new Rig(0,false);rig.down.add(rig.keys[0]);rig.tick();var first=rig.input.current().identity();rig.wire.clear();
        rig.down.clear();rig.pre();rig.down.add(rig.keys[0]);rig.pre();var second=rig.input.current().identity();
        rig.post();
        check(rig.wire.equals(List.of("release:"+first.wire(),"start:"+second.wire())),"rapid repress sends prior release before fresh start");
    }
    private static void borrowingSlot() {
        for (int slot=0;slot<4;slot++) {
            Rig rig=paidRig(slot); int borrowingSlot=(slot+1)%4;
            rig.down.add(rig.keys[borrowingSlot]); rig.tick();
            check(rig.input.permits(1,2)&&rig.wire.equals(List.of("221:start")),"selecting existing NEW221 bound slot preserves released locator identity");
            rig.down.clear();rig.tick();
            check(rig.input.permits(1,2)&&rig.wire.equals(List.of("221:start")),"NEW221 physical release does not cancel locator");
        }
        for(int level=2;level<=5;level++)check(eligible(level,true,true),"locator eligible from level2 through5");
        check(!eligible(1,true,true)&&!eligible(6,true,true)&&!eligible(2,false,true)&&!eligible(2,true,false),"catalog eligibility requires NEW221 and learned locator");
    }
    private static void receipts() {
        var state=new ScatterLocatorState();
        check(state.accept(f(1,1,EDIT,0,List.of()),true),"unpaid edit accepted without invented plane");
        check(state.accept(f(1,2,ACTIVE,400,List.of(A)),true),"first placement initializes400tick lease and heldCP");
        check(!state.accept(f(1,2,ACTIVE,400,List.of(A)),true),"duplicate sequence rejected");
        check(!state.accept(f(1,3,ACTIVE,401,List.of(A)),true),"lifetime cannot exceed400");
        check(state.accept(f(2,3,PREVIEW,390,List.of(A)),true),"fresh input accepts preview without fire status");
        check(!state.accept(f(2,4,ACTIVE,391,List.of(A)),true),"heartbeat cannot renew original lifetime");
        check(!state.accept(frame(2,5,4,ACTIVE,PREPARED,389,List.of(A),List.of(),25),true),"heldCP cannot change after placement");
        check(!state.accept(f(2,4,ACTIVE,389,List.of(new Plate(0,A.center().add(v(.1,0,0)),LOOK))),true),"fixed world center cannot move");
        Plate rotated=new Plate(0,A.center(),v(1,0,0));
        check(!state.accept(f(2,4,ACTIVE,389,List.of(rotated)),true),"rotation needs authenticated edit phase");
        check(state.accept(f(3,4,EDIT,389,List.of(A)),true),"edit preserves reserved plane and original lifetime");
        check(state.accept(f(3,5,ACTIVE,388,List.of(rotated)),true),"edit release may rotate the existing center");
        check(!state.accept(f(2,6,ACTIVE,387,List.of(rotated)),true),"old input cannot retake token");
        check(state.accept(f(4,6,ENDED,0,List.of()),true)&&state.size()==0,"terminal receipt retires all presentation");
        check(!state.accept(f(4,7,ACTIVE,386,List.of(rotated)),true),"ended token never resurrects");
        state.cancel(2,10);state.cancel(1,999);
        check(!state.accept(frame(10,6,1,EDIT,0,0,List.of(),List.of(),0),true),"cancel-before-receipt fences stale response");
        check(state.accept(frame(11,6,1,EDIT,0,0,List.of(),List.of(),0),true),"fresh input permits replacement");
        state.clear();check(!state.accept(frame(11,6,2,ACTIVE,0,400,List.of(A),List.of(),24),true),"world/menu clear preserves same-connection tombstone");
        for(int status:List.of(4,5,7,-1))for(int phase=0;phase<=3;phase++)
            check(!valid(frame(1,1,1,phase,status,phase==ENDED?0:400,List.of(A),List.of(),phase==ENDED?0:24)),"locator rejects hit, absorption and unknown outcomes in every phase");
        check(!valid(frame(1,1,1,ACTIVE,0,400,List.of(A,new Plate(1,v(4,64,8),LOOK)),List.of(),24)),"second plane rejected");
        check(!valid(frame(1,1,1,ACTIVE,0,400,List.of(A),List.of(new Segment(SOURCE,SOURCE.add(v(0,0,40.001)))),24)),"path cannot exceed forty meters");
        check(!valid(frame(1,1,1,ACTIVE,0,400,List.of(A),List.of(),Double.NaN)),"nonfinite heldCP rejected");
        check(!valid(frame(1,1,1,ENDED,0,0,List.of(),List.of(),24)),"ended marker cannot retain heldCP");
        state.resetSession();
        for(int i=1;i<=65;i++) {
            var packet=new Frame(new Identity(i,new UUID(223,i),1,1,1),1,ACTIVE,0,400,SOURCE,List.of(A),ZERO,ZERO,List.of(),24);
            check(state.accept(packet,false)==(i<=64),"observer contexts have hard64-owner cap");
        }
        check(state.size()==64&&state.watermarkCount()==64,"overflow allocates neither marker nor watermark");
    }
    private static void lifetime() {
        var lease=new ScatterLocatorState.Lease();lease.received(f(1,1,EDIT,0,List.of()));
        check(lease.tick(false,true)&&lease.visible(),"unpaid edit may be visible without lifetime");
        lease.received(f(1,2,ACTIVE,400,List.of(A)));
        for(int i=0;i<12;i++)check(lease.tick(false,true),"packet gap preserves reserved server token");
        check(!lease.visible()&&lease.remaining()==388,"packet gap hides stale geometry without extending lifetime");
        lease.received(f(2,3,PREVIEW,390,List.of(A)));
        check(lease.visible()&&lease.remaining()==388,"new preview cannot renew local lifetime after gap");
        int remaining=lease.remaining();check(lease.tick(true,true)&&lease.remaining()==remaining,"pause does not consume game lifetime");
        while(lease.remaining()>1)lease.tick(false,true);
        check(!lease.tick(false,true)&&!lease.visible(),"exact lifetime expiry hides preview and plane");
        var status=status(f(2,4,PREVIEW,200,List.of(A)),200,0);
        check(status.heldCp()==24&&status.seconds()==10&&status.recallable()&&status.key().equals("preview"),"HUD conveys held controlCP, remaining lifetime and recallability");
        check(!status(f(2,4,PREVIEW,200,List.of(A)),200,QUIET_TICKS).recallable(),"stale preview cannot advertise recall");
    }
    private static void geometry() {
        List<Segment> legs=List.of(new Segment(SOURCE,A.center()),new Segment(A.center(),v(0,64,-24)));
        Frame preview=frame(1,5,1,PREVIEW,MISS,400,List.of(A),legs,24);
        check(valid(preview)&&ScatterLocatorState.mirror(preview).phase()==FoldedMirrorState.ACTIVE,"PREVIEW is explicitly adapted to non-fire state");
        Vector camera=v(2,65,-4),look=v(0,0,1);
        var path=ScatterLocatorMesh.path(preview,camera,look);
        check(!path.isEmpty()&&path.stream().allMatch(face->face.kind()==FoldedMirrorMesh.PLANNED),"all clipped preview legs remain dashed even after short-tap receipt");
        check(path.stream().noneMatch(face->face.kind()==FoldedMirrorMesh.BEAM_CORE||face.kind()==FoldedMirrorMesh.REFRACTION||face.kind()==FoldedMirrorMesh.BEAM_HALO),"locator never creates solid attacks, impacts or refraction flashes");
        var plate=ScatterLocatorMesh.plate(preview,4);
        check(plate.equals(FoldedMirrorMesh.plate(A,false,4,null,0)),"exact original finite ellipse and mdshield material reused");
        check(plate.stream().allMatch(face->face.material().contactPower()==0),"unfired locator cannot energize a hit contact");
        var editing=frame(2,5,2,EDIT,0,399,List.of(A),List.of(),24);
        check(ScatterLocatorMesh.plate(editing,0).size()==FoldedMirrorMesh.plate(A,false).size(),"existing edit renders one plane instead of stacking ghost and actual plane");
        var arrow=ScatterLocatorMesh.arrow(preview,camera,look);
        check(arrow.size()==3&&arrow.stream().allMatch(face->face.kind()==FoldedMirrorMesh.PLANNED),"small direction arrow remains planning geometry");
        var blockedBeforePlate=frame(1,5,1,PREVIEW,BLOCKED,400,List.of(A),List.of(new Segment(SOURCE,v(0,64,4))),24);
        check(ScatterLocatorMesh.arrow(blockedBeforePlate,camera,look).isEmpty(),"obstruction before plate cannot invent an outgoing direction arrow");
        check(ScatterLocatorMesh.arrow(f(1,1,ACTIVE,400,List.of(A)),camera,look).isEmpty(),"stored surface normal is never misrepresented as an outgoing ray");
        var budget=new ScatterLocatorMesh.Budget();
        for(int i=0;i<4;i++) {
            var faces=new ArrayList<>(plate);faces.addAll(path);faces.addAll(arrow);
            var clipped=ScatterLocatorMesh.clipped(faces,camera,look);
            check(budget.take(clipped.size()),"four normal visible locator groups fit reserved HUD and mesh budget");
        }
        check(budget.vertices()<=4096&&!budget.take(1024),"oversize complete group never exceeds hard4096vertex cap");
        check(ScatterLocatorMesh.SPAWNED_PARTICLES==0,"locator spawns no particles");
        check(ScatterLocatorMesh.plate(f(2,4,ENDED,0,List.of()),0).isEmpty()&&ScatterLocatorMesh.path(f(2,4,ENDED,0,List.of()),camera,look).isEmpty(),"terminal frame produces no geometry");
    }
    private static FoldedMirrorState.Frame borrowedFrame(UUID owner,long sequence,int phase,int status,int remaining) {
        return new FoldedMirrorState.Frame(new Identity(9,owner,2,1,5),sequence,phase,status,remaining,SOURCE,
                phase==FoldedMirrorState.ENDED?List.of():List.of(A),ZERO,ZERO,
                phase==FoldedMirrorState.ENDED?List.of():List.of(new Segment(SOURCE,A.center()),new Segment(A.center(),v(0,64,-24))));
    }
    private static void borrowedShot() {
        var state=new FoldedMirrorState();var tail=new FoldedMirrorShotTail();
        var fire=borrowedFrame(OWNER,1,FoldedMirrorState.FIRE,FoldedMirrorState.MISS,0);
        check(FoldedMirrorState.valid(fire)&&state.accept(fire,true)&&tail.accepted(fire),"actual borrowed221 FIRE0 is valid and retains only its accepted path");
        var ended=borrowedFrame(OWNER,2,FoldedMirrorState.ENDED,FoldedMirrorState.PREPARED,0);
        check(state.accept(ended,true)&&state.size()==0&&!tail.accepted(ended)&&!tail.empty(),"immediate ENDED retires controller but does not swallow confirmed shot flash");
        check(!state.accept(borrowedFrame(OWNER,3,FoldedMirrorState.FIRE,FoldedMirrorState.DAMAGED,0),true),"retired token cannot create a late second flash");
        check(!state.accept(fire,true)&&!tail.accepted(fire),"duplicate accepted fire never resets flash age");
        tail.tick(true,f->true);check(tail.current(OWNER).age()==0,"paused flash does not advance");
        for(int tick=1;tick<FoldedMirrorState.FLASH_TICKS;tick++) {
            tail.tick(false,f->true);check(!tail.empty()&&tail.current(OWNER).age()==tick,"borrowed native shot keeps original flash duration");
        }
        tail.tick(false,f->true);check(tail.empty(),"borrowed shot ends exactly at native duration");
        check(!tail.accepted(borrowedFrame(OWNER,1,FoldedMirrorState.FIRE,FoldedMirrorState.REFUSED,0)),"refusal cannot create a detached attack path");
        check(!tail.accepted(borrowedFrame(OWNER,1,FoldedMirrorState.FIRE,FoldedMirrorState.DAMAGED,400)),"ordinary paid221 presentation remains unchanged");
        for(int lifecycle=0;lifecycle<6;lifecycle++) {
            tail.accepted(fire);tail.tick(false,f->false);check(tail.empty(),"invalid world/player/menu/category/epoch/ownership clears retained shot "+lifecycle);
        }
        tail.accepted(fire);tail.cancel(OWNER,2,1);check(tail.empty(),"explicit input abort removes borrowed shot immediately");
        tail.accepted(fire);tail.clear();state.clear();check(tail.empty()&&!state.accept(fire,true),"reload and lifecycle cleanup retain sequence fences");
        for(int i=1;i<=65;i++)check(tail.accepted(borrowedFrame(new UUID(221,i),1,FoldedMirrorState.FIRE,FoldedMirrorState.BLOCKED,0))==(i<=64),"borrowed flash owners remain bounded");
        var faces=FoldedMirrorMesh.path(fire,v(2,65,-4),LOOK,0);
        check(faces.stream().anyMatch(face->face.kind()==FoldedMirrorMesh.BEAM_CORE),"accepted221 shot retains original actual solid-ray renderer");
        check(ScatterLocatorState.heldText(.02).equals("0.02")&&ScatterLocatorState.heldText(.00001).equals("0.00001")
                &&ScatterLocatorState.heldText(24).equals("24"),"heldCP never rounds positive control reservation down to zero");
    }
    private static void integration() throws Exception {
        String source=Files.readString(Path.of("src/main/java/cn/academy/port/client/AcademyClient.java"));
        int start=source.indexOf("private static void flushScatterLocatorStart"),end=source.indexOf("public static boolean scatterLocatorInputMatches",start);
        String flush=source.substring(start,end);
        check(flush.contains("scatterLocatorInputs.queuedFor(scatterLocatorDispatchContext(mc))")&&flush.contains("request(\"hp_scatter_locator_start\",dispatch.start().wire())"),"actual client flush revalidates native-ordered context before authenticated start");
        check(source.contains("flushScatterLocatorRelease();flushScatterLocatorStart();flushScatterLocatorRelease();flushDisplacedProjectionStart();"),"actual Post sends older release before fresh start and early-tap release after it");
        int press=source.indexOf("if(skill.equals(cn.academy.port.highpower.ScatterLocatorPlate.ID)){");
        String pressBody=source.substring(press,source.indexOf("if(skill.equals(",press+10));
        check(!pressBody.contains("request("),"Pre press cannot dispatch before vanilla crouch");
        String effects=Files.readString(Path.of("src/main/java/cn/academy/port/client/ScatterLocatorEffects.java"));
        check(effects.contains("capturedGeneration")&&effects.contains("capturedWorld")&&effects.contains("capturedConnection")&&effects.contains("sameInputSessionEpoch"),"receipt scheduling fences world, connection, generation and session replacement");
        check(effects.contains("state.presets.currentContains(ScatterLocatorPlate.ID)")&&!effects.contains("delegateActive("),"marker eligibility does not depend on selected221 slot");
        check(effects.contains("RemoteScatterRelayHudLayout.fit")&&effects.contains("reservedHudBoxes"),"status reuses measured non-overlapping native HUD layout");
        String mirror=Files.readString(Path.of("src/main/java/cn/academy/port/client/FoldedMirrorEffects.java"));
        int tailBranch=mirror.indexOf("if (receipt.phase() == FoldedMirrorState.FIRE && receipt.remainingTicks() == 0)");
        String branch=mirror.substring(tailBranch,mirror.indexOf("if (old == null",tailBranch));
        check(branch.contains("capturedTailGeneration == tailGeneration")&&branch.contains("return;"),"reload-fenced borrowed fire cannot fall through into ordinary live markers");
        for(String action:List.of("hp_scatter_locator_start","hp_scatter_locator_release","hp_scatter_locator_abort"))check(action.length()<=32,"action fits existing max32 wire field");
    }
}

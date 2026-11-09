package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.nio.file.Files;
import java.nio.file.Path;
import cn.academy.port.core.TargetedContextTermination;
import java.util.*;
import java.util.UUID;

/** NEW231 real physical latches, authenticated finite receipts and true-perspective planar visual contracts. */
public final class DisintegrationSweepVisualRegressionTest {
    private static int checks;
    private static final UUID OWNER = new UUID(231, 1);
    private static final Vector SOURCE = v(0, 64, 0), LOOK = v(0, 0, 1), ACROSS = v(1, 0, 0), ZERO = v(0, 0, 0);
    private static Vector v(double x, double y, double z) { return new Vector(x, y, z); }
    private static Vector unit(Vector p) { return FoldedMirrorGeometry.unit(p).orElseThrow(); }
    private static void check(boolean success, String description) { checks++; if (!success) throw new AssertionError(description); }
    private static void denied(Runnable action, String description) { try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; } throw new AssertionError(description); }
    private static DisintegrationSweepState.Frame frame(long input, long token, long sequence, int phase, int status, int remaining, boolean mining, Vector origin, Vector forward, Vector across, List<DisintegrationSweepState.Segment> path) {
        return new DisintegrationSweepState.Frame(new DisintegrationSweepState.Identity(9, OWNER, 2, input, token), sequence, phase, status, remaining, mining,
                SOURCE.subtract(v(0, 1.5, 0)), origin, forward, across, -.2, .2, path, List.of());
    }
    private static DisintegrationSweepState.Frame f(long input, long token, long sequence, int phase, int remaining) {
        return frame(input, token, sequence, phase, 0, remaining, false, SOURCE, LOOK, ACROSS, List.of());
    }
    private static DisintegrationSweepState.Segment segment(int status) { return new DisintegrationSweepState.Segment(SOURCE, SOURCE.add(LOOK.scale(48)), status); }
    public static void main(String[] args) throws Exception {
        input(); deferredNativeOrdering(); receipts(); leases(); afterglow(); geometry(); perspective(); budget(); integration();
        System.out.println("PASS DisintegrationSweepVisualRegressionTest: " + checks + " checks");
    }
    private static void input() {
        var state = new DisintegrationSweepInputState(); var press = new HighPowerInput.Input(0, 1, 9, 2);
        state.begin(press, -100, true);
        check(state.holding() && state.current().mining() && state.current().physicalKey() == -100, "press snapshots physical key and sneak mode");
        check(state.queuedStart() != null && !state.permits(1, 2), "Pre queues input without granting unsent receipt permission");
        check(state.flushQueuedStart().start().equals(press) && state.flushQueuedStart() == null, "Post dispatches one queued authenticated start");
        for (int i = 0; i < 100; i++) check(state.releaseWhenUp(true) == null && state.current().mining(), "held key cannot locally reinterpret mode or auto-fire");
        check(state.release(new HighPowerInput.Input(1, 1, 9, 2)) == null, "wrong slot cannot release another owner");
        check(state.release(press).equals(press) && !state.holding(), "release owns exactly one request");
        state.accepted(1, 2);
        check(state.releaseWhenUp(false) == null && state.cancel(press) == null && state.permits(1, 2), "delegate disposal after release preserves paid sweep");
        var second = new HighPowerInput.Input(0, 2, 9, 2); state.begin(second, -100, false); state.flushQueuedStart();
        check(state.permits(1, 2) && state.permits(2, 2) && !state.current().mining(), "new normal press retains prior paid context until receipt");
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
        final DisintegrationSweepInputState input = new DisintegrationSweepInputState();
        final ClassicClientInputBinding binding;
        boolean mining, terminal, live = true, activated = true, overloadFine = true, interfering, consuming;
        boolean learned = true, parentLearned = true, mapped = true, enabled = true;
        String category = "meltdowner"; int cooldown, level = 3;
        Object world = new Object(), player = new Object(), connection = new Object();
        long session = 1, revision; int preset;
        boolean nativeSneaking;
        final List<Boolean> observedServerModes = new ArrayList<>();
        long next;
        Rig(int slot, boolean mining) { this.slot = slot; this.mining = mining; binding = new ClassicClientInputBinding(this); binding.runtime().updateDefaultGroup(); }
        public boolean available() { return live; } public boolean inGame() { return live; }
        public boolean categoryActivated() { return activated; }
        public boolean canUseAbility() { return activated && overloadFine && !interfering && !consuming; }
        public boolean terminalOpen() { return terminal; } public int actorId() { return 9; } public long ownerEpoch() { return 2; }
        public boolean physicalDown(int key) { return down.contains(key); }
        public boolean registeredMapping(String skill) { return skill.equals("disintegration_sweep"); }
        public boolean eligible(ClassicClientInputBinding.Node node) {
            return live && category.equals("meltdowner") && canUseAbility() && mapped && enabled
                    && DisintegrationSweepState.eligible(level, parentLearned, learned) && cooldown == 0;
        }
        public String mappedSkill(int slot) { return slot == this.slot ? "disintegration_sweep" : ""; }
        public int mappedKey(int slot) { return keys[slot]; } public int slotCount() { return 4; }
        public void rawActivation(boolean desired) {} public void replaceOverrides(int[] keys) {}
        public long down(ClassicClientInputBinding.Node node) {
            var identity = new HighPowerInput.Input(node.slot, ++next, node.actorId(), node.ownerEpoch());
            input.begin(identity, node.key, mining, context()); return next;
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
        DisintegrationSweepInputState.DispatchContext context() {
            return new DisintegrationSweepInputState.DispatchContext(world, player, connection, session, preset, revision, mappedSkill(slot) + ":" + keys[slot]);
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

            Rig toggled = new Rig(slot, false); toggled.nativeSneaking = true; toggled.down.add(toggled.keys[slot]); toggled.pre(); toggled.post();
            check(toggled.observedServerModes.equals(List.of(true)), "already active native toggle-crouch remains authoritative without synthetic key changes");
            toggled.nativeSneaking = false; toggled.down.clear(); toggled.tick();
            check(toggled.observedServerModes.equals(List.of(true)) && toggled.wire.size() == 2, "later crouch change cannot rewrite mode captured at authenticated press");

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
        var pending = new DisintegrationSweepInputState(); var identity = new HighPowerInput.Input(0, 1, 9, 2);
        pending.begin(identity, 19, true); pending.release(identity);
        check(pending.cancel(identity).equals(identity) && pending.flushQueuedStart() == null, "explicit cancellation after an early queued release discards both unsent actions");
        pending.begin(new HighPowerInput.Input(0, 2, 9, 2), 19, true); pending.reset();
        check(pending.flushQueuedStart() == null && !pending.permits(2, 2), "session reset discards deferred packets and receipt permissions");
    }
    private static void receipts() {
        var state = new DisintegrationSweepState(); var charging = f(1, 5, 1, 0, 100);
        check(state.accept(charging, true), "valid authenticated first charging warning");
        check(!state.accept(charging, true), "duplicate sequence cannot render twice");
        check(state.accept(f(1, 5, 2, 0, 96), true), "charge heartbeat consumes remaining time");
        check(!state.accept(f(1, 5, 3, 0, 97), true), "charge cannot refill its lease");
        var sweep = frame(1, 5, 3, 1, 4, 10, false, SOURCE, LOOK, ACROSS, List.of(segment(4)));
        check(state.accept(sweep, true), "first paid native receipt begins its own ten tick lease");
        check(!state.accept(f(1, 5, 4, 0, 80), true), "sweep cannot rewind into unpaid charge");
        check(!state.accept(frame(1, 5, 4, 1, 4, 9, false, SOURCE.add(v(.01,0,0)), LOOK, ACROSS, List.of(segment(4))), true), "receipt cannot move frozen release origin");
        check(!state.accept(frame(1, 5, 4, 1, 0, 9, true, SOURCE, LOOK, v(0,1,0), List.of()), true), "receipt cannot switch press-time mining mode");
        check(state.accept(f(1, 5, 4, 2, 0), true) && state.size() == 0, "terminal retires context");
        check(!state.accept(f(1, 5, 999, 1, 8), true), "terminal token never resurrects");
        state.cancel(2, 9); state.cancel(1, 999);
        check(!state.accept(f(9, 6, 1, 0, 100), true), "cancel-before-receipt fence cannot rewind");
        check(state.accept(f(10, 6, 1, 0, 100), true), "new physical input may start new token");
        state.clear(); check(!state.accept(f(10, 6, 2, 1, 10), true), "same-connection clear retains replay fence");
        check(!DisintegrationSweepState.valid(frame(1, 1, 1, 0, 4, 100, false, SOURCE, LOOK, ACROSS, List.of(segment(4)))), "charging never carries confirmed hit");
        check(!DisintegrationSweepState.valid(frame(1, 1, 1, 1, 4, 10, true, SOURCE, LOOK, v(0,1,0), List.of(segment(4)))), "vertical mining cannot claim entity damage");
        check(!DisintegrationSweepState.valid(frame(1, 1, 1, 1, 1, 10, false, SOURCE, LOOK, v(1,0,.01), List.of(segment(1)))), "nonorthogonal aim basis rejected");
        check(!DisintegrationSweepState.valid(frame(1, 1, 1, 1, 1, 10, false, SOURCE, LOOK, ACROSS, List.of(new DisintegrationSweepState.Segment(SOURCE, SOURCE.add(LOOK.scale(48.01)),1)))), "range is exactly48 meters");
        check(!DisintegrationSweepState.valid(frame(1, 1, 1, 1, 1, 10, false, SOURCE, LOOK, ACROSS, List.of(new DisintegrationSweepState.Segment(SOURCE.add(v(.251,0,0)),SOURCE.add(v(.251,0,40)),1)))), "current hand cannot escape release origin leash");
        var mining = new DisintegrationSweepState.Frame(new DisintegrationSweepState.Identity(9,OWNER,2,1,1),1,1,0,10,true,SOURCE,SOURCE,LOOK,v(0,1,0),-.2,.2,List.of(),java.util.Collections.nCopies(16,SOURCE.add(LOOK.scale(10))));
        check(DisintegrationSweepState.valid(mining), "all sixteen frozen mining marks are admitted without claiming sixteen removals per tick");
        var excess = new DisintegrationSweepState.Frame(mining.id(),1,1,0,10,true,SOURCE,SOURCE,LOOK,v(0,1,0),-.2,.2,List.of(),java.util.Collections.nCopies(17,SOURCE.add(LOOK.scale(10))));
        check(!DisintegrationSweepState.valid(excess), "seventeenth frozen cell mark is refused");
        state.resetSession();
        for (int i=1; i<=65; i++) {
            var f = new DisintegrationSweepState.Frame(new DisintegrationSweepState.Identity(i,new UUID(231,i),1,1,1),1,0,0,100,false,SOURCE,SOURCE,LOOK,ACROSS,-.2,.2,List.of(),List.of());
            check(state.accept(f,false)==(i<=64), "global observer context cap");
        }
        check(state.size()==64 && state.watermarkCount()==64, "overflow never allocates replay storage");
    }
    private static void leases() {
        for(int level=0;level<=7;level++)check(DisintegrationSweepState.eligible(level,true,true)==(level>=3&&level<=5),"only integerLV3..5 eligible");
        check(!DisintegrationSweepState.eligible(3,false,true)&&!DisintegrationSweepState.eligible(5,true,false),"both actual learned parent and skill required");
        var lease=new DisintegrationSweepState.Lease();lease.received(f(1,1,1,0,100));
        for(int i=1;i<100;i++){if(i%4==0)lease.received(f(1,1,i+1,0,100));check(lease.tick(false,true),"charge remains finite with honest heartbeat");}
        check(!lease.tick(false,true),"heartbeat cannot extend100 tick charge");
        lease=new DisintegrationSweepState.Lease();lease.received(f(1,1,1,0,100));lease.tick(false,true);lease.received(f(1,1,2,1,10));
        for(int i=1;i<10;i++){lease.received(f(1,1,i+2,1,10));check(lease.tick(false,true),"sweep uses its own decreasing ten tick lease");}
        check(lease.tick(false,true)&&lease.remaining()==0,"zero-remaining sweep boundary stays open for actual final server receipt");
        check(!lease.tick(false,true),"missing terminal expires one client boundary later without lease renewal");
        lease=new DisintegrationSweepState.Lease();lease.received(f(1,1,1,0,100));
        for(int i=0;i<1000;i++)check(lease.tick(true,true)&&lease.remaining()==100,"pause never advances or refills");
        check(!lease.tick(true,false),"session invalidation still retires while paused");
        for(int i=1;i<12;i++)check(lease.tick(false,true),"finite quiet grace");
        check(!lease.tick(false,true),"quiet stale warning ends at12 ticks");
    }
    private static void afterglow() {
        var receipt=frame(1,5,10,1,4,0,false,SOURCE,LOOK,ACROSS,List.of(segment(4)));var ended=f(1,5,11,2,0);
        var finalLease=new DisintegrationSweepState.Lease();finalLease.received(frame(1,5,9,1,1,1,false,SOURCE,LOOK,ACROSS,List.of(segment(1))));
        check(finalLease.tick(false,true)&&finalLease.remaining()==0,"server tick9 remaining1 then client Post does not prematurely end input ownership");
        finalLease.received(receipt);finalLease.received(ended);check(!finalLease.tick(false,true),"real tick10 terminal immediately ends active lease");
        var gate=new DisintegrationSweepState();check(gate.accept(receipt,true)&&gate.accept(ended,true)&&gate.size()==0,"final native receipt followed immediately by ENDED closes active ownership");
        var tail=new DisintegrationSweepState.Afterglow(receipt,ended);
        check(tail.tick(false,true),"receive then ENDED then ClientTick.Post keeps a bounded visual tail");
        var faces=DisintegrationSweepMesh.afterglow(tail,SOURCE,LOOK);
        check(!faces.isEmpty()&&faces.stream().anyMatch(face->face.kind()==DisintegrationSweepMesh.HIT_STAR),"last-tick confirmed native damage remains visible after immediate terminal receipt");
        check(faces.stream().allMatch(face->face.kind()==DisintegrationSweepMesh.TRAIL||face.kind()==DisintegrationSweepMesh.HIT_STAR),"terminal tail never restores warning or live attack core");
        for(int age=2;age<4;age++){check(tail.tick(false,true),"final receipt fades for fewer than four ticks");check(DisintegrationSweepMesh.afterglow(tail,SOURCE,LOOK).stream().filter(face->face.kind()==DisintegrationSweepMesh.TRAIL).allMatch(face->face.alpha()<=30),"terminal trail is explicitly dim");}
        check(!tail.tick(false,true)&&DisintegrationSweepMesh.afterglow(tail,SOURCE,LOOK).isEmpty(),"tail expires absolutely by fourth tick");
        check(!tail.tick(true,true)&&!tail.tick(false,true),"expired final receipt cannot restart through pause or later tick");
        check(!gate.accept(receipt,true)&&!gate.accept(frame(1,5,100,1,4,0,false,SOURCE,LOOK,ACROSS,List.of(segment(4))),true),"terminal replay fence blocks any replacement hit tail");
        tail=new DisintegrationSweepState.Afterglow(receipt,ended);check(!tail.tick(true,false)&&DisintegrationSweepMesh.afterglow(tail,SOURCE,LOOK).isEmpty()&&!tail.tick(false,true),"menu/cancel/world/player/session invalidation permanently clears visual tail even while paused");
        denied(()->new DisintegrationSweepState.Afterglow(f(1,5,10,0,10),ended),"unpaid warning cannot become terminal afterglow");
        denied(()->new DisintegrationSweepState.Afterglow(receipt,f(2,6,11,2,0)),"foreign terminal cannot authorize old afterglow");
        var miss=frame(1,5,10,1,1,0,false,SOURCE,LOOK,ACROSS,List.of(segment(1)));
        check(DisintegrationSweepMesh.afterglow(new DisintegrationSweepState.Afterglow(miss,ended),SOURCE,LOOK).stream().noneMatch(face->face.kind()==DisintegrationSweepMesh.HIT_STAR),"terminal miss never invents a hit star");
    }
    private static void geometry() {
        for(boolean mining:List.of(false,true)) {
            Vector across=mining?v(0,1,0):ACROSS;
            var charge=frame(1,1,1,0,0,100,mining,SOURCE,LOOK,across,List.of());
            var warnings=DisintegrationSweepMesh.warning(charge,SOURCE,LOOK);
            check(!warnings.isEmpty()&&warnings.stream().allMatch(face->face.kind()==DisintegrationSweepMesh.WARNING),"charge consists only of hollow warning lines");
            for(var face:warnings){
                Vector a=face.a().add(face.b()).scale(.5),b=face.c().add(face.d()).scale(.5);
                for(Vector p:List.of(a,b)){
                    Vector d=p.subtract(SOURCE);check(Math.abs(d.dot(LOOK.cross(across)))<1e-7,"every warning centerline remains in its exact frozen plane");
                    check(d.length()<=48+1e-7,"warning does not exceed48 meter radius");
                    double angle=Math.atan2(d.dot(across),d.dot(LOOK));
                    check(Math.abs(Math.abs(angle)-DisintegrationSweepState.HALF_ANGLE)<1e-7||d.length()>47.9,"interior contains no warning fill or broad slab");
                }
            }
            for(int status=1;status<=6;status++) {
                if(mining&&(status==4||status==5)||!mining&&status==6)continue;
                var f=frame(1,1,1,1,status,10,mining,SOURCE,LOOK,across,List.of(segment(status)));
                var faces=DisintegrationSweepMesh.path(f,SOURCE,LOOK,0);
                check(faces.stream().anyMatch(face->face.kind()==DisintegrationSweepMesh.BEAM_CORE),"native emitted rays retain narrow green-white core");
                check(DisintegrationSweepMesh.path(f,SOURCE,LOOK,1).stream().anyMatch(face->face.kind()==DisintegrationSweepMesh.BEAM_CORE),"receipt then ClientTick.Post then first render retains a visible narrow core");
                check(DisintegrationSweepMesh.path(f,SOURCE,LOOK,2).stream().filter(face->face.kind()!=DisintegrationSweepMesh.HIT_STAR).allMatch(face->face.kind()==DisintegrationSweepMesh.TRAIL&&face.alpha()<=40),"later receipts leave only dim non-damaging trails");
                check(faces.stream().anyMatch(face->face.kind()==DisintegrationSweepMesh.HIT_STAR)==(!mining&&status==4),"only actual entity damage creates tiny local stars");
                for(var face:faces)if(face.kind()==DisintegrationSweepMesh.HIT_STAR)for(var p:face.vertices())check(p.subtract(segment(status).end()).length()<.061,"stars remain within six centimeters of confirmed native endpoint");
                check(DisintegrationSweepMesh.path(f,SOURCE,LOOK,4).isEmpty(),"native ray trail lives at most four ticks");
                check(DisintegrationSweepMesh.path(f,SOURCE,v(0,0,-1),0).isEmpty(),"behind-camera ray never becomes mirrored fan");
            }
        }
        var near=new DisintegrationSweepMesh.Face(v(-1,0,-.5),v(1,0,-.5),v(1,0,.5),v(-1,0,.5),1,255,0,-1);
        var clipped=DisintegrationSweepMesh.nearClip(near,ZERO,LOOK);check(!clipped.isEmpty(),"crossing geometry clips real silhouette");
        for(var face:clipped)for(var p:face.vertices())check(p.z()>=.08-1e-12&&p.z()<=.5,"polygon clipping remains within real input face");
    }
    private static void perspective() {
        var f=frame(1,1,1,1,1,10,false,SOURCE,LOOK,ACROSS,List.of(segment(1)));int seen=0;
        for(double x:new double[]{-.6,-.1,0,.1,.6})for(double y:new double[]{-.15,0,.15})for(double z:new double[]{-.15,0,.15})for(int yaw:new int[]{-25,0,25})for(int pitch:new int[]{-15,0,15}){
            Vector camera=SOURCE.add(v(x,y,z));double ya=Math.toRadians(yaw),pi=Math.toRadians(pitch);
            Vector look=unit(v(Math.sin(ya)*Math.cos(pi),Math.sin(pi),Math.cos(ya)*Math.cos(pi))),right=unit(look.cross(v(0,1,0))),up=right.cross(look);
            for(var face:DisintegrationSweepMesh.path(f,camera,look,0)){
                Vector a=face.a().add(face.b()).scale(.5),b=face.c().add(face.d()).scale(.5);
                check(Math.abs(a.x())<1e-7&&Math.abs(b.x())<1e-7&&Math.abs(a.y()-64)<1e-7&&Math.abs(b.y()-64)<1e-7,"camera never repositions actual beam centerline");
                Vector[] center={a,a,b,b};int index=0;
                for(Vector vertex:face.vertices()){
                    Vector c=center[index++].subtract(camera),p=vertex.subtract(camera);double depth=p.dot(look),centerDepth=c.dot(look);
                    check(centerDepth>=.20-1e-8&&depth>.08,"centerline clips before perspective width expansion");
                    double angle=Math.hypot(p.dot(right)/depth-c.dot(right)/centerDepth,p.dot(up)/depth-c.dot(up)/centerDepth);
                    check(angle<=DisintegrationSweepMesh.MAX_ANGULAR_HALF_WIDTH+1e-8,"sweep core remains narrow under actual perspective");
                    for(int fov:new int[]{60,70,90,110})check(angle*812/Math.tan(Math.toRadians(fov/2.0))<3,"nativeFOV avoids first-person opaque wedge");seen++;
                }
            }
        }
        check(seen>10000,"moving-camera regression covers every first-person source offset");
    }
    private static void budget(){var budget=new DisintegrationSweepMesh.Budget();int count=0;for(int i=0;i<10000;i++)if(budget.take())count++;check(count==1536&&budget.used()==count&&DisintegrationSweepMesh.SPAWNED_PARTICLES==0,"global postclip face budget and zero spawned particles");}
    private static void integration() throws Exception {
        String effects=Files.readString(Path.of("src/main/java/cn/academy/port/client/DisintegrationSweepEffects.java"));
        check(effects.contains("DisintegrationSweep.clientReceiver = DisintegrationSweepEffects::receive")&&effects.contains("ClassicRenderTypes.world("),"typed payload reaches real native depth-tested rendering");
        check(effects.contains("generation != capturedGeneration")&&effects.contains("!AcademyClient.sameInputSessionEpoch(capturedSession)")&&effects.contains("owner.getUUID().equals(packet.ownerUuid())"),"queued receipts require exact current connection/world/player/generation identity");
        check(effects.contains("mc.screen == null")&&effects.contains("mc.isWindowActive()")&&effects.contains("mc.getCameraEntity() == mc.player"),"first-person local geometry honors focus menu and camera eligibility");
        String client=Files.readString(Path.of("src/main/java/cn/academy/port/client/AcademyClient.java"));
        check(client.contains("disintegrationSweepInputs.begin(identity,node.key,down(mc.options.keyShift),disintegrationSweepDispatchContext(mc))") && client.contains("disintegrationSweepInputs.releaseWhenUp(physicalDown(sweepHeld.physicalKey()))"), "actual all-slot production binding preserves press identity and fallback release");
        String flush=client.substring(client.indexOf("private static void flushDisintegrationSweepStart()"),client.indexOf("public static boolean disintegrationSweepInputMatches"));
        check(flush.contains("disintegrationSweepInputs.queuedFor(disintegrationSweepDispatchContext(mc))") && flush.indexOf("request(\"hp_disintegration_sweep_start\"") < flush.indexOf("releaseDisintegrationSweepInput(dispatch.release())"), "real Post revalidates captured dispatch context then sends start before queued release");
        check(client.contains("finishLocalDisposals();flushFoldedMirrorStart();flushDisintegrationSweepStart();")&&!flush.contains("ServerboundPlayerCommandPacket")&&!flush.contains("PRESS_SHIFT_KEY"), "real native Post ordering adds no synthetic crouch packet or trusted client mode");
        check(effects.contains("TAILS.size() < DisintegrationSweepState.MAX_CONTEXTS")&&effects.contains("TAILS.clear()")&&effects.contains("TAILS.remove(mc.player.getUUID())")&&effects.contains("DisintegrationSweepMesh.afterglow(entry.tail.fade"),"real terminal-tail adapter has fixed capacity and all cancellation paths");
        check(effects.contains("List<RenderOwner> visible")&&effects.contains("limit(DisintegrationSweepMesh.MAX_VISIBLE_CONTEXTS)"),"live owners and completed tails share one visible-owner cap and draw budget");
        check(effects.contains("loadedWarning(marker.frame)")&&effects.contains("loadedFrame(flash.frame)")&&!effects.contains("disableDepthTest")&&!effects.contains("addParticle("),"unloaded geometry is skipped without particles or depth bypass");
    }
}

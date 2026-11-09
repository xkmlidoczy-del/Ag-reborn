package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.WindPressureLift;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.server.level.ServerPlayer;

/** Real connected-server clocks, payments and lifecycle; declared step identities do not claim a LocalPlayer run. */
@GameTestHolder("academy_wind_pressure_lift")
@PrefixGameTestTemplate(false)
public final class HighPowerWindPressureLiftDualClockRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_wind_pressure_lift_dual_clock";
    private HighPowerWindPressureLiftDualClockRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void wind_lift_dual_first_paid_pre_and_two_current_steps(GameTestHelper h) {
        F f = new F(h); h.assertTrue(f.toggle(1), "native paid start");
        h.assertTrue(f.snapshot(1,0) == null, "payment alone cannot open a partial initial Pre slot");
        f.after(1, () -> {
            var s = f.required(1); h.assertTrue(s.paidFrom() <= s.grant().issuedAt() && s.paidUntil() - s.paidFrom() == 20, "twenty aligned paid Pre slots");
            h.assertFalse(WindPressureLift.consumeIntegratedStep(s,f.p.server,1,1,0,2), "a caller without the final setter lock cannot consume");
            h.assertTrue(f.take(s,1,1,0,2) && f.take(s,2,1,1,2), "two distinct declared current native steps share one paid snapshot");
            h.assertTrue(f.outcome(s,2,1,1,2)==WindPressureLift.StepOutcome.INVALID_STEP, "same native step cannot replay or misreport clock closure");
            h.assertFalse(f.take(s,3,2,0,1), "a third same-tick step cannot gain a third allowance");
            f.publish(); h.assertTrue(f.snapshot(1,0) == null, "a closed step fence cannot be reopened by Post"); f.succeed();
        });
    }

    /** Diagnostic pairing with NativeTimerPhaseProbe: real timer emits 2 then 1; these are declared
     * native-step tuples inside a real paid server context, not a forged LocalPlayer input proof. */
    @GameTest(template=TEMPLATE,batch=BATCH+"_phase_same",timeoutTicks=15)
    public static void wind_lift_phase_two_then_one_same_server_tick_closes_paid_context(GameTestHelper h) {
        F f=new F(h);h.assertTrue(f.toggle(1),"paid real connected fixture");
        f.after(1,()->{
            var s=f.required(1);long receipt=s.receipt();double cp=f.cp();
            h.assertTrue(f.take(s,101,9,0,2)&&f.take(s,102,9,1,2),"two genuine-step-shaped entries in a permitted two-step run");
            h.assertTrue(f.snapshot(1,s.grant().token())==s,"third attempt still has current immutable paid snapshot");
            h.assertTrue(f.outcome(s,103,10,0,1)==WindPressureLift.StepOutcome.CLOCK_SKEW_CLOSED,"ordinary next frame third step reports exact fixed-age closure, not generic budget failure");
            h.assertTrue(f.outcome(s,104,11,0,1)==WindPressureLift.StepOutcome.INVALID_SNAPSHOT,"already closed publication cannot repeat the timing-stop notification");
            h.assertTrue(f.cp()==cp && f.payment().getLong("receipt")==receipt,"closure does not refund, renew or mint resource");
            h.assertTrue(f.snapshot(1,s.grant().token())==null,"closed fence immediately withdraws usable snapshot");
            f.after(1,()->{h.assertFalse(WindPressureLift.active(f.p),"next genuine native tick retires closed context");h.assertTrue(f.payment().getLong("receipt")==receipt,"no replacement payment follows timing stop");f.succeed();});
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_phase_next",timeoutTicks=15)
    public static void wind_lift_phase_two_then_one_next_server_tick_retains_paid_context(GameTestHelper h) {
        F f=new F(h);h.assertTrue(f.toggle(1),"paid real connected fixture");
        f.after(1,()->{
            var s=f.required(1);long receipt=s.receipt();
            h.assertTrue(f.take(s,101,9,0,2)&&f.take(s,102,9,1,2),"identical initial two-step run");
            f.after(1,()->{
                var next=f.required(1);h.assertTrue(next.grant().issuedAt()==s.grant().issuedAt()+1,"exactly one native server tick advanced");
                h.assertTrue(f.take(next,103,10,0,1),"same ordinary third step is allowed at next server phase");
                h.assertTrue(WindPressureLift.active(f.p)&&f.payment().getLong("receipt")==receipt,"one paid window stays active without renewal");f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void wind_lift_dual_snapshot_identity_and_replacement_are_exact(GameTestHelper h) {
        F f = new F(h); h.assertTrue(f.toggle(1), "paid setup");
        f.after(1, () -> {
            var s = f.required(1);
            var forged = new WindPressureLift.Snapshot(s.grant(),s.receipt(),s.paidFrom(),s.paidUntil(),s.revision(),s.issuedNanos());
            h.assertTrue(f.outcome(forged,1,1,0,1)==WindPressureLift.StepOutcome.INVALID_SNAPSHOT, "equal data is not the exact immutable publication and must not show timing closure");
            h.assertTrue(f.snapshot(1,s.grant().token()+1) == null && f.snapshot(2,0) == null, "bound token and captured input are exact");
            h.assertTrue(WindPressureLift.latestSnapshot(f.p.server,f.p.getId(),UUID.randomUUID(),1,f.epoch(),f.p.serverLevel().dimension().location(),0) == null, "another owner cannot bootstrap");
            f.after(1, () -> {
                var next = f.required(1);
                h.assertTrue(next.revision() > s.revision() && next.grant().issuedAt() > s.grant().issuedAt(), "actual next native publication advances the version");
                h.assertFalse(f.take(s,1,1,0,1), "replaced evidence cannot consume even within nominal age");
                h.assertTrue(f.take(next,1,1,0,1), "current evidence retains a current-step opportunity"); f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=18)
    public static void wind_lift_dual_prior_snapshot_covers_real_pre_authentication_gap(GameTestHelper h) {
        F f = new F(h); h.assertTrue(f.toggle(1), "paid setup");
        f.after(1, () -> {
            var prior = f.required(1); int nextTick = f.p.server.getTickCount()+1;
            CompoundTag original = f.payment(); boolean[] seen = {false}, consumed = {false};
            CompoundTag observer = new CompoundTag() {
                @Override public int getInt(String key) {
                    if (!seen[0] && key.equals("remaining") && f.p.server.getTickCount() == nextTick) {
                        seen[0] = true;
                        h.assertTrue(f.snapshot(1,prior.grant().token()) == prior, "real Pre advanced the clock but has not yet replaced last Post evidence");
                        consumed[0] = f.take(prior,1,1,0,1);
                    }
                    return super.getInt(key);
                }
            };
            for (String key : original.getAllKeys()) observer.put(key,original.get(key).copy());
            f.p.getPersistentData().put(WindPressureLift.PAYMENT_KEY,observer);
            f.after(1, () -> {
                h.assertTrue(seen[0] && consumed[0], "one current declared step consumed paid previous-tick evidence inside actual Pre authentication");
                h.assertFalse(f.take(prior,1,1,0,1), "that prior evidence/step cannot replay after replacement"); f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void wind_lift_dual_stop_before_first_snapshot_never_resurrects(GameTestHelper h) {
        F f = new F(h); h.assertTrue(f.toggle(1), "paid setup before first eligible Pre");
        double cp=f.cp(); f.stop(1,0); f.publish();
        h.assertFalse(WindPressureLift.active(f.p), "paid session publication permits cancellation before the first movement snapshot");
        h.assertTrue(f.snapshot(1,0) == null && f.cp()==cp, "canceled payment cannot refund or republish"); f.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void wind_lift_dual_small_normal_teleport_revokes_immediately(GameTestHelper h) {
        F f = new F(h); h.assertTrue(f.toggle(1), "paid setup");
        f.after(1, () -> {
            var s=f.required(1); double cp=f.cp(); var at=f.p.position().add(.1,0,0);
            f.p.connection.teleport(at.x,at.y,at.z,f.p.getYRot(),f.p.getXRot());
            h.assertTrue(f.p.position().equals(at), "native teleport keeps its exact destination");
            h.assertTrue(f.snapshot(1,0)==null && !f.take(s,1,1,0,1), "explicit teleport revokes before another native tick");
            h.assertTrue(f.cp()==cp, "teleport cancellation does not refund or charge again"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=18)
    public static void wind_lift_dual_unpublished_core_mutation_is_rechecked_next_pre(GameTestHelper h) {
        F f = new F(h); h.assertTrue(f.toggle(1), "paid setup");
        f.after(1, () -> {
            var s=f.required(1); AbilityStorage.get(f.p).activated=false;
            h.assertTrue(f.snapshot(1,0)==s, "raw public-state mutation has no invented instantaneous publication");
            f.after(1, () -> {
                h.assertFalse(WindPressureLift.active(f.p), "next genuine Pre rejects the now-ineligible server state");
                h.assertTrue(f.snapshot(1,0)==null && !f.take(s,1,1,0,1), "old immutable evidence cannot survive reauthentication"); f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=18)
    public static void wind_lift_dual_unpublished_outside_position_is_rechecked_next_pre(GameTestHelper h) {
        F f = new F(h); h.assertTrue(f.toggle(1), "paid setup");
        f.after(1, () -> {
            var s=f.required(1); f.p.setPos(f.p.position().add(4,0,0));
            h.assertTrue(s.grant().anchor().equals(f.start()), "unknown position assignment never moves the fixed anchor");
            f.after(1, () -> {
                h.assertFalse(WindPressureLift.active(f.p), "next Pre rejects outside-column/accepted-drift movement");
                h.assertTrue(f.snapshot(1,0)==null && !f.take(s,1,1,0,1), "unknown mutation is tested as next-Pre rejection, not instant detection"); f.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void wind_lift_dual_last_paid_slot_survives_counter_zero_then_new_window_requires_receipt(GameTestHelper h) {
        F f = new F(h); h.assertTrue(f.toggle(1), "paid setup"); int last=f.p.server.getTickCount()+20;
        long receipt=f.payment().getLong("receipt"); boolean[] witnessed={false};
        f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGH) public void post(ServerTickEvent.Post e) {
            if (witnessed[0] || e.getServer()!=f.p.server || f.p.server.getTickCount()!=last) return;
            try {
                witnessed[0]=true; var s=f.required(1);
                h.assertTrue(f.payment().getInt("remaining")==0 && s.receipt()==receipt && s.grant().issuedAt()<s.paidUntil(), "last whole Pre slot remains prepaid when renewal counter reaches zero");
                h.assertTrue(f.take(s,1,1,0,1), "last paid current step can consume before renewal Post");
            } catch (Throwable failure) { f.fail(failure); }
        }});
        f.after(21, () -> {
            var s=f.required(1); h.assertTrue(witnessed[0] && s.receipt()>receipt && s.grant().issuedAt()>=s.paidFrom(), "next window has an independently successful receipt");
            h.assertTrue(f.take(s,2,2,0,1), "new window can consume without resetting owner history"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void wind_lift_dual_failed_renewal_revokes_before_next_pre(GameTestHelper h) {
        F f = new F(h); h.assertTrue(f.toggle(1), "paid setup"); long receipt=f.payment().getLong("receipt");
        f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(ServerTickEvent.Post e) {
            if(e.getServer()==f.p.server) f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp",0);
        }});
        f.after(19, () -> h.assertTrue(f.required(1).receipt()==receipt, "last receipt remains usable before its paid horizon"));
        f.after(20, () -> h.assertTrue(!WindPressureLift.active(f.p) && f.snapshot(1,0)==null, "failed renewal Post closes the old slot immediately"));
        f.after(21, () -> { h.assertTrue(f.snapshot(1,0)==null && f.cp()==0, "next Pre has no unpaid-window authority"); f.succeed(); });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void wind_lift_dual_normal_logout_reload_preserves_owner_budget(GameTestHelper h) {
        F f = new F(h); h.assertTrue(f.toggle(1), "paid setup");
        f.after(1, () -> {
            var s=f.required(1); h.assertTrue(f.take(s,1,1,0,2) && f.take(s,2,1,1,2), "two quota debts exist before normal logout");
            UUID owner=f.p.getUUID(); WindPressureLift.remove(f.p);
            CompoundTag saved=f.p.getPersistentData().getCompound(WindPressureLift.BUDGET_KEY).copy(); f.close();
            F joined=new F(h,owner);
            h.assertTrue(saved.equals(joined.p.getPersistentData().getCompound(WindPressureLift.BUDGET_KEY)), "real PlayerList save/login retains remaining tick and nanosecond debts");
            h.assertTrue(joined.snapshot(1,0)==null && !joined.take(s,3,2,0,1), "new entity/session cannot revive the previous proof");
            h.assertFalse(joined.toggle(1), "restart/cooldown debt survives login");
            joined.after(10, () -> {
                double cp=joined.cp(); h.assertTrue(joined.toggle(2), "genuine online ticks age the existing restart debt");
                h.assertTrue(joined.cp()==cp-.5, "new session pays fresh startup");
                h.assertTrue(joined.p.getPersistentData().getCompound(WindPressureLift.BUDGET_KEY).getIntArray("ticks").length>=2, "fresh token/window does not erase still-live owner history"); joined.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void wind_lift_dual_normal_activation_entry_revokes_and_unchanged_request_preserves(GameTestHelper h) {
        F f=new F(h); h.assertTrue(f.toggle(1),"paid setup");
        f.after(1,()->{
            var s=f.required(1);
            h.assertTrue(AcademyGameplay.requestFromClient(f.p,new AcademyNetwork.Request("activate_state","true")),"ordinary unchanged activation request is accepted");
            h.assertTrue(f.snapshot(1,0)==s && WindPressureLift.active(f.p),"unchanged activation preserves this exact publication");
            h.assertTrue(AcademyGameplay.requestFromClient(f.p,new AcademyNetwork.Request("activate_state","false")),"ordinary changed activation request is accepted");
            h.assertTrue(f.snapshot(1,0)==null && !f.take(s,1,1,0,1),"normal deactivation revokes before another native tick");
            f.after(10,()->{
                h.assertTrue(AcademyGameplay.requestFromClient(f.p,new AcademyNetwork.Request("activate_state","true")) && f.toggle(2),"fresh paid cast after genuine restart ticks");
                f.after(1,()->{
                    var next=f.required(2);
                    h.assertTrue(AcademyGameplay.requestFromClient(f.p,new AcademyNetwork.Request("toggle","")),"ordinary toggle accepted");
                    h.assertTrue(f.snapshot(2,0)==null && !f.take(next,1,1,0,1),"normal toggle revokes current evidence immediately");f.succeed();
                });
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void wind_lift_dual_initial_budget_write_callback_refuses_before_payment(GameTestHelper h) {
        F f=new F(h); double cp=f.cp();
        CompoundTag root=new CompoundTag(){boolean replaced;
            @Override public Tag put(String key,Tag value){
                Tag previous=super.put(key,value);
                if(!replaced && key.equals(WindPressureLift.BUDGET_KEY) && value instanceof CompoundTag tag){
                    replaced=true;CompoundTag foreign=tag.copy();foreign.putLong("receipt",1);super.put(key,foreign);
                }
                return previous;
            }
        };
        for(String key:f.p.getPersistentData().getAllKeys())root.put(key,f.p.getPersistentData().get(key));
        setField(f.p,"persistentData",root);
        h.assertFalse(f.toggle(1),"first serialization callback cannot pass a now-invalid admission budget");
        h.assertTrue(f.cp()==cp && !WindPressureLift.active(f.p) && f.snapshot(1,0)==null,"no startup debit or permission precedes validated budget persistence");
        h.assertTrue(root.contains(WindPressureLift.BUDGET_QUARANTINE_KEY) && root.getCompound(WindPressureLift.BUDGET_KEY).getLong("receipt")==1,"independent quarantine preserves the unknown replacement");f.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void wind_lift_dual_persisted_budget_receipt_cannot_lead_payment(GameTestHelper h) {
        F f=new F(h);double cp=f.cp();CompoundTag ahead=new CompoundTag();
        ahead.putInt("version",1);ahead.putIntArray("ticks",new int[0]);ahead.putLongArray("nanos",new long[0]);ahead.putLong("receipt",1);ahead.putInt("uses",0);
        f.p.getPersistentData().put(WindPressureLift.BUDGET_KEY,ahead);
        h.assertFalse(f.toggle(1),"well-shaped budget cannot refer to an unissued payment receipt");
        h.assertTrue(f.cp()==cp && f.p.getPersistentData().contains(WindPressureLift.BUDGET_QUARANTINE_KEY),"receipt inconsistency closes before startup CP");
        h.assertTrue(f.p.getPersistentData().get(WindPressureLift.BUDGET_KEY)==ahead,"foreign ledger identity is not repaired");f.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void wind_lift_dual_unknown_debt_reduction_stays_quarantined_after_native_save_cold_load(GameTestHelper h) {
        F f=new F(h);h.assertTrue(f.toggle(1),"paid setup");
        f.after(1,()->{
            var s=f.required(1);h.assertTrue(f.take(s,1,1,0,1),"a real owner quota debt precedes unknown replacement");
            CompoundTag foreign=f.p.getPersistentData().getCompound(WindPressureLift.BUDGET_KEY).copy();
            foreign.putIntArray("ticks",new int[0]);foreign.putLongArray("nanos",new long[0]);foreign.putLong("receipt",s.receipt());foreign.putInt("uses",0);
            f.p.getPersistentData().put(WindPressureLift.BUDGET_KEY,foreign);WindPressureLift.remove(f.p);
            h.assertTrue(f.p.getPersistentData().contains(WindPressureLift.BUDGET_QUARANTINE_KEY),"unknown legal debt reduction gets independent persistent quarantine");
            h.assertTrue(f.p.getPersistentData().get(WindPressureLift.BUDGET_KEY)==foreign,"refusal never rolls back the external ledger");
            UUID owner=f.p.getUUID();var server=f.p.server;f.close();
            forgetOnlyBudgetCache(server,owner); // Model this owner's stopped cleanup, never mutate another live test's state.
            F joined=new F(h,owner);
            h.assertTrue(joined.p.getPersistentData().contains(WindPressureLift.BUDGET_QUARANTINE_KEY),"real PlayerList disk save/login retains quarantine after owner-cache eviction");
            joined.after(10,()->{
                h.assertTrue(joined.payment().getInt("cooldown")==0 && joined.payment().getInt("restart")==0,"ordinary stop clocks are no longer the refusal reason");
                double cp=joined.cp();h.assertFalse(joined.toggle(2),"cold-loaded legal-looking debt cannot restore quarantined enhancement");
                h.assertTrue(joined.cp()==cp && joined.snapshot(2,0)==null,"quarantine refuses free before charging a new start");joined.succeed();
            });
        });
    }

    private static void setField(Object owner,String name,Object value){
        try{for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass()){
            try{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(owner,value);return;}catch(NoSuchFieldException absent){}
        }throw new NoSuchFieldException(name);}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    private static void forgetOnlyBudgetCache(net.minecraft.server.MinecraftServer server,UUID owner){
        synchronized(WindPressureLift.integratedCommitLock()){
            try{for(String name:new String[]{"BUDGETS","BUDGET_IMAGES"}){
                Field field=WindPressureLift.class.getDeclaredField(name);field.setAccessible(true);
                var map=(java.util.Map<?,?>)field.get(null);var it=map.keySet().iterator();
                while(it.hasNext()){
                    Object key=it.next();Method s=key.getClass().getDeclaredMethod("server"),o=key.getClass().getDeclaredMethod("owner");s.setAccessible(true);o.setAccessible(true);
                    if(s.invoke(key)==server && owner.equals(o.invoke(key)))it.remove();
                }
            }}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
        }
    }

    private static final class F {
        final GameTestHelper h; final Object fixture; final ServerPlayer p;
        F(GameTestHelper h) { this(h,null); }
        F(GameTestHelper h,UUID rejoin) {
            this.h=h;
            try {
                Class<?> type=Class.forName("cn.academy.port.gametest.HighPowerWindPressureLiftRuntimeTests$Fixture");
                Constructor<?> ctor=rejoin==null?type.getDeclaredConstructor(GameTestHelper.class):type.getDeclaredConstructor(GameTestHelper.class,int.class,UUID.class,boolean.class);
                ctor.setAccessible(true); fixture=rejoin==null?ctor.newInstance(h):ctor.newInstance(h,7,rejoin,true);
                Field field=type.getDeclaredField("p"); field.setAccessible(true); p=(ServerPlayer)field.get(fixture);
            } catch (InvocationTargetException failure) { throw new AssertionError(failure.getCause()); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        Object call(String name,Class<?>[] signature,Object...values) {
            try { Method m=fixture.getClass().getDeclaredMethod(name,signature); m.setAccessible(true); return m.invoke(fixture,values); }
            catch (InvocationTargetException failure) { throw new AssertionError(failure.getCause()); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        long epoch(){return(long)call("epoch",new Class<?>[0]);}
        WindPressureLift.Snapshot snapshot(long input,long token){return WindPressureLift.latestSnapshot(p.server,p.getId(),p.getUUID(),input,epoch(),p.serverLevel().dimension().location(),token);}
        WindPressureLift.Snapshot required(long input){var s=snapshot(input,0); h.assertTrue(s!=null,"current paid immutable snapshot is available in this genuine native tick");return s;}
        WindPressureLift.StepOutcome outcome(WindPressureLift.Snapshot s,long step,long run,int index,int size){synchronized(WindPressureLift.integratedCommitLock()){return WindPressureLift.consumeIntegratedStepOutcome(s,p.server,step,run,index,size);}}
        boolean take(WindPressureLift.Snapshot s,long step,long run,int index,int size){synchronized(WindPressureLift.integratedCommitLock()){return WindPressureLift.consumeIntegratedStep(s,p.server,step,run,index,size);}}
        void stop(long input,long token){WindPressureLift.stopIntegrated(p.server,p.getId(),p.getUUID(),input,epoch(),p.serverLevel().dimension().location(),token);}
        boolean toggle(long input){return(boolean)call("toggle",new Class<?>[]{long.class},input);}
        void publish(){call("publish",new Class<?>[0]);}
        double cp(){return(double)call("cp",new Class<?>[0]);}
        CompoundTag payment(){return(CompoundTag)call("payment",new Class<?>[0]);}
        net.minecraft.world.phys.Vec3 start(){try{Field f=fixture.getClass().getDeclaredField("start");f.setAccessible(true);return(net.minecraft.world.phys.Vec3)f.get(fixture);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
        void after(int ticks,Runnable action){call("afterPosts",new Class<?>[]{int.class,Runnable.class},ticks,action);}
        void hook(Object hook){call("hook",new Class<?>[]{Object.class},hook);}
        void succeed(){call("succeed",new Class<?>[0]);}
        void fail(Throwable failure){call("fail",new Class<?>[]{Throwable.class},failure);}
        void close(){call("close",new Class<?>[0]);}
    }
}

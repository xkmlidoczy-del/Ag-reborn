package cn.academy.port.client;

import cn.academy.port.highpower.client.WindPressureLiftTravelMixinPlugin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.phys.Vec3;

/** Client-only native witness. Public events never open its opaque run/step/travel capabilities. */
public final class WindPressureLiftNativeTravel {
    private static final String MC="Lnet/minecraft/client/Minecraft;", VEC="Lnet/minecraft/world/phys/Vec3;";
    private static final String OP="Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";
    private static final String ENTITY="Lnet/minecraft/world/entity/Entity;";
    private static final WindPressureLiftTravelState STATE=new WindPressureLiftTravelState();
    private static final StackWalker WALKER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static WindPressureLiftTravelState.Identity identity, observed;
    private static WindPressureLiftTravelState.Run run;
    private static WindPressureLiftTravelState.Step step;
    private static long generation;
    // QA only: one bounded static-symbol report per connection, with eight reports per process.
    private static final Object[] QA_STACK_CONNECTIONS=new Object[8];
    private static int qaStackReports;
    // Free preflight diagnostics: distinct reasons only, capped across all connections.
    private static final java.util.Set<Integer> QA_READY_REASONS=new java.util.HashSet<>();
    private WindPressureLiftNativeTravel() {}

    /** The original boolean and invocation count are unchanged, including exceptional returns. */
    public static void run(Minecraft mc, boolean ticking, Operation<Void> original) {
        var previous=run;
        WindPressureLiftTravelState.Run candidate=null;
        try {candidate=mc.isSameThread()?STATE.beginRun(identity(mc),Thread.currentThread()):null;}
        catch(RuntimeException | LinkageError unavailable) {refuse("native_run_witness_exception");}
        run=candidate;
        try { original.call(ticking); }
        finally { STATE.endRun(candidate);run=previous; }
    }
    /** Observe, never replace, the native timer's exact result before any queued tasks or ticks. */
    public static int plan(DeltaTracker.Timer timer,long now,boolean ticking) {
        int count=timer.advanceTime(now,ticking);
        if(run!=null) {
            if(!STATE.plan(run,count,ticking)) refuse("native_invalid_plan");
            else if(run.catchup()) refuse("native_catchup_over_two");
        }
        return count;
    }
    public static void step(Minecraft mc) {
        var previous=step;
        WindPressureLiftTravelState.Step candidate=null;
        try {candidate=mc.isSameThread()?STATE.beginStep(run,identity(mc),Thread.currentThread()):null;}
        catch(RuntimeException | LinkageError unavailable) {refuse("native_step_witness_exception");}
        step=candidate;
        try { mc.tick(); }
        finally { STATE.endStep(candidate);step=previous; }
    }
    /** Called only by the separately witnessed LocalPlayer override, immediately before native travel. */
    public static WindPressureLiftTravelState.Travel beforeTravel(LocalPlayer actor,Vec3 vector) {
        WindPressureLiftTravelState.Travel ticket=null;
        try {
            Minecraft mc=Minecraft.getInstance();
            if(!WindPressureLiftTravelMixinPlugin.available() || !mc.isSameThread() || mc.player!=actor
                    || actor.getClass()!=LocalPlayer.class || vector==null) return null;
            if(!sableStateReason(actor).isEmpty()){refuse("sable_dynamic_state");return null;}
            var owner=identity(mc);
            if(!nativeStack()) {
                refuse("unrecognized_native_travel_stack");return null;
            }
            ticket=STATE.beginTravel(step,owner,Thread.currentThread(),true);
            if(ticket==null)return null;
            observed=owner;
            WindPressureLiftClientMotion.nativeTravel(ticket,vector);
            return ticket;
        } catch(RuntimeException | LinkageError failure) {
            STATE.invalidate();observed=null;STATE.endTravel(ticket);
            refuse("native_witness_or_safety_exception");
            return null;
        }
    }
    public static void afterTravel(WindPressureLiftTravelState.Travel ticket) {
        try{if(ticket!=null && !sableStateReason(Minecraft.getInstance().player).isEmpty())refuse("sable_dynamic_state_after_travel");}
        finally{STATE.endTravel(ticket);}
    }
    /** Optional Sable classes are resolved only after their exact native weave and API resource proof succeeded. */
    public static String sableStateReason(LocalPlayer player){
        if(!WindPressureLiftTravelMixinPlugin.requiresSablePlainState())return "";
        try{return cn.academy.port.client.sable.SableWindTravelState.blocker(player);}
        catch(RuntimeException | LinkageError unavailable){return "unreadable_sable_state";}
    }
    private static void refuse(String reason) {
        STATE.invalidate();observed=null;
        // Optional controller cleanup/notification must never prevent the original native delegate.
        try {WindPressureLiftClientMotion.nativeBoundaryRefused(reason);}
        catch(RuntimeException | LinkageError unavailable) { /* native authority already revoked */ }
    }
    public static boolean ready(Minecraft mc) {
        var current=identity(mc);
        boolean available=WindPressureLiftTravelMixinPlugin.available(),sameThread=mc.isSameThread();
        boolean seen=WindPressureLiftTravelState.sameIdentity(observed,current);
        boolean allowed=STATE.allowsStart(run,current,Thread.currentThread());
        boolean ready=available && sameThread && seen && allowed && sableStateReason(mc.player).isEmpty();
        if(!ready && WindPressureLiftTrace.ENABLED)traceReady(available,sameThread,seen,allowed);
        return ready;
    }
    private static void traceReady(boolean available,boolean sameThread,boolean seen,boolean allowed) {
        int reason=(available?1:0)|(sameThread?2:0)|(seen?4:0)|(allowed?8:0);
        if(QA_READY_REASONS.size()>=8 || !QA_READY_REASONS.add(reason))return;
        try {System.getLogger("academy.qa.wind_pressure_lift").log(System.Logger.Level.INFO,
                "NEW443_QA stage=native_ready_refused available="+available+" sameThread="+sameThread
                        +" observedIdentity="+seen+" allowsStart="+allowed+" runPresent="+(run!=null)
                        +" planned="+(run==null?-1:run.plannedCount())+" admission="+WindPressureLiftTravelMixinPlugin.diagnosticState()
                        +" sableState="+sableStateReason(Minecraft.getInstance().player));}
        catch(RuntimeException | LinkageError unavailable) { /* diagnostics cannot alter admission */ }
    }
    /** Pure identity/lifetime check, safe inside the shared short authorization lock. */
    public static boolean owns(WindPressureLiftTravelState.Travel ticket,Minecraft mc) {
        return mc.isSameThread() && STATE.owns(ticket,identity(mc),Thread.currentThread());
    }
    public static void cancelCurrent() { STATE.invalidate(); }
    public static void clear() {
        STATE.invalidate();observed=null;identity=null;
        if(generation!=Long.MAX_VALUE)generation++;
    }
    private static WindPressureLiftTravelState.Identity identity(Minecraft mc) {
        if(identity==null || identity.client()!=mc || identity.world()!=mc.level || identity.player()!=mc.player
                || identity.connection()!=mc.getConnection()) {
            STATE.invalidate();observed=null;
            if(generation==Long.MAX_VALUE)return null;
            identity=new WindPressureLiftTravelState.Identity(mc,mc.level,mc.player,mc.getConnection(),++generation);
        }
        return identity;
    }
    private static boolean nativeStack() {
        var shape=WindPressureLiftTravelMixinPlugin.minecraftShape();if(shape==null)return false;
        List<WindPressureLiftTravelState.Frame> expected=List.of(
                f(WindPressureLiftNativeTravel.class,"nativeStack","()Z"),
                f(WindPressureLiftNativeTravel.class,"beforeTravel","(Lnet/minecraft/client/player/LocalPlayer;"+VEC+")Lcn/academy/port/client/WindPressureLiftTravelState$Travel;"),
                f(LocalPlayer.class,"travel","("+VEC+")V"),
                f(LivingEntity.class,"aiStep","()V"),f(Player.class,"aiStep","()V"),f(LocalPlayer.class,"aiStep","()V"),
                f(LivingEntity.class,"tick","()V"),f(Player.class,"tick","()V"),f(AbstractClientPlayer.class,"tick","()V"),f(LocalPlayer.class,"tick","()V"),
                f(ClientLevel.class,"tickNonPassenger","("+ENTITY+")V"),f(Level.class,"guardEntityTick","(Ljava/util/function/Consumer;"+ENTITY+")V"),
                f(ClientLevel.class,"lambda$tickEntities$4","("+ENTITY+")V"),f(EntityTickList.class,"forEach","(Ljava/util/function/Consumer;)V"),
                f(ClientLevel.class,"tickEntities","()V"),f(Minecraft.class,"tick","()V"),
                f(WindPressureLiftNativeTravel.class,"step","("+MC+")V"),f(Minecraft.class,shape.stepHandler(),"("+MC+")V"),
                f(Minecraft.class,shape.nativeBody(),"(Z)V"),f(Minecraft.class,shape.bridge(),"([Ljava/lang/Object;)Ljava/lang/Void;"),
                f(WindPressureLiftNativeTravel.class,"run","("+MC+"Z"+OP+")V"),
                f(Minecraft.class,shape.runHandler(),"(Z"+OP+")V"),f(Minecraft.class,"runTick","(Z)V"));
        var actual=WALKER.walk(frames -> frames.limit(48)
                .map(frame -> f(frame.getDeclaringClass(),frame.getMethodName(),frame.getDescriptor())).toList());
        boolean matches=WindPressureLiftTravelState.stackPrefix(actual,expected);
        if(!matches && WindPressureLiftTrace.ENABLED)traceStackMismatch(actual);
        return matches;
    }
    private static void traceStackMismatch(List<WindPressureLiftTravelState.Frame> actual) {
        Object connection=identity==null?null:identity.connection();
        if(connection==null || qaStackReports>=QA_STACK_CONNECTIONS.length)return;
        for(int i=0;i<qaStackReports;i++)if(QA_STACK_CONNECTIONS[i]==connection)return;
        QA_STACK_CONNECTIONS[qaStackReports++]=connection;
        StringBuilder symbols=new StringBuilder("NEW443_QA stage=native_stack_mismatch frames=").append(actual.size());
        for(int i=0;i<actual.size() && symbols.length()<16384;i++) {
            var frame=actual.get(i);
            String symbol=((Class<?>)frame.declaringClass()).getName()+"#"+frame.method()+frame.descriptor();
            symbols.append(" | ").append(i).append(':').append(symbol,0,Math.min(symbol.length(),512));
        }
        try {System.getLogger("academy.qa.wind_pressure_lift").log(System.Logger.Level.INFO,symbols.toString());}
        catch(RuntimeException | LinkageError unavailable) { /* diagnostics cannot alter admission */ }
    }
    private static WindPressureLiftTravelState.Frame f(Class<?> type,String name,String descriptor) {
        return new WindPressureLiftTravelState.Frame(type,name,descriptor);
    }
}

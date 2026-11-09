package cn.academy.port.qa;

import cn.academy.port.AbilityStorage;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.ExactCombatState;
import cn.academy.port.highpower.core.SharedShieldReserveShares;
import com.mojang.logging.LogUtils;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.*;

/** Real native Arrow ticks from an ordinary enemy. No hurt calls, hit results, event posts or skill API transitions. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class GameplayAcceptanceNativeArrowModes {
    private static String mode;private static UUID owner;private static Zombie enemy;private static Arrow arrow,early,late;
    private static final Set<Arrow> phaseArrows=Collections.newSetFromMap(new IdentityHashMap<>());private static boolean armed;private static int burst;private static long lastBurstTick;
    private static boolean watching,post,committed,earlyIncoming,earlyCanceled,earlyUntouched,lateIncoming,latePost,latePaid;
    private static Vec3 origin;private static Object capture;private static long jumpTick,baselineTick,earlyElapsed,lateElapsed,paidClock;
    private static ExactCombatState before,phaseBefore,earlyBefore,lateBefore;
    private static net.minecraft.world.damagesource.DamageSource earlySource;private static boolean earlyChecked;private static SharedShieldReserveShares sharesBefore;
    private static ExactCombatState toggleBase;private static SharedShieldReserveShares toggleShares;private static long toggleSpendTick,toggleReceipt;
    private static BigDecimal nativeDamage;private static int quotaBefore;private static double cp,minCp,budget,minBudget;
    private GameplayAcceptanceNativeArrowModes(){}
    public static void run(Minecraft mc,String action){
        if(action.startsWith("setup:")){
            mode=action.substring(6);require(Set.of("vector_parry","autonomous_vector_circle","phase_corridor").contains(mode),"supported actual native arrow mode");
            if(mode.equals("phase_corridor")){GameplayAcceptanceTpVm.run(mc,"setup:"+mode);GameplayAcceptanceTpVm.run(mc,"prepare");}
            else GameplayAcceptanceVmUtility.run(mc,"setup:"+mode);
            GameplayAcceptanceTpVm.server(mc,p->{owner=p.getUUID();watching=post=committed=earlyIncoming=earlyCanceled=earlyUntouched=lateIncoming=latePost=latePaid=false;arrow=early=late=null;capture=null;earlySource=null;earlyChecked=false;jumpTick=-1;armed=false;burst=0;lastBurstTick=-1;phaseArrows.clear();
                for(var e:p.serverLevel().getEntitiesOfClass(Zombie.class,p.getBoundingBox().inflate(35)))e.discard();
                enemy=new Zombie(EntityType.ZOMBIE,p.serverLevel());enemy.setNoAi(true);enemy.setSilent(true);enemy.setPos(p.position().add(mode.equals("phase_corridor")?7:0,0,mode.equals("phase_corridor")?17.5:7));require(p.serverLevel().addFreshEntity(enemy),"real stationary enemy archer body joins native world");
            });return;
        }
        GameplayAcceptanceTpVm.server(mc,p->{require(p.getUUID().equals(owner),"same actual actor");switch(action){
            case "baseline"->{origin=p.position();baselineTick=p.level().getGameTime();cp=minCp=AbilityStorage.get(p).cp;budget=minBudget=HighPowerResources.availableCp(p);paidClock=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_"+mode);watching=true;}
            case "toggle-baseline"->{require(mode.equals("autonomous_vector_circle")&&committed&&AutonomousVectorCircle.active(p),"real arrow committed before actual close/reopen baseline");toggleBase=state(p);toggleShares=shares(p,toggleBase);toggleSpendTick=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getLong("lastShieldSpend");toggleReceipt=p.getPersistentData().getCompound(AutonomousVectorCircle.PAYMENT_KEY).getLong("receipt");conserved(p);}
            case "toggle-off"->{require(!AutonomousVectorCircle.active(p),"fresh actual R closed the paid circle");conserved(p);LogUtils.getLogger().info("QA_CIRCLE_TOGGLE_OFF_BALANCE_PASS base={} lower={} upper={} elapsedSinceNativeSpend={}",toggleBase.shield(),toggleShares.lower(),toggleShares.upper(),p.level().getGameTime()-toggleSpendTick);}
            case "restart-ready"->{require(!AutonomousVectorCircle.active(p),"same circle stays off during natural restart wait");var payment=p.getPersistentData().getCompound(AutonomousVectorCircle.PAYMENT_KEY);require(payment.getInt("restart")==0&&AbilityStorage.get(p).cooldowns.getOrDefault(mode,0)==0,"actual native player ticks retired the original restart and cooldown fences");conserved(p);}
            case "toggle-on"->{require(AutonomousVectorCircle.active(p),"fresh actual R reopened the circle after natural restart");require(p.getPersistentData().getCompound(AutonomousVectorCircle.PAYMENT_KEY).getLong("receipt")>toggleReceipt,"reopening owns a fresh genuinely paid activation receipt");conserved(p);LogUtils.getLogger().info("QA_CIRCLE_TOGGLE_REOPEN_BALANCE_PASS base={} lower={} upper={} elapsedSinceNativeSpend={} newPaidReceipt={} noResourcesWritten=true",toggleBase.shield(),toggleShares.lower(),toggleShares.upper(),p.level().getGameTime()-toggleSpendTick,p.getPersistentData().getCompound(AutonomousVectorCircle.PAYMENT_KEY).getLong("receipt"));}
            case "arm-phase"->{require(watching&&mode.equals("phase_corridor")&&!armed,"ordinary arrow lane is armed before the real jump input");phaseBefore=state(p);armed=true;shootLane(p);}
            case "launch"->{require(watching&&!mode.equals("phase_corridor")&&arrow==null,"one ordinary defense projectile");require(active(p),"real bound key opened the selected defense");arrow=spawn(p,false);}
            case "assert"->{require(minCp<cp-1e-6||minBudget<budget-1e-6||p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_"+mode)>paidClock,"real finite skill payment accompanies actual effect");
                if(mode.equals("phase_corridor"))require(jumpTick>baselineTick&&earlyIncoming&&earlyCanceled&&earlyUntouched&&lateIncoming&&latePost&&latePaid,"actual native enemy arrows witness early cancellation with no ledger debit and exposed-window real damage; jump="+jumpTick+" early="+earlyElapsed+"/"+earlyIncoming+"/"+earlyCanceled+"/"+earlyUntouched+" late="+lateElapsed+"/"+lateIncoming+"/"+latePost+"/"+latePaid);
                else require(post&&committed,"real native arrow must commit its own specialized capture/quota/resources; base reflection is insufficient; nativePost="+post+" capture="+capture+" arrowOwner="+(arrow==null?null:arrow.getOwner()));
                LogUtils.getLogger().info("QA_NATIVE_ARROW_MODE_PASS mode={} specializedCommitted={} earlyElapsed={} lateElapsed={} phaseUntouched={} exposedPaid={} realArrowTicks=true noInjectedHit=true",mode,committed,earlyElapsed,lateElapsed,earlyUntouched,latePaid);watching=false;}
            default->throw new IllegalArgumentException(action);
        }});
    }
    private static void conserved(ServerPlayer p){
        require(toggleBase!=null&&toggleShares!=null,"same post-arrow balance baseline exists");
        long now=p.level().getGameTime(),last=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getLong("lastShieldSpend");
        require(last==toggleSpendTick&&now>=last&&now-last<60,"close and reopen occur entirely inside original native shield recovery delay, without resetting or freezing it");
        var actual=state(p);var reserves=shares(p,actual);
        require(actual.health().compareTo(toggleBase.health())==0&&actual.shield().compareTo(toggleBase.shield())==0
                &&reserves.lower().compareTo(toggleShares.lower())==0&&reserves.upper().compareTo(toggleShares.upper())==0,
                "actual close/wait/reopen preserves each of health, base S, lower and upper exactly; no hidden refill or debit");
    }
    private static Arrow spawn(ServerPlayer p,boolean phase){
        require(enemy.isAlive()&&!enemy.isRemoved(),"real living original native shooter");
        var a=new Arrow(p.serverLevel(),enemy,new ItemStack(Items.ARROW),new ItemStack(Items.BOW));
        if(phase){a.setPos(enemy.getEyePosition());a.setDeltaMovement(p.getBoundingBox().getCenter().subtract(a.position()).normalize().scale(7.8));}
        else{a.setPos(enemy.position().add(0,2.5,0));a.setDeltaMovement(0,0,-.8);}
        require(p.serverLevel().addFreshEntity(a),"ordinary plain enemy arrow actually enters native ticking");
        LogUtils.getLogger().info("QA_NATIVE_ARROW_LAUNCH mode={} arrow={} shooter={} position={} velocity={} tick={}",mode,a.getUUID(),enemy.getUUID(),a.position(),a.getDeltaMovement(),p.level().getGameTime());return a;
    }
    private static void shootLane(ServerPlayer p){
        var a=new Arrow(p.serverLevel(),enemy,new ItemStack(Items.ARROW),new ItemStack(Items.BOW));
        a.setPos(enemy.getEyePosition());a.setDeltaMovement(-7.8,0,0);
        require(p.serverLevel().addFreshEntity(a),"real lane arrow joins ordinary ticking from its actual enemy shooter");phaseArrows.add(a);burst++;lastBurstTick=p.level().getGameTime();
        LogUtils.getLogger().info("QA_PHASE_NATIVE_ARROW_LAUNCH arrow={} shooter={} position={} velocity={} tick={} burst={}",a.getUUID(),enemy.getUUID(),a.position(),a.getDeltaMovement(),lastBurstTick,burst);
    }
    private static long actualJump(ServerPlayer p){try{var f=NewSkillRuntime.class.getDeclaredField("LAST_JUMP");f.setAccessible(true);Object value=((Map<?,?>)f.get(null)).get(owner);return value instanceof Long?(long)value:-1;}catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST)public static void tickBefore(ServerTickEvent.Post e){
        if(!watching)return;var p=e.getServer().getPlayerList().getPlayer(owner);if(p==null)return;
        minCp=Math.min(minCp,AbilityStorage.get(p).cp);minBudget=Math.min(minBudget,HighPowerResources.availableCp(p));
        if(!mode.equals("phase_corridor"))return;
        long now=p.level().getGameTime();if(p.position().distanceTo(origin)>5&&actualJump(p)>baselineTick)jumpTick=actualJump(p);
        // A short ordinary arrow lane already exists when the real key arrives. No sampling-phase
        // assumption can extend the two native ticks: Incoming below must certify the actual times.
        if(armed&&burst<6&&now>lastBurstTick)shootLane(p);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public static void incoming(LivingIncomingDamageEvent event){
        if(!watching||!mode.equals("phase_corridor")||!(event.getEntity() instanceof ServerPlayer p)||!p.getUUID().equals(owner))return;
        if(!(event.getSource().getDirectEntity() instanceof Arrow a)||!phaseArrows.contains(a))return;
        long actual=actualJump(p);if(actual<=baselineTick)return;jumpTick=actual;long elapsed=p.level().getGameTime()-jumpTick;
        if(elapsed>=0&&elapsed<2){require(NewSkillRuntime.evading(p)&&event.isCanceled(),"real lane projectile is canceled only inside actual two-tick phase window");if(!earlyIncoming){early=a;earlySource=event.getSource();earlyBefore=state(p);earlyIncoming=earlyCanceled=true;earlyElapsed=elapsed;LogUtils.getLogger().info("QA_PHASE_EARLY_INCOMING arrow={} elapsed={} healthBefore={} shieldBefore={} canceled=true",a.getUUID(),elapsed,earlyBefore.health(),earlyBefore.shield());}}
        if(elapsed>=2&&elapsed<5&&!lateIncoming){require(!NewSkillRuntime.evading(p)&&!event.isCanceled(),"real lane projectile enters actual three-tick exposed window without phase cancellation");late=a;lateBefore=state(p);lateIncoming=true;lateElapsed=elapsed;}

    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)public static void damage(LivingDamageEvent.Post event){
        if(!watching||!(event.getEntity() instanceof ServerPlayer p)||!p.getUUID().equals(owner))return;
        if(mode.equals("phase_corridor")){require(earlySource==null||event.getSource()!=earlySource,"canceled exact early native source must never reach damage Post");if(event.getSource().getDirectEntity()==late){require(event.getNewDamage()>0,"real positive late projectile damage reaches native Post");latePost=true;}return;}
        if(event.getSource().getDirectEntity()!=arrow)return;
        post=true;before=state(p);sharesBefore=shares(p,before);nativeDamage=new BigDecimal(Float.toString(event.getNewDamage()));quotaBefore=quota(p);
        try{var f=HighPowerCombat.class.getDeclaredField("FRAMES");f.setAccessible(true);Object frame=((Map<?,?>)f.get(null)).get(p);capture=field(frame,mode.equals("vector_parry")?"vectorParry":"autonomousVectorCircle");}catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)public static void afterArrow(EntityTickEvent.Post event){
        if(!watching||event.getEntity().level().isClientSide)return;var p=event.getEntity().getServer().getPlayerList().getPlayer(owner);if(p==null)return;
        if(event.getEntity()==early&&earlyIncoming&&!earlyChecked){earlyChecked=true;var now=state(p);earlyUntouched=now.health().compareTo(earlyBefore.health())==0&&now.shield().compareTo(earlyBefore.shield())==0;require(earlyUntouched,"this exact canceled early native hurt preserves its own incoming-to-tick-return HP/S; before="+earlyBefore+" after="+now);LogUtils.getLogger().info("QA_PHASE_EARLY_LEDGER_PASS arrow={} elapsed={} health={} shield={} exactCanceledSourceNoPost=true sameArrowFirstTickReturn=true",early.getUUID(),earlyElapsed,now.health(),now.shield());}
        if(event.getEntity()==late&&latePost&&!latePaid){var now=state(p);latePaid=now.health().add(now.shield()).compareTo(lateBefore.health().add(lateBefore.shield()))<0;if(latePaid)LogUtils.getLogger().info("QA_PHASE_EXPOSED_LEDGER_PASS arrow={} elapsed={} before={} after={} actualNativePost=true",late.getUUID(),lateElapsed,lateBefore.health().add(lateBefore.shield()),now.health().add(now.shield()));}
        if(event.getEntity()!=arrow||!post||committed)return;
        require(capture!=null,"native arrow needs a captured selected specialist, not base fallback");
        try{
            require((boolean)field(capture,"committed"),"selected specialist capture actually committed");Object result=field(capture,"planned");require(result!=null,"committed native source has its own planned result");
            BigDecimal absorbed=(BigDecimal)result.getClass().getMethod("absorbed").invoke(result);var after=state(p);var afterShares=shares(p,after);
            BigDecimal debit=before.shield().add(sharesBefore.total()).subtract(after.shield().add(afterShares.total()));
            require(absorbed.signum()>0&&debit.compareTo(absorbed)==0&&quota(p)==quotaBefore-1&&after.health().compareTo(before.health())==0,"actual source-bound quota admission and exact positive shield/share debit protect HP");
            if(mode.equals("vector_parry"))require(debit.compareTo(nativeDamage.multiply(new BigDecimal(".75")))==0,"actual ordinary arrow receives dedicated25% efficiency");
            else require(field(capture,"arrow")!=null&&debit.compareTo(nativeDamage)==0,"autonomous ordinary arrow owns the authenticated native arrow window and exact debit");
            require(arrow.getOwner()==enemy,"non-reflecting specialist cannot borrow base shield reflected ownership");committed=true;
            LogUtils.getLogger().info("QA_SPECIALIST_NATIVE_ARROW_COMMIT_PASS mode={} arrow={} raw={} absorbed={} actualDebit={} quotaBefore={} quotaAfter={} sameSourceCapture=true",mode,arrow.getUUID(),nativeDamage,absorbed,debit,quotaBefore,quota(p));
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    private static boolean active(ServerPlayer p){return mode.equals("vector_parry")?VectorParry.active(p):AutonomousVectorCircle.active(p);}
    private static int quota(ServerPlayer p){return mode.equals("vector_parry")?VectorParry.remainingHits(p):AutonomousVectorCircle.remainingHits(p);}
    private static ExactCombatState state(ServerPlayer p){try{var m=HighPowerCombat.class.getDeclaredMethod("exactStateSnapshot",net.minecraft.world.entity.LivingEntity.class);m.setAccessible(true);return(ExactCombatState)((Optional<?>)m.invoke(null,p)).orElseThrow();}catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}}
    private static SharedShieldReserveShares shares(ServerPlayer p,ExactCombatState base){try{var m=MagneticDomainReserve.class.getDeclaredMethod("savedShares",net.minecraft.world.entity.LivingEntity.class,ExactCombatState.class);m.setAccessible(true);return(SharedShieldReserveShares)((Optional<?>)m.invoke(null,p,base)).orElseThrow();}catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}}
    private static Object field(Object value,String name)throws ReflectiveOperationException{if(value==null)return null;var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("QA_NATIVE_ARROW_MODE_FAILED mode="+mode+" "+why);}
}

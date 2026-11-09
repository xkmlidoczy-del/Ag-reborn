package cn.academy.port.qa;

import cn.academy.port.highpower.*;
import com.mojang.logging.LogUtils;
import java.math.BigDecimal;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** Same client/actor/source: native no-cast control then true-key specialist, measuring actual S loss. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class GameplayAcceptanceVmEfficiency {
    private static String id;private static UUID owner;private static Zombie enemy;private static BigDecimal raw,controlRaw,controlCost;
    private static boolean hitting,controlled,passed;private static Object capture;private static String frameEvidence="";
    private GameplayAcceptanceVmEfficiency(){}
    public static void run(Minecraft mc,String action){
        if(action.startsWith("setup:")){
            id=action.substring(6);require(id.equals("vector_parry")||id.equals("circulation_relief"),"directional efficiency skill");
            GameplayAcceptanceVmUtility.run(mc,"setup:"+id);
            GameplayAcceptanceTpVm.server(mc,p->{owner=p.getUUID();controlled=passed=false;enemy=new Zombie(EntityType.ZOMBIE,p.serverLevel());enemy.setNoAi(true);enemy.setSilent(true);enemy.setPos(p.position().add(0,0,12));require(p.serverLevel().addFreshEntity(enemy)&&HighPowerCombat.enroll(enemy,5),"same durable plain native enemy survives the real category counter between A/B hits");fixture(p);});return;
        }
        GameplayAcceptanceTpVm.server(mc,p->{require(p.getUUID().equals(owner),"same actor");switch(action){
            case "control"->{require(!active(p),"no-cast baseline has no specialist");controlCost=hit(p);controlRaw=raw;require(controlCost.compareTo(raw)==0,"ordinary category S pays full native damage");controlled=true;}
            case "prepare-cast"->{require(controlled,"actual control already observed");fixture(p);}
            case "cast"->{require(controlled&&active(p),"same actual actor has real-key skill active");int quota=remaining(p);BigDecimal cost=hit(p);
                require(raw.compareTo(controlRaw)==0,"same real native melee amount before reduction");require(cost.compareTo(controlCost.multiply(new BigDecimal(".75")))==0,"casting reduces actual S loss by25%, not just active pose; control="+controlCost+" cast="+cost+" raw="+raw+" quotaBefore="+quota+" quotaAfter="+remaining(p)+" frame="+frameEvidence+" planned="+read(capture,"planned"));
                require(remaining(p)==quota-1,"one exact positive contact consumes original specialist quota");passed=true;
                LogUtils.getLogger().info("QA_VM_EFFICIENCY_AB_PASS skill={} controlS={} castS={} nativeDamage={} ratio=0.75 sameClient=true nativeDoHurtTarget=true",id,controlCost,cost,raw);}
            default->throw new IllegalArgumentException(action);
        }});
    }
    private static void fixture(ServerPlayer p){var d=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);d.putString("shield","20");d.putLong("lastShieldSpend",p.level().getGameTime());p.invulnerableTime=0;}
    private static BigDecimal hit(ServerPlayer p){
        require(enemy.isAlive()&&!enemy.isRemoved()&&p.serverLevel().getEntity(enemy.getUUID())==enemy,"A/B must never invoke native attacks on a dead or replaced source");
        enemy.setPos(p.position().add(id.equals("circulation_relief")?1.2:0,0,id.equals("circulation_relief")?0:1.2));p.invulnerableTime=0;
        BigDecimal s=HighPowerCombat.shield(p),h=new BigDecimal(HighPowerCombat.healthText(p));raw=null;capture=null;frameEvidence="";hitting=true;
        LogUtils.getLogger().info("QA_VM_EFFICIENCY_BEFORE skill={} active={} categoryEligible={} S={} categoryBypass={} crouching={} grounded={} position={} facing={} attacker={} quota={} protecting={} ground={}",id,active(p),EsperShields.eligible(p),s,EsperLegacyBypass.standAside(),p.isShiftKeyDown(),p.onGround(),p.position(),p.getLookAngle(),enemy.position(),remaining(p),id.equals("circulation_relief")?CirculationRelief.protecting(p):true,ground(p));
        try{enemy.doHurtTarget(p);}finally{hitting=false;}
        LogUtils.getLogger().info("QA_VM_EFFICIENCY_AFTER skill={} raw={} shieldBefore={} shieldAfter={} quota={} frame={} planned={}",id,raw,s,HighPowerCombat.shield(p),remaining(p),frameEvidence,read(capture,"planned"));
        require(raw!=null&&raw.signum()>0&&new BigDecimal(HighPowerCombat.healthText(p)).compareTo(h)==0&&HighPowerCombat.shield(p).signum()>0,"real native contact costs positive S with unchanged HP");return s.subtract(HighPowerCombat.shield(p));
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void post(LivingDamageEvent.Post e){if(hitting&&e.getEntity().getUUID().equals(owner)){
        raw=new BigDecimal(Float.toString(e.getNewDamage()));
        try{var field=HighPowerCombat.class.getDeclaredField("FRAMES");field.setAccessible(true);var frame=((java.util.Map<?,?>)field.get(null)).get(e.getEntity());capture=read(frame,id.equals("vector_parry")?"vectorParry":"circulationRelief");
            frameEvidence="capture="+(capture!=null)+",poisoned="+read(frame,"poisoned")+",parent="+(read(frame,"parent")!=null)+",postGuard="+read(frame,"anchorPostGuard")+",source="+e.getSource().getClass().getName()+","+sourceEvidence((ServerPlayer)e.getEntity(),e.getSource());
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }}
    private static String sourceEvidence(ServerPlayer p,net.minecraft.world.damagesource.DamageSource source){
        try{
            Class<?> controller=id.equals("vector_parry")?VectorParry.class:CirculationRelief.class;
            var method=controller.getDeclaredMethod("reviewedSource",ServerPlayer.class,net.minecraft.world.damagesource.DamageSource.class);method.setAccessible(true);
            Object reviewed=method.invoke(null,p,source);var owned=controller.getDeclaredMethod("owned",ServerPlayer.class);owned.setAccessible(true);Object context=owned.invoke(null,p);
            Object validity="no_context";if(context!=null){var valid=controller.getDeclaredMethod("valid",context.getClass(),boolean.class);valid.setAccessible(true);validity=valid.invoke(null,context,false);}
            var key=source.typeHolder().unwrapKey().orElseThrow().location();var resource=p.server.getResourceManager().getResource(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(key.getNamespace(),"damage_type/"+key.getPath()+".json"));
            return "reviewedSource="+(reviewed!=null)+",type="+key+",tags="+source.typeHolder().tags().map(t->t.location().toString()).toList()+",pack="+resource.map(r->r.sourcePackId()+"/"+r.knownPackInfo()).orElse("absent")
                    +",attackerSupported="+HighPowerCombat.supported(enemy)+",allied="+p.isAlliedTo(enemy)+"/"+enemy.isAlliedTo(p)+",sourceOrigin="+source.getSourcePosition()+",attackerPosition="+enemy.position()
                    +",valid="+validity+",busy="+read(context,"paying")+"/"+read(context,"planning")+"/"+read(context,"capturing");
        }catch(ReflectiveOperationException ex){return "sourceEvidence="+ex;}
    }
    private static Object read(Object value,String name){if(value==null)return null;try{var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}catch(ReflectiveOperationException ex){return ex.toString();}}
    private static Object ground(ServerPlayer p){if(!id.equals("circulation_relief"))return "not_applicable";try{var f=CirculationRelief.class.getDeclaredMethod("ground",ServerPlayer.class,net.minecraft.world.phys.Vec3.class);f.setAccessible(true);return f.invoke(null,p,p.position());}catch(ReflectiveOperationException ex){return ex.toString();}}
    private static boolean active(ServerPlayer p){return id.equals("vector_parry")?VectorParry.active(p):CirculationRelief.active(p);}
    private static int remaining(ServerPlayer p){return id.equals("vector_parry")?VectorParry.remainingHits(p):CirculationRelief.remainingHits(p);}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("QA_VM_EFFICIENCY_FAILED skill="+id+" "+why);}
}

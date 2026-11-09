package cn.academy.port.qa;

import cn.academy.port.*;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.BrakingTouchRules;
import cn.academy.port.client.CirculationListeningState;
import com.mojang.logging.LogUtils;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.*;

/** Independent real-input witnesses. Only fixtures/terrain are prepared; no fall, motion, cue or skill callback is fabricated. */
@EventBusSubscriber(modid="academy", value=Dist.CLIENT)
public final class GameplayAcceptanceVmAlternatives {
    private static final Map<String,String> MODES=Map.of("parry-full","vector_parry","parry-right","vector_parry","parry-left","vector_parry",
            "braking-fall","braking_touch","braking-snowball","braking_touch","anchor-fall","inertial_anchor","anchor-control-fall","inertial_anchor",
            "listening-runner","circulation_listening","trajectory-hammer","trajectory_return");
    private static boolean anchorControlVerified;private static BigDecimal anchorControlCost,anchorControlRaw;
    private static String mode,id; private static UUID owner; private static Zombie enemy; private static BlockPos platform;
    private static Snowball snowball;private static boolean watching,passed,contact,removedPlatform,braked,serverCue,clientCue;
    private static Vec3 start,runnerStart,motion;private static BigDecimal health,shield,targetBefore,landingHealth,landingShield,fallPostDamage;private static float fallDistance,fallMultiplier;
    private static double safeFall,fallFactor,cpBefore,budgetBefore,minCp,minBudget;private static long paidClock;private static int stock;
    private GameplayAcceptanceVmAlternatives(){}
    public static void run(Minecraft mc,String action){
        if(action.startsWith("setup:")){
            mode=action.substring(6);id=MODES.get(mode);require(id!=null,"known alternate VM mode");
            GameplayAcceptanceVmUtility.run(mc,"setup:"+id);
            GameplayAcceptanceTpVm.server(mc,GameplayAcceptanceVmAlternatives::prepare);return;
        }
        // Native mouse callback, then the next script action presses real R. No projectile is spawned by QA.
        if(action.equals("direction:snowball")){GameplayAcceptanceEmMdModes.run(mc,"mouse:click");return;}
        GameplayAcceptanceTpVm.server(mc,p->{require(owner!=null&&p.getUUID().equals(owner),"same fixture owner");
            switch(action){case "baseline"->baseline(p);case "assert"->verify(p);case "drop-control"->{require(mode.equals("anchor-control-fall")&&watching&&!removedPlatform,"prepared control fall");removedPlatform=true;p.serverLevel().setBlockAndUpdate(platform,Blocks.AIR.defaultBlockState());}default->throw new IllegalArgumentException(action);}
        });
    }
    private static void prepare(ServerPlayer p){
        if(mode.equals("anchor-control-fall"))anchorControlVerified=false;
        owner=p.getUUID();watching=passed=contact=removedPlatform=braked=serverCue=clientCue=false;snowball=null;fallDistance=0;motion=Vec3.ZERO;landingHealth=landingShield=fallPostDamage=null;
        var world=p.serverLevel();for(var e:world.getEntities(p,p.getBoundingBox().inflate(30)))if(!(e instanceof net.minecraft.world.entity.player.Player))e.discard();
        start=p.position();platform=BlockPos.containing(start).above(7);
        if(mode.endsWith("fall"))world.setBlockAndUpdate(platform,Blocks.STONE.defaultBlockState());
        if(mode.equals("braking-snowball")){p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.SNOWBALL,8));p.inventoryMenu.broadcastChanges();}
        if(mode.startsWith("parry")||mode.equals("listening-runner")||mode.equals("trajectory-hammer")){
            enemy=new Zombie(EntityType.ZOMBIE,world);enemy.setSilent(true);enemy.setNoAi(!mode.equals("listening-runner"));
            enemy.setPos(start.add(mode.equals("listening-runner")?-7:0,0,mode.equals("listening-runner")?7:mode.startsWith("parry")?1.2:6));
            require(world.addFreshEntity(enemy),"real plain native zombie fixture");
            if(mode.equals("trajectory-hammer"))require(HighPowerCombat.enroll(enemy,5),"durable actual air-hammer target");
            runnerStart=enemy.position();
        }else enemy=null;
        AcademyNetwork.sync(p);LogUtils.getLogger().info("QA_VM_ALTERNATIVE_PREPARED mode={} platform={} normalSurvival={}",mode,platform,!p.isCreative());
    }
    private static void baseline(ServerPlayer p){
        require(!watching&&!passed,"one baseline");watching=true;start=p.position();
        if(mode.equals("parry-right")||mode.equals("parry-left")||mode.endsWith("fall")){
            var ledger=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
            ledger.putString("shield",mode.startsWith("anchor-")?"2":mode.startsWith("parry")?"1":"0");
            ledger.putLong("lastShieldSpend",p.level().getGameTime());AcademyNetwork.sync(p);
        }
        health=hp(p);shield=HighPowerCombat.shield(p);targetBefore=mode.equals("trajectory-hammer")?total(enemy):BigDecimal.ZERO;
        cpBefore=minCp=AbilityStorage.get(p).cp;budgetBefore=minBudget=HighPowerResources.availableCp(p);
        paidClock=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_"+id);
        stock=p.getMainHandItem().getCount();safeFall=p.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);fallFactor=p.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
        if(mode.endsWith("fall"))require(p.onGround()&&Math.abs(p.getY()-(platform.getY()+1))<.001,"actual native support on eight-metre platform before activation");
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void joined(EntityJoinLevelEvent e){
        if(watching&&!passed&&!e.getLevel().isClientSide&&mode.equals("braking-snowball")&&e.getEntity().getClass()==Snowball.class){
            var ball=(Snowball)e.getEntity();if(ball.getOwner()!=null&&ball.getOwner().getUUID().equals(owner)){
                require(snowball==null&&ball.getDeltaMovement().lengthSqr()>0,"one genuinely thrown moving owner snowball");snowball=ball;
            }
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void preEntity(EntityTickEvent.Pre e){
        if(watching&&!passed&&snowball==e.getEntity()&&!e.getEntity().level().isClientSide&&snowball.getDeltaMovement().equals(Vec3.ZERO))braked=true;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void falling(LivingFallEvent e){
        if(watching&&!passed&&e.getEntity() instanceof ServerPlayer player&&player.getUUID().equals(owner)&&mode.endsWith("fall")){
            // Native standing contacts may emit later harmless FallEvents; they are not another damaging fall.
            if(e.getDistance()<=e.getEntity().getAttributeValue(Attributes.SAFE_FALL_DISTANCE))return;
            LogUtils.getLogger().info("QA_VM_NATIVE_FALL mode={} distance={} multiplier={} previous={} platformRemoved={} position={} anchorActive={} brakeActive={} nativeLandingAvailable={} landingHurtAvailable={} baseS={} grounded={} elapsedServerTicks={}",mode,e.getDistance(),e.getDamageMultiplier(),fallDistance,removedPlatform,player.position(),InertialAnchor.active(player),BrakingTouch.active(player),BrakingTouchLandingProof.available(player),BrakingTouchLandingProof.landingHurtAvailable(player),HighPowerCombat.shield(player),player.onGround(),player.tickCount);
            require(fallDistance==0,"one genuine damaging native fall event; previous="+fallDistance+" next="+e.getDistance());fallDistance=e.getDistance();fallMultiplier=e.getDamageMultiplier();
            require(fallDistance>7&&fallDistance<9,"real gravity produced the eight-metre fall, not an injected damage event");
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void fallDamage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post e){
        if(watching&&!passed&&e.getEntity() instanceof ServerPlayer player&&player.getUUID().equals(owner)&&mode.endsWith("fall")&&e.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)){
            require(fallDistance>0&&fallPostDamage==null,"one actual native accepted fall-damage Post");
            fallPostDamage=new BigDecimal(Float.toString(e.getNewDamage()));
            LogUtils.getLogger().info("QA_VM_NATIVE_FALL_POST mode={} nativeDamage={} nativeDistance={} health={} shield={} anchorActive={} brakeActive={} anchorQuota={}",mode,fallPostDamage,fallDistance,hp((ServerPlayer)e.getEntity()),HighPowerCombat.shield(e.getEntity()),InertialAnchor.active((ServerPlayer)e.getEntity()),BrakingTouch.active((ServerPlayer)e.getEntity()),e.getEntity().getPersistentData().getCompound(InertialAnchor.QUOTA_KEY));
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void post(ServerTickEvent.Post e){
        if(!watching||passed)return;var p=e.getServer().getPlayerList().getPlayer(owner);if(p==null)return;
        minCp=Math.min(minCp,AbilityStorage.get(p).cp);minBudget=Math.min(minBudget,HighPowerResources.availableCp(p));
        if(mode.endsWith("fall")&&fallPostDamage!=null&&landingHealth==null){landingHealth=hp(p);landingShield=HighPowerCombat.shield(p);}
        if(mode.startsWith("parry")&&!contact&&VectorParry.active(p)&&VectorParry.precisionAvailable(p)){
            // Attack at the first real native server frame of the real key activation, inside its unchanged precision window.
            contact=true;int quota=VectorParry.remainingHits(p);BigDecimal before=HighPowerCombat.shield(p);p.invulnerableTime=0;
            enemy.doHurtTarget(p);motion=p.getDeltaMovement();
            require(VectorParry.remainingHits(p)==quota-1&&HighPowerCombat.shield(p).compareTo(before)<0,"actual melee consumes one positive parry absorption");
            if(mode.equals("parry-full"))require(HighPowerCombat.shield(p).signum()>0&&hp(p).equals(health)&&motion.horizontalDistanceSqr()<1e-10&&VectorParry.precisionAvailable(p),"full shield keeps superarmor and does not fabricate a precise impulse");
            else require(HighPowerCombat.shield(p).signum()==0&&hp(p).compareTo(health)<0&&!VectorParry.precisionAvailable(p)
                    &&Math.abs(motion.z)<1e-6&&(mode.equals("parry-left")?motion.x>0:motion.x<0),"actual breaking hit spends precise token and rotates genuine knockback to selected side");
        }
        if(mode.endsWith("fall")&&!removedPlatform&&(mode.equals("anchor-fall")?InertialAnchor.active(p):BrakingTouch.active(p))){
            require(p.onGround(),"cast starts on genuine upper support");removedPlatform=true;
            // Physical terrain removal only; native gravity, movement packets, contact, fall event and hurt do all subsequent work.
            p.serverLevel().setBlockAndUpdate(platform,Blocks.AIR.defaultBlockState());
        }
        if(mode.equals("listening-runner")&&CirculationListening.active(p)){
            enemy.setSprinting(true);if(enemy.getNavigation().isDone())enemy.getNavigation().moveTo(runnerStart.x+14,runnerStart.y,runnerStart.z,1.3);
            var v=CirculationListening.lastVisual(p);if(v!=null&&v.cues().stream().anyMatch(c->c.kind()==1&&c.strength()>0))serverCue=true;
        }
    }
    @SubscribeEvent public static void client(ClientTickEvent.Post e){
        if(!watching||passed||!mode.equals("listening-runner"))return;
        try{var field=Class.forName("cn.academy.port.client.CirculationListeningEffects").getDeclaredField("STATE");field.setAccessible(true);
            var state=(CirculationListeningState)field.get(null);if(state.controllers().stream().anyMatch(c->c.id().owner().equals(owner)&&c.frame().cues().stream().anyMatch(v->v.kind()==1&&v.strength()>0)))clientCue=true;
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    private static void verify(ServerPlayer p){
        require(watching&&!passed,"real input witness required");
        switch(mode){
            case "parry-full","parry-right","parry-left"->require(contact,"actual native melee and immediate precision assertions completed");
            case "braking-fall","anchor-fall","anchor-control-fall"->{
                require(removedPlatform&&fallDistance>7&&p.onGround()&&Math.abs(p.getY()-(start.y-8))<.03,"native gravity completed genuine eight-metre landing");
                float effective=mode.equals("braking-fall")?BrakingTouchRules.reducedDistance(fallDistance):fallDistance;
                int damage=net.minecraft.util.Mth.ceil((double)((effective-(float)safeFall)*fallMultiplier)*fallFactor);
                float scaled=(float)cn.academy.port.highpower.core.EsperDamageRules.environmentalAmount(EsperProtectionConfig.policy(),"minecraft:fall",damage,HighPowerGrowth.values(p).damage());
                BigDecimal expected=new BigDecimal(Float.toString(scaled));
                require(fallPostDamage!=null&&fallPostDamage.compareTo(expected)==0&&landingHealth!=null,"actual native fall Post carries the reduced distance and normal configured environment scaling; rawPost="+fallPostDamage+" expected="+expected+" distance="+fallDistance+" multiplier="+fallMultiplier+" effective="+effective+" nativeDamage="+damage+" landingHP="+landingHealth);
                if(mode.equals("braking-fall")){
                    require(damage<net.minecraft.util.Mth.ceil((double)((fallDistance-(float)safeFall)*fallMultiplier)*fallFactor),"landing must actually prevent positive native damage");
                    require(landingShield.signum()==0,"empty shield cannot mask actual braking benefit at real landing");
                }else{
                    if(mode.equals("anchor-fall"))expected=expected.multiply(new BigDecimal(".5"));
                    expected=expected.subtract(shield.min(expected));require(landingShield.signum()==0,"actual landing spends two existing S before HP");
                    if(mode.equals("anchor-fall"))require(p.getPersistentData().getCompound(InertialAnchor.QUOTA_KEY).getInt("hits")==1,"real grounded fall commits one anchor admission, not merely base shield");
                }
                require(health.subtract(landingHealth).compareTo(expected)==0,"exact HP loss at landing matches actual native fall remainder: expected="+expected+" actual="+health.subtract(landingHealth));
                var cost=health.subtract(landingHealth).add(shield.subtract(landingShield));
                if(mode.equals("anchor-control-fall")){anchorControlCost=cost;anchorControlRaw=fallPostDamage;anchorControlVerified=true;require(!InertialAnchor.active(p)&&cost.compareTo(fallPostDamage)==0,"true no-cast landing pays full native cost");}
                if(mode.equals("anchor-fall")&&anchorControlVerified){require(cost.multiply(anchorControlRaw).compareTo(anchorControlCost.multiply(fallPostDamage).multiply(new BigDecimal(".5")))==0,"same-client A/B proves50% lower resource cost after normalizing each genuine native fall");LogUtils.getLogger().info("QA_VM_EFFICIENCY_AB_PASS skill=inertial_anchor controlCost={} controlNative={} castCost={} castNative={} ratio=0.5",anchorControlCost,anchorControlRaw,cost,fallPostDamage);}
            }
            case "braking-snowball"->require(snowball!=null&&braked&&p.getMainHandItem().is(Items.SNOWBALL)&&p.getMainHandItem().getCount()==stock-1,"real native item-use consumes one snowball and real R brakes that same moving projectile");
            case "listening-runner"->require(enemy.position().distanceTo(runnerStart)>1&&serverCue&&clientCue,"actual sprinting native AI movement produces accepted runner disturbance cue on owner client");
            case "trajectory-hammer"->{var r=TrajectoryReturn.receipt(p);require(r!=null&&r.fallback()&&r.paid()&&r.hurt()&&total(enemy).compareTo(targetBefore)<0,"empty arrow corridor triggers paid air hammer with actual native target damage; receipt="+r+" impacts="+TrajectoryReturn.receipts(p)+" before="+targetBefore+" actual="+total(enemy)+" eye="+p.getEyePosition()+" look="+p.getLookAngle()+" target="+enemy.getBoundingBox());require(p.serverLevel().getEntitiesOfClass(AbstractArrow.class,p.getBoundingBox().inflate(20)).isEmpty(),"fallback has no native arrows to reverse");}
            default->throw new IllegalStateException(mode);
        }
        require(mode.equals("anchor-control-fall")||minCp<cpBefore-1e-6||minBudget<budgetBefore-1e-6||p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_"+id)>paidClock,"actual finite payment must accompany effect");
        passed=true;watching=false;LogUtils.getLogger().info("QA_GAMEPLAY_PASS skill={} mechanism=vm_alternate mode={} nativeImpulse={} actualFall={} actualHpLoss={} serverRunnerCue={} ownerRunnerCue={} sameSnowballBraked={} normalGLFW=true",id,mode,motion,fallDistance,health.subtract(hp(p)),serverCue,clientCue,braked);
    }
    private static BigDecimal hp(ServerPlayer p){return new BigDecimal(HighPowerCombat.healthText(p));}
    private static BigDecimal total(LivingEntity e){return new BigDecimal(HighPowerCombat.healthText(e)).add(HighPowerCombat.shield(e));}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("QA_VM_ALTERNATIVE_FAILED mode="+mode+" "+why);}
}

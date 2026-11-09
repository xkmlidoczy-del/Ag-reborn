package cn.academy.port.qa;

import cn.academy.port.*;
import cn.academy.port.highpower.*;
import cn.academy.port.client.CirculationListeningState;
import com.mojang.logging.LogUtils;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Actual native impacts/projectiles/items. All skill activation and direction choices use real client keys. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class GameplayAcceptanceVmUtility {
    private static final Set<String> IDS=Set.of("vec_deviation","vec_reflection","vector_parry","inertial_anchor","circulation_relief","multi_axis_reflection","autonomous_vector_circle","braking_touch","circulation_listening","trajectory_return","impulse_split");
    private static final class Fixture {
        final UUID owner;final String id;Zombie enemy;Arrow arrow;UUID item;Vec3 originalVelocity,lastUnsplit;
        double originalDamage,lastUnsplitDamage,cp,budget,minCp,minBudget,incoming,remaining,itemSpeed;BigDecimal shield,hp;
        boolean begun,passed,melee,arrowChanged,braked,serverCue,clientCue,split,multiCommitted;
        String multiEvidence="no committed dedicated flight observed";
        Object multiCapture,multiSnapshot,multiDefender;
        double splitBefore,splitAfter;int inputChoices,multiQuotaBefore;long paidClock;
        Fixture(ServerPlayer p,String id){owner=p.getUUID();this.id=id;}
    }
    private static Fixture fixture;
    private GameplayAcceptanceVmUtility(){}
    public static void run(Minecraft mc,String action){
        if(action.startsWith("efficiency:")){GameplayAcceptanceVmEfficiency.run(mc,action.substring(11));return;}
        if(action.startsWith("vmalt:")){GameplayAcceptanceVmAlternatives.run(mc,action.substring(6));return;}
        if(action.startsWith("tpalt:")){GameplayAcceptanceTpAlternatives.run(mc,action.substring(6));return;}
        if(action.startsWith("incident:")){GameplayAcceptanceIncidentBypass.run(mc,action.substring(9));return;}
        if(action.startsWith("setup:")){
            String id=action.substring(6);require(IDS.contains(id),"implemented VM utility skill "+id);
            LiveInputQa.run(mc,"setup:"+id);
            GameplayAcceptanceTpVm.server(mc,p->prepare(p,id));LiveInputQa.assertHealthy();return;
        }
        if(action.equals("aim-item")){
            require(fixture!=null&&fixture.item!=null,"real Q toss baseline required");
            var item=mc.level.getEntitiesOfClass(ItemEntity.class,mc.player.getBoundingBox().inflate(8)).stream().filter(e->e.getUUID().equals(fixture.item)).findFirst().orElseThrow();
            aim(mc,item.getBoundingBox().getCenter());return;
        }
        if(action.startsWith("direction:")){
            double yaw=Double.parseDouble(action.substring(10));mc.player.setYRot((float)yaw);mc.player.setXRot(0);fixture.inputChoices++;return;
        }
        GameplayAcceptanceTpVm.server(mc,p->{
            require(fixture!=null&&fixture.owner.equals(p.getUUID()),"same VM fixture actor");
            switch(action){
                case "baseline"->baseline(p);
                case "arrow"->launch(p);
                case "melee"->melee(p);
                case "tossed"->tossed(p);
                case "assert"->verify(p);
                case "verify"->require(fixture.passed,"actual selected mechanism passed");
                default->throw new IllegalArgumentException(action);
            }
        });
    }
    private static void prepare(ServerPlayer p,String id){
        fixture=new Fixture(p,id);var f=fixture;var world=p.serverLevel();var at=p.blockPosition();
        for(var e:world.getEntities(p,p.getBoundingBox().inflate(35)))if(!(e instanceof net.minecraft.world.entity.player.Player))e.discard();
        for(int x=-10;x<=10;x++)for(int z=-10;z<=24;z++){
            world.setBlock(at.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
            for(int y=0;y<6;y++)world.setBlock(at.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        world.setWeatherParameters(6000,0,false,false);world.setDayTime(18000);p.removeAllEffects();
        f.enemy=new Zombie(EntityType.ZOMBIE,world);f.enemy.setNoAi(true);f.enemy.setSilent(true);f.enemy.setPos(p.position().add(0,0,12));
        require(world.addFreshEntity(f.enemy),"actual plain native hostile shooter added");
        if(id.equals("braking_touch"))p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_INGOT));
        p.inventoryMenu.broadcastChanges();AcademyNetwork.sync(p);
        LogUtils.getLogger().info("QA_GAMEPLAY_VM_UTILITY_PREPARED skill={} survival={} actualNativeEnemy={} noSkillInvocation=true",id,!p.isCreative(),f.enemy.getUUID());
    }
    private static void baseline(ServerPlayer p){
        var f=fixture;require(!f.begun,"one baseline per utility cast");f.begun=true;
        f.cp=f.minCp=AbilityStorage.get(p).cp;f.budget=f.minBudget=HighPowerResources.availableCp(p);f.hp=hp(p);f.shield=HighPowerCombat.shield(p);f.paidClock=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_"+f.id);
    }
    private static void launch(ServerPlayer p){
        var f=fixture;require(f.begun&&f.arrow==null,"one actual native incoming arrow per fixture");
        if(f.id.equals("multi_axis_reflection")){f.multiQuotaBefore=MultiAxisReflection.remainingHits(p);require(MultiAxisReflection.active(p)&&f.multiQuotaBefore==4,"real-key dedicated reflection begins with four actual admissions");}
        var a=new Arrow(p.serverLevel(),f.enemy,new ItemStack(Items.ARROW),new ItemStack(Items.BOW));
        double height=f.id.equals("multi_axis_reflection")?2.5:f.id.equals("trajectory_return")?p.getEyeHeight():1.15;
        Vec3 at=p.position().add(f.id.equals("circulation_listening")?2:0,height,f.id.equals("circulation_listening")?14:7);
        // Finite physical setup only. Gravity/drag/ticking/collision and the skill itself perform all subsequent work.
        if(f.id.equals("multi_axis_reflection")){
            var center=p.getBoundingBox().getCenter();var facing=p.getLookAngle();
            var pose=cn.academy.port.highpower.core.MultiAxisReflectionGeometry.pose(new cn.academy.port.highpower.core.MultiAxisReflectionGeometry.Vector(center.x,center.y,center.z),new cn.academy.port.highpower.core.MultiAxisReflectionGeometry.Vector(facing.x,facing.y,facing.z));
            var normal=cn.academy.port.highpower.core.MultiAxisReflectionGeometry.facetNormal(pose,1);
            Vec3 axis=new Vec3(normal.x(),0,normal.z());
            // Aim through a real face. The old zero-degree line is a deliberate gap between +/-20-degree faces.
            f.enemy.setPos(p.position().add(axis.scale(7)));a.setPos(f.enemy.getEyePosition());a.setDeltaMovement(axis.scale(-.8).add(0,.08,0));
        }else{a.setPos(at);a.setDeltaMovement(0,0,-.8);}f.arrow=a;f.originalVelocity=f.lastUnsplit=a.getDeltaMovement();f.originalDamage=f.lastUnsplitDamage=a.getBaseDamage();
        require(p.serverLevel().addFreshEntity(a),"actual ordinary enemy-owned arrow joins native level");
        LogUtils.getLogger().info("QA_GAMEPLAY_VM_ARROW skill={} arrow={} enemy={} velocity={} baseDamage={} ordinaryGravity=true",f.id,a.getUUID(),f.enemy.getUUID(),f.originalVelocity,f.originalDamage);
    }
    private static void melee(ServerPlayer p){
        var f=fixture;require(f.begun,"native melee needs baseline");f.enemy.setPos(p.position().add(f.id.equals("circulation_relief")?1.2:0,0,f.id.equals("circulation_relief")?0:1.2));
        BigDecimal s=HighPowerCombat.shield(p),h=hp(p);float attacker=f.enemy.getHealth();int quota=remaining(p,f.id);
        f.melee=true;try{f.enemy.doHurtTarget(p);}finally{f.melee=false;}
        require(hp(p).compareTo(h)==0&&HighPowerCombat.shield(p).compareTo(s)<0,"actual native melee residual spends shield before HP");
        if(f.id.equals("vec_deviation")||f.id.equals("vec_reflection")){
            require(f.incoming>0&&f.remaining>=0&&f.remaining<f.incoming,"real classic incoming hook reduced the actual native hit before shield settlement");
            if(f.id.equals("vec_reflection"))require(f.enemy.getHealth()<attacker,"actual classic reflection damaged the native melee attacker");
        }else if(f.id.equals("inertial_anchor")){
            // Anchor's quota belongs to admitted native knockback/fall, not ordinary base-shield absorption.
            // A positive remaining shield cancels knockback first under the shared superarmor contract.
            require(quota>0&&remaining(p,f.id)==quota,"full shield cancels native impulse without spending anchor's knockback quota");
            require(p.getDeltaMovement().horizontalDistanceSqr()<1e-10,"full shield preserves superarmor during the actual native melee hit");
            var modifier=p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getModifier(InertialAnchor.MOVEMENT_MODIFIER);
            require(modifier!=null&&modifier.amount()==-.2,"paid anchor actually installs its native twenty-percent movement modifier");
        }else require(quota>0&&remaining(p,f.id)==quota-1,"actual selected defense committed one absorption quota; base shield alone cannot pass");
        LogUtils.getLogger().info("QA_GAMEPLAY_VM_MELEE_PASS skill={} incoming={} postLegacy={} shieldBefore={} shieldAfter={} hpUnchanged=true attackerBefore={} attackerAfter={} quotaBefore={} quotaAfter={} nativeDoHurtTarget=true",f.id,f.incoming,f.remaining,s,HighPowerCombat.shield(p),attacker,f.enemy.getHealth(),quota,remaining(p,f.id));
    }
    private static int remaining(ServerPlayer p,String id){return switch(id){case "vector_parry"->VectorParry.remainingHits(p);case "inertial_anchor"->InertialAnchor.remainingHits(p);case "circulation_relief"->CirculationRelief.remainingHits(p);case "autonomous_vector_circle"->AutonomousVectorCircle.remainingHits(p);default->-1;};}
    private static void tossed(ServerPlayer p){
        var f=fixture;var items=p.serverLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(6),e->e.getItem().is(Items.IRON_INGOT)&&MagneticItemProvenance.ownedBy(e,p.getUUID()));
        require(items.size()==1,"one real owner-proven Q item, not fixture-spawned substitute");var item=items.getFirst();
        require(item.getDeltaMovement().lengthSqr()>1e-4,"real tossed item is initially moving");f.item=item.getUUID();f.itemSpeed=item.getDeltaMovement().length();
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void beforeItem(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre e){
        var f=fixture;if(f!=null&&f.begun&&!f.passed&&f.item!=null&&e.getEntity().getUUID().equals(f.item)
                &&!e.getEntity().level().isClientSide&&e.getEntity().getDeltaMovement().equals(Vec3.ZERO))f.braked=true;
    }
    /** Keep the real live-stack reference before native impact callbacks; endTick may retire its flight. */
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void multiImpact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent event){
        var f=fixture;if(f==null||!f.begun||f.passed||!f.id.equals("multi_axis_reflection")||event.getEntity()!=f.arrow
                ||event.getEntity().level().isClientSide)return;
        try{
            var ticks=MultiAxisReflectionArrowBridge.class.getDeclaredField("TICKS");ticks.setAccessible(true);
            Object tick=((Map<?,?>)ticks.get(null)).get(f.arrow);if(tick==null)return;
            Object receipt=field(tick,"receipt");if(receipt==null)return;
            f.multiCapture=field(receipt,"capture");f.multiSnapshot=field(tick,"snapshot");f.multiDefender=field(receipt,"defender");
        }catch(ReflectiveOperationException ex){throw new IllegalStateException("read-only native multi-axis reference observation failed",ex);}
    }
    /** Read only after this real arrow's complete native tick; capture creation alone is not a payment. */
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void afterArrow(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event){
        var f=fixture;
        if(f!=null&&f.begun&&!f.passed&&!f.multiCommitted&&f.id.equals("multi_axis_reflection")
                &&event.getEntity()==f.arrow&&!event.getEntity().level().isClientSide){
            var p=event.getEntity().getServer().getPlayerList().getPlayer(f.owner);
            if(p!=null)observeMultiAxis(p,f);
        }
    }
    private static void observeMultiAxis(ServerPlayer p,Fixture f){
        try{
            var flights=MultiAxisReflectionArrowBridge.class.getDeclaredField("FLIGHTS");flights.setAccessible(true);
            Object flight=((Map<?,?>)flights.get(null)).get(f.arrow);
            f.multiEvidence="dedicatedFlight="+(flight!=null)+",quota="+p.getPersistentData().get(MultiAxisReflection.QUOTA_KEY)
                    +",spent="+f.arrow.getPersistentData().getBoolean(MultiAxisReflectionArrowBridge.SPENT)+",owner="+f.arrow.getOwner();
            Object capture=flight==null?f.multiCapture:field(flight,"capture"),snapshot=flight==null?f.multiSnapshot:field(flight,"identity");
            Object defender=flight==null?f.multiDefender:field(flight,"defender");
            if(capture==null||snapshot==null)return; // A crossing alone may legitimately need a later native tick.
            boolean committed=(boolean)field(capture,"committed"),reflected=(boolean)field(capture,"reflected");
            require(defender==p&&field(capture,"arrow")==f.arrow
                    &&field(capture,"arrowUuid").equals(f.arrow.getUUID())
                    &&((Integer)field(capture,"arrowId"))==f.arrow.getId(),"dedicated reflection belongs to this actual defender and same native arrow");
            if(!committed||!reflected)return; // Preserve only fully committed evidence, never an in-progress capture.
            int facet=(int)field(capture,"facet");require(facet>=0&&facet<4,"one actual dedicated facet was selected");
            Object before=field(capture,"ledger");var resources=MultiAxisReflection.class.getDeclaredMethod("resources",ServerPlayer.class);resources.setAccessible(true);
            Object after=resources.invoke(null,p);require(after!=null,"same actual post-tick shield resources remain valid");
            var baseBefore=(cn.academy.port.highpower.core.ExactCombatState)field(before,"base");
            var baseAfter=(cn.academy.port.highpower.core.ExactCombatState)field(after,"base");
            var sharesBefore=(cn.academy.port.highpower.core.SharedShieldReserveShares)field(before,"shares");
            var sharesAfter=(cn.academy.port.highpower.core.SharedShieldReserveShares)field(after,"shares");
            Vec3 velocity=(Vec3)field(snapshot,"velocity");double baseDamage=(double)field(snapshot,"baseDamage");
            BigDecimal cost=cn.academy.port.highpower.core.MultiAxisReflectionRules.nativeArrowDamage(velocity.length(),baseDamage,field(snapshot,"weapon")!=null);
            BigDecimal spent=baseBefore.shield().add(sharesBefore.lower()).subtract(baseAfter.shield().add(sharesAfter.lower()));
            var quota=p.getPersistentData().getCompound(MultiAxisReflection.QUOTA_KEY);
            require(cost.signum()>0&&spent.compareTo(cost)==0&&baseAfter.health().compareTo(baseBefore.health())==0
                    &&sharesAfter.upper().compareTo(sharesBefore.upper())==0,"dedicated committed reflection debits exactly the real native integer amount from base/lower, never HP or upper");
            require(MultiAxisReflection.remainingHits(p)==f.multiQuotaBefore-1&&(quota.getInt("facets")&(1<<facet))!=0,
                    "one dedicated quota and the actual crossed facet slot were committed");
            require(f.arrow.getOwner()==f.enemy&&f.arrow.getPersistentData().getBoolean(MultiAxisReflectionArrowBridge.SPENT),
                    "dedicated native reversal retains its real enemy shooter and own spent marker; base shield ownership replacement cannot pass");
            f.multiCommitted=true;
            f.multiEvidence="committed=true,reflected=true,facet="+facet+",quota=3,nativeCost="+cost+",actualSPlusLowerSpent="+spent;
            LogUtils.getLogger().info("QA_GAMEPLAY_MULTI_AXIS_DEDICATED_PASS arrow={} defender={} facet={} quota=3 cost={} baseBefore={} baseAfter={} lowerBefore={} lowerAfter={} upperUnchanged={} sameNativeShooter=true actualCommittedCapture=true readOnly=true",f.arrow.getUUID(),p.getUUID(),facet,cost,baseBefore.shield(),baseAfter.shield(),sharesBefore.lower(),sharesAfter.lower(),sharesAfter.upper());
        }catch(ReflectiveOperationException ex){throw new IllegalStateException("read-only dedicated reflection observation failed",ex);}
    }
    private static Object field(Object value,String name)throws ReflectiveOperationException{var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void beforePlayer(net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre e){
        var f=fixture;if(f!=null&&f.begun&&!f.passed&&e.getEntity() instanceof ServerPlayer p&&p.getUUID().equals(f.owner)){
            f.minCp=Math.min(f.minCp,AbilityStorage.get(p).cp);f.minBudget=Math.min(f.minBudget,HighPowerResources.availableCp(p));
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void incoming(LivingIncomingDamageEvent e){var f=fixture;if(f!=null&&f.melee&&e.getEntity().getUUID().equals(f.owner))f.incoming=e.getAmount();}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void reduced(LivingIncomingDamageEvent e){var f=fixture;if(f!=null&&f.melee&&e.getEntity().getUUID().equals(f.owner))f.remaining=e.getAmount();}
    // HIGHEST captures the native arrow immediately before NewSkillRuntime's NORMAL split; LOWEST observes the actual result.
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void before(ServerTickEvent.Post e){sample(e,false);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void after(ServerTickEvent.Post e){sample(e,true);}
    private static void sample(ServerTickEvent.Post e,boolean after){
        var f=fixture;if(f==null||!f.begun||f.passed)return;var p=e.getServer().getPlayerList().getPlayer(f.owner);if(p==null)return;
        f.minCp=Math.min(f.minCp,AbilityStorage.get(p).cp);f.minBudget=Math.min(f.minBudget,HighPowerResources.availableCp(p));
        var a=f.arrow;
        if(a!=null&&!a.isRemoved()){
            if(!a.getPersistentData().getBoolean(NewSkillRuntime.BYPASSED)){f.lastUnsplit=a.getDeltaMovement();f.lastUnsplitDamage=a.getBaseDamage();}
            if(Set.of("vec_reflection","multi_axis_reflection","trajectory_return").contains(f.id)&&a.getDeltaMovement().z>.05)f.arrowChanged=true;
            if(f.id.equals("vec_deviation")&&a.getBaseDamage()==0&&a.getDeltaMovement().length()<.051)f.arrowChanged=true;
            if(f.id.equals("impulse_split")&&after&&!f.split&&a.getPersistentData().getBoolean(NewSkillRuntime.BYPASSED)){
                var pair=p.serverLevel().getEntitiesOfClass(Arrow.class,p.getBoundingBox().inflate(20),x->x.getPersistentData().getBoolean(NewSkillRuntime.BYPASSED)&&x.getOwner()==f.enemy);
                if(pair.size()==2){
                    Vec3 v0=pair.get(0).getDeltaMovement(),v1=pair.get(1).getDeltaMovement();
                    int before=cn.academy.port.highpower.core.ImpulseSplitRules.damageBudget(f.lastUnsplit.length(),f.lastUnsplitDamage,a.getWeaponItem()!=null).total();
                    var share0=ImpulseSplitArrowBudget.snapshot(pair.get(0));var share1=ImpulseSplitArrowBudget.snapshot(pair.get(1));
                    require(share0!=null&&share1!=null,"two real server-object permits, never NBT budgets");
                    int combined=share0.budget()+share1.budget();
                    require(combined==before,"two actual arrows partition the one native integer damage budget without ceil inflation");
                    require(v0.add(v1).length()<=f.lastUnsplit.length()&&v0.lengthSqr()+v1.lengthSqr()<=f.lastUnsplit.lengthSqr(),"actual emitted velocity sum and energy do not amplify the incoming arrow");
                    require(Math.abs(v0.normalize().dot(v1.normalize()))<1e-6&&v0.z>0&&v1.z>0&&v0.x*v1.x<0,"actual two arrows follow the two distinct chosen ±45-degree directions");
                    f.split=true;f.splitBefore=before;f.splitAfter=combined;
                }
            }
        }
        if(f.item!=null&&p.serverLevel().getEntity(f.item) instanceof ItemEntity item&&item.getDeltaMovement().equals(Vec3.ZERO))f.braked=true;
        if(f.id.equals("circulation_listening")){var v=CirculationListening.lastVisual(p);if(v!=null&&v.cues().stream().anyMatch(c->c.kind()==0&&c.strength()>0))f.serverCue=true;}
    }
    @SubscribeEvent public static void client(ClientTickEvent.Post e){
        var f=fixture;if(f==null||!f.begun||f.passed||!f.id.equals("circulation_listening"))return;
        try{var field=Class.forName("cn.academy.port.client.CirculationListeningEffects").getDeclaredField("STATE");field.setAccessible(true);
            var state=(CirculationListeningState)field.get(null);
            if(state.controllers().stream().anyMatch(c->c.id().owner().equals(f.owner)&&c.frame().cues().stream().anyMatch(cue->cue.kind()==0&&cue.strength()>0)))f.clientCue=true;
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    private static void verify(ServerPlayer p){
        var f=fixture;require(f.begun&&!f.passed,"mechanism witness requires baseline and real input");
        switch(f.id){
            case "vec_deviation","vec_reflection","trajectory_return"->require(f.arrowChanged,"actual native arrow was stopped/reversed; active pose/signature is insufficient");
            case "multi_axis_reflection"->require(f.arrowChanged&&f.multiCommitted,"actual native arrow reversal plus dedicated facet/quota/resource commit required; "+f.multiEvidence);
            case "vector_parry","inertial_anchor","circulation_relief","autonomous_vector_circle"->require(f.incoming>0,"real melee absorption witness required");
            case "braking_touch"->require(f.braked&&f.itemSpeed>0,"same actually Q-tossed moving item reached exact zero velocity");
            case "circulation_listening"->require(f.serverCue&&f.clientCue,"actual moving native arrow generated server disturbance cue and accepted owner-only client cue");
            case "impulse_split"->require(f.inputChoices==2&&f.split,"two real direction choices split one actual enemy arrow into two conserved shares");
            default->throw new IllegalStateException(f.id);
        }
        require(f.minCp<f.cp-1e-6||f.minBudget<f.budget-1e-6||p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_"+f.id)>f.paidClock,"actual finite ability payment observed; native recovery cannot hide a real closed-spend clock");
        f.passed=true;
        LogUtils.getLogger().info("QA_GAMEPLAY_PASS skill={} mechanism=actual_native_vm_utility arrowChanged={} itemBraked={} serverCue={} ownerClientCue={} split={} splitNativeIntegerBudgetBefore={} splitNativeIntegerBudgetAfter={} nativeCpDip={} finiteBudgetDip={} source=normal_GLFW_not_controller_invocation",f.id,f.arrowChanged,f.braked,f.serverCue,f.clientCue,f.split,f.splitBefore,f.splitAfter,f.cp-f.minCp,f.budget-f.minBudget);
    }
    private static void aim(Minecraft mc,Vec3 target){var d=target.subtract(mc.player.getEyePosition());mc.player.setYRot((float)-Math.toDegrees(Math.atan2(d.x,d.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));}
    private static BigDecimal hp(ServerPlayer p){return new BigDecimal(HighPowerCombat.healthText(p));}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("QA_GAMEPLAY_VM_UTILITY_FAILED "+message);}
}

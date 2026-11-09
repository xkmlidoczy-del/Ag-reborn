package cn.academy.port.qa;

import cn.academy.port.AbilityStorage;
import cn.academy.port.highpower.*;
import cn.academy.port.skill.LightShieldSession;
import com.mojang.logging.LogUtils;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Real incoming native arrows/commands. A base category shield alone cannot satisfy a specialist's assertion. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class GameplayAcceptanceEmMdDefense {
    private static final Logger LOG=LogUtils.getLogger();
    private static final Set<String> IDS=Set.of("magnetic_bracer","charge_decoy","magnetic_domain","grounding_return","potential_buffer","light_shield","thermal_shell","scatter_plates","directional_dissipation_curtain","zero_gap_armor");
    private static final class Fixture {
        final UUID owner;final String id;Husk enemy;Arrow arrow;Vec3 enemyAt,initialArrow,traceAt;long traceTick=-1;
        BigDecimal hp,shield,enemyPool,domainBefore,domainMinimum,domainArrowCost,domainArrowBefore,domainArrowMinimum;double cp,budget,minCp,minBudget;long clock,tick;String mastery,lastDecoy="";
        boolean begun,passed,specialSeen,thermalDamageSeen,slowFallSeen;int pulses,absorbed=-1;
        Fixture(UUID owner,String id){this.owner=owner;this.id=id;}
    }
    private static Fixture fixture;
    private GameplayAcceptanceEmMdDefense(){}
    public static void run(Minecraft mc,String action){
        var server=mc.getSingleplayerServer();require(server!=null&&mc.player!=null,"integrated player");UUID owner=mc.player.getUUID();
        if(action.startsWith("setup:")){
            String id=action.substring(6);require(IDS.contains(id),"implemented defense fixture "+id);LiveInputQa.run(mc,"setup:"+id);
            server.submit(()->{prepare(server.getPlayerList().getPlayer(owner),id);return true;}).join();LiveInputQa.assertHealthy();
        }else server.submit(()->{
            var p=server.getPlayerList().getPlayer(owner);require(fixture!=null&&fixture.owner.equals(owner),"same defense owner");
            if(action.equals("baseline"))baseline(p);
            else if(action.equals("decoy-placed")){
                Object c=contexts("cn.academy.port.highpower.ElectromasterChargeDecoy","CONTEXTS").get(p.getUUID());
                var hit=p.serverLevel().clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(),p.getEyePosition().add(p.getLookAngle().scale(20)),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
                LOG.info("QA_GAMEPLAY_DECOY_PLACEMENT context={} placed={} ground={} actualBlockHit={} actualHitSide={} actualLook={} actualAt={} budgetClock={}",c!=null,c==null?null:field(c,"placed"),c==null?null:field(c,"ground"),hit.getBlockPos(),hit.getDirection(),p.getLookAngle(),p.position(),clock(p,fixture.id));
                require(c!=null&&(long)field(c,"placed")>=0,"normal release placed an actual paid decoy before incoming arrow");
            }else if(action.equals("arrow"))arrow(p);
            else if(action.equals("assert"))verify(p);
            else if(action.equals("verify"))require(fixture.passed,"real specialist effect passed");
            else throw new IllegalArgumentException(action);return true;
        }).join();
    }
    private static void prepare(ServerPlayer p,String id){
        if(fixture!=null){if(fixture.enemy!=null)fixture.enemy.discard();if(fixture.arrow!=null)fixture.arrow.discard();}
        fixture=new Fixture(p.getUUID(),id);var f=fixture;var world=p.serverLevel();var feet=p.blockPosition();
        for(int z=-2;z<=14;z++)for(int x=-4;x<=4;x++){
            world.setBlock(feet.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
            for(int y=0;y<5;y++)world.setBlock(feet.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        world.setWeatherParameters(6000,0,false,false);
        f.enemy=EntityType.HUSK.create(world);require(f.enemy!=null,"real source mob");
        boolean physicalPulse=id.equals("potential_buffer");f.enemy.setNoAi(!physicalPulse);f.enemy.setNoGravity(!physicalPulse);f.enemy.setSilent(true);
        if(physicalPulse)pauseGoalControls(f.enemy);
        f.enemy.addTag("gameem_attacker");f.enemy.setPos(p.getX()+1,p.getY(),p.getZ()+2);
        // The incoming domain arrow starts at +8m and travels toward the owner.
        // Its genuine shooter must be behind that flight, not between arrow and
        // defender: the domain deliberately never steers a shot back at its shooter.
        if(id.equals("magnetic_domain"))f.enemy.setPos(p.getX()+1,p.getY(),p.getZ()+12);
        require(world.addFreshEntity(f.enemy)&&HighPowerCombat.enroll(f.enemy,5),"durable real source mob enrolled");
        if(id.equals("zero_gap_armor")){
            require(ScatterPlateStorage.ensure(p).isPresent()&&ZeroGapArmorStorage.ensureLearned(p),"normal shield-learning postconditions partition the prepared finite shield");
            LOG.info("QA_GAMEPLAY_DEFENSE_LEARNING_POSTCONDITION skill=zero_gap_armor existingParentBalancePreserved=true nativeLearningSeam=HighPowerProgression.complete");
        }
        LOG.info("QA_GAMEPLAY_DEFENSE_PREPARED skill={} realAttacker={} ordinaryIncomingRequired=true",id,f.enemy.getUUID());
    }
    /** NoAI suppresses native travel itself. Keep real physics and suppress only
     * this newly created fixture's goal inputs, so a real pulse must move it. */
    private static void pauseGoalControls(Husk enemy){
        try{
            for(String name:java.util.List.of("goalSelector","targetSelector")){
                var field=net.minecraft.world.entity.Mob.class.getDeclaredField(name);field.setAccessible(true);
                var selector=(net.minecraft.world.entity.ai.goal.GoalSelector)field.get(enemy);
                // Mob.tick re-enables MOVE/JUMP/LOOK every five ticks. Removing only this
                // fresh fixture's goals keeps native gravity/travel/knockback without autonomous walking.
                selector.removeAllGoals(goal -> true);
            }
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    private static void baseline(ServerPlayer p){
        var f=fixture;require(!f.begun,"one defense baseline");f.hp=new BigDecimal(HighPowerCombat.healthText(p));f.shield=HighPowerCombat.shield(p);
        f.enemyPool=pool(f.enemy);f.enemyAt=f.enemy.position();f.cp=f.minCp=AbilityStorage.get(p).cp;f.budget=f.minBudget=HighPowerResources.availableCp(p);
        f.clock=clock(p,f.id);f.tick=p.serverLevel().getGameTime();f.mastery=mastery(p,f.id);f.begun=true;
        if(f.id.equals("magnetic_domain")){
            f.domainBefore=f.domainMinimum=MagneticDomainReserve.reserve(p);f.domainArrowCost=MagneticDomainReserve.capacity(p).divide(BigDecimal.valueOf(100));
            require(f.domainArrowCost.signum()>0&&f.domainBefore.compareTo(f.domainArrowCost)>=0,"off-state natural recovery prepared real finite reserve for one arrow before activation");
            LOG.info("QA_GAMEPLAY_DOMAIN_REAL_RESERVE_BASELINE tick={} reserve={} oneArrowCost={} lastShieldSpend={}",f.tick,f.domainBefore,f.domainArrowCost,p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getLong("lastShieldSpend"));
        }
        LOG.info("QA_GAMEPLAY_DEFENSE_BASELINE skill={} hp={} shield={} cp={} budget={} specialistMastery={} ownerPosition={} ownerFacing={} enemyPosition={} enemyVelocity={}",f.id,f.hp,f.shield,f.cp,f.budget,f.mastery,p.position(),p.getLookAngle(),f.enemy.position(),f.enemy.getDeltaMovement());
    }
    /** Creates an actual vanilla projectile as an environmental threat, never calls hurt/attack/impact. */
    private static void arrow(ServerPlayer p){
        var f=fixture;require(f.begun&&f.arrow==null,"one real airborne threat after baseline");
        Vec3 at=p.position().add(0,1,8),velocity=new Vec3(0,0,-.65);
        if(f.id.equals("magnetic_bracer")){at=p.position().add(0,1.2,3.5);velocity=new Vec3(0,0,-.25);}
        if(f.id.equals("charge_decoy")){at=p.position().add(0,1.1,5);velocity=new Vec3(0,.02,-.18);}
        boolean mdSpecial=Set.of("scatter_plates","directional_dissipation_curtain","zero_gap_armor").contains(f.id);
        if(mdSpecial){at=p.position().add(0,1,6);velocity=new Vec3(0,.18,-.65);}
        if(f.id.equals("directional_dissipation_curtain")){at=p.position().add(0,1.35,8);velocity=new Vec3(0,.25,-.65);}
        if(f.id.equals("zero_gap_armor")){
            var saved=ZeroGapArmorStorage.capture(p).orElseThrow();var timing=saved.timing().orElseThrow();
            var pose=cn.academy.port.highpower.core.ZeroGapArmorGeometry.capture(new cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector(p.getX(),p.getY(),p.getZ()),p.getYRot(),timing);
            var front=pose.plates().stream().filter(cn.academy.port.highpower.core.ZeroGapArmorGeometry.Plate::active).max(java.util.Comparator.comparingDouble(plate->plate.normal().x()*p.getLookAngle().x+plate.normal().z()*p.getLookAngle().z)).orElseThrow();
            var normal=new Vec3(front.normal().x(),front.normal().y(),front.normal().z());
            at=p.position().add(0,1.1,0).add(normal.scale(3));velocity=normal.scale(-3);
            LOG.info("QA_GAMEPLAY_DEFENSE_ARROW_AIM skill=zero_gap_armor actualPhase={} actualActivePlate={} normal={} realOneTickFlight=true",timing.phaseTick(),front.id(),normal);
        }
        f.arrow=new Arrow(p.serverLevel(),f.enemy,new ItemStack(Items.ARROW),new ItemStack(Items.BOW));
        f.arrow.setPos(at);f.arrow.setDeltaMovement(velocity);f.arrow.setNoGravity(false);f.initialArrow=velocity;f.traceAt=at;
        if(f.id.equals("magnetic_domain")){
            f.domainArrowBefore=f.domainArrowMinimum=MagneticDomainReserve.total(p);
            LOG.info("QA_GAMEPLAY_DOMAIN_REAL_ARROW_WALLET before={} base={} reserve={} onePercentCost={}",f.domainArrowBefore,HighPowerCombat.shield(p),MagneticDomainReserve.reserve(p),f.domainArrowCost);
        }
        require(p.serverLevel().addFreshEntity(f.arrow),"real native arrow flight added");
        LOG.info("QA_GAMEPLAY_REAL_ARROW skill={} id={} from={} velocity={} owner={}",f.id,f.arrow.getUUID(),at,velocity,f.enemy.getUUID());
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)public static void beforeImpact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent e){
        var f=fixture;if(f==null||!f.begun||f.arrow!=e.getProjectile())return;
        LOG.info("QA_GAMEPLAY_DEFENSE_NATIVE_IMPACT_BEFORE skill={} gravity={} hit={} scatterPending={} curtainPending={} zeroPending={} arrowAt={} velocity={}",f.id,!f.arrow.isNoGravity(),e.getRayTraceResult(),pending("ScatterPlateArrowReceipts"),pending("DissipationCurtainArrowReceipts"),pending("ZeroGapArmorArrowReceipts"),f.arrow.position(),f.arrow.getDeltaMovement());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public static void afterImpact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent e){
        var f=fixture;if(f==null||!f.begun||f.arrow!=e.getProjectile())return;var p=f.arrow.level().getServer().getPlayerList().getPlayer(f.owner);
        LOG.info("QA_GAMEPLAY_DEFENSE_NATIVE_IMPACT_AFTER skill={} canceled={} removed={} hp={} shield={} mastery={} facetState={}",f.id,e.isCanceled(),f.arrow.isRemoved(),HighPowerCombat.healthText(p),HighPowerCombat.shield(p),mastery(p,f.id),facets(p));
    }
    /** A base plasma-shell melee counter can also damage this attacker. Accept
     * only the thermal skill's real closed pulse source, never that counter. */
    @SubscribeEvent(priority=EventPriority.LOWEST)public static void thermalDamage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post e){
        var f=fixture;if(f==null||!f.begun||f.passed||!f.id.equals("thermal_shell")||e.getEntity()!=f.enemy||e.getNewDamage()<=0)return;
        var source=e.getSource();
        if(source.getEntity() instanceof ServerPlayer p&&p.getUUID().equals(f.owner)&&HighPowerCombat.isExactSkillSource(source)
                &&"meltdowner.thermal_shell".equals(field(source,"skill"))&&(boolean)field(source,"thermalShellPulse")){
            f.thermalDamageSeen=true;sample(p);
            LOG.info("QA_GAMEPLAY_THERMAL_NATIVE_DAMAGE skill=thermal_shell owner={} target={} nativePost={} exactSource={} thermalPulse=true",f.owner,f.enemy.getUUID(),e.getNewDamage(),source.getClass().getName());
        }
    }
    private static boolean pending(String type){Object tick=contexts("cn.academy.port.highpower."+type,"TICKS").get(fixture.arrow);if(tick==null)return false;Object pair=field(tick,"pending");return pair!=null&&field(pair,"capture")!=null;}
    private static String facets(ServerPlayer p){return "scatter="+ScatterPlateStorage.read(p)+" armor="+ZeroGapArmorStorage.read(p)+" armorTiming="+p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).get(ZeroGapArmorStorage.TIMING_KEY)+" armorPayment="+p.getPersistentData().get(ZeroGapArmor.PAYMENT_KEY);}
    @SubscribeEvent(priority=EventPriority.HIGHEST)public static void early(PlayerTickEvent.Post e){if(e.getEntity() instanceof ServerPlayer p)sample(p);}
    @SubscribeEvent public static void post(ServerTickEvent.Post e){var f=fixture;if(f!=null){var p=e.getServer().getPlayerList().getPlayer(f.owner);if(p!=null){sample(p);traceCurtain(p);}}}
    private static void traceCurtain(ServerPlayer p){
        var f=fixture;if(f==null||!f.id.equals("directional_dissipation_curtain")||f.arrow==null||f.arrow.isRemoved()||f.traceTick==p.serverLevel().getGameTime())return;
        f.traceTick=p.serverLevel().getGameTime();var at=f.arrow.position();
        var c=contexts("cn.academy.port.highpower.DissipationCurtain","CONTEXTS").get(p.getUUID());
        var ledger=contexts("cn.academy.port.highpower.DissipationCurtainArrowReceipts","LEDGERS").get(f.arrow);
        var captures=new java.util.ArrayList<Boolean>();if(ledger!=null)for(var pair:(java.util.List<?>)field(ledger,"pairs"))captures.add(field(pair,"capture")!=null);
        var geometry=cn.academy.port.highpower.core.DissipationCurtainGeometry.pose(curtainVector(p.getEyePosition()),curtainVector(p.getLookAngle()));
        var crossing=cn.academy.port.highpower.core.DissipationCurtainGeometry.crossing(geometry,curtainVector(f.traceAt),curtainVector(at),7);
        LOG.info("QA_GAMEPLAY_CURTAIN_FLIGHT tick={} age={} at={} previous={} look={} pureCrossing={} actualCaptures={} currentActive={} paidUntil={} started={} ledgerRetired={}",f.traceTick,f.arrow.tickCount,at,f.traceAt,p.getLookAngle(),crossing,captures,DissipationCurtain.active(p),c==null?null:field(c,"paidUntil"),c==null?null:field(c,"started"),ledger==null?null:field(ledger,"retired"));f.traceAt=at;
    }
    private static cn.academy.port.highpower.core.DissipationCurtainGeometry.Vector curtainVector(Vec3 v){return new cn.academy.port.highpower.core.DissipationCurtainGeometry.Vector(v.x,v.y,v.z);}
    private static void sample(ServerPlayer p){
        var f=fixture;if(f==null||!f.begun||f.passed||!p.getUUID().equals(f.owner))return;
        f.minCp=Math.min(f.minCp,AbilityStorage.get(p).cp);f.minBudget=Math.min(f.minBudget,HighPowerResources.availableCp(p));
        if(f.id.equals("magnetic_domain")&&f.domainMinimum!=null){
            f.domainMinimum=f.domainMinimum.min(MagneticDomainReserve.reserve(p));
            if(f.domainArrowMinimum!=null)f.domainArrowMinimum=f.domainArrowMinimum.min(MagneticDomainReserve.total(p));
            Object c=contexts("cn.academy.port.highpower.MagneticDomain","CONTEXTS").get(p.getUUID());
            if(!f.slowFallSeen&&c!=null&&MagneticDomainSlowFall.active(p,c)&&p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)){
                f.slowFallSeen=true;LOG.info("QA_GAMEPLAY_DOMAIN_REAL_DESCENDING_SLOWFALL tick={} at={} nativeVelocity={} ground={} ownedNativeEffect={}",p.serverLevel().getGameTime(),p.position(),p.getDeltaMovement(),p.onGround(),p.getEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING));
            }
        }
        if(f.id.equals("charge_decoy")){
            Object c=contexts("cn.academy.port.highpower.ElectromasterChargeDecoy","CONTEXTS").get(p.getUUID());
            String state=c==null?"absent":"placed="+field(c,"placed")+" ground="+field(c,"ground");
            if(!state.equals(f.lastDecoy)){LOG.info("QA_GAMEPLAY_DECOY_STATE tick={} state={} budget={} paymentClock={} look={} position={}",p.serverLevel().getGameTime(),state,HighPowerResources.availableCp(p),clock(p,f.id),p.getLookAngle(),p.position());f.lastDecoy=state;}
        }

        if(f.arrow!=null){
            boolean changed=f.arrow.getDeltaMovement().distanceTo(f.initialArrow)>.03;
            if(f.id.equals("magnetic_bracer")&&ElectromasterPrototype.leased(f.arrow)&&f.arrow.getDeltaMovement().z>0)f.specialSeen=true;
            if(f.id.equals("charge_decoy")&&ElectromasterChargeDecoy.leased(f.arrow)&&changed&&ElectromasterChargeDecoy.affectedCount(p)>0)f.specialSeen=true;
            if(f.id.equals("magnetic_domain")&&MagneticDomainArrows.leased(f.arrow)&&changed&&MagneticDomain.refractedCount(p)>0){
                if(!f.specialSeen)LOG.info("QA_GAMEPLAY_DOMAIN_NATIVE_ARROW_WITNESS tick={} arrow={} actualVelocity={} lease={} reserveBefore={} reserveNow={} reserveMin={}",p.serverLevel().getGameTime(),f.arrow.getUUID(),f.arrow.getDeltaMovement(),f.arrow.getPersistentData().get(MagneticDomainArrows.LEASE_KEY),f.domainBefore,MagneticDomainReserve.reserve(p),f.domainMinimum);
                f.specialSeen=true;
            }
        }
        if(f.id.equals("potential_buffer"))f.pulses=Math.max(f.pulses,PotentialBuffer.pulseCount(p));
        if(f.id.equals("thermal_shell"))f.pulses=Math.max(f.pulses,ThermalShell.pulseCount(p));
        if(f.id.equals("light_shield")){
            Object hold=contexts("cn.academy.port.skill.LightShield","HOLDS").get(p.getUUID());
            if(hold!=null)f.absorbed=Math.max(f.absorbed,((LightShieldSession)field(hold,"session")).lastAbsorb());
        }
        if(Set.of("scatter_plates","directional_dissipation_curtain","zero_gap_armor").contains(f.id)&&!mastery(p,f.id).equals(f.mastery))f.specialSeen=true;
    }
    private static void verify(ServerPlayer p){
        var f=fixture;require(f.begun&&!f.passed,"one actual defense opportunity");sample(p);
        BigDecimal hp=new BigDecimal(HighPowerCombat.healthText(p)),shield=HighPowerCombat.shield(p);
        LOG.info("QA_GAMEPLAY_DEFENSE_DIAGNOSTIC skill={} hpBefore={} hp={} shieldBefore={} shield={} specialSeen={} masteryBefore={} mastery={} activeScatter={} activeCurtain={} activeArmor={} clockBefore={} clockAfter={} minCp={} minBudget={} arrowRemoved={} arrowGravity={} facets={}",f.id,f.hp,hp,f.shield,shield,f.specialSeen,f.mastery,mastery(p,f.id),ScatterPlates.active(p),DissipationCurtain.active(p),ZeroGapArmor.active(p),f.clock,clock(p,f.id),f.minCp,f.minBudget,f.arrow!=null&&f.arrow.isRemoved(),f.arrow!=null&&!f.arrow.isNoGravity(),facets(p));
        LOG.info("QA_GAMEPLAY_DEFENSE_NATIVE_GEOMETRY skill={} ownerPosition={} ownerFacing={} enemyBaseline={} enemyPosition={} enemyVelocity={} actualPulses={}",f.id,p.position(),p.getLookAngle(),f.enemyAt,f.enemy.position(),f.enemy.getDeltaMovement(),f.pulses);
        require(hp.compareTo(f.hp)==0,"specialist defense preserves actual HP in the prepared supported case");
        boolean paid=f.minCp<f.cp||f.minBudget<f.budget||clock(p,f.id)>f.clock&&clock(p,f.id)>f.tick;
        if(f.id.equals("grounding_return")){
            Object c=contexts("cn.academy.port.highpower.GroundingReturn","CONTEXTS").get(p.getUUID());
            require(c!=null&&((BigDecimal)field(c,"spent")).signum()>0,"actual supported electric damage committed grounding absorption");
            require((boolean)field(c,"precisionUsed"),"actual short precise window refunded first absorption");
            require(shield.compareTo(f.shield)==0,"precise supported hit keeps real shield after its bounded refund");
        }else{
            require(paid,"real finite specialist payment, not merely active flag");
            if(f.id.equals("potential_buffer"))require(f.pulses>0&&f.enemy.position().distanceTo(f.enemyAt)>.05,"actual non-damaging buffer pulse moved real hostile mob");
            else if(f.id.equals("thermal_shell"))require(f.pulses>0&&f.thermalDamageSeen&&pool(f.enemy).compareTo(f.enemyPool)<0,"actual attributed thermal pulse damaged real hostile mob, not base category counter");
            else if(f.id.equals("light_shield"))require(f.absorbed>=0&&shield.compareTo(f.shield)==0,"real source damage absorbed by light shield before base S");
            else require(f.specialSeen,"actual specialist arrow lease/turn or paid native-contact reward, not base category vaporization");
            if(f.id.equals("magnetic_domain")){
                require(f.slowFallSeen,"normal jump/descent received the real owned self slow-fall during magnetic domain");
                require(f.domainArrowMinimum!=null&&f.domainArrowBefore.subtract(f.domainArrowMinimum).compareTo(f.domainArrowCost)==0,"actual base-plus-reserve wallet paid exactly the one-percent native arrow debit");
            }
            if(f.id.equals("charge_decoy"))require(shield.compareTo(f.shield)==0,"decoy steers without spending shield");
        }
        f.passed=true;
        LOG.info("QA_GAMEPLAY_PASS skill={} witness=actual_specialist_defense hp={} shieldBefore={} shield={} specialistSeen={} pulses={} nativeAbsorbTick={} minCp={} minBudget={} paymentClockBefore={} paymentClockAfter={} actualArrow={} actualArrowVelocity={} enemyBefore={} enemyAfter={}",f.id,hp,f.shield,shield,f.specialSeen,f.pulses,f.absorbed,f.minCp,f.minBudget,f.clock,clock(p,f.id),f.arrow==null?null:f.arrow.getUUID(),f.arrow==null?null:f.arrow.getDeltaMovement(),f.enemyPool,pool(f.enemy));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void groundingIncoming(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent e){
        var f=fixture;if(f==null||!f.begun||f.passed||!f.id.equals("grounding_return")||!(e.getEntity() instanceof ServerPlayer p)||!p.getUUID().equals(f.owner))return;
        Object c=contexts("cn.academy.port.highpower.GroundingReturn","CONTEXTS").get(p.getUUID());
        LOG.info("QA_GAMEPLAY_GROUNDING_INCOMING tick={} canceled={} amount={} sourceClass={} lightning={} direct={} attacker={} stance={} started={} spent={} preciseUsed={} crouch={} ground={} pos={} shield={}",p.serverLevel().getGameTime(),e.isCanceled(),e.getAmount(),e.getSource().getClass().getName(),e.getSource().is(net.minecraft.world.damagesource.DamageTypes.LIGHTNING_BOLT),e.getSource().getDirectEntity(),e.getSource().getEntity(),c!=null,c==null?null:field(c,"started"),c==null?null:field(c,"spent"),c==null?null:field(c,"precisionUsed"),p.isShiftKeyDown(),p.onGround(),p.position(),HighPowerCombat.shield(p));
    }
    private static BigDecimal pool(Husk e){return new BigDecimal(HighPowerCombat.healthText(e)).add(HighPowerCombat.shield(e));}
    private static long clock(ServerPlayer p,String id){return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_"+id);}
    private static String mastery(ServerPlayer p,String id){String key=switch(id){case "scatter_plates"->ScatterPlates.MASTERY_KEY;case "directional_dissipation_curtain"->DissipationCurtain.MASTERY_KEY;case "zero_gap_armor"->ZeroGapArmor.MASTERY_KEY;default->"academy:qa_absent_read_only";};var tag=p.getPersistentData().get(key);return tag==null?"absent":tag.toString();}
    private static Map<?,?> contexts(String type,String name){try{var f=Class.forName(type).getDeclaredField(name);f.setAccessible(true);return(Map<?,?>)f.get(null);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static Object field(Object object,String name){try{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("QA_GAMEPLAY_DEFENSE_FAILED "+why);}
}

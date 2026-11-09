package cn.academy.port.qa;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.highpower.*;
import cn.academy.port.skill.MagManip;
import cn.academy.port.skill.MagneticBlockEntity;
import com.mojang.logging.LogUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Actual block/entity/item/route witnesses for the remaining nine EM/MD active skills. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class GameplayAcceptanceEmMdConstruction {
    private static final Logger LOG=LogUtils.getLogger();
    private static final Set<String> IDS=Set.of("mag_manip","magnetic_item_pull","conductive_circuit","pulse_shield","rail_relay","folded_mirror_array","scatter_locator_plate","remote_scatter_relay","particle_laminate");
    private static final class Fixture {
        final UUID owner;final String id;final List<Husk> targets=new ArrayList<>();
        final List<BigDecimal> pools=new ArrayList<>();final Set<UUID> hits=new java.util.HashSet<>();
        BlockPos iron;MagneticBlockEntity block;ItemEntity item;UUID itemId;Vec3 playerAt,itemAt;
        final List<GoalPause> goalPauses=new ArrayList<>();
        double cp,budget,minCp,minBudget,maxMove;long clock,tick;int peak,materialUnits,circuitAttempts;boolean begun,passed,heldWitness,routeWitness,circuitDamageSeen;
        Fixture(UUID owner,String id){this.owner=owner;this.id=id;}
    }
    private static Fixture fixture;
    private record GoalPause(net.minecraft.world.entity.ai.goal.GoalSelector selector,Set<net.minecraft.world.entity.ai.goal.Goal.Flag> disabled){}
    private GameplayAcceptanceEmMdConstruction(){}
    public static void run(Minecraft mc,String action){
        var server=mc.getSingleplayerServer();require(server!=null&&mc.player!=null,"integrated player");UUID owner=mc.player.getUUID();
        if(action.startsWith("setup:")){
            String id=action.substring(6);require(IDS.contains(id),"implemented construction fixture "+id);LiveInputQa.run(mc,"setup:"+id);
            server.submit(()->{prepare(server.getPlayerList().getPlayer(owner),id);return true;}).join();LiveInputQa.assertHealthy();
        }else if(action.equals("gui-drag"))LiveInputQa.run(mc,"gui:item-pull-drag");
        else server.submit(()->{
            var p=server.getPlayerList().getPlayer(owner);require(fixture!=null&&fixture.owner.equals(owner),"same construction owner");
            if(action.equals("baseline"))baseline(p);
            else if(action.equals("far-target")){require(!fixture.begun&&fixture.id.equals("mag_manip"),"far ballistic target only before untouched baseline");fixture.targets.getFirst().setPos(p.getX()+.45,p.getY(),p.getZ()+10);LOG.info("QA_GAMEPLAY_MAG_FAR_SETUP target={} normalAimMustCompensateGravity=true",fixture.targets.getFirst().position());}
            else if(action.equals("held"))held(p);
            else if(action.equals("rail-trace"))railTrace(p);
            else if(action.equals("circuit-ready")){require(ConductiveCircuit.phase(p)==cn.academy.port.highpower.core.ConductiveCircuitRules.Phase.RESTING,"two genuine circuit anchors ready before AI and pulse");LOG.info("QA_GAMEPLAY_CIRCUIT_READY phase={} tick={}",ConductiveCircuit.phase(p),p.serverLevel().getGameTime());}
            else if(action.equals("crossing-mob"))crossingMob(p);
            else if(action.equals("path-ready"))pathReady(p);
            else if(action.equals("assert"))verify(p);
            else if(action.equals("verify"))require(fixture.passed,"real construction mechanism passed");
            else throw new IllegalArgumentException(action);return true;
        }).join();
    }
    private static void prepare(ServerPlayer p,String id){
        if(fixture!=null)for(var e:fixture.targets)e.discard();fixture=new Fixture(p.getUUID(),id);var f=fixture;var w=p.serverLevel();var feet=p.blockPosition();
        for(var old:w.getEntitiesOfClass(Husk.class,p.getBoundingBox().inflate(64),Husk::isNoAi))old.discard();
        for(int z=-2;z<=26;z++)for(int x=-8;x<=8;x++){
            w.setBlock(feet.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
            for(int y=0;y<6;y++)w.setBlock(feet.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        w.setWeatherParameters(6000,0,false,false);
        if(id.equals("mag_manip")){f.iron=feet.offset(0,1,5);w.setBlock(f.iron,Blocks.IRON_BLOCK.defaultBlockState(),3);target(p,.45,4.5,true);}
        else if(id.equals("magnetic_item_pull"))p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_INGOT));
        else if(id.equals("pulse_shield")){
            Husk e=target(p,0,6,true);e.getPersistentData().putBoolean(NewSkillRuntime.CIRCUIT_FLAG,true);e.setTarget(p);
        }else if(id.equals("particle_laminate")){
            w.setBlock(feet.offset(-4,1,6),Blocks.STONE.defaultBlockState(),3);w.setBlock(feet.offset(4,1,6),Blocks.STONE.defaultBlockState(),3);target(p,0,5,true);
        }else if(Set.of("folded_mirror_array","scatter_locator_plate","remote_scatter_relay").contains(id))target(p,6,8,true);
        if(id.equals("scatter_locator_plate"))AbilityStorage.get(p).presets.edit(0,3,"folded_mirror_array",AbilityStorage.get(p)::learned);
        p.inventoryMenu.broadcastChanges();AcademyNetwork.sync(p);
        LOG.info("QA_GAMEPLAY_CONSTRUCTION_PREPARED skill={} realTargetCount={} blocksOnlySetup=true",id,f.targets.size());
    }
    private static Husk target(ServerPlayer p,double x,double z,boolean noAi){
        var e=EntityType.HUSK.create(p.serverLevel());require(e!=null,"real target");e.setNoAi(noAi);e.setNoGravity(noAi);e.setSilent(true);e.addTag("gameem_target");
        e.setPos(p.getX()+x,p.getY(),p.getZ()+z);require(p.serverLevel().addFreshEntity(e)&&HighPowerCombat.enroll(e,5),"durable real target");fixture.targets.add(e);return e;
    }
    private static void baseline(ServerPlayer p){
        var f=fixture;require(!f.begun,"one untouched baseline");f.cp=f.minCp=AbilityStorage.get(p).cp;f.budget=f.minBudget=HighPowerResources.availableCp(p);
        if(f.id.equals("mag_manip"))f.materialUnits=ironUnits(p);
        f.clock=clock(p,f.id);f.tick=p.serverLevel().getGameTime();f.playerAt=p.position();for(var e:f.targets)f.pools.add(pool(e));
        if(f.id.equals("magnetic_item_pull")){
            var items=p.serverLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(20),e->e.getItem().is(Items.IRON_INGOT)&&MagneticItemProvenance.ownedBy(e,p.getUUID()));
            require(items.size()==1,"one genuine owner-proven native Q drop, not a summoned item marker");f.item=items.getFirst();f.itemId=f.item.getUUID();f.itemAt=f.item.position();
        }
        f.begun=true;LOG.info("QA_GAMEPLAY_CONSTRUCTION_BASELINE skill={} targets={} nativeCp={} budget={} item={}",f.id,f.pools,f.cp,f.budget,f.itemAt);
    }
    private static void held(ServerPlayer p){
        var f=fixture;require(f.id.equals("mag_manip")&&f.begun,"magnetic block held stage");var b=MagManip.block(p);
        require(b!=null&&MagManip.owns(p,b)&&b.carriedState().is(Blocks.IRON_BLOCK)&&p.serverLevel().getBlockState(f.iron).isAir(),"actual terrain iron removed and retained by owned magnetic block entity");
        require(b.position().distanceTo(Vec3.atCenterOf(f.iron))>.5,"actual iron block moved away from its source");f.block=b;f.heldWitness=true;
        LOG.info("QA_GAMEPLAY_MAG_MANIP_HELD block={} source={} actualPosition={} realTerrainRemoved=true",b.getUUID(),f.iron,b.position());
    }
    /** A normal AI hostile enters the live fence; no teleport/velocity/hurt is injected after spawn. */
    private static void crossingMob(ServerPlayer p){
        var f=fixture;require(f.id.equals("conductive_circuit")&&f.begun&&f.targets.isEmpty(),"one normal AI crossing mob");
        require(ConductiveCircuit.phase(p)==cn.academy.port.highpower.core.ConductiveCircuitRules.Phase.RESTING,"real AI starts beyond an existing resting line");
        // Native hostile AI waits before its first path update. Start just beyond
        // the line, so its own ordinary walk can cross during the actual 20-tick pulse.
        var e=target(p,0,6.5,false);pauseGoals(e);f.pools.add(pool(e));
        LOG.info("QA_GAMEPLAY_CIRCUIT_REAL_LANDING_STAGE mob={} from={} gravity={} ground={} noAi={} goalControlsTemporarilyPaused=true",e.getUUID(),e.position(),!e.isNoGravity(),e.onGround(),e.isNoAi());
    }
    private static void pathReady(ServerPlayer p){
        var f=fixture;require(f.id.equals("conductive_circuit")&&f.targets.size()==1&&ConductiveCircuit.phase(p)==cn.academy.port.highpower.core.ConductiveCircuitRules.Phase.RESTING,"one real landed threat and resting line");
        var e=f.targets.getFirst();require(e.onGround()&&!e.isNoGravity(),"native gravity actually landed the threat before navigation initialization");
        for(var saved:f.goalPauses)for(var flag:net.minecraft.world.entity.ai.goal.Goal.Flag.values())if(saved.disabled().contains(flag))saved.selector().disableControlFlag(flag);else saved.selector().enableControlFlag(flag);
        f.goalPauses.clear();e.setTarget(p);require(e.getNavigation().moveTo(p,1),"normal ground navigation found a real walkable path across the resting line");
        LOG.info("QA_GAMEPLAY_CIRCUIT_REAL_AI_START mob={} from={} target={} nativePathTarget={} ground={} nativeAi=true",e.getUUID(),e.position(),p.position(),e.getNavigation().getTargetPos(),e.onGround());
    }
    private static void pauseGoals(Husk e){
        try{
            for(String name:List.of("goalSelector","targetSelector")){
                var field=net.minecraft.world.entity.Mob.class.getDeclaredField(name);field.setAccessible(true);var selector=(net.minecraft.world.entity.ai.goal.GoalSelector)field.get(e);
                var flags=net.minecraft.world.entity.ai.goal.GoalSelector.class.getDeclaredField("disabledFlags");flags.setAccessible(true);
                var original=java.util.EnumSet.noneOf(net.minecraft.world.entity.ai.goal.Goal.Flag.class);
                for(Object value:(Set<?>)flags.get(selector))original.add((net.minecraft.world.entity.ai.goal.Goal.Flag)value);
                fixture.goalPauses.add(new GoalPause(selector,Set.copyOf(original)));
                for(var flag:net.minecraft.world.entity.ai.goal.Goal.Flag.values())selector.disableControlFlag(flag);
            }
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)public static void early(PlayerTickEvent.Post e){if(e.getEntity() instanceof ServerPlayer p)sample(p);}
    @SubscribeEvent public static void pre(ServerTickEvent.Pre e){observeServer(e.getServer());}
    @SubscribeEvent public static void post(ServerTickEvent.Post e){observeServer(e.getServer());}
    private static void observeServer(net.minecraft.server.MinecraftServer s){var f=fixture;if(f!=null){var p=s.getPlayerList().getPlayer(f.owner);if(p!=null)sample(p);}}
    private static void sample(ServerPlayer p){
        var f=fixture;if(f==null||!f.begun||f.passed||!p.getUUID().equals(f.owner))return;f.minCp=Math.min(f.minCp,AbilityStorage.get(p).cp);f.minBudget=Math.min(f.minBudget,HighPowerResources.availableCp(p));
        f.maxMove=Math.max(f.maxMove,p.position().distanceTo(f.playerAt));
        if(f.id.equals("mag_manip")&&f.block!=null&&!f.block.isRemoved())LOG.info("QA_GAMEPLAY_MAG_BLOCK_FLIGHT tick={} pos={} velocity={} hovering={} target={} targetPool={} hits={} cp={}",p.serverLevel().getGameTime(),f.block.position(),f.block.getDeltaMovement(),f.block.hovering(),f.targets.getFirst().position(),pool(f.targets.getFirst()),f.hits,AbilityStorage.get(p).cp);
        if(f.id.equals("rail_relay")&&!NewSkillRuntime.anchors(p).isEmpty())railTrace(p);
        if(f.id.equals("conductive_circuit")&&!f.targets.isEmpty()){
            f.circuitAttempts=Math.max(f.circuitAttempts,ConductiveCircuit.attemptedCount(p));
            LOG.info("QA_GAMEPLAY_CIRCUIT_TRACE tick={} phase={} attempts={} mob={} delta={} pool={} target={} nativePathTarget={}",p.serverLevel().getGameTime(),ConductiveCircuit.phase(p),ConductiveCircuit.attemptedCount(p),f.targets.getFirst().position(),f.targets.getFirst().getDeltaMovement(),pool(f.targets.getFirst()),f.targets.getFirst().getTarget(),f.targets.getFirst().getNavigation().getTargetPos());
        }
        if(f.id.equals("magnetic_item_pull"))f.peak=Math.max(f.peak,MagneticItemPull.targetCount(p));
        if(f.id.equals("particle_laminate")){
            f.peak=Math.max(f.peak,ParticleLaminate.panels(p).size());var r=ParticleLaminate.receipt(p);if(r!=null&&r.paid()&&r.successes()>0)f.routeWitness=true;
        }
        if(f.id.equals("folded_mirror_array")||f.id.equals("scatter_locator_plate")){
            var r=FoldedMirrorArray.receipt(p);if(r!=null&&r.crossings()>0&&r.status()==FoldedMirrorArray.DAMAGED)f.routeWitness=true;
            if(f.id.equals("scatter_locator_plate"))f.peak=Math.max(f.peak,ScatterLocatorPlate.plates(p).size());
        }
        if(f.id.equals("remote_scatter_relay")){var r=RemoteScatterRelay.receipt(p);f.peak=Math.max(f.peak,RemoteScatterRelay.plates(p).size());if(r!=null&&r.crossings()>0&&r.status()==RemoteScatterRelay.DAMAGED)f.routeWitness=true;}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)public static void damaged(LivingDamageEvent.Post e){
        var f=fixture;if(f==null||!f.begun||f.passed||e.getNewDamage()<=0)return;
        if(e.getSource().getEntity() instanceof ServerPlayer p&&p.getUUID().equals(f.owner)&&f.targets.stream().anyMatch(t->t==e.getEntity())){
            sample(p);f.hits.add(e.getEntity().getUUID());
            if(f.id.equals("conductive_circuit")&&exactSkill(e.getSource(),"electromaster.conductive_circuit")){
                f.circuitDamageSeen=true;
                LOG.info("QA_GAMEPLAY_CIRCUIT_NATIVE_DAMAGE owner={} target={} nativePost={} actualSource=electromaster.conductive_circuit actualAttempts={}",f.owner,e.getEntity().getUUID(),e.getNewDamage(),f.circuitAttempts);
            }
        }
    }
    private static void verify(ServerPlayer p){
        var f=fixture;require(f.begun&&!f.passed,"one real baseline");sample(p);
        if(f.id.equals("mag_manip"))LOG.info("QA_GAMEPLAY_MAG_BLOCK_END blockRemoved={} blockPos={} hovering={} ironBefore={} ironAfter={} cpBefore={} cpAfter={} actualPool={} hits={}",f.block==null?null:f.block.isRemoved(),f.block==null?null:f.block.position(),f.block==null?null:f.block.hovering(),f.materialUnits,ironUnits(p),f.cp,AbilityStorage.get(p).cp,f.targets.stream().map(GameplayAcceptanceEmMdConstruction::pool).toList(),f.hits);
        long after=clock(p,f.id);boolean paid=f.minCp<f.cp||f.minBudget<f.budget||after>f.clock&&after>f.tick;
        if(f.id.equals("scatter_locator_plate"))paid|=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_folded_mirror_array")>f.tick;
        require(paid,"actual finite debit/reservation or successful closed payment clock");
        if(f.id.equals("rail_relay"))require(f.maxMove>2,"actual player travelled along two real anchors");
        else if(f.id.equals("magnetic_item_pull")){
            require(f.peak>0,"actual paid controller selected owned item");require(p.position().distanceTo(f.playerAt)<.2,"player did not walk into ordinary pickup");
            require(p.getInventory().countItem(Items.IRON_INGOT)>0||f.item.position().distanceTo(p.position())+.3<f.itemAt.distanceTo(p.position()),"real selected item physically approached stationary owner or entered inventory");
        }else{
            require(!f.targets.isEmpty()&&f.targets.size()==f.pools.size(),"real durable target baseline");
            for(int i=0;i<f.targets.size();i++)require(pool(f.targets.get(i)).compareTo(f.pools.get(i))<0&&f.hits.contains(f.targets.get(i).getUUID()),"actual player-attributed target damage");
            if(f.id.equals("conductive_circuit"))require(f.circuitAttempts>0&&f.circuitDamageSeen,"actual finite fence crossing committed its own conductive-circuit damage, not base shield retaliation");
            if(f.id.equals("mag_manip")){require(f.heldWitness&&f.block!=null&&!f.block.hovering(),"real terrain extraction, held motion and normal released projectile");require(ironUnits(p)==f.materialUnits,"one extracted iron block conserved across terrain, flight or native dropped item");}
            if(f.id.equals("pulse_shield"))require(f.targets.getFirst().getTarget()==null&&f.targets.getFirst().getPersistentData().getLong(NewSkillRuntime.PULSE_UNTIL)>f.tick,"real circuit interference stamp and AI target cleared");
            if(Set.of("folded_mirror_array","scatter_locator_plate","remote_scatter_relay","particle_laminate").contains(f.id))require(f.routeWitness,"paid native multisegment receipt with actual success, not placement alone");
            if(f.id.equals("particle_laminate"))require(f.peak==2,"two actual panels formed the damaging corridor");
            if(f.id.equals("remote_scatter_relay"))require(f.peak>0,"real paid relay node precedes terminal fire");
            if(f.id.equals("scatter_locator_plate"))require(f.peak==1&&ScatterLocatorPlate.plates(p).isEmpty(),"borrowed one-use locator was actually consumed by folded mirror shot");
        }
        f.passed=true;
        LOG.info("QA_GAMEPLAY_PASS skill={} witness=actual_multistage_world_effect hits={} actualTargets={} targetPoolsAfter={} paidRoute={} deployedPeak={} maxPlayerMove={} minNativeCp={} minAvailableBudget={} paymentClockBefore={} paymentClockAfter={} nativeItem={}",f.id,f.hits,f.targets.size(),f.targets.stream().map(GameplayAcceptanceEmMdConstruction::pool).toList(),f.routeWitness,f.peak,f.maxMove,f.minCp,f.minBudget,f.clock,after,f.itemId);
    }
    private static int ironUnits(ServerPlayer p){
        int count=0;var feet=BlockPos.containing(fixture.playerAt==null?p.position():fixture.playerAt);
        for(int x=-8;x<=8;x++)for(int z=-2;z<=26;z++)for(int y=0;y<6;y++)if(p.serverLevel().getBlockState(feet.offset(x,y,z)).is(Blocks.IRON_BLOCK))count++;
        count+=p.serverLevel().getEntitiesOfClass(MagneticBlockEntity.class,p.getBoundingBox().inflate(40),e->!e.isRemoved()&&e.carriedState().is(Blocks.IRON_BLOCK)&&p.getUUID().equals(e.ownerId())).size();
        for(var e:p.serverLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(40),e->e.getItem().is(Items.IRON_BLOCK)))count+=e.getItem().getCount();
        return count+p.getInventory().countItem(Items.IRON_BLOCK);
    }
    private static void railTrace(ServerPlayer p){
        var anchors=NewSkillRuntime.anchors(p);var at=p.position();var d=p.getDeltaMovement();
        var next=cn.academy.port.highpower.core.RailRelayRules.step(new cn.academy.port.highpower.core.V3(at.x,at.y,at.z),anchors,new cn.academy.port.highpower.core.V3(d.x,d.y,d.z),true);
        boolean clear=next.isPresent()&&p.serverLevel().noCollision(p,p.getBoundingBox().move(next.get().x(),next.get().y(),next.get().z()));
        Vec3 clipped=null;if(next.isPresent()){var requested=new Vec3(next.get().x(),next.get().y(),next.get().z());var sweep=p.getBoundingBox().expandTowards(requested);clipped=net.minecraft.world.entity.Entity.collideBoundingBox(p,requested,p.getBoundingBox(),p.serverLevel(),p.serverLevel().getEntityCollisions(p,sweep));}
        LOG.info("QA_GAMEPLAY_RAIL_TRACE tick={} anchors={} pos={} delta={} ground={} computed={} movedBoxClear={} nativeClipped={} maxMove={}",p.serverLevel().getGameTime(),anchors,at,d,p.onGround(),next,clear,clipped,fixture.maxMove);
    }
    private static BigDecimal pool(Husk e){return new BigDecimal(HighPowerCombat.healthText(e)).add(HighPowerCombat.shield(e));}
    private static boolean exactSkill(net.minecraft.world.damagesource.DamageSource source,String id){
        if(!HighPowerCombat.isExactSkillSource(source))return false;
        try{var field=source.getClass().getDeclaredField("skill");field.setAccessible(true);return id.equals(field.get(source));}
        catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    private static long clock(ServerPlayer p,String id){return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_"+id);}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("QA_GAMEPLAY_CONSTRUCTION_FAILED "+why);}
}

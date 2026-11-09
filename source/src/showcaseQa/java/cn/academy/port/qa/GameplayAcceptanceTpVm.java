package cn.academy.port.qa;

import cn.academy.port.*;
import cn.academy.port.highpower.*;
import com.mojang.logging.LogUtils;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.EventPriority;

/** Environment preparation and read-only functional witnesses. All skill actions use normal client keys/UI. */
@EventBusSubscriber(modid="academy",value=net.neoforged.api.distmarker.Dist.CLIENT)
final class GameplayAcceptanceTpVm {
    static final org.slf4j.Logger LOG=LogUtils.getLogger();
    private static String skill="";
    private static ServerPlayer owner;
    private static Vec3 origin;
    private static double cp,budget,minCp,minBudget,maxDistance,maxY,maxSpeed;
    private static long started;
    private static long paidClockBefore;
    private static final List<Zombie> targets=new ArrayList<>();
    private static final Map<UUID,BigDecimal> hp=new HashMap<>();
    private static final Map<UUID,BigDecimal> loss=new HashMap<>();
    private static final Set<UUID> attributed=new HashSet<>();
    private static final Map<UUID,Vec3> targetOrigins=new HashMap<>();
    private static boolean watching;
    private static int walkTicks;
    private static int stockBefore;
    private static UUID medium;
    private static Vec3 mediumBefore;
    private static int constructBlocks;
    private static Vec3 home;
    private static BigDecimal playerBefore;
    private static boolean pveHit,slowSeen;
    private static final Map<UUID,Double> initialSpeed=new HashMap<>(), minX=new HashMap<>(),maxX=new HashMap<>();

    static boolean walk(Minecraft mc,String arg){
        var a=arg.split(",");double x=Double.parseDouble(a[0]),z=Double.parseDouble(a[1]);
        double dx=x-mc.player.getX(),dz=z-mc.player.getZ();
        boolean arrived=dx*dx+dz*dz<.25*.25;
        if(walkTicks==0) LogUtils.getLogger().info("QA_WALK_BEGIN target=({}, {}) position={}",x,z,mc.player.position());
        if(arrived||++walkTicks>240){
            key(mc,87,false);walkTicks=0;require(arrived,"normal walking target=("+x+", "+z+") position="+mc.player.position());return true;
        }
        mc.player.setYRot((float)-Math.toDegrees(Math.atan2(dx,dz)));mc.player.setXRot(0);key(mc,87,true);return false;
    }
    private static void key(Minecraft mc,int code,boolean down){
        if(down)System.setProperty("academy.qa.key."+code,"true");else System.clearProperty("academy.qa.key."+code);
        mc.keyboardHandler.keyPress(mc.getWindow().getWindow(),code,0,down?org.lwjgl.glfw.GLFW.GLFW_PRESS:org.lwjgl.glfw.GLFW.GLFW_RELEASE,0);
    }

    static void run(Minecraft mc,String arg){
        if(arg.startsWith("setup:")){skill=arg.substring(6);watching=false;LiveInputQa.run(mc,arg);return;}
        if(arg.startsWith("aim-level:")){
            String[] point=arg.substring(10).split(",");
            LiveInputQa.aim(mc,point[0]+","+mc.player.getEyeY()+","+point[1]);return;
        }
        if(arg.equals("aim-medium")){
            Vec3 point=mc.getSingleplayerServer().submit(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                var needle=DisplacedProjection.receipt(p);
                LOG.info("QA_GAMEPLAY_NEEDLE_RECEIPT skill={} receipt={} registryAvailability={} actualExisting={}",skill,needle,DisplacedNeedleRegistry.availability(p),DisplacedNeedleRegistry.existing(p).map(Entity::getUUID).orElse(null));
                require(needle!=null&&needle.medium()!=null,"actual projected needle exists");
                medium=needle.medium();var e=p.serverLevel().getEntity(medium);require(e!=null,"same real medium remains in world");mediumBefore=e.position();return e.getBoundingBox().getCenter();
            }).join();LiveInputQa.aim(mc,point.x+","+point.y+","+point.z);return;
        }
        if(arg.equals("aim-construct")){
            Vec3 point=mc.getSingleplayerServer().submit(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                for(var pos:BlockPos.betweenClosed(p.blockPosition().offset(-24,0,-24),p.blockPosition().offset(24,12,48)))
                    if(constructMaterial(p.serverLevel().getBlockState(pos)))return Vec3.atCenterOf(pos);
                throw new IllegalStateException("real landed construct block required");
            }).join();LiveInputQa.aim(mc,point.x+","+point.y+","+point.z);return;
        }
        String[] f=arg.split(":",2);String op=f[0],value=f.length>1?f[1]:"";
        server(mc,p->{switch(op){
            case "prepare"->{
                owner=p;watching=false;targets.clear();hp.clear();loss.clear();attributed.clear();targetOrigins.clear();
                // A previous real displaced needle remains registered across skill switches.
                // Generic fixture discard would mark that escrow LOST, so a later normal F
                // correctly refuses medium_missing. Preserve the exact registry-proven entity.
                var retainedNeedle=DisplacedNeedleRegistry.existing(p).orElse(null);
                for(var e:p.serverLevel().getEntities(p,p.getBoundingBox().inflate(40)))
                    if(!(e instanceof net.minecraft.world.entity.player.Player)&&e!=retainedNeedle)e.discard();
                if(retainedNeedle!=null)LOG.info("QA_GAMEPLAY_PRESERVED_REGISTERED_NEEDLE skill={} entity={} position={} owner={} fixtureDoesNotDiscardOwnedMedium=true",skill,retainedNeedle.getUUID(),retainedNeedle.position(),p.getUUID());
                int x=p.getBlockX(),z=p.getBlockZ(),y=p.getBlockY();
                for(int dx=-12;dx<=12;dx++)for(int dz=-8;dz<=38;dz++){
                    p.serverLevel().setBlock(new BlockPos(x+dx,y-1,z+dz),Blocks.STONE.defaultBlockState(),3);
                    for(int dy=0;dy<5;dy++)p.serverLevel().setBlock(new BlockPos(x+dx,y+dy,z+dz),Blocks.AIR.defaultBlockState(),3);
                }
                if(skill.equals("threatening_teleport"))p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(AcademyCraft.NEEDLE.get(),16));
                if(skill.equals("shift_tp"))p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.COBBLESTONE,16));
                if(Set.of("displaced_projection","cross_substitution","spatial_pincer_corridor","multidimensional_landing_array","short_interception").contains(skill))
                    p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(AcademyCraft.NEEDLE.get(),16));
                if(skill.equals("recovery_mark")){
                    p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(AcademyCraft.NEEDLE.get(),16));
                    var state=AbilityStorage.get(p);state.presets.edit(0,3,"displaced_projection",state::learned);AbilityStorage.save(p);AcademyNetwork.sync(p);
                }
                if(skill.equals("spatial_relay"))p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_INGOT));
                p.inventoryMenu.broadcastChanges();
                LOG.info("QA_GAMEPLAY_PREPARE skill={} actualSurvival={} nativeGround={} position={}",skill,!p.getAbilities().instabuild,p.onGround(),p.position());
            }
            case "baseline"->{
                origin=p.position();cp=AbilityStorage.get(p).cp;budget=HighPowerResources.cp(p);minCp=cp;minBudget=budget;maxDistance=maxY=maxSpeed=0;
                started=p.serverLevel().getGameTime();watching=true;playerBefore=total(p);pveHit=slowSeen=false;initialSpeed.clear();minX.clear();maxX.clear();
                for(var t:targets){initialSpeed.put(t.getUUID(),t.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));minX.put(t.getUUID(),t.getX());maxX.put(t.getUUID(),t.getX());}
                paidClockBefore=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_"+skill);
                stockBefore=p.getInventory().countItem(AcademyCraft.NEEDLE.get());
                attributed.clear();for(var target:targets)targetOrigins.put(target.getUUID(),target.position());
                for(var t:targets){hp.put(t.getUUID(),total(t));loss.put(t.getUUID(),BigDecimal.ZERO);}
                LOG.info("QA_GAMEPLAY_BASELINE skill={} cp={} budget={} position={} targets={}",skill,cp,budget,origin,targets.size());
            }
            case "target"->{
                String[] xyz=value.split(",");var at=p.position().add(Double.parseDouble(xyz[0]),Double.parseDouble(xyz[1]),Double.parseDouble(xyz[2]));
                var t=EntityType.HUSK.create(p.serverLevel());idlePhysicalMob(t);t.setSilent(true);t.setPos(at);p.serverLevel().addFreshEntity(t);
                require(HighPowerCombat.enroll(t,5),"real durable native target enrollment");targets.add(t);hp.put(t.getUUID(),total(t));loss.put(t.getUUID(),BigDecimal.ZERO);
                LOG.info("QA_GAMEPLAY_TARGET skill={} entity={} position={} hp={}",skill,t.getUUID(),at,total(t));
            }
            case "check"->check(p,value);
            case "remember-home"->home=p.position();
            case "native-melee"->{require(!targets.isEmpty(),"actual native attacker required");boolean hurt=targets.getFirst().doHurtTarget(p);LOG.info("QA_GAMEPLAY_REAL_MELEE skill={} accepted={} actualSource={}",skill,hurt,targets.getFirst().getUUID());}
            case "mastery"->{AbilityStorage.get(p).addExperience(skill,1);AbilityStorage.save(p);AcademyNetwork.sync(p);}
            case "receipt"->LOG.info("QA_GAMEPLAY_STAGE skill={} receipt={}",skill,invoke(value,"receipt",p));
            case "construct-baseline"->{constructBlocks=countConstruct(p);require(constructBlocks>0,"real source block structure");LOG.info("QA_GAMEPLAY_CONSTRUCT_BASELINE blocks={}",constructBlocks);}
            case "item-snapshot"->{
                var items=p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,p.getBoundingBox().inflate(15),e->e.getItem().is(Items.IRON_INGOT)&&MagneticItemProvenance.ownedBy(e,p.getUUID()));
                require(items.size()==1,"one actual self-Q-tossed iron stack");medium=items.getFirst().getUUID();mediumBefore=items.getFirst().position();
                LOG.info("QA_GAMEPLAY_ITEM_BASELINE skill={} uuid={} position={} actualQToss=true",skill,medium,mediumBefore);
            }
            case "state"->LOG.info("QA_GAMEPLAY_STATE skill={} pos={} cp={} budget={} targetDamage={} maxDistance={} maxY={} maxSpeed={}",skill,p.position(),AbilityStorage.get(p).cp,HighPowerResources.cp(p),loss,maxDistance,maxY,maxSpeed);
            default->throw new IllegalArgumentException(arg);
        }});
    }
    private static void check(ServerPlayer p,String value){
        sample();
        if(skill.equals("vector_cage"))LOG.info("QA_GAMEPLAY_CAGE_STATE receipt={} hits={} player={} targets={}",VectorCage.receipt(p),VectorCage.receipts(p),p.position(),targets.stream().map(Entity::position).toList());
        if(skill.equals("deflection_strike"))LOG.info("QA_GAMEPLAY_DEFLECTION_STATE receipts={} player={} targets={} loss={} minCp={} minBudget={}",DeflectionStrike.receipts(p),p.position(),targets.stream().map(Entity::position).toList(),loss,minCp,minBudget);
        if(value.equals("damage")){
            require(!targets.isEmpty()&&loss.values().stream().anyMatch(v->v.signum()>0),"real target HP/shield must decrease; losses="+loss);
            require(!attributed.isEmpty(),"actual native damage must be attributed to the casting player");
            require(paid(),"real finite resource consumption required");
        }else if(value.equals("no-damage-push")){
            require(loss.values().stream().allMatch(v->v.signum()==0),"nonlethal mode must cause no HP or shield loss");
            require(targets.stream().anyMatch(t->t.position().distanceTo(targetOrigins.get(t.getUUID()))>.2),"actual target must be pushed");require(paid(),"actual finite nonlethal payment");
        }else if(value.equals("push-left")||value.equals("push-right")){
            boolean left=value.equals("push-left");require(targets.stream().anyMatch(t->left?maxX.get(t.getUUID())>targetOrigins.get(t.getUUID()).x+.15:minX.get(t.getUUID())<targetOrigins.get(t.getUUID()).x-.15),"native target must move to selected side: "+minX+" / "+maxX);require(paid(),"actual side push payment");
        }else if(value.equals("cage-slow")){
            var r=VectorCage.receipt(p);require(r!=null&&r.low()&&r.paid(),"actual low cage must be paid");require(slowSeen,"actual native movement speed modifier during pressure contact");require(paid(),"real low cage payment");
        }else if(value.equals("auto-return")){
            var r=EmergencyReturn.receipt(p);LOG.info("QA_GAMEPLAY_AUTO_STATE home={} actual={} receipt={} pveHit={} totalBefore={} actualTotal={}",home,p.position(),r,pveHit,playerBefore,total(p));require(r!=null&&r.automatic()&&r.paid()&&r.moved(),"actual automatic PVE return receipt: "+r);require(home!=null&&p.position().distanceTo(home)<1,"actual automatic return to original home");require(pveHit&&total(p).compareTo(playerBefore)<0,"real PVE damage remains suffered");LOG.info("QA_GAMEPLAY_AUTO_RETURN receipt={} home={} actual={} sufferedNativePve={}",r,home,p.position(),pveHit);
        }else if(value.equals("sideways-lift")){
            require(maxDistance>.5&&maxY<.8,"actual fixed horizontal wind movement, not vertical lift");require(paid(),"actual wind payment");
        }else if(value.equals("movement")){
            require(maxDistance>1,"authoritative player displacement required, got "+maxDistance);require(paid(),"movement requires actual finite payment");
        }else if(value.startsWith("receipt-")){
            String type=value.substring(8);Object receipt=invoke(type,"receipt",p);require(receipt!=null,"production receipt required "+type);
            require(bool(receipt,"paid"),"receipt must witness actual payment: "+receipt);
            require(bool(receipt,"moved"),"receipt must witness authoritative transport: "+receipt);
            require(maxDistance>.5,"same real player must actually have moved");
            LOG.info("QA_GAMEPLAY_RECEIPT skill={} receipt={}",skill,receipt);
        }else if(value.startsWith("stopped-")){
            String type=value.substring(8);require(Boolean.FALSE.equals(invoke(type,"active",p)),"normal release/cancel must stop actual controller "+type);
            LOG.info("QA_GAMEPLAY_STOPPED skill={} controller={} actualStopped=true",skill,type);return;
        }else if(value.startsWith("volley-")){
            String type=value.substring(7);Object receipt=invoke(type,"receipt",p);require(receipt!=null,"actual volley receipt");
            require(number(receipt,"published")>= (type.equals("MultidimensionalLandingArray")?6:3),"all original shots published: "+receipt);
            require(number(receipt,"hurts")>0,"actual native medium contact caused hurt: "+receipt);
            require(loss.values().stream().anyMatch(v->v.signum()>0),"same real targets actually lost HP/shield");
            int stock=p.getInventory().countItem(AcademyCraft.NEEDLE.get());
            if(type.equals("MultidimensionalLandingArray"))require(stock==stockBefore&&number(receipt,"retained")==0,"borrowed originals must all return without duplication: "+receipt);
            else require(stockBefore-stock>=3,"three actual original needles must be spent");
            LOG.info("QA_GAMEPLAY_VOLLEY skill={} receipt={} realNeedlesBefore={} realNeedlesAfter={}",skill,receipt,stockBefore,stock);
        }else if(value.equals("recovery")){
            var receipt=RecoveryMark.receipt(p);require(receipt!=null&&receipt.paid()&&receipt.moved(),"actual original needle recovery receipt "+receipt);
            require(medium!=null&&medium.equals(receipt.needleUuid()),"recovery must move same previously projected original");
            var entity=p.serverLevel().getEntity(medium);
            require(entity!=null&&entity.position().distanceTo(receipt.destination())<.8||entity==null&&p.getInventory().countItem(AcademyCraft.NEEDLE.get())>=stockBefore,"same original reached destination or was actually picked up");
            LOG.info("QA_GAMEPLAY_RECOVERY receipt={} actualOriginal={} before={} after={}",receipt,medium,mediumBefore,entity==null?"picked_up":entity.position());
        }else if(value.equals("item-relay")){
            var receipt=SpatialRelay.receipt(p);require(receipt!=null&&receipt.paid()&&receipt.moved(),"actual paid item transport: "+receipt);
            var e=p.serverLevel().getEntity(medium);require(e!=null&&e.position().distanceTo(mediumBefore)>3,"same self-Q item truly moved along full route");
            LOG.info("QA_GAMEPLAY_ITEM_RELAY uuid={} before={} after={} receipt={}",medium,mediumBefore,e.position(),receipt);
        }else if(value.equals("construct-hold")){
            require(ConstructRuntime.heldBody(p)!=null&&countConstruct(p)==0,"real rigid body exists and all original blocks left ordinary world");
            LOG.info("QA_GAMEPLAY_CONSTRUCT_HOLD originalBlocks={} remainingOrdinaryBlocks={} body={}",constructBlocks,countConstruct(p),ConstructRuntime.heldBody(p));return;
        }else if(value.equals("construct-land")){
            require(ConstructRuntime.heldBody(p)==null&&countConstruct(p)==constructBlocks,"normal landing returns every original block exactly once");
            LOG.info("QA_GAMEPLAY_CONSTRUCT_LAND originalBlocks={} returnedBlocks={}",constructBlocks,countConstruct(p));
        }else if(value.equals("overworld")){
            require(p.serverLevel().dimension().location().toString().equals("minecraft:overworld"),"real cross-dimension skill must return to saved overworld");require(paid(),"cross-dimension transport must actually pay finite cost");
        }else if(value.equals("carried")){
            require(targets.stream().anyMatch(t->t.position().distanceTo(targetOrigins.get(t.getUUID()))>3),"same nearby native entity must truly accompany the location transport");
            LOG.info("QA_GAMEPLAY_CARRIED skill={} originals={} actual={}",skill,targetOrigins,targets.stream().map(Entity::position).toList());
        }else throw new IllegalArgumentException(value);
        LOG.info("QA_GAMEPLAY_PASS skill={} mechanism={} source=normal_GLFW_or_GUI actualTargetDamage={} nativeCpSpent={} budgetSpent={} displacement={} height={} elapsedTicks={}",
                skill,value,loss,cp-minCp,budget-minBudget,maxDistance,maxY,p.serverLevel().getGameTime()-started);
        LOG.info("QA_GAMEPLAY_PAYMENT skill={} observedNativeDip={} observedBudgetDip={} closedSpendClockBefore={} closedSpendClockAfter={} sameTickRecoveryMayHideDip=true",skill,cp-minCp,budget-minBudget,paidClockBefore,p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_"+skill));
    }
    private static boolean paid(){return minCp<cp-1e-5||minBudget<budget-1e-6
            ||owner.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_"+skill)>paidClockBefore;}
    private static Object invoke(String type,String method,ServerPlayer p){
        try{return Class.forName("cn.academy.port.highpower."+type).getMethod(method,ServerPlayer.class).invoke(null,p);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static boolean bool(Object r,String method){try{return Boolean.TRUE.equals(r.getClass().getMethod(method).invoke(r));}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static int number(Object r,String method){try{return ((Number)r.getClass().getMethod(method).invoke(r)).intValue();}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static boolean constructMaterial(net.minecraft.world.level.block.state.BlockState state){return state.is(Blocks.OAK_PLANKS)||state.is(Blocks.SPRUCE_PLANKS)||state.is(Blocks.GLASS);}
    private static int countConstruct(ServerPlayer p){int count=0;for(var pos:BlockPos.betweenClosed(p.blockPosition().offset(-24,0,-24),p.blockPosition().offset(24,12,48)))if(constructMaterial(p.serverLevel().getBlockState(pos)))count++;return count;}
    static void server(Minecraft mc,Consumer<ServerPlayer> work){
        require(mc.player!=null&&mc.getSingleplayerServer()!=null,"actual integrated native client required");var id=mc.player.getUUID();var server=mc.getSingleplayerServer();
        server.submit(()->{var p=server.getPlayerList().getPlayer(id);require(p!=null,"same connected player");work.accept(p);}).join();
    }
    /** NoAI suppresses travel, so idle goals while retaining native gravity and knockback integration. */
    private static void idlePhysicalMob(Zombie mob) {
        mob.setNoAi(false);
        try { for(String name:new String[]{"goalSelector","targetSelector"}) {
            var field=net.minecraft.world.entity.Mob.class.getDeclaredField(name);field.setAccessible(true);
            ((net.minecraft.world.entity.ai.goal.GoalSelector)field.get(mob)).removeAllGoals(goal->true);
        }} catch(ReflectiveOperationException e){throw new IllegalStateException("native idle physical target preparation",e);}
    }
    private static BigDecimal total(LivingEntity e){return e.isRemoved()?BigDecimal.ZERO:new BigDecimal(HighPowerCombat.healthText(e)).add(HighPowerCombat.shield(e));}
    static void require(boolean value,String message){if(!value)throw new IllegalStateException("QA_GAMEPLAY_FAILED skill="+skill+" "+message);}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){if(watching&&owner!=null&&owner.server==e.getServer())sample();}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void playerBefore(PlayerTickEvent.Pre e){if(e.getEntity()==owner)sample();}
    @SubscribeEvent public static void section(net.neoforged.neoforge.event.entity.EntityEvent.EnteringSection e){if(e.getEntity()==owner)sample();}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post e){if(watching&&e.getEntity()==owner&&e.getNewDamage()>0&&targets.contains(e.getSource().getEntity()))pveHit=true;if(watching&&e.getSource().getEntity()==owner){if(e.getNewDamage()>0&&hp.containsKey(e.getEntity().getUUID()))attributed.add(e.getEntity().getUUID());sample();}}
    private static void sample(){
        if(!watching||owner==null||owner.isRemoved())return;
        minCp=Math.min(minCp,AbilityStorage.get(owner).cp);minBudget=Math.min(minBudget,HighPowerResources.cp(owner));
        maxDistance=Math.max(maxDistance,owner.position().distanceTo(origin));maxY=Math.max(maxY,owner.getY()-origin.y);maxSpeed=Math.max(maxSpeed,owner.getDeltaMovement().length());
        for(var t:targets){loss.merge(t.getUUID(),hp.get(t.getUUID()).subtract(total(t)).max(BigDecimal.ZERO),BigDecimal::max);minX.merge(t.getUUID(),t.getX(),Math::min);maxX.merge(t.getUUID(),t.getX(),Math::max);if(initialSpeed.containsKey(t.getUUID())&&t.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)<initialSpeed.get(t.getUUID())*.99)slowSeen=true;}
    }
}

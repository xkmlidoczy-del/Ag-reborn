package cn.academy.port.qa;

import cn.academy.port.*;
import cn.academy.port.highpower.*;
import cn.academy.port.skill.*;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Environment and ordinary stock only. All skill transitions originate from actual driver keys. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class GameplayAcceptanceTpPlayModes {
    private static String mode,id;private static UUID owner;private static Zombie target;
    private static boolean watching,paid,airborneActive;private static Vec3 origin,freeOrigin;
    private static double cp,budget,minCp,minBudget,maxDistance,maxY,maxHorizontal,minX,maxX,freeBudget;
    private static long clock,freeClock,oldToken;private static int stock;private static final Set<BlockPos> placed=new HashSet<>();
    private static final List<UUID> needles=new ArrayList<>();private static final Map<UUID,Vec3> needleBefore=new HashMap<>();
    private static UUID selected;private static int needleStock;private static boolean firstRecovery;
    private GameplayAcceptanceTpPlayModes(){}
    public static void run(Minecraft mc,String action){
        if(action.startsWith("setup:")){
            mode=action.substring(6);id=mode.startsWith("cancel-")?mode.substring(7):switch(mode){
                case "item-transport"->"threatening_teleport";case "block-placement"->"shift_tp";
                case "mark-entity","mark-air"->"mark_teleport";case "accel-air"->"vec_accel";
                case "flash-air"->"flashing";case "two-needles"->"recovery_mark";default->throw new IllegalArgumentException(mode);};
            GameplayAcceptanceTpVm.run(mc,"setup:"+id);GameplayAcceptanceTpVm.run(mc,"prepare");
            GameplayAcceptanceTpVm.server(mc,p->prepare(p));return;
        }
        if(action.startsWith("aim-needle:")){
            int slot=Integer.parseInt(action.substring(11))-1;Vec3 aim=mc.getSingleplayerServer().submit(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(owner);require(slot>=0&&slot<needles.size(),"same real needle slot");
                var e=p.serverLevel().getEntity(needles.get(slot));require(e!=null,"original needle remains present for actual selection");return e.getBoundingBox().getCenter();
            }).join();LiveInputQa.aim(mc,aim.x+","+aim.y+","+aim.z);return;
        }
        GameplayAcceptanceTpVm.server(mc,p->{require(p.getUUID().equals(owner),"same real owner");switch(action){
            case "baseline"->baseline(p);
            case "marked"->{require(count(p)>0,"real key created the described preview/mark/point");oldToken=token(p);}
            case "free-baseline"->{freeBudget=HighPowerResources.availableCp(p);freeClock=paymentClock(p);freeOrigin=p.position();}
            case "cleared"->{require(count(p)==0,"actual cancel input removed all old marks");free(p);freeOrigin=p.position();LogUtils.getLogger().info("QA_TP_PLAY_CANCEL_PASS mode={} oldToken={} count=0 noPayment=true actualKey=true",mode,oldToken);}
            case "fresh"->{require(count(p)==1&&token(p)!=oldToken,"next key creates a fresh first mark instead of using the cleared old plan");require(p.position().distanceTo(freeOrigin)<.02,"cleared plan did not teleport player");free(p);LogUtils.getLogger().info("QA_TP_PLAY_FRESH_PASS mode={} oldToken={} freshToken={} noOldPlanTransport=true",mode,oldToken,token(p));}
            case "assert"->verify(p);
            case "needle-capture"->captureNeedle(p);
            case "registered-two"->{require(needles.size()==2&&RecoveryMark.markedCount(p)==2,"two original real Q needles have independent live registrations");var ids=RecoveryMark.handles(p).stream().map(RecoveryMark.HandleVisual::needleUuid).collect(java.util.stream.Collectors.toSet());require(ids.equals(new HashSet<>(needles)),"handles bind exactly the two original identities");}
            case "recovery-cleared"->{require(RecoveryMark.markedCount(p)==0,"actual crouch key clears both registrations");free(p);for(UUID n:needles)require(p.serverLevel().getEntity(n) instanceof ItemEntity,"free clearing never consumes the actual needle");LogUtils.getLogger().info("QA_TP_PLAY_TWO_NEEDLES_CLEAR_PASS originals={} free=true",needles);}
            case "select-first"->select(p,0);
            case "select-second"->select(p,1);
            case "recovered"->recovered(p);
            default->throw new IllegalArgumentException(action);
        }});
    }
    private static void prepare(ServerPlayer p){
        owner=p.getUUID();watching=paid=airborneActive=false;placed.clear();needles.clear();needleBefore.clear();selected=null;firstRecovery=false;target=null;
        if(mode.equals("item-transport"))p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_INGOT,4));
        if(mode.equals("block-placement"))p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.COBBLESTONE,4));
        if(mode.equals("two-needles")){for(int slot=0;slot<p.getInventory().getContainerSize();slot++)if(p.getInventory().getItem(slot).is(AcademyCraft.NEEDLE.get()))p.getInventory().setItem(slot,ItemStack.EMPTY);p.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(AcademyCraft.NEEDLE.get(),2));needleStock=2;}
        if(mode.equals("item-transport")||mode.equals("block-placement"))for(int y=0;y<4;y++)p.serverLevel().setBlockAndUpdate(p.blockPosition().offset(0,y,8),Blocks.STONE.defaultBlockState());
        if(mode.equals("mark-entity")||id.equals("short_interception")){
            target=new Zombie(EntityType.ZOMBIE,p.serverLevel());target.setNoAi(true);target.setSilent(true);target.setPos(p.position().add(0,0,8));require(p.serverLevel().addFreshEntity(target),"actual ordinary selectable native body");
        }
        p.inventoryMenu.broadcastChanges();AcademyNetwork.sync(p);LogUtils.getLogger().info("QA_TP_PLAY_PREPARED mode={} id={} normalSurvival={}",mode,id,!p.isCreative());
    }
    private static void baseline(ServerPlayer p){origin=p.position();cp=minCp=AbilityStorage.get(p).cp;budget=minBudget=HighPowerResources.availableCp(p);clock=paymentClock(p);stock=p.getMainHandItem().getCount();maxDistance=maxHorizontal=0;maxY=origin.y;minX=maxX=origin.x;watching=true;}
    private static long paymentClock(ServerPlayer p){return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_"+id);}
    private static void free(ServerPlayer p){require(HighPowerResources.availableCp(p)+1e-8>=freeBudget&&paymentClock(p)==freeClock,"clearing/first mark pays no hidden skill fee");}
    @SubscribeEvent(priority=EventPriority.LOWEST)public static void sample(ServerTickEvent.Post e){
        if(!watching)return;var p=e.getServer().getPlayerList().getPlayer(owner);if(p==null)return;
        minCp=Math.min(minCp,AbilityStorage.get(p).cp);minBudget=Math.min(minBudget,HighPowerResources.availableCp(p));
        maxDistance=Math.max(maxDistance,p.position().distanceTo(origin));maxHorizontal=Math.max(maxHorizontal,p.position().subtract(origin).horizontalDistance());maxY=Math.max(maxY,p.getY());minX=Math.min(minX,p.getX());maxX=Math.max(maxX,p.getX());
        if(!p.onGround()&&(id.equals("vec_accel")&&VecAccel.active(p)||id.equals("flashing")&&Flashing.active(p)))airborneActive=true;
        if(mode.equals("block-placement"))for(var at:BlockPos.betweenClosed(BlockPos.containing(origin.add(-2,0,3)),BlockPos.containing(origin.add(2,4,10))))if(p.serverLevel().getBlockState(at).is(Blocks.COBBLESTONE))placed.add(at.immutable());
    }
    private static void verify(ServerPlayer p){
        require(watching,"actual baseline precedes use");require(minCp<cp-1e-6||minBudget<budget-1e-6||paymentClock(p)>clock,"actual finite payment observed");
        switch(mode){
            case "item-transport"->{var items=p.serverLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(15),x->x.getItem().is(Items.IRON_INGOT));require(p.getMainHandItem().getCount()==stock-1&&items.size()==1&&items.getFirst().getItem().getCount()==1,"one actual held ordinary item was transferred without duplication");require(items.getFirst().position().distanceTo(origin)>5&&p.position().distanceTo(origin)<.05,"ordinary item reaches distant traced surface while actor stays put; origin="+origin+" actor="+p.position()+" item="+items.getFirst().position()+" range="+ThreateningTeleport.rules(AbilityStorage.get(p).exp(id)).range());}
            case "block-placement"->{require(p.getMainHandItem().getCount()==stock-1&&placed.size()==1,"exactly one real held block refills the native endpoint and is spent once");require(placed.iterator().next().getZ()>origin.z+5,"placed block really is distant");}
            case "mark-entity"->{require(target!=null&&target.isAlive()&&maxDistance>5&&p.position().distanceTo(target.position())<3,"actual entity aim produces distant player arrival at the original native body");}
            case "mark-air"->require(maxDistance>8&&maxY>origin.y+2,"actual empty-air endpoint produces a paid elevated teleport");
            case "accel-air"->require(AbilityStorage.get(p).exp(id)>.5&&airborneActive&&maxHorizontal>3,"high mastery true airborne acceleration produces real horizontal movement");
            case "flash-air"->require(airborneActive&&maxX>origin.x+3,"real lateral A-release hop works while actually airborne");
            default->throw new IllegalArgumentException(mode);
        }
        watching=false;LogUtils.getLogger().info("QA_TP_PLAY_MODE_PASS mode={} displacement={} maxY={} origin={} actual={} actualPaid=true airborneActive={} blocks={}",mode,maxDistance,maxY,origin,p.position(),airborneActive,placed);
    }
    private static int count(ServerPlayer p){return switch(id){case "safe_landing_probe"->SafeLandingProbe.active(p)?1:0;case "return_mark"->ReturnMark.marked(p)?1:0;case "emergency_return"->EmergencyReturn.marked(p)?1:0;case "recovery_mark"->RecoveryMark.markedCount(p);case "short_interception"->ShortInterception.marked(p)?1:0;case "spatial_relay"->SpatialRelay.points(p);case "spatial_pincer_corridor"->SpatialPincerCorridor.preparedPoints(p);case "multidimensional_landing_array"->MultidimensionalLandingArray.preparedPoints(p);default->throw new IllegalArgumentException(id);};}
    private static long token(ServerPlayer p){try{if(id.equals("safe_landing_probe")){var f=SafeLandingProbe.class.getDeclaredField("CONTEXTS");f.setAccessible(true);Object context=((Map<?,?>)f.get(null)).get(p.getUUID());require(context!=null,"actual owned live probe preview");var identity=context.getClass().getDeclaredField("identity");identity.setAccessible(true);return ((cn.academy.port.core.LegacySingleKeyServerIdentity)identity.get(context)).token();}Object receipt=switch(id){case "safe_landing_probe"->SafeLandingProbe.receipt(p);case "return_mark"->ReturnMark.receipt(p);case "emergency_return"->EmergencyReturn.receipt(p);case "short_interception"->ShortInterception.receipt(p);case "spatial_relay"->SpatialRelay.receipt(p);case "spatial_pincer_corridor"->SpatialPincerCorridor.receipt(p);case "multidimensional_landing_array"->MultidimensionalLandingArray.receipt(p);default->null;};require(receipt!=null,"actual production receipt");return(long)receipt.getClass().getMethod("token").invoke(receipt);}catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}}
    private static void captureNeedle(ServerPlayer p){var items=p.serverLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(12),x->x.getItem().is(AcademyCraft.NEEDLE.get())&&MagneticItemProvenance.ownedBy(x,p.getUUID())&&!needles.contains(x.getUUID()));require(items.size()==1&&items.getFirst().getItem().getCount()==1,"exactly one newly Q-tossed original plain needle");needles.add(items.getFirst().getUUID());}
    private static void select(ServerPlayer p,int index){require(RecoveryMark.markedCount(p)==(firstRecovery?1:2),"actual remaining registrations before aimed recovery");selected=needles.get(index);needleBefore.clear();for(UUID n:needles){var e=p.serverLevel().getEntity(n);if(e!=null)needleBefore.put(n,e.position());}baseline(p);}
    private static void recovered(ServerPlayer p){var r=RecoveryMark.receipt(p);require(selected!=null&&r!=null&&r.paid()&&r.moved()&&selected.equals(r.needleUuid()),"actual R recovered exactly the aimed original needle");require(RecoveryMark.markedCount(p)==(firstRecovery?0:1),"only selected registration consumed");require(minCp<cp-1e-6||minBudget<budget-1e-6||paymentClock(p)>clock,"targeted recovery made an actual finite debit");require(needleBefore.get(selected).distanceTo(r.destination())>1,"selected original was actually distant from its registered recovery ground");var entity=p.serverLevel().getEntity(selected);int merged=p.serverLevel().getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(r.destination(),r.destination()).inflate(1),x->x.getItem().is(AcademyCraft.NEEDLE.get())).stream().mapToInt(x->x.getItem().getCount()).sum();require(entity!=null&&entity.position().distanceTo(r.destination())<1||p.getInventory().countItem(AcademyCraft.NEEDLE.get())>0||firstRecovery&&merged==2,"selected original reached its own registered anchor or was naturally picked up");if(!firstRecovery){for(UUID n:needles)if(!n.equals(selected)){var other=p.serverLevel().getEntity(n);require(other!=null&&other.position().distanceTo(needleBefore.get(n))<.15,"nonselected original needle did not move");}firstRecovery=true;}else{int world=p.serverLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(20),x->x.getItem().is(AcademyCraft.NEEDLE.get())).stream().mapToInt(x->x.getItem().getCount()).sum();require(world+p.getInventory().countItem(AcademyCraft.NEEDLE.get())==needleStock,"both actual original needles conserved through native pickup/merge");watching=false;}LogUtils.getLogger().info("QA_TP_PLAY_TARGETED_RECOVERY_PASS selected={} remaining={} destination={} originals={}",selected,RecoveryMark.markedCount(p),r.destination(),needles);}
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("QA_TP_PLAY_FAILED mode="+mode+" "+text);}
}

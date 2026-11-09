package cn.academy.port.qa;

import cn.academy.port.*;
import cn.academy.port.highpower.*;
import com.mojang.logging.LogUtils;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Separate single-player alternate-mode witnesses; no skill start, emission permit, receiver or damage injection. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class GameplayAcceptanceTpAlternatives {
    private static final Set<String> IDS=Set.of("short_interception","cross_substitution","spatial_relay","multidimensional_landing_array","phase_sidestep","corner_exchange");
    private static String id;private static UUID owner,item;private static Vec3 origin,itemOrigin;private static float yaw;
    private static Zombie target,shooter;private static Arrow arrow;private static BigDecimal hp,targetLoss;
    private static int needles;private static boolean watching,passed,redirected;
    private GameplayAcceptanceTpAlternatives(){}
    public static void run(Minecraft mc,String action){
        if(action.startsWith("setup:")){
            id=action.substring(6);require(IDS.contains(id),"supported TP alternate mode");
            GameplayAcceptanceTpVm.run(mc,"setup:"+id);GameplayAcceptanceTpVm.run(mc,"prepare");
            GameplayAcceptanceTpVm.server(mc,p->{owner=p.getUUID();watching=passed=redirected=false;target=shooter=null;arrow=null;item=null;
                if(Set.of("short_interception","cross_substitution").contains(id)){p.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).is(AcademyCraft.NEEDLE.get()))p.getInventory().setItem(i,ItemStack.EMPTY);}
                if(id.equals("multidimensional_landing_array"))p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(AcademyCraft.NEEDLE.get(),6));
                p.inventoryMenu.broadcastChanges();AcademyNetwork.sync(p);
            });return;
        }
        GameplayAcceptanceTpVm.server(mc,p->{require(p.getUUID().equals(owner),"same alternate-mode actor");switch(action){
            case "target"->{
                target=EntityType.HUSK.create(p.serverLevel());target.setNoAi(true);target.setSilent(true);target.setPos(p.position().add(0,0,id.equals("short_interception")?12:18));
                require(p.serverLevel().addFreshEntity(target)&&HighPowerCombat.enroll(target,5),"real durable native hostile target");
            }
            case "arrow"->{
                require(id.equals("short_interception")&&target!=null,"locked actual hostile target fixture");
                shooter=new Zombie(EntityType.ZOMBIE,p.serverLevel());shooter.setNoAi(true);shooter.setSilent(true);shooter.setPos(p.position().add(6,0,12));require(p.serverLevel().addFreshEntity(shooter),"real enemy shooter");
                arrow=new Arrow(p.serverLevel(),shooter,new ItemStack(Items.ARROW),new ItemStack(Items.BOW));arrow.setPos(p.getEyePosition().add(0,0,7));arrow.setDeltaMovement(0,0,-.8);require(p.serverLevel().addFreshEntity(arrow),"original enemy-owned ordinary arrow");
            }
            case "item"->{var list=p.serverLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(8),e->e.getItem().is(Items.IRON_INGOT)&&MagneticItemProvenance.ownedBy(e,p.getUUID()));require(list.size()==1,"real Q drop provenance");item=list.getFirst().getUUID();itemOrigin=list.getFirst().position();}
            case "baseline"->{origin=p.position();yaw=p.getYRot();needles=p.getInventory().countItem(AcademyCraft.NEEDLE.get());hp=target==null?BigDecimal.ZERO:total(target);targetLoss=BigDecimal.ZERO;watching=true;}
            case "assert"->verify(p);
            default->throw new IllegalArgumentException(action);
        }});
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){if(!watching||passed||owner==null)return;var p=e.getServer().getPlayerList().getPlayer(owner);if(p==null)return;
        if(target!=null)targetLoss=targetLoss.max(hp.subtract(total(target)));
        if(arrow!=null&&!arrow.isRemoved()&&arrow.getDeltaMovement().z>.05)redirected=true;
    }
    private static void verify(ServerPlayer p){
        require(watching&&!passed,"alternate mode baseline");
        int stock=p.getInventory().countItem(AcademyCraft.NEEDLE.get());
        switch(id){
            case "phase_sidestep"->{var r=PhaseSidestep.receipt(p);require(r!=null&&r.paid()&&r.moved()&&r.left(),"real crouching left-mode successful receipt");require(p.getX()>origin.x+2.9&&Math.abs(p.getZ()-origin.z)<.05,"authoritative actor really moved left of yaw zero (+X)");}
            case "corner_exchange"->{var r=CornerExchange.receipt(p);require(r!=null&&r.paid()&&r.moved()&&r.reversed(),"real crouch exchange has reversed orientation");require(p.position().distanceTo(origin)>3&&Math.abs(Math.abs(net.minecraft.util.Mth.wrapDegrees(p.getYRot()-yaw))-180)<.05,"actual native destination and 180-degree rotation, not only a receipt flag");}
            case "cross_substitution"->{var r=CrossSubstitution.receipt(p);require(r!=null&&r.selfSwap()&&r.swapAdmitted()&&r.paid()&&r.moved()&&r.published()==0&&r.paidNeedles()==0,"real crouch self swap rather than projectile volley");require(p.position().distanceTo(origin)>3&&needles==0&&stock==0,"authoritative movement without any physical needle material");}
            case "spatial_relay"->{var r=SpatialRelay.receipt(p);require(r!=null&&r.paid()&&r.moved()&&r.completed()>0&&item!=null&&item.equals(r.item()),"two-point crouch run moved the same genuine Q item");var e=p.serverLevel().getEntity(item);require(e instanceof ItemEntity&&e.position().distanceTo(itemOrigin)>2&&e.position().distanceTo(r.destination())<.8,"same original item actually reached real two-point final leg");}
            case "short_interception"->{var r=ShortInterception.receipt(p);require(r!=null&&r.paid()&&r.projected()&&r.hurt()&&!r.fallback()&&r.admitted()>0,"actual original-arrow mode, never needle fallback");require(redirected&&targetLoss.signum()>0&&needles==0&&stock==0,"same real arrow changed course and damaged the actual native target without material substitution");}
            case "multidimensional_landing_array"->{var r=MultidimensionalLandingArray.receipt(p);require(r!=null&&r.freeOrigin()&&r.published()>=6&&r.hurts()>0&&targetLoss.signum()>0,"held free-origin mode emitted at least six actual damaging native contacts");require(needles==6&&stock==6&&r.retained()==0&&r.pending()==0,"all six actual borrowed originals returned with no duplication or custody residue");}
            default->throw new IllegalStateException(id);
        }
        passed=true;watching=false;
        LogUtils.getLogger().info("QA_GAMEPLAY_PASS skill={} mechanism=actual_alternate_mode start={} end={} nativeYawBefore={} nativeYawAfter={} needlesBefore={} needlesAfter={} actualTargetLoss={} sameArrowRedirected={} normalGLFW=true",id,origin,p.position(),yaw,p.getYRot(),needles,stock,targetLoss,redirected);
    }
    private static BigDecimal total(net.minecraft.world.entity.LivingEntity e){return e.isRemoved()?BigDecimal.ZERO:new BigDecimal(HighPowerCombat.healthText(e)).add(HighPowerCombat.shield(e));}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("QA_TP_ALTERNATIVE_FAILED skill="+id+" "+message);}
}

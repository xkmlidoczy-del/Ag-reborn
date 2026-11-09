package cn.academy.port.qa;

import cn.academy.port.highpower.MagneticItemProvenance;
import com.mojang.logging.LogUtils;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** QA-only preconditions. Never casts a skill, writes a lease, pays a resource, or emits a VFX receipt. */
public final class CanonCompleteFixturesQa {
    private static CompletableFuture<Vec3> pending;
    private static String operation;
    private CanonCompleteFixturesQa() {}
    public static boolean step(Minecraft mc,String op) {
        if(op.startsWith("passive-"))return CanonPassiveAcceptanceQa.step(mc,op);
        if(!Boolean.getBoolean("academy.qa.showcase")||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)
            throw new IllegalStateException("Isolated host/integrated QA fixture only");
        if(pending==null){
            operation=op;pending=new CompletableFuture<>();var owner=mc.player.getUUID();var server=mc.getSingleplayerServer();
            server.execute(()->{try{var p=server.getPlayerList().getPlayer(owner);if(p==null)throw new IllegalStateException("same owner required");pending.complete(prepare(p,op));}
                catch(Throwable e){pending.completeExceptionally(e);}});
        }
        if(!operation.equals(op))throw new IllegalStateException("fixture operation changed while pending");
        if(!pending.isDone())return false;
        Vec3 aim=pending.join();pending=null;operation=null;
        if(aim!=null){Vec3 d=aim.subtract(mc.player.getEyePosition());mc.player.setYRot((float)-Math.toDegrees(Math.atan2(d.x,d.z)));mc.player.setYHeadRot(mc.player.getYRot());mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));}
        return true;
    }
    private static Vec3 prepare(ServerPlayer p,String op){
        var world=p.serverLevel();
        if(op.equals("aim-owned-item")){
            var item=world.getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(12),i->MagneticItemProvenance.ownedBy(i,p.getUUID())&&!i.getItem().isEmpty()).stream()
                .min(Comparator.comparingDouble(p::distanceToSqr)).orElseThrow(()->new IllegalStateException("A real native Q-tossed owned item must already exist"));
            LogUtils.getLogger().info("QA_COMPLETE_FIXTURE op={} item={} owner={} position={} nativeQProvenance=true positionAndVelocityUnmodified=true",op,item.getUUID(),p.getUUID(),item.position());
            return item.getBoundingBox().getCenter();
        }
        if(op.equals("mental-alert")||op.equals("mental-route")||op.equals("mental-other")){
            var mobs=world.getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(16),m->m instanceof net.minecraft.world.entity.monster.Monster&&!m.isNoAi());
            if(mobs.isEmpty())throw new IllegalStateException("real AI-enabled hostiles required");
            net.minecraft.world.entity.LivingEntity target=p;
            if(op.equals("mental-other")){
                var cow=EntityType.COW.create(world);if(cow==null)throw new IllegalStateException("cow fixture");cow.setPos(p.position().add(4,0,4));cow.setNoAi(true);if(!world.addFreshEntity(cow))throw new IllegalStateException("cow admission");target=cow;
            }
            for(var mob:mobs){mob.setTarget(target);if(!mob.getNavigation().moveTo(target,1.0))throw new IllegalStateException("native navigation path required");}
            LogUtils.getLogger().info("QA_COMPLETE_FIXTURE op={} mobs={} actualTarget={} nativeNavigationPrepared=true isSkillResult=false",op,mobs.size(),target.getUUID());return null;
        }
        if(op.equals("incoming-arrow")){
            var shooter=EntityType.HUSK.create(world);if(shooter==null)throw new IllegalStateException("shooter fixture");
            Vec3 direction=p.getLookAngle().normalize(),at=p.getEyePosition().add(direction.scale(3.8));shooter.setPos(at.add(1.2,-1.6,0));shooter.setNoAi(true);
            if(!world.addFreshEntity(shooter))throw new IllegalStateException("shooter admission");
            var arrow=new Arrow(world,shooter,new ItemStack(Items.ARROW),new ItemStack(Items.BOW));arrow.setPos(at);arrow.setDeltaMovement(direction.scale(-.22).add(0,.15,0));arrow.setNoGravity(false);
            if(!world.addFreshEntity(arrow))throw new IllegalStateException("arrow admission");
            LogUtils.getLogger().info("QA_COMPLETE_FIXTURE op={} arrow={} hostileOwner={} position={} velocity={} vanillaArrow=true actualSkillInputStillRequired=true",op,arrow.getUUID(),shooter.getUUID(),at,arrow.getDeltaMovement());return null;
        }
        throw new IllegalArgumentException("Unknown complete-acceptance fixture: "+op);
    }
}

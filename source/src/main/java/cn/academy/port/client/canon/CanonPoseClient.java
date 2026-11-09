package cn.academy.port.client.canon;

import cn.academy.port.canon.CanonVfx;
import cn.academy.port.canon.CanonVfxDesign;
import cn.academy.port.client.CastPoseClient;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/** Render-only poses keyed by real, accepted server visual events. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class CanonPoseClient {
    private static final Map<UUID,Lease> POSES=new HashMap<>();
    private static ClientLevel world;
    private record Lease(UUID owner,int entity,String skill,String category,long start,int duration,boolean impact) {}
    public record Snapshot(String skill,String category,double age,int duration,boolean impact) {}
    private CanonPoseClient() {}
    static void accept(CanonVfx.Visual visual){
        Minecraft mc=Minecraft.getInstance();sync(mc);
        if(world==null||mc.player==null||visual==null)return;
        var entity=world.getEntity(visual.entity());
        if(!(entity instanceof Player player)||!player.getUUID().equals(visual.owner()))return;
        var design=CanonVfxDesign.find(visual.skill());if(design==null)return;
        if(visual.phase()==CanonVfx.Phase.END){
            var existing=POSES.get(visual.owner());
            if(existing!=null&&existing.skill.equals(visual.skill()))POSES.remove(visual.owner());
            return;
        }
        int duration=Math.max(8,Math.min(18,visual.duration()));
        if(POSES.size()>=64&&!POSES.containsKey(visual.owner()))return;
        POSES.put(visual.owner(),new Lease(visual.owner(),visual.entity(),visual.skill(),design.category(),world.getGameTime(),duration,visual.phase()==CanonVfx.Phase.HIT));
    }
    private static void sync(Minecraft mc){if(world!=mc.level){POSES.clear();world=mc.level;}}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        Minecraft mc=Minecraft.getInstance();sync(mc);if(world==null)return;
        long now=world.getGameTime();POSES.values().removeIf(p->now<p.start||now-p.start>=p.duration);
    }
    private static boolean ownPose(Player p){return p.isSleeping()||p.isFallFlying()||p.isSwimming()||p.isVisuallyCrawling()
            ||p.isAutoSpinAttack()||p.isDeadOrDying()||p.isUsingItem();}
    public static Snapshot snapshot(Player player,double partial){
        Minecraft mc=Minecraft.getInstance();if(player==null||mc.level==null||world!=mc.level||ownPose(player)
                ||CastPoseClient.profile(player)!=null)return null;
        var lease=POSES.get(player.getUUID());
        if(lease==null||lease.entity!=player.getId()||!Double.isFinite(partial))return null;
        double age=world.getGameTime()-lease.start+Math.max(0,Math.min(1,partial));
        return CanonPoseMath.envelope(age,lease.duration)>0?new Snapshot(lease.skill,lease.category,age,lease.duration,lease.impact):null;
    }
    /** Invoked by the dedicated TAIL mixin only for Player models. */
    public static void apply(HumanoidModel<?> model,LivingEntity entity,float ageInTicks){
        if(ageInTicks==0||!(entity instanceof Player p))return;
        var mc=Minecraft.getInstance();var snap=snapshot(p,mc.getTimer().getGameTimeDeltaPartialTick(false));if(snap==null)return;
        boolean right=p.getMainArm()==HumanoidArm.RIGHT;double envelope=CanonPoseMath.envelope(snap.age,snap.duration);
        var pose=CanonPoseMath.sample(snap.category,snap.skill,snap.age,snap.duration,right,snap.impact);double keep=1-envelope;
        boolean swing=model.attackTime>0;
        if(!(swing&&right)){model.rightArm.xRot=(float)(model.rightArm.xRot*keep+pose.rightX());model.rightArm.yRot=(float)(model.rightArm.yRot*keep+pose.rightY());model.rightArm.zRot=(float)(model.rightArm.zRot*keep+pose.rightZ());}
        if(!(swing&&!right)){model.leftArm.xRot=(float)(model.leftArm.xRot*keep+pose.leftX());model.leftArm.yRot=(float)(model.leftArm.yRot*keep+pose.leftY());model.leftArm.zRot=(float)(model.leftArm.zRot*keep+pose.leftZ());}
        model.body.xRot+=(float)pose.lean();model.head.xRot-=(float)(pose.lean()*.45);
    }
    @SubscribeEvent public static void hand(RenderHandEvent event){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||event.getHand()!=InteractionHand.MAIN_HAND)return;
        var snap=snapshot(mc.player,event.getPartialTick());if(snap==null)return;
        boolean right=mc.player.getMainArm()==HumanoidArm.RIGHT;
        var pose=CanonPoseMath.sample(snap.category,snap.skill,snap.age,snap.duration,right,snap.impact);
        var stack=event.getPoseStack();stack.translate(pose.handX(),pose.handY(),pose.handZ());
        stack.mulPose(Axis.XP.rotationDegrees((float)((right?pose.rightX():pose.leftX())*14)));
        stack.mulPose(Axis.YP.rotationDegrees((float)((right?pose.rightY():pose.leftY())*12)));
        stack.mulPose(Axis.ZP.rotationDegrees((float)((right?pose.rightZ():pose.leftZ())*12)));
    }
}

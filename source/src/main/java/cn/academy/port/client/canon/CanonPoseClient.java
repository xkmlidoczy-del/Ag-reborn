package cn.academy.port.client.canon;

import cn.academy.port.canon.CanonVfx;
import cn.academy.port.canon.CanonVfxDesign;
import cn.academy.port.client.CastPoseClient;
import cn.academy.port.macro.MacroSkillRules;
import cn.academy.port.macro.MacroVfx;
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
    private record Lease(int entity,String skill,String category,long start,int duration,boolean impact,UUID cast,CanonPoseMath.Pose settle) {}
    public record Snapshot(String skill,String category,double age,int duration,boolean impact) {}
    private CanonPoseClient() {}
    static void accept(CanonVfx.Visual visual){
        Minecraft mc=Minecraft.getInstance();sync(mc);
        if(world==null||mc.player==null||visual==null)return;
        var entity=world.getEntity(visual.entity());
        if(!(entity instanceof Player player)||!player.getUUID().equals(visual.owner()))return;
        var lease=POSES.get(visual.owner());
        if(lease!=null&&lease.cast!=null&&lease.skill.equals(visual.skill()))return;
        var design=CanonVfxDesign.find(visual.skill());if(design==null)return;
        if(visual.phase()==CanonVfx.Phase.END){
            var existing=POSES.get(visual.owner());
            if(existing!=null&&existing.skill.equals(visual.skill()))POSES.remove(visual.owner());
            return;
        }
        int duration=Math.max(8,Math.min(18,visual.duration()));
        if(POSES.size()>=64&&!POSES.containsKey(visual.owner()))return;
        POSES.put(visual.owner(),new Lease(visual.entity(),visual.skill(),design.category(),world.getGameTime(),duration,
                visual.phase()==CanonVfx.Phase.HIT,null,null));
    }
    static void accept(MacroVfx.Event event){
        Minecraft mc=Minecraft.getInstance();sync(mc);
        if(world==null)return;
        var profile=MacroSkillRules.find(event.skill());
        if(profile==null)return;
        var old=POSES.get(event.owner());
        long now=world.getGameTime();
        if(event.phase()==MacroVfx.Phase.END){
            if(old!=null&&event.cast().equals(old.cast)){
                var actor=world.getEntity(event.entity());
                if(old.impact&&now-old.start<6)return; // confirmed contact gets its natural recoil
                var pose=actor instanceof LivingEntity living?pose(old,now-old.start,living.getMainArm()==HumanoidArm.RIGHT):null;
                if(pose!=null)POSES.put(event.owner(),new Lease(old.entity,old.skill,old.category,now,6,false,old.cast,pose));
                else POSES.remove(event.owner());
            }
            return;
        }
        boolean impact=event.phase()==MacroVfx.Phase.HIT||event.phase()==MacroVfx.Phase.RELEASE;
        if(impact&&old!=null&&event.cast().equals(old.cast)&&old.impact&&now-old.start<8)return;
        if(!impact&&event.phase()==MacroVfx.Phase.ACTIVE&&old!=null&&event.cast().equals(old.cast)&&old.impact
                &&now-old.start<old.duration)return;
        int duration=impact?12:(int)Math.min(2400,event.tick()-event.phaseStart()+event.life());
        long start=impact?event.tick():event.phaseStart();
        if(now-start>=duration||POSES.size()>=80&&!POSES.containsKey(event.owner()))return;
        POSES.put(event.owner(),new Lease(event.entity(),event.skill(),profile.category(),start,duration,impact,event.cast(),null));
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
        var pose=pose(POSES.get(p.getUUID()),snap.age,right);
        applyArms(model,pose,envelope,right);
    }
    /** City model calls this after its idle/activity pose, before sleeves copy the arms. */
    public static void applyNpc(HumanoidModel<?> model,LivingEntity npc,float partial){
        var mc=Minecraft.getInstance();
        if(mc.level==null||npc.level()!=world||!npc.isAlive()||npc.isUsingItem()||npc.isPassenger())return;
        var pose=POSES.get(npc.getUUID());
        if(pose==null||pose.entity!=npc.getId())return;
        double age=world.getGameTime()-pose.start+Math.clamp(partial,0,1), strength=CanonPoseMath.envelope(age,pose.duration);
        if(strength<=0)return;
        boolean right=npc.getMainArm()==HumanoidArm.RIGHT;
        applyArms(model,pose(pose,age,right),strength,right);
    }
    private static CanonPoseMath.Pose pose(Lease pose,double age,boolean right){
        if(pose.settle!=null)return CanonPoseMath.scale(pose.settle,Math.clamp(1-age/pose.duration,0,1));
        return CanonPoseMath.sample(pose.category,pose.skill,age,pose.duration,right,pose.impact);
    }
    private static void applyArms(HumanoidModel<?> model,CanonPoseMath.Pose pose,double envelope,boolean right){
        double keep=1-envelope;
        boolean swing=model.attackTime>0;
        if(!(swing&&right)){model.rightArm.xRot=(float)(model.rightArm.xRot*keep+pose.rightX());model.rightArm.yRot=(float)(model.rightArm.yRot*keep+pose.rightY());model.rightArm.zRot=(float)(model.rightArm.zRot*keep+pose.rightZ());}
        if(!(swing&&!right)){model.leftArm.xRot=(float)(model.leftArm.xRot*keep+pose.leftX());model.leftArm.yRot=(float)(model.leftArm.yRot*keep+pose.leftY());model.leftArm.zRot=(float)(model.leftArm.zRot*keep+pose.leftZ());}
        model.body.xRot+=(float)pose.lean();model.head.xRot-=(float)(pose.lean()*.45);
    }
    @SubscribeEvent public static void hand(RenderHandEvent event){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||event.getHand()!=InteractionHand.MAIN_HAND)return;
        var snap=snapshot(mc.player,event.getPartialTick());if(snap==null)return;
        boolean right=mc.player.getMainArm()==HumanoidArm.RIGHT;
        var pose=pose(POSES.get(mc.player.getUUID()),snap.age,right);
        var stack=event.getPoseStack();stack.translate(pose.handX(),pose.handY(),pose.handZ());
        stack.mulPose(Axis.XP.rotationDegrees((float)((right?pose.rightX():pose.leftX())*14)));
        stack.mulPose(Axis.YP.rotationDegrees((float)((right?pose.rightY():pose.leftY())*12)));
        stack.mulPose(Axis.ZP.rotationDegrees((float)((right?pose.rightZ():pose.leftZ())*12)));
    }
}

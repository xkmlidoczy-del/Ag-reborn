package cn.academy.port.client.canon;

import cn.academy.port.VisualTier;
import cn.academy.port.VisualTierConfig;
import cn.academy.port.macro.*;
import cn.academy.port.client.CastPoseClient;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix4f;

/** Authoritative result endpoints, real 3D surfaces; no camera-facing beam boards. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class AgEnergyBeam {
    private record Pulse(BeamImpactVfx.Scene scene,double began,boolean alignHand){}
    private record Resolved(Vec3 from,Vec3 to,double radius,double alpha,double age){}
    private static final List<Pulse> LIVE=new ArrayList<>();
    private static ClientLevel world;
    private static MultiBufferSource.BufferSource buffers;
    private static Map<String,Object> diagnostics=Map.of();
    private AgEnergyBeam(){}
    @EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD) public static final class Setup{
        @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->BeamImpactVfx.receiver=AgEnergyBeam::receive);}
    }
    private static void sync(){var w=Minecraft.getInstance().level;if(w!=world){LIVE.clear();world=w;}}
    public static boolean replacesClassic(){return AgEffectMaterials.beams();}
    private static void receive(BeamImpactVfx.Scene v){
        receive(v,true);
    }
    private static void receive(BeamImpactVfx.Scene v,boolean alignHand){
        sync();var mc=Minecraft.getInstance();if(world==null||mc.player==null||!world.dimension().location().equals(v.dimension())||MacroSkillRules.find(v.skill())==null
                ||!MacroVfx.finite(v.from())||!MacroVfx.finite(v.to())||v.from().distanceToSqr(v.to())>192*192||!Float.isFinite(v.radius())||v.radius()<=0||v.radius()>12
                ||!Float.isFinite(v.yaw())||Math.abs(v.hand())!=1||v.life()<1||v.life()>200||v.tick()>world.getGameTime()+100||world.getGameTime()-v.tick()>200)return;
        var actor=world.getEntity(v.entity());if(actor!=null&&!actor.getUUID().equals(v.owner()))return;
        if(v.style()==BeamImpactVfx.Style.BEAM){if(LIVE.size()>=32)LIVE.removeFirst();LIVE.add(new Pulse(v,Math.max(v.tick(),world.getGameTime()-4),alignHand));}
        AgImpactFx.accept(v);
    }
    /** Existing NPC adapter has already validated the entity-skill envelope. */
    public static void entity(net.minecraft.world.entity.LivingEntity actor,Vec3 from,Vec3 to){
        sync();if(world==null||actor.level()!=world||!MacroVfx.finite(from)||!MacroVfx.finite(to))return;
        // EntityAbilityRuntime already supplies the NPC's right-hand origin. Do not offset it again.
        receive(new BeamImpactVfx.Scene(UUID.randomUUID(),actor.getId(),actor.getUUID(),world.dimension().location(),"meltdowner",BeamImpactVfx.Style.BEAM,from,to,2,1,actor.getYHeadRot(),world.getGameTime(),50,List.of(),List.of()),false);
    }
    private static Resolved resolve(Pulse pulse,double now){
        var mc=Minecraft.getInstance();var v=pulse.scene;double age=Math.max(0,now-pulse.began),life=v.life();
        double alpha=Math.min(1,(age+.5)/2)*Math.clamp((life-age)/14,0,1);if(alpha<=0)return null;
        var direction=v.to().subtract(v.from()).normalize();double yaw=Math.toRadians(v.yaw());Vec3 side=new Vec3(-Math.cos(yaw),0,-Math.sin(yaw));
        var actor=world.getEntity(v.entity());boolean handOrigin=pulse.alignHand&&(actor==null||actor.getEyePosition().distanceToSqr(v.from())<4);
        Vec3 from=handOrigin?v.from().add(side.scale(v.hand()*.32)).add(0,-.35,0).add(direction.scale(.65)):v.from();
        boolean first=actor==mc.player&&mc.getCameraEntity()==mc.player&&mc.options.getCameraType().isFirstPerson();
        if(handOrigin&&actor instanceof Player p&&!first&&actor.getUUID().equals(v.owner())&&actor.position().distanceToSqr(v.from())<16)from=CastPoseClient.worldHandAnchor(p,world.getGameTime(),0);
        // Near the hand the dense beam tapers, leaving the upper view clear. The checked endpoint never moves.
        return new Resolved(from,v.to(),v.radius(),alpha,age);
    }
    static List<MacroVfxClient.VolumeSample> volumes(double now){
        sync();LIVE.removeIf(p->now-p.began>p.scene.life());if(!AgEffectMaterials.beams())return List.of();var result=new ArrayList<MacroVfxClient.VolumeSample>();
        for(var p:LIVE){var a=resolve(p,now);if(a==null)continue;var v=p.scene;
            var event=new MacroVfx.Event(v.cast(),v.entity(),v.owner(),v.dimension(),v.skill(),MacroVfx.Phase.ACTIVE,a.from,a.to,MacroSkillRules.Shape.LINE,(float)a.radius,0,v.tick(),v.life());
            result.add(new MacroVfxClient.VolumeSample(event,a.age,a.from.lerp(a.to,.5),Math.min(1.2,a.radius*.38),a.alpha*.38));
        }return result;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||AgVolumeRenderer.shadow())return;sync();if(world==null||!AgEffectMaterials.beams())return;
        double now=world.getGameTime()+e.getPartialTick().getGameTimeDeltaPartialTick(false);LIVE.removeIf(p->now-p.began>p.scene.life());if(LIVE.isEmpty())return;
        if(buffers==null)buffers=MultiBufferSource.immediate(new ByteBufferBuilder(1<<18));var cam=e.getCamera().getPosition();var model=RenderSystem.getModelViewStack();model.pushMatrix();
        try{model.set(e.getModelViewMatrix());RenderSystem.applyModelViewMatrix();
            var tier=VisualTierConfig.tier();int sides=tier==VisualTier.CINEMATIC?24:tier==VisualTier.STANDARD?18:12;
            int ceiling=tier==VisualTier.CINEMATIC?3072:tier==VisualTier.STANDARD?1536:576;
            var visible=LIVE.stream().map(p->resolve(p,now)).filter(a->a!=null&&MacroVfx.distanceSquared(cam,a.from,a.to)<=160*160)
                    .sorted(Comparator.comparingDouble(a->MacroVfx.distanceSquared(cam,a.from,a.to)))
                    .limit(tier==VisualTier.CINEMATIC?6:tier==VisualTier.STANDARD?4:3).toList();
            int segments=Math.max(2,Math.min(32,ceiling/Math.max(1,visible.size()*3*sides)));
            if(Boolean.getBoolean("academy.qa.signatureBudget"))diagnostics=Map.of("visible",visible.size(),"quadsCeiling",visible.size()*3*sides*segments,"endpoints",visible.stream().map(Object::toString).toList());
            for(var a:visible){
                for(int layer=2;layer>=0;layer--)tube(e.getPoseStack().last().pose(),cam,a,layer,sides,segments);
            }
            buffers.endBatch();
        }finally{model.popMatrix();RenderSystem.applyModelViewMatrix();}
    }
    private static void tube(Matrix4f matrix,Vec3 camera,Resolved a,int layer,int sides,int segments){
        var axis=a.to.subtract(a.from);double length=axis.length();if(length<.05)return;axis=axis.scale(1/length);Vec3 right=axis.cross(new Vec3(0,1,0));if(right.lengthSqr()<1e-9)right=new Vec3(1,0,0);else right=right.normalize();var up=right.cross(axis).normalize();
        double radius=Math.min(layer==0?.22:layer==1?.75:1.3,a.radius*(layer==0?.075:layer==1?.25:.44));
        var out=buffers.getBuffer(AgEffectMaterials.type(layer));
        for(int j=0;j<segments;j++){
            // Always reserve the first segment for the hand taper, even when many beams share the mesh budget.
            double first=Math.min(.18,2.8/length);
            double t0=j==0?0:first+(1-first)*(j-1)/(segments-1),t1=first+(1-first)*j/(segments-1);
            double reach=1-Math.pow(1-Math.clamp(a.age/3,0,1),3);if(t0>reach)break;t1=Math.min(t1,reach);
            double d0=t0*length,d1=t1*length;
            double r0=radius*(.04+.96*Math.clamp(d0/3.6,0,1))*(.95+.05*Math.sin(d0*3-a.age*.5));double r1=radius*(.04+.96*Math.clamp(d1/3.6,0,1))*(.95+.05*Math.sin(d1*3-a.age*.5));
            for(int k=0;k<sides;k++){
                double f0=k/(double)sides,f1=(k+1)/(double)sides;Vec3 s0=right.scale(Math.cos(f0*Math.PI*2)).add(up.scale(Math.sin(f0*Math.PI*2))),s1=right.scale(Math.cos(f1*Math.PI*2)).add(up.scale(Math.sin(f1*Math.PI*2)));
                vertex(out,matrix,camera,a.from.add(axis.scale(d0)).add(s0.scale(r0)),d0,f0,a.alpha);vertex(out,matrix,camera,a.from.add(axis.scale(d1)).add(s0.scale(r1)),d1,f0,a.alpha);vertex(out,matrix,camera,a.from.add(axis.scale(d1)).add(s1.scale(r1)),d1,f1,a.alpha);vertex(out,matrix,camera,a.from.add(axis.scale(d0)).add(s1.scale(r0)),d0,f1,a.alpha);
            }
        }
    }
    private static void vertex(VertexConsumer out,Matrix4f m,Vec3 camera,Vec3 p,double u,double v,double alpha){p=p.subtract(camera);out.addVertex(m,(float)p.x,(float)p.y,(float)p.z).setUv((float)u,(float)v).setColor(255,255,255,(int)(Math.clamp(alpha,0,1)*255));}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){LIVE.clear();world=null;}
}

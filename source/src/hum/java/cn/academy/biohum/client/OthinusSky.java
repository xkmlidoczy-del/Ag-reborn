package cn.academy.biohum.client;

import cn.academy.biohum.BioHum;
import cn.academy.biohum.OthinusPhase;
import cn.academy.biohum.Payloads;
import cn.academy.biohum.PresentationConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class OthinusSky {
    private static final Vec3 X=new Vec3(1,0,0),Y=new Vec3(0,1,0),Z=new Vec3(0,0,1);
    private static final RenderType TEAR=texture("phase_tear");
    private static final RenderType SPARKS=texture("festival_sparks");
    private static final RenderType MOON=texture("black_moon");
    private static final RenderType BOW=texture("sky_crossbow");
    private static final RenderType SPEAR=SkillRenderType.material(
            ResourceLocation.fromNamespaceAndPath(BioHum.ID,"textures/item/gungnir.png"),false);
    private static ClientLevel world;
    private static Payloads.PhaseState state=Payloads.PhaseState.clear();
    private static int packets;

    private static RenderType texture(String name){
        return SkillRenderType.material(ResourceLocation.fromNamespaceAndPath(BioHum.ID,"textures/phase/"+name+".png"),false);
    }

    static void receive(Payloads.PhaseState next){
        world=Minecraft.getInstance().level;
        state=next;
        packets++;
    }

    public static Payloads.PhaseState current(){
        return world!=null&&world==Minecraft.getInstance().level?state:Payloads.PhaseState.clear();
    }

    public static int packets(){return packets;}

    private static OthinusPhase phase(Vec3 camera){
        var data=current();
        var phase=OthinusPhase.read(data.phase());
        if(phase==null)return null;
        double dx=camera.x-(data.anchor().getX()+.5),dz=camera.z-(data.anchor().getZ()+.5);
        return dx*dx+dz*dz<=(data.radius()+9.0)*(data.radius()+9)?phase:null;
    }

    static void color(ViewportEvent.ComputeFogColor event){
        var phase=phase(event.getCamera().getPosition());
        if(phase==null)return;
        if(phase==OthinusPhase.DARK){
            event.setRed(.012f);
            event.setGreen(.009f);
            event.setBlue(.022f);
        }else if(phase==OthinusPhase.SPACE){
            event.setRed(.018f);
            event.setGreen(.022f);
            event.setBlue(.049f);
        }else if(phase==OthinusPhase.ALPHA){
            event.setRed(event.getRed()*.53f+.22f);
            event.setGreen(event.getGreen()*.45f+.067f);
            event.setBlue(event.getBlue()*.43f+.037f);
        }
    }

    static void fog(ViewportEvent.RenderFog event){
        var phase=phase(event.getCamera().getPosition());
        if(phase==null||!phase.empty())return;
        boolean sky=event.getMode()==FogRenderer.FogMode.FOG_SKY;
        float far=sky?12:phase==OthinusPhase.DARK?40:63;
        event.setNearPlaneDistance(sky?0:12);
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
    }

    static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var camera=event.getCamera().getPosition();
        var phase=phase(camera);
        if(phase==null)return;
        var mc=Minecraft.getInstance();
        float age=mc.level.getGameTime()-state.began()+event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float sweep=Mth.clamp(age/70f,0,1);
        var anchor=Vec3.atBottomCenterOf(state.anchor()).subtract(camera);
        var buffer=mc.renderBuffers().bufferSource();
        var pose=event.getPoseStack().last();
        if(age<90){
            var layer=phase==OthinusPhase.OMEGA?SPARKS:TEAR;
            var v=buffer.getBuffer(layer);
            double radius=Math.max(5,state.radius()*(.06+.94*sweep));
            int segments=8+PresentationConfig.quality().extraLayers*4;
            for(int i=0;i<segments;i++){
                double angle=i*Math.PI*2/segments+age*.009;
                double r=phase==OthinusPhase.BURIAL?Math.min(12,radius):radius;
                var at=anchor.add(Math.cos(angle)*r,3+age*.038,Math.sin(angle)*r);
                var tangent=new Vec3(-Math.sin(angle),0,Math.cos(angle));
                float fade=.83f*(1-Mth.clamp((age-63)/27,0,1));
                switch(phase){
                    case DARK -> SkillFx.fullSprite(v,pose,at.add(0,3,0),tangent,Y,4+sweep*12,5+sweep*13,fade);
                    case ALPHA -> SkillFx.fullSprite(v,pose,at.add(0,6+Math.sin(i*2.3)*5,0),tangent,Y,3+sweep*8,9+sweep*6,fade*.87f);
                    case BETA -> SkillFx.fullSprite(v,pose,at,tangent,Y,9+sweep*11,2.2+sweep*.7,fade*.47f);
                    case BURIAL -> SkillFx.fullSprite(v,pose,at.add(0,-1.4,0),tangent,Y,2.6,2.8,fade*.51f);
                    case SEA -> SkillFx.fullSprite(v,pose,at.add(0,-3,0),tangent,new Vec3(Math.cos(angle),0,Math.sin(angle)),
                            8+sweep*16,5+sweep*4,fade*.48f);
                    case SPACE -> SkillFx.fullSprite(v,pose,at.add(0,18+Math.sin(i)*12,0),tangent,Y,9+sweep*16,8+sweep*6,fade*.62f);
                    case OMEGA -> SkillFx.fullSprite(v,pose,at,tangent,Y,6+sweep*7,4+sweep*3,fade);
                    case DUEL -> SkillFx.fullSprite(v,pose,at,tangent,Y,5+sweep*13,7+sweep*12,fade*.77f);
                    case RETURN -> SkillFx.fullSprite(v,pose,at,tangent,Y,5+sweep*4,3+sweep*5,fade*.52f);
                }
            }
            buffer.endBatch(layer);
        }
        if(phase==OthinusPhase.OMEGA){
            var v=buffer.getBuffer(SPARKS);
            for(int i=0;i<8;i++){
                double turn=i*2.399963;
                var at=anchor.add(Math.cos(turn)*(8+i*2.35),1.3+(i%3)*1.2+Math.sin(age*.075+i)*.65,
                        -7+Math.sin(turn)*(9+i*2.22));
                SkillFx.fullSprite(v,pose,at,X,Y,1.4+i*.11,1.8,.24f+(float)Math.sin(i+age*.05)*.10f);
            }
            buffer.endBatch(SPARKS);
        }
    }

    static boolean drawCue(Payloads.SkillCue cue, net.minecraft.client.renderer.MultiBufferSource buffers,
                           com.mojang.blaze3d.vertex.PoseStack.Pose pose,Vec3 from,Vec3 to,Vec3 right,Vec3 up,
                           float time,float alpha){
        if(cue.skill().equals("othinus_black_moon")&&cue.cue()==Payloads.Cue.MOVE){
            var v=buffers.getBuffer(MOON);
            float drop=Mth.clamp(time*35/34,0,1);
            Vec3 at=from.lerp(to,drop*drop);
            double radius=cue.size()*(.35+.53*Math.min(1,time*2.8));
            SkillFx.fullSprite(v,pose,at,right,up,radius,radius,1f-.20f*time);
            return true;
        }
        if(cue.skill().equals("othinus_sky_crossbow")&&cue.cue()==Payloads.Cue.FIELD){
            var v=buffers.getBuffer(BOW);
            float a=Math.min(1,.25f+time*6)*Math.min(1,(1-time)*3);
            double radius=cue.size()*(.66+.27*Math.min(1,time*3));
            SkillFx.fullSprite(v,pose,to,X,Z,radius,radius,a*.86f);
            SkillFx.fullSprite(v,pose,to.add(0,-8,0),X,Y,radius*.70,radius*.39,a*.55f);
            return true;
        }
        if(cue.skill().equals("othinus_sky_crossbow")&&cue.step()>=0
                &&(cue.cue()==Payloads.Cue.BEAM||cue.cue()==Payloads.Cue.REFLECT)){
            var v=buffers.getBuffer(SkillFx.material(cue.skill(),false));
            boolean rear=cue.step()==9;
            int tone=rear?0xffd7ac:0xcfbcff;
            // The contact was resolved on this tick. The illuminated trail recedes from the rear.
            SkillFx.ribbon(v,pose,from.lerp(to,time*.65),to,rear?.26:.14,1,tone,alpha*.94f);
            SkillFx.ribbon(v,pose,from.lerp(to,time*.81),to,rear?.065:.045,1,0xffffff,alpha);
            SkillFx.fullSprite(buffers.getBuffer(TEAR),pose,to,right,up,rear?.72:.41,.37+.50*time,alpha*.71f);
            return true;
        }
        if(cue.skill().equals("gungnir")&&cue.cue()==Payloads.Cue.MOVE){
            Vec3 direction=to.subtract(from).normalize();
            Vec3 at=from.lerp(to,Math.min(1,.08+time*.92));
            double angle=Math.atan2(direction.dot(up),direction.dot(right))-Math.PI*.25;
            Vec3 x=right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            Vec3 y=up.scale(Math.cos(angle)).subtract(right.scale(Math.sin(angle)));
            double size=1.2+time*.84;
            SkillFx.fullSprite(buffers.getBuffer(SPEAR),pose,at,x,y,size,size,.94f);
            return true;
        }
        return false;
    }

    private OthinusSky(){}
}

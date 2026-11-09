package cn.academy.port.client.city.o5;

import cn.academy.port.city.defense.*;
import cn.academy.port.client.city.GuardianRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Same eighteen-joint source model, metal sheen, traveling filigree and contact-confirmed fists. */
@EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class O5OperativeRenderer extends EntityRenderer<O5OperativeEntity> {
    private final GuardianRenderer fallback;
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(CityDefenseContent.O5.get(),O5OperativeRenderer::new);}
    public O5OperativeRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=.65f;fallback=new GuardianRenderer(context);}
    @Override public ResourceLocation getTextureLocation(O5OperativeEntity entity){return FullBodyRig.BASE;}
    @Override public void render(O5OperativeEntity e,float yaw,float partial,PoseStack ps,MultiBufferSource buffers,int light){
        if(!e.armored()){fallback.render(e,yaw,partial,ps,buffers,light);return;}
        double now=e.level().getGameTime()+partial,age=now-e.actionStart();int move=e.move();
        var frame=FullBodyMotion.sample(Math.max(0,move),move<0?-1:age,-1,100);frame.owner=e.getId();
        boolean active=move>=0&&age>=0&&age<O5Moves.of(move).ticks;
        if(e.descending()){
            frame.lh.set(.18f,1.16f,-.38f);frame.rh.set(-.18f,1.16f,-.38f);frame.chest.x=-.16f;frame.spine.x=.06f;frame.pelvis.x=.08f;frame.lf.set(.14f,.11f,-.08f);frame.rf.set(-.14f,.11f,.08f);frame.energyDrive=.25f;
        }else if(now-e.landTick()>=0&&now-e.landTick()<15){
            float crouch=(float)Math.sin(Math.PI*(now-e.landTick())/15);frame.root.y-=crouch*.22f;frame.chest.x-=crouch*.20f;
        }else if(!active){
            float speed=Mth.clamp(e.walkAnimation.speed(partial),0,1),phase=e.walkAnimation.position(partial)*.67f;
            frame.lf.z+=(float)Math.sin(phase)*.18f*speed;frame.rf.z+=(float)Math.sin(phase+Math.PI)*.18f*speed;
            frame.lf.y+=Math.max(0,(float)Math.cos(phase))*.09f*speed;frame.rf.y+=Math.max(0,(float)Math.cos(phase+Math.PI))*.09f*speed;
        }
        float body=Mth.rotLerp(partial,e.yBodyRotO,e.yBodyRot),head=Mth.wrapDegrees(Mth.rotLerp(partial,e.yHeadRotO,e.yHeadRot)-body),pitch=Mth.lerp(partial,e.xRotO,e.getXRot());
        var eye=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(e.getPosition(partial));double rad=body*Mth.DEG_TO_RAD;float scale=e.getScale()*.9375f;
        frame.viewLocal.set((float)((Math.cos(rad)*eye.x+Math.sin(rad)*eye.z)/scale),(float)(eye.y/scale),(float)((Math.sin(rad)*eye.x-Math.cos(rad)*eye.z)/scale));
        ps.pushPose();ps.mulPose(Axis.YP.rotationDegrees(-body));ps.scale(scale,scale,-scale);
        FullBodyRig.draw(ps,buffers,frame,head*Mth.DEG_TO_RAD,pitch*Mth.DEG_TO_RAD,light,now,active||e.descending(),false,0xFFFFFFFF,false);ps.popPose();super.render(e,yaw,partial,ps,buffers,light);
    }
}

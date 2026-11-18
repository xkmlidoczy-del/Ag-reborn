package cn.academy.port.titan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Hazard boundaries have their own geometry and remain visible at minimal particle settings. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class TitanPatternRenderer {
    private record Flash(TitanPatternPayload packet,long until){}
    private static final Map<Integer,Flash> active=new HashMap<>();
    private static ClientLevel world;
    public static void accept(TitanPatternPayload p) {
        var level=Minecraft.getInstance().level;
        if(world!=level){active.clear();world=level;}
        if(level==null)return;
        if(level.getEntity(p.entity()) instanceof TitanEntity titan)TitanFxClient.pattern(titan,p);
        active.put(p.entity(),new Flash(p,level.getGameTime()+(p.warning()?6:8)));
    }
    private static void line(VertexConsumer out,Matrix4f m,Vec3 camera,Vec3 a,Vec3 b,int color,int alpha) {
        Vec3 d=b.subtract(a).normalize();if(d.lengthSqr()<.5)return;
        for(Vec3 v:new Vec3[]{a,b})out.addVertex(m,(float)(v.x-camera.x),(float)(v.y-camera.y+.12),(float)(v.z-camera.z))
                .setColor((color>>16)&255,(color>>8)&255,color&255,alpha).setNormal((float)d.x,(float)d.y,(float)d.z);
    }
    private static Vec3 radial(Vec3 a,Vec3 forward,double angle,double r) {
        return a.add(forward.yRot((float)Math.toRadians(angle)).scale(r));
    }
    private static void draw(VertexConsumer v,Matrix4f m,Vec3 camera,TitanPattern.Zone z,int color,int alpha) {
        Vec3 a=z.a(),b=z.b(),axis=b.subtract(a).normalize();
        if(z.kind()==TitanPattern.Kind.ARC){
            int n=Math.max(8,(int)Math.ceil(z.angle()/5));
            for(double r:new double[]{z.outer(),z.inner()}){
                if(r<=0)continue;
                Vec3 prev=radial(a,axis,-z.angle(),r);
                for(int i=1;i<=n;i++){Vec3 next=radial(a,axis,-z.angle()+2*z.angle()*i/n,r);line(v,m,camera,prev,next,color,alpha);prev=next;}
            }
            if(z.angle()<180)for(int s:new int[]{-1,1})line(v,m,camera,radial(a,axis,s*z.angle(),z.inner()),radial(a,axis,s*z.angle(),z.outer()),color,alpha);
            return;
        }
        if(z.kind()==TitanPattern.Kind.TRAIL){line(v,m,camera,a,b,color,alpha*2/3);return;}
        Vec3 sideways=new Vec3(-axis.z,0,axis.x).normalize().scale(z.outer());Vec3 left=a.add(sideways),right=a.subtract(sideways);
        line(v,m,camera,left,b.add(sideways),color,alpha);line(v,m,camera,right,b.subtract(sideways),color,alpha);
        line(v,m,camera,left,right,color,alpha);line(v,m,camera,b.add(sideways),b.subtract(sideways),color,alpha);
        if(z.kind()==TitanPattern.Kind.RAY){
            line(v,m,camera,a,b,0xffffff,alpha);
            Vec3 up=axis.cross(sideways).normalize().scale(z.outer());
            line(v,m,camera,a.add(up),b.add(up),color,alpha);line(v,m,camera,a.subtract(up),b.subtract(up),color,alpha);
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();
        if(world!=mc.level){active.clear();world=mc.level;}
        if(world==null)return;
        active.values().removeIf(f->f.until()<world.getGameTime());
        if(active.isEmpty())return;
        var stack=RenderSystem.getModelViewStack();stack.pushMatrix();
        try {
            stack.set(event.getModelViewMatrix());RenderSystem.applyModelViewMatrix();
            var buffers=mc.renderBuffers().bufferSource();var type=RenderType.lines();var out=buffers.getBuffer(type);
            Matrix4f m=event.getPoseStack().last().pose();Vec3 camera=event.getCamera().getPosition();
            for(Flash f:active.values())for(var z:f.packet().zones())draw(out,m,camera,z,f.packet().color(),f.packet().warning()?200:255);
            buffers.endBatch(type);
        } finally {stack.popMatrix();RenderSystem.applyModelViewMatrix();}
    }
    private TitanPatternRenderer(){}
}
